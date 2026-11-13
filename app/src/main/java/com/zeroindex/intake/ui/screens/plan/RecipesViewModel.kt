package com.zeroindex.intake.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.recipes.HuntResult
import com.zeroindex.intake.data.ai.recipes.HuntProgress
import com.zeroindex.intake.data.ai.recipes.HuntedRecipe
import com.zeroindex.intake.data.ai.recipes.RecentHuntsStore
import com.zeroindex.intake.data.ai.recipes.RecipeHunter
import com.zeroindex.intake.domain.ai.SavedHunt
import com.zeroindex.intake.domain.ai.HuntKeys
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.RecipeRepository
import com.zeroindex.intake.data.repository.RecipeView
import com.zeroindex.intake.data.recipe.RecipeNutritionEstimator
import com.zeroindex.intake.domain.recipe.RecipeEstimate
import com.zeroindex.intake.domain.recipe.ReferenceFood
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
    private val recipeLogger: com.zeroindex.intake.data.repository.RecipeLogger,
    private val plans: MealPlanRepository,
    private val recipeRepo: RecipeRepository,
    private val estimator: RecipeNutritionEstimator,
    private val clock: Clock,
) : ViewModel() {
    /** Foods the person picked for ingredient lines of the estimate, by recipe and line. Kept while the screen is open. */
    private val picks = MutableStateFlow<Map<String, Map<Int, ReferenceFood>>>(emptyMap())

    /**
     * The estimate from a recipe's ingredient lines, only for a recipe the page left without some of calories,
     * protein, carbs or fat. Offline and deterministic; it never touches the network or an AI model.
     */
    fun estimateOf(recipeId: String): Flow<RecipeEstimate?> =
        kotlinx.coroutines.flow.combine(viewOf(recipeId), picks) { view, chosen ->
            val r = view?.recipe
            if (view == null || r == null) return@combine null
            val missing = r.kcalPerServing == null || r.proteinPerServing == null || r.carbsPerServing == null || r.fatPerServing == null
            if (!missing || view.ingredients.isEmpty()) {
                null
            } else {
                estimator.estimate(view.ingredients.map { it.text }, r.servings, chosen[recipeId].orEmpty())
            }
        }

    fun pickFood(recipeId: String, lineIndex: Int, food: ReferenceFood) {
        picks.update { all -> all + (recipeId to (all[recipeId].orEmpty() + (lineIndex to food))) }
    }

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

    /**
     * Logs one serving. Numbers the page gave are logged as they are; protein, carbs and fat it did not give come from
     * the estimate when there is one (and the row says so), and otherwise are stored as unlisted, not as 0 g.
     */
    fun logServing(recipe: RecipeEntity, slot: MealSlot, estimate: RecipeEstimate?, onDone: () -> Unit) = viewModelScope.launch {
        if (recipeLogger.logServings(recipe, slot, estimate?.perServing) != null) onDone()
    }

    fun delete(recipe: RecipeEntity) = viewModelScope.launch { recipeDao.softDeleteRecipe(recipe.id, clock.nowMillis()) }
}

sealed interface HuntStage {
    data object NeedsKey : HuntStage
    data object Form : HuntStage
    data class Running(val steps: List<HuntProgress>) : HuntStage
    /** [working] is set while "Find more" runs, [error] when it failed and the earlier results are still shown. */
    data class Done(
        val result: HuntResult,
        /** [HuntKeys.urlKey] of every result already in My recipes, from the start and after each save. */
        val saved: Set<String> = emptySet(),
        val working: HuntProgress? = null,
        val error: String? = null,
        /** The Recent searches entry this result is saved under, so "Find more" updates it. */
        val huntId: String? = null,
    ) : HuntStage
    data class Failed(val message: String) : HuntStage
}

data class HuntUiState(
    val stage: HuntStage = HuntStage.Form,
    val query: RecipeQuery = RecipeQuery(),
    /** Recent searches, newest first, for the bottom of the start screen. */
    val recent: List<SavedHunt> = emptyList(),
)

@HiltViewModel
class RecipeHunterViewModel @Inject constructor(
    private val hunter: RecipeHunter,
    private val allergens: AllergenRepository,
    private val recentStore: RecentHuntsStore,
    private val gateway: AiGateway,
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
        viewModelScope.launch {
            recentStore.recent.collect { list -> _state.update { it.copy(recent = list) } }
        }
    }

    fun updateQuery(block: (RecipeQuery) -> RecipeQuery) = _state.update {
        // The avoided allergens are locked: nothing in the form can remove them.
        it.copy(query = block(it.query).copy(avoidedAllergens = it.query.avoidedAllergens))
    }

    fun hunt() {
        val query = _state.value.query
        _state.update { it.copy(stage = HuntStage.Running(emptyList())) }
        val huntId = "hunt-${hunter.now()}"
        viewModelScope.launch {
            runCatching {
                hunter.hunt(query) { progress ->
                    _state.update { s ->
                        val steps = (s.stage as? HuntStage.Running)?.steps.orEmpty()
                        s.copy(stage = HuntStage.Running(steps + progress))
                    }
                }
            }.onSuccess { result ->
                val saved = runCatching { hunter.savedKeys() }.getOrDefault(emptySet())
                remember(huntId, query, result)
                _state.update { it.copy(stage = HuntStage.Done(result, saved = saved, huntId = huntId)) }
            }
                .onFailure { e ->
                    _state.update {
                        it.copy(stage = HuntStage.Failed((e as? AiError)?.message ?: "The search failed. Try again."))
                    }
                }
        }
    }

    /** Continues the same search with everything already seen excluded. Runs only when the person taps Find more. */
    fun findMore() {
        val done = _state.value.stage as? HuntStage.Done ?: return
        if (done.working != null) return
        val query = _state.value.query
        _state.update { it.copy(stage = done.copy(working = HuntProgress("Looking for more recipes…", 0), error = null)) }
        viewModelScope.launch {
            runCatching {
                hunter.hunt(query, previous = done.result) { progress ->
                    _state.update { s ->
                        val cur = s.stage as? HuntStage.Done ?: return@update s
                        s.copy(stage = cur.copy(working = progress))
                    }
                }
            }.onSuccess { result ->
                val saved = runCatching { hunter.savedKeys() }.getOrDefault(emptySet())
                done.huntId?.let { remember(it, query, result) }
                _state.update { s ->
                    val cur = s.stage as? HuntStage.Done ?: return@update s
                    s.copy(stage = cur.copy(result = result, working = null, saved = cur.saved + saved))
                }
            }.onFailure { e ->
                _state.update { s ->
                    val cur = s.stage as? HuntStage.Done ?: return@update s
                    s.copy(stage = cur.copy(working = null, error = (e as? AiError)?.message ?: "The search failed. Try again."))
                }
            }
        }
    }

    fun save(hunted: HuntedRecipe) = viewModelScope.launch {
        hunter.save(hunted)
        _state.update { s ->
            val done = s.stage as? HuntStage.Done ?: return@update s
            s.copy(stage = done.copy(saved = done.saved + HuntKeys.urlKey(hunted.recipe.sourceUrl)))
        }
    }

    fun backToForm() = _state.update { it.copy(stage = if (gateway.isConfigured()) HuntStage.Form else HuntStage.NeedsKey) }

    /**
     * Writes a finished search to Recent searches (the list keeps the newest 20). A failure to save never costs the person
     * the results on screen. The first save of a search is stamped now; a "Find more" keeps the original time.
     */
    private fun remember(huntId: String, query: RecipeQuery, result: HuntResult) {
        viewModelScope.launch {
            runCatching {
                val now = hunter.now()
                val created = recentStore.find(huntId)?.createdAt ?: now
                recentStore.save(hunter.toSaved(huntId, query, result, created), now)
            }
        }
    }

    /**
     * Reopens a saved search from what was stored: no search, no AI, no page downloads, nothing spent. Every recipe is
     * judged again on this phone against the allergen profile as it is now, and "Saved" is re-read from My recipes.
     */
    fun openRecent(id: String) {
        val saved = _state.value.recent.firstOrNull { it.id == id } ?: return
        if (_state.value.stage is HuntStage.Running) return
        _state.update { it.copy(stage = HuntStage.Running(listOf(HuntProgress("Reopening the saved results…", 0)))) }
        viewModelScope.launch {
            runCatching {
                val (query, result) = hunter.rebuild(saved)
                val keys = runCatching { hunter.savedKeys() }.getOrDefault(emptySet())
                query to HuntStage.Done(result, saved = keys, huntId = saved.id)
            }.onSuccess { (query, done) ->
                _state.update { it.copy(query = query, stage = done) }
            }.onFailure {
                _state.update { it.copy(stage = HuntStage.Form) }
            }
        }
    }

    /** Puts a saved search's parameters in the form and runs it again. Spends money, so the screen asks with a visible button. */
    fun runAgain(id: String) {
        val saved = _state.value.recent.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(query = saved.query.toQuery(it.query.avoidedAllergens)) }
        hunt()
    }

    fun deleteRecent(id: String) {
        viewModelScope.launch { runCatching { recentStore.softDelete(id, hunter.now()) } }
    }
}
