package com.zeroindex.intake.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.RecipeLogger
import com.zeroindex.intake.domain.allergen.AllergenOverride
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.recipe.RecipeCandidate
import com.zeroindex.intake.domain.recipe.RecipeSearch
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.traceChipLabel
import com.zeroindex.intake.ui.allergen.verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One recipe row in Log food: its per-serving numbers (or "not listed") and the allergen chip when the person avoids anything. */
data class RecipeRowUi(val recipe: RecipeCandidate, val allergen: RowAllergen?)

data class RecipeSearchUi(
    /** Matches for the words typed, best first. */
    val results: List<RecipeRowUi> = emptyList(),
    /** Recipes logged lately, shown with Recent before anything is typed. */
    val recents: List<RecipeRowUi> = emptyList(),
    /** Every saved recipe, A to Z, for the Mine tab. */
    val all: List<RecipeRowUi> = emptyList(),
    /** False until the first read of the saved recipes: the screen shows nothing, not "no recipes". */
    val loaded: Boolean = false,
)

/** A recipe serving waiting on the allergen sheet's explicit confirmation. */
data class PendingRecipeLog(val recipeId: String, val name: String, val scan: AllergenScanResult, val ingredientText: String?)

/**
 * Saved recipes in Log food (feedback round 4, item 6). Kept apart from [AddFoodViewModel] so the food search is not
 * touched: the screen forwards the typed words here. Everything is on this phone; no network and no model.
 */
@HiltViewModel
class RecipeSearchViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    foodLogDao: FoodLogDao,
    private val allergens: AllergenRepository,
    private val logger: RecipeLogger,
    private val recipeRepo: com.zeroindex.intake.data.repository.RecipeRepository,
    private val estimator: com.zeroindex.intake.data.recipe.RecipeNutritionEstimator,
    private val foodLog: FoodLogRepository,
    private val messages: AppMessages,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _pending = MutableStateFlow<PendingRecipeLog?>(null)
    val pending: StateFlow<PendingRecipeLog?> = _pending

    fun setQuery(text: String) { query.value = text }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<RecipeSearchUi> = combine(
        query.debounce(300),
        recipeDao.observeAll(),
        foodLogDao.observeRecentPrepared(60),
        allergens.observeAvoidedAllergens(),
        allergens.observeAliases(),
    ) { q, recipes, prepared, _, _ -> Triple(q, recipes, prepared) }.mapLatest { (q, recipes, prepared) ->
        val logged = prepared.map { RecipeSearch.LoggedName(it.name, it.at) }
        val candidates = RecipeSearch.withLastLogged(withContext(Dispatchers.Default) { recipes.map { candidateOf(it) } }, logged)
        val byId = recipes.associateBy { it.id }
        val shown = RecipeSearch.rank(q, candidates).take(12)
        val recents = if (q.isBlank()) RecipeSearch.recents(candidates, logged) else emptyList()
        val everything = candidates.sortedBy { it.name.lowercase() }
        val chips = chipsFor((shown + recents + everything).distinctBy { it.id }.mapNotNull { byId[it.id] })
        fun row(c: RecipeCandidate) = RecipeRowUi(c, chips[c.id])
        RecipeSearchUi(shown.map(::row), recents.map(::row), everything.map(::row), loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeSearchUi())

    /** The engine's verdict per recipe, empty when the person avoids nothing. Text work, so off the main thread. */
    private suspend fun chipsFor(recipes: List<RecipeEntity>): Map<String, RowAllergen> {
        if (recipes.isEmpty() || allergens.avoidedAllergens().isEmpty()) return emptyMap()
        return withContext(Dispatchers.Default) {
            recipes.associate { r ->
                r.id to logger.rowAllergen(r)
            }
        }
    }

    private suspend fun scanOf(r: RecipeEntity): AllergenScanResult = logger.scan(r)

    /**
     * The recipe as Log food shows it: its stored per-serving figures, or, for a recipe the page gave none for, the
     * estimate from its ingredient lines (the same one the Recipes list and the recipe page use), marked "~". A recipe
     * that still has no figure says why. Offline and deterministic.
     */
    private suspend fun candidateOf(r: RecipeEntity): RecipeCandidate {
        if (r.kcalPerServing != null) return RecipeCandidate(r.id, r.name, r.kcalPerServing, r.proteinPerServing)
        val lines = estimateLines(r)
        val est = if (lines.isEmpty()) null else runCatching { estimator.estimate(lines, r.servings) }.getOrNull()
        val per = est?.perServing
        return if (per != null) {
            RecipeCandidate(r.id, r.name, per.kcal, r.proteinPerServing ?: per.protein, estimated = true)
        } else {
            RecipeCandidate(
                r.id, r.name, null, r.proteinPerServing,
                whyNoKcal = RecipeCandidate.reasonNoKcal(lines.size, est?.notCountedLines?.map { it.line }.orEmpty()),
            )
        }
    }

    private suspend fun estimateLines(r: RecipeEntity): List<String> =
        recipeRepo.view(r.id)?.ingredients?.map { it.text }.orEmpty()

    /** The estimate to log with, only for a recipe with no stored calories. Null otherwise. */
    private suspend fun estimateTotalsFor(r: RecipeEntity): com.zeroindex.intake.domain.recipe.EstimateTotals? {
        if (r.kcalPerServing != null) return null
        val lines = estimateLines(r)
        if (lines.isEmpty()) return null
        return runCatching { estimator.estimate(lines, r.servings).perServing }.getOrNull()
    }

    /**
     * The "+" on a recipe row: one serving through the one recipe logging path. The allergen scan runs first, and a
     * match stages the log behind the warning sheet. A recipe with no calorie figure is not logged.
     */
    fun requestLog(row: RecipeRowUi, slot: MealSlot, at: Long) = viewModelScope.launch {
        if (!row.recipe.loggable) {
            messages.post(AppMessage(row.recipe.whyNoKcal ?: "${row.recipe.name} has no calories listed. Open it in Recipes to estimate them."))
            return@launch
        }
        val recipe = recipeDao.findById(row.recipe.id) ?: return@launch
        val scan = scanOf(recipe)
        if (scan.hasAnything) {
            _pending.value = PendingRecipeLog(recipe.id, recipe.name, scan, recipe.ingredientsText.takeIf { it.isNotBlank() })
        } else {
            logNow(recipe, slot, at)
        }
    }

    fun confirmPending(slot: MealSlot, at: Long) {
        val p = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val recipe = recipeDao.findById(p.recipeId) ?: return@launch
            logNow(recipe, slot, at, AllergenOverride.noteFor(p.scan))
        }
    }

    fun dismissPending() { _pending.value = null }

    private suspend fun logNow(recipe: RecipeEntity, slot: MealSlot, at: Long, overrideNote: String? = null) {
        val logged = logger.logServings(recipe, slot, estimate = estimateTotalsFor(recipe), servings = 1.0, at = at) ?: return
        val entry = if (overrideNote != null) foodLog.markOverridden(logged, overrideNote) else logged
        messages.post(AppMessage("Logged ${recipe.name} to ${slot.name.lowercase()}", "Undo") { foodLog.softDelete(entry.id) })
    }
}
