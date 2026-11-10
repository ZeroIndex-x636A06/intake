package com.zeroindex.intake.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.recipes.HuntProgress
import com.zeroindex.intake.data.ai.recipes.HuntResult
import com.zeroindex.intake.data.ai.recipes.HuntedRecipe
import com.zeroindex.intake.data.ai.recipes.RecipeHunter
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.RecipeRepository
import com.zeroindex.intake.data.repository.RecipeView
import com.zeroindex.intake.domain.ai.RecipeQuery
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** My recipes, for the Plan hub and recipe detail. */
@HiltViewModel
class RecipeLibraryViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    private val foodLog: FoodLogRepository,
    private val plans: MealPlanRepository,
    private val recipeRepo: RecipeRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** "Add to this week": puts the recipe in the current week of the active plan. */
    fun addToThisWeek(recipe: RecipeEntity) = viewModelScope.launch {
        val where = plans.addToCurrentWeek(recipe)
        _notice.value = if (where != null) "Added to $where." else "Could not add it to a plan."
    }

    val recipes: StateFlow<List<RecipeEntity>> =
        recipeDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One recipe with its ingredients in order, their groups and each line's own allergen tag. Reloads when the recipe changes. */
    fun viewOf(recipeId: String): Flow<RecipeView?> =
        recipes.map { list -> list.firstOrNull { it.id == recipeId } }
            .distinctUntilChanged()
            .map { if (it == null) null else recipeRepo.view(recipeId) }

    fun logServing(recipe: RecipeEntity, slot: MealSlot, onDone: () -> Unit) = viewModelScope.launch {
        foodLog.logPrepared(
            name = recipe.name,
            macros = Macros(
                kcal = recipe.kcalPerServing ?: 0.0,
                protein = recipe.proteinPerServing ?: 0.0,
                carbs = recipe.carbsPerServing ?: 0.0,
                fat = recipe.fatPerServing ?: 0.0,
            ),
            slot = slot,
            servingLabel = "1 serving",
            at = clock.nowMillis(),
        )
        onDone()
    }

    fun delete(recipe: RecipeEntity) = viewModelScope.launch { recipeDao.softDeleteRecipe(recipe.id, clock.nowMillis()) }
}

sealed interface HuntStage {
    data object NeedsKey : HuntStage
    data object Form : HuntStage
    data class Running(val steps: List<HuntProgress>) : HuntStage
    data class Done(val result: HuntResult, val saved: Set<String> = emptySet()) : HuntStage
    data class Failed(val message: String) : HuntStage
}

data class HuntUiState(
    val stage: HuntStage = HuntStage.Form,
    val query: RecipeQuery = RecipeQuery(),
)

@HiltViewModel
class RecipeHunterViewModel @Inject constructor(
    private val hunter: RecipeHunter,
    private val allergens: AllergenRepository,
    gateway: AiGateway,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HuntUiState(stage = if (!gateway.isConfigured()) HuntStage.NeedsKey else HuntStage.Form),
    )
    val state: StateFlow<HuntUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val avoided = allergens.avoidedAllergens()
            _state.update { it.copy(query = it.query.copy(avoidedAllergens = avoided)) }
        }
    }

    fun updateQuery(block: (RecipeQuery) -> RecipeQuery) = _state.update {
        // The avoided allergens are locked: nothing in the form can remove them.
        it.copy(query = block(it.query).copy(avoidedAllergens = it.query.avoidedAllergens))
    }

    fun hunt() {
        val query = _state.value.query
        _state.update { it.copy(stage = HuntStage.Running(emptyList())) }
        viewModelScope.launch {
            runCatching {
                hunter.hunt(query) { progress ->
                    _state.update { s ->
                        val steps = (s.stage as? HuntStage.Running)?.steps.orEmpty()
                        s.copy(stage = HuntStage.Running(steps + progress))
                    }
                }
            }.onSuccess { result -> _state.update { it.copy(stage = HuntStage.Done(result)) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(stage = HuntStage.Failed((e as? AiError)?.message ?: "The search failed. Try again."))
                    }
                }
        }
    }

    fun save(hunted: HuntedRecipe) = viewModelScope.launch {
        hunter.save(hunted)
        _state.update { s ->
            val done = s.stage as? HuntStage.Done ?: return@update s
            s.copy(stage = done.copy(saved = done.saved + hunted.recipe.sourceUrl))
        }
    }

    fun backToForm() = _state.update { it.copy(stage = HuntStage.Form) }
}
