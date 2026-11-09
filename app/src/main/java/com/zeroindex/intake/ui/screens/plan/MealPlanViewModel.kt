package com.zeroindex.intake.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.plan.PlanImportBatch
import com.zeroindex.intake.data.repository.MealLogResult
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.PlanImportPreview
import com.zeroindex.intake.data.repository.RecipeContent
import com.zeroindex.intake.data.repository.WeekContent
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.plan.EatOutFilter
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanPosition
import com.zeroindex.intake.domain.plan.PlanSchedule
import com.zeroindex.intake.domain.model.PlanLoopMode
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.domain.plan.ShoppingListBuilder
import com.zeroindex.intake.domain.plan.ShoppingSections
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.data.plan.PlanImportBuilder
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row in the "Can't eat what I cooked?" sheet. */
data class EatOutRow(
    val option: EatOutOptionEntity,
    val match: EatOutFilter.Match,
    /** Worse of the imported verdict and a fresh local scan. */
    val status: AllergenStatus,
    val hasEstimate: Boolean,
)

/** A log waiting on the user because macros are missing or partial. */
sealed interface PendingLog {
    data class Meal(val content: RecipeContent) : PendingLog
    data class EatOut(val option: EatOutOptionEntity) : PendingLog
}

data class ImportUiState(
    val reading: Boolean = false,
    val preview: PlanImportPreview? = null,
)

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    private val repo: MealPlanRepository,
    private val clock: Clock,
    private val messages: AppMessages,
    settings: SettingsRepository,
) : ViewModel() {

    /** The week the calendar says it is (from the plan's start date and repeat setting). */
    val currentWeek: StateFlow<Int> =
        repo.observeCurrentWeekNumber().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    val plans: StateFlow<List<MealPlanEntity>> =
        repo.observePlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activePlan: StateFlow<MealPlanEntity?> =
        repo.observeActivePlan().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val position: StateFlow<PlanPosition?> =
        repo.observePosition().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val length: StateFlow<Int> =
        repo.observePlanLength().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** The week on screen. Null follows the current week; picking a week to look at or edit does not move the schedule. */
    private val viewed = MutableStateFlow<Int?>(null)
    val viewedWeek: StateFlow<Int> =
        combine(viewed, currentWeek, length) { v, c, n -> (v ?: c).coerceIn(1, n.coerceAtLeast(1)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    init {
        // The week can change by itself as days pass: keep the marker current and reset shopping checks (SPEC §9).
        viewModelScope.launch { repo.observePosition().collect { repo.syncWeekMarker(it) } }
        viewModelScope.launch { repo.observeActivePlan().map { it?.id }.distinctUntilChanged().collect { viewed.value = null } }
    }

    val plannedWeeks: StateFlow<Set<Int>> =
        repo.observePlannedWeekNumbers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val meta: StateFlow<PlanMeta> =
        repo.observeMeta().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanMeta())

    val recipes: StateFlow<List<RecipeEntity>> =
        repo.observeRecipes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weightUnit: StateFlow<WeightUnit> =
        settings.settings.map { it.weightUnit }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.POUNDS)

    @OptIn(ExperimentalCoroutinesApi::class)
    /** Soft "Check the label" terms in force, for ingredient and shopping rows. */
    val checkLabelTerms: StateFlow<List<com.zeroindex.intake.domain.allergen.CheckLabelTerm>> =
        repo.observeCheckLabelTerms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val content: StateFlow<WeekContent?> =
        viewedWeek.flatMapLatest { repo.observeWeekContent(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val shopping: StateFlow<ShoppingSections> =
        content.map { it?.week?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) {
                flowOf(ShoppingSections(emptyList(), emptyList()))
            } else {
                repo.ensureShoppingList(id)
                repo.observeShopping(id).map { ShoppingListBuilder.group(it) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingSections(emptyList(), emptyList()))

    private val showAllEatOut = MutableStateFlow(false)
    val showingAllEatOut: StateFlow<Boolean> = showAllEatOut.asStateFlow()

    /** Every option on file for the "Show all options" count, regardless of filter. */
    val eatOut: StateFlow<List<EatOutRow>> =
        combine(repo.observeEatOutOptions(), content, showAllEatOut) { options, c, all -> Triple(options, c, all) }
            .map { (options, c, all) ->
                val scanner = repo.scanner()
                EatOutFilter.forWeek(options, c?.week?.id, c?.week?.proteinType, includeAll = all).map { r ->
                    val live = PlanImportBuilder.statusOf(scanner.scan(r.option.whatToOrder, null))
                    val stored = r.option.allergenStatus
                    val status = if (PlanImportBuilder.rank(stored) > PlanImportBuilder.rank(live)) stored!! else live
                    EatOutRow(r.option, r.match, status, hasEstimate = r.option.estimatedKcal > 0)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hiddenEatOutCount: StateFlow<Int> =
        combine(repo.observeEatOutOptions(), content) { options, c ->
            options.size - EatOutFilter.forWeek(options, c?.week?.id, c?.week?.proteinType).size
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _pending = MutableStateFlow<PendingLog?>(null)
    val pending: StateFlow<PendingLog?> = _pending.asStateFlow()

    private val _import = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _import.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    /** Look at (and edit) a week without changing which week is current. */
    fun selectWeek(n: Int) { viewed.value = n }

    /** Makes the week on screen the current one, by moving the plan's start date. */
    fun jumpToViewedWeek() = viewModelScope.launch {
        repo.setCurrentWeek(viewedWeek.value)
        viewed.value = null
    }

    fun today() = clock.today()

    // ---- plans and weeks ---------------------------------------------------------------------

    fun createPlan(name: String, weeks: Int, mode: PlanLoopMode, count: Int, startDay: Long) = viewModelScope.launch {
        val p = repo.createPlan(name, weeks, mode, count, startDay)
        _notice.value = "Created ${p.name} with ${weeks.coerceIn(1, PlanSchedule.MAX_WEEKS)} weeks."
    }

    fun updatePlan(plan: MealPlanEntity, name: String, mode: PlanLoopMode, count: Int, startDay: Long) = viewModelScope.launch {
        repo.updatePlan(plan.id, name, plan.notes, startDay, mode, count)
    }

    fun switchPlan(plan: MealPlanEntity) = viewModelScope.launch {
        repo.setActive(plan.id)
        _notice.value = "Now using ${plan.name}."
    }

    fun archivePlan(plan: MealPlanEntity) = viewModelScope.launch {
        repo.archivePlan(plan.id)
        _notice.value = "Archived ${plan.name}. Nothing was erased."
    }

    fun addWeek() = viewModelScope.launch {
        val n = repo.addWeek()
        if (n == null) _notice.value = "A plan can have up to ${PlanSchedule.MAX_WEEKS} weeks." else viewed.value = n
    }

    fun duplicateWeek(n: Int) = viewModelScope.launch {
        val copy = repo.duplicateWeek(n)
        if (copy == null) _notice.value = "A plan can have up to ${PlanSchedule.MAX_WEEKS} weeks." else viewed.value = copy
    }

    fun removeWeek(n: Int) = viewModelScope.launch {
        if (!repo.removeWeek(n)) _notice.value = "A plan needs at least one week." else viewed.value = null
    }

    fun moveWeek(n: Int, delta: Int) = viewModelScope.launch {
        if (repo.moveWeek(n, delta)) viewed.value = n + delta
    }

    fun saveWeek(n: Int, dish: String, protein: String, note: String, variations: List<String>) =
        viewModelScope.launch { repo.saveWeekDetails(n, dish, protein, note, variations) }

    fun toggleShowAllEatOut() = showAllEatOut.update { !it }

    // ---- assigning ---------------------------------------------------------------------------

    fun assign(weekNumber: Int, recipe: RecipeEntity, protein: String) =
        viewModelScope.launch {
            if (!repo.assignRecipe(weekNumber, recipe, protein)) _notice.value = "Make a plan first, then choose recipes for its weeks."
        }

    fun setProtein(weekNumber: Int, protein: String) = viewModelScope.launch { repo.updateProtein(weekNumber, protein) }

    fun clearWeek(weekNumber: Int) = viewModelScope.launch { repo.clearWeek(weekNumber) }

    // ---- shopping ----------------------------------------------------------------------------

    fun setChecked(id: String, checked: Boolean) = viewModelScope.launch { repo.setChecked(id, checked) }
    fun removeItem(id: String) = viewModelScope.launch { repo.removeItem(id) }
    fun addItem(name: String, quantity: String, category: StoreCategory) {
        val weekId = content.value?.week?.id ?: return
        viewModelScope.launch { repo.addManualItem(weekId, name, quantity, category) }
    }

    fun shareText(): String? {
        val c = content.value ?: return null
        return ShoppingListBuilder.shareText("Week ${c.week.weekNumber} · ${c.week.mainDishName}", shopping.value, weightUnit.value)
    }

    // ---- logging -----------------------------------------------------------------------------

    private fun slotNow() = TemplateLink.slotFor(LocalTime.now(clock.zone()))

    /** One tap: logs complete macros straight away, otherwise asks first so nothing is silently undercounted. */
    fun logMeal() {
        val main = content.value?.main ?: return
        if (main.macros.isComplete) doLogMeal(main, null) else _pending.value = PendingLog.Meal(main)
    }

    fun logEatOut(option: EatOutOptionEntity) {
        if (option.estimatedKcal > 0) doLogEatOut(option, null) else _pending.value = PendingLog.EatOut(option)
    }

    fun dismissPending() { _pending.value = null }

    fun confirmPending(macros: Macros) {
        val p = _pending.value ?: return
        _pending.value = null
        when (p) {
            is PendingLog.Meal -> doLogMeal(p.content, macros)
            is PendingLog.EatOut -> doLogEatOut(p.option, macros)
        }
    }

    private fun doLogMeal(content: RecipeContent, override: Macros?) = viewModelScope.launch {
        when (val r = repo.logMeal(content, slotNow(), override)) {
            is MealLogResult.Blocked -> _notice.value =
                "Not logged: ${r.terms.joinToString()} matches your allergen profile."
            is MealLogResult.Logged -> messages.post(
                AppMessage(
                    text = "Logged ${content.recipe.name} to ${slotNow().name.lowercase()}" +
                        if (r.status == AllergenStatus.MAY_CONTAIN) " · check the labels for allergens" else "",
                    actionLabel = "Undo",
                    onAction = { repo.undoLog(r.log) },
                ),
            )
        }
    }

    private fun doLogEatOut(option: EatOutOptionEntity, override: Macros?) = viewModelScope.launch {
        val log = repo.logEatOut(option, slotNow(), override)
        messages.post(
            AppMessage(
                text = "Logged ${option.venueName} to ${slotNow().name.lowercase()}",
                actionLabel = "Undo",
                onAction = { repo.undoLog(log) },
            ),
        )
    }

    // ---- import ------------------------------------------------------------------------------

    fun startReading() = _import.update { ImportUiState(reading = true) }

    fun previewImport(text: String) = viewModelScope.launch {
        _import.value = ImportUiState(preview = repo.previewImport(text))
    }

    fun importFailed(message: String) {
        _import.value = ImportUiState(preview = PlanImportPreview.Invalid(listOf(message)))
    }

    fun confirmImport(batch: PlanImportBatch) = viewModelScope.launch {
        repo.applyImport(batch)
        _import.value = ImportUiState()
        val name = batch.plan?.name ?: "your plan"
        _notice.value = (if (batch.isUpdate) "Updated $name: " else "Created $name: ") +
            "${batch.weeks.size} weeks and ${batch.recipes.size} recipes. It is now the active plan."
    }

    fun cancelImport() { _import.value = ImportUiState() }

    /** The current plan as script text, for the "Export plan as script" action. */
    suspend fun exportScript(): String = repo.exportPlanScript()

    fun postNotice(text: String) { _notice.value = text }
}
