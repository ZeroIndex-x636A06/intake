package com.zeroindex.intake.ui.screens.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TemplateItemInput
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.drinks.DrinkRule
import com.zeroindex.intake.domain.drinks.SearchScope
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.recipe.RecipeCandidate
import com.zeroindex.intake.domain.recipe.RecipeSearch
import com.zeroindex.intake.domain.template.MealTemplateRules
import com.zeroindex.intake.domain.template.RecipeFood
import com.zeroindex.intake.domain.template.TemplateLine
import com.zeroindex.intake.domain.units.PortionUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One line in the editor. [amountText] is what the person typed: servings for a recipe, the portion unit for a food. */
data class EditorItem(
    val key: String,
    val foodId: String,
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val proteinListed: Boolean,
    val isRecipe: Boolean,
    val amountText: String,
)

data class SearchHits(
    val foods: List<FoodEntity> = emptyList(),
    val database: List<RefFood> = emptyList(),
    val recipes: List<RecipeCandidate> = emptyList(),
)

data class EditorUi(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val items: List<EditorItem> = emptyList(),
    val unit: PortionUnit = PortionUnit.GRAMS,
    val problem: String? = null,
    val saving: Boolean = false,
)

/**
 * Makes or edits a meal template: a name, then foods and recipes with amounts, found with the same on-phone search Log
 * food uses. A template needs no meal plan. Nothing here is sent anywhere and no model is involved.
 */
@HiltViewModel
class MealTemplateEditorViewModel @Inject constructor(
    private val templates: MealTemplateRepository,
    private val foods: FoodRepository,
    private val foodRef: FoodRefRepository,
    private val recipeDao: RecipeDao,
    settings: SettingsRepository,
) : ViewModel() {

    private val ui = MutableStateFlow(EditorUi())
    private var editingId: String? = null
    private var started = false

    private val unit = settings.settings.map { it.portionUnit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortionUnit.GRAMS)

    val state: StateFlow<EditorUi> = combine(ui, unit) { u, p -> u.copy(unit = p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUi())

    private val query = MutableStateFlow("")
    fun setQuery(text: String) { query.value = text }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val hits: StateFlow<SearchHits> = query.debounce(250).flatMapLatest { q ->
        if (q.isBlank()) {
            flowOf(SearchHits())
        } else {
            combine(
                foods.search(q.trim()).map { list -> list.filter { SearchScope.FOODS.keeps(DrinkRule.isStoredDrink(it.drinkCategory)) } },
                recipeDao.observeAll(),
                flow { emit(runCatching { foodRef.search(q, scope = SearchScope.FOODS) }.getOrDefault(emptyList())) },
            ) { f, recipes, db ->
                SearchHits(
                    foods = f.take(20),
                    database = db.take(15),
                    recipes = RecipeSearch.rank(q, recipes.map { RecipeCandidate(it.id, it.name, it.kcalPerServing, it.proteinPerServing) }).take(15),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchHits())

    /** Loads the template to edit once; a null id starts an empty one. Safe to call again on recomposition. */
    fun start(templateId: String?) {
        if (started) return
        started = true
        editingId = templateId
        if (templateId == null) {
            ui.update { it.copy(loading = false, isNew = true) }
            return
        }
        viewModelScope.launch {
            val found = templates.find(templateId)
            if (found == null) {
                ui.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            val p = unit.value
            ui.value = EditorUi(
                loading = false,
                isNew = false,
                name = found.template.name,
                items = found.items.map { (food, grams) -> itemOf(food, grams, p) },
            )
        }
    }

    private fun isRecipeFood(f: FoodEntity) =
        f.source == FoodSource.RECIPE && f.servingLabel == RecipeFood.SERVING_LABEL && f.servingSizeGrams == RecipeFood.SERVING_GRAMS

    private fun itemOf(food: FoodEntity, grams: Double, p: PortionUnit): EditorItem {
        val line = com.zeroindex.intake.data.repository.templateLineOf(food, grams)
        val recipe = isRecipeFood(food)
        return EditorItem(
            key = newId(),
            foodId = food.id,
            name = food.name,
            kcalPer100g = food.kcalPer100g,
            proteinPer100g = food.proteinPer100g,
            proteinListed = line.proteinListed,
            isRecipe = recipe,
            amountText = if (recipe) RecipeFood.servingsText(grams) else p.number(grams),
        )
    }

    fun setName(text: String) = ui.update { it.copy(name = text.take(MealTemplateRules.MAX_NAME + 20), problem = null) }

    fun setAmount(key: String, text: String) = ui.update { s ->
        s.copy(items = s.items.map { if (it.key == key) it.copy(amountText = text) else it }, problem = null)
    }

    fun remove(key: String) = ui.update { s -> s.copy(items = s.items.filterNot { it.key == key }, problem = null) }

    fun addFood(food: FoodEntity) = ui.update { s ->
        s.copy(items = s.items + itemOf(food, food.servingSizeGrams.coerceAtLeast(1.0), unit.value), problem = null)
    }

    /** A bundled database food is copied into the person's foods first, like Log food does. */
    fun addRef(ref: RefFood) {
        viewModelScope.launch { addFood(foodRef.copyToMyFoods(ref)) }
    }

    /** Adds a recipe at one serving. A recipe with no calorie figure cannot be added: it says so instead. */
    fun addRecipe(id: String) {
        viewModelScope.launch {
            val recipe = recipeDao.findById(id) ?: return@launch
            val food = templates.foodForRecipe(recipe)
            if (food == null) {
                ui.update { it.copy(problem = "${recipe.name} has no calories listed, so it cannot go in a template yet. Open it in Recipes to add or estimate them.") }
            } else {
                addFood(food)
            }
        }
    }

    private fun gramsOf(item: EditorItem, p: PortionUnit): Double? =
        if (item.isRecipe) {
            item.amountText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.let(RecipeFood::gramsForServings)
        } else {
            p.parseToGrams(item.amountText)
        }

    /** The totals line under the list, from what is typed now. Unreadable amounts count as nothing yet. */
    fun linesOf(s: EditorUi): List<TemplateLine> = s.items.map { i ->
        TemplateLine(i.name, gramsOf(i, s.unit) ?: 0.0, i.kcalPer100g, i.proteinPer100g, i.proteinListed)
    }

    fun save(onSaved: () -> Unit) {
        val s = state.value
        val lines = linesOf(s)
        val problem = MealTemplateRules.problem(s.name, lines)
        if (problem != null) {
            ui.update { it.copy(problem = problem) }
            return
        }
        ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val inputs = s.items.mapIndexed { i, item -> TemplateItemInput(item.foodId, lines[i].grams) }
            val id = editingId
            if (id == null) templates.create(s.name, inputs) else templates.update(id, s.name, inputs)
            ui.update { it.copy(saving = false) }
            onSaved()
        }
    }
}
