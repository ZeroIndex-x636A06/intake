package com.zeroindex.intake.ui.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.MealLogResult
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.DayEntry
import com.zeroindex.intake.domain.nutrition.DayNutrition
import com.zeroindex.intake.domain.nutrition.DashboardModel
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.nutrition.NutritionDashboard
import com.zeroindex.intake.domain.nutrition.NutritionDay
import com.zeroindex.intake.domain.nutrition.Unlisted
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.screens.today.NextMealUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What the section switch (Plan, Recipes, Meals, Eating out) shows. */
data class SectionsUi(
    val planName: String? = null,
    /** "Week 3 of 12", or null with no plan. */
    val weekLine: String? = null,
    val weekDish: String? = null,
    val recipeCount: Int = 0,
    val recipeNames: List<String> = emptyList(),
    val templateCount: Int = 0,
    val templateNames: List<String> = emptyList(),
    val eatOutCount: Int = 0,
)

/** One meal row on the Nutrition tab: what was logged in the slot today. Empty [foods] means nothing logged. */
data class SlotRowUi(
    val slot: MealSlot,
    val foods: List<String> = emptyList(),
    val kcal: Int = 0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
) {
    val logged: Boolean get() = foods.isNotEmpty()
}

/**
 * The four meal rows of the Nutrition tab's meals card. [MealSlot.DRINKS] is deliberately left out: drinks have no row here
 * (they live on the water card and in Log food). Their kcal and sugar still count in the day totals, which are built from
 * every row, not from these rows.
 */
internal fun nutritionSlotRows(rows: List<FoodLogEntity>): List<SlotRowUi> = MealSlot.MEALS.map { slot ->
    val mine = rows.filter { it.mealSlot == slot }.sortedBy { it.loggedAt }
    SlotRowUi(
        slot = slot,
        foods = mine.map { it.foodNameAtLog },
        kcal = mine.sumOf { it.kcal }.roundToInt(),
        protein = mine.sumOf { it.protein },
        carbs = mine.sumOf { it.carbs },
        fat = mine.sumOf { it.fat },
    )
}

data class NutritionHomeState(
    /** False until the first read of the day finishes, so the card does not flash an empty day. */
    val ready: Boolean = false,
    /** The day every card shows, and today (so the screen can say "Today" and stop the next arrow). */
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val dashboard: DashboardModel? = null,
    val hydration: HydrationDay? = null,
    val settings: AppSettings = AppSettings(),
    val next: NextMealUi? = null,
    /** Breakfast, lunch, dinner and snack, in that order, from the chosen day's log. */
    val slots: List<SlotRowUi> = MealSlot.MEALS.map { SlotRowUi(it) },
    val sections: SectionsUi = SectionsUi(),
    /** The chosen day's drinks in a phrase: "water, coffee, tea". */
    val drinkNames: String = "",
) {
    val isToday: Boolean get() = date == today
    val canGoNext: Boolean get() = NutritionDay.canGoNext(date, today)
    val dateLabel: String get() = NutritionDay.label(date, today)
}

/**
 * The Nutrition tab's data (IA 5a, Option A): one day's whole nutrition (today until the day switcher moves it), the planned
 * meal with Log it, the hydration line and the counts behind the sections.
 *
 * Every card follows [selectedDate]. The date maths (steps, the picker, the day boundary, the logging time) is
 * [NutritionDay], tested on its own. Future days are blocked: see its doc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionHomeViewModel @Inject constructor(
    private val foodLog: FoodLogRepository,
    private val targets: TargetRepository,
    private val hydration: HydrationRepository,
    private val plans: MealPlanRepository,
    templates: MealTemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(clock.today())

    /** Moves the tab to [date], never past today. Blocked future days are explained once, in a short message. */
    fun showDate(date: LocalDate) {
        val today = clock.today()
        if (NutritionDay.isFuture(date, today)) messages.post(AppMessage(NutritionDay.FUTURE_NOTE))
        selectedDate.value = NutritionDay.clamp(date, today)
    }

    fun showPreviousDay() { selectedDate.value = NutritionDay.shift(selectedDate.value, -1, clock.today()) }
    fun showNextDay() { selectedDate.value = NutritionDay.shift(selectedDate.value, 1, clock.today()) }
    fun showToday() { selectedDate.value = clock.today() }

    /** The date picker's answer (the UTC midnight of the chosen calendar date). */
    fun showPickerDate(utcMillis: Long) { showDate(NutritionDay.fromPickerMillis(utcMillis, clock.today())) }

    private val dayEntries = selectedDate.flatMapLatest { foodLog.observeDay(it) }.map { rows ->
        rows.map { r ->
            DayEntry(
                Macros(r.kcal, r.protein, r.carbs, r.fat, r.fiber, r.sugar, r.sodium, r.saturatedFat),
                Unlisted.decode(r.unlistedNutrients),
            )
        }
    }

    private val slotRows = selectedDate.flatMapLatest { foodLog.observeDay(it) }.map { nutritionSlotRows(it) }

    private val dashboard: Flow<DashboardModel?> = combine(
        dayEntries,
        selectedDate.flatMapLatest { targets.observeTargetFor(it) },
        settingsRepository.settings,
    ) { entries, target, s ->
        NutritionDashboard.build(DayNutrition.aggregate(entries), target, saltInsteadOfSodium = s.showSodium == SodiumDisplay.SALT)
    }

    /** The planned meal for the chosen day: the plan week that day falls in. */
    private val next = selectedDate
        .flatMapLatest { plans.observePositionOn(it) }
        .map { it?.week ?: 1 }
        .distinctUntilChanged()
        .flatMapLatest { plans.observeWeekContent(it) }
        .map { week -> week?.main?.let { NextMealUi(it, TemplateLink.slotFor(java.time.LocalTime.now(clock.zone()))) } }

    private val sections = combine(
        plans.observeActivePlan(),
        plans.observePosition(),
        plans.observeCurrentWeekNumber().flatMapLatest { plans.observeWeekContent(it) },
        combine(plans.observeRecipes(), templates.observeSummaries(), plans.observeEatOutOptions()) { r, t, e -> Triple(r, t, e) },
    ) { plan, pos, week, (recipes, tmpl, eatOut) ->
        SectionsUi(
            planName = plan?.name,
            weekLine = if (plan != null && pos != null) "Week ${pos.week} of ${pos.length}" else null,
            weekDish = week?.main?.recipe?.name,
            recipeCount = recipes.size,
            recipeNames = recipes.take(3).map { it.name },
            templateCount = tmpl.size,
            templateNames = tmpl.take(3).map { it.template.name },
            eatOutCount = eatOut.size,
        )
    }

    private val partial = combine(dashboard, selectedDate.flatMapLatest { hydration.observeDay(it) }, settingsRepository.settings) { d, h, s -> Triple(d, h, s) }

    val state: StateFlow<NutritionHomeState> = combine(combine(partial, selectedDate) { p, date -> p to date }, next, sections, slotRows) { (pd, date), n, sec, slots ->
        val (d, h, s) = pd
        NutritionHomeState(
            ready = true,
            date = date,
            today = clock.today(),
            dashboard = d,
            hydration = h,
            settings = s,
            next = n,
            slots = slots,
            sections = sec,
            drinkNames = h.logs.map { it.drinkType.label.lowercase() }.distinct().take(3).joinToString(", "),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionHomeState())

    /** "Log it": one tap logs the planned meal onto the chosen day. An allergen match blocks it, exactly as on Today and the Plan screen. */
    fun logNextMeal(next: NextMealUi) {
        viewModelScope.launch {
            val at = loggingMillis(next.slot)
            when (val r = plans.logMeal(next.content, next.slot, at = at)) {
                is MealLogResult.Blocked -> messages.post(AppMessage("Not logged: ${r.terms.joinToString()} matches your allergen profile."))
                is MealLogResult.Logged -> messages.post(
                    AppMessage(
                        text = "Logged ${next.content.recipe.name} to ${next.slot.name.lowercase()}" + if (selectedDate.value == clock.today()) "" else " on ${NutritionDay.label(selectedDate.value, clock.today())}",
                        actionLabel = "Undo",
                        onAction = { plans.undoLog(r.log) },
                    ),
                )
            }
        }
    }

    /** The meal slot a "Log food" starts in: by the clock, since the tab has no slot of its own. */
    fun currentSlot(): MealSlot = TemplateLink.slotFor(java.time.LocalTime.now(clock.zone()))

    /** When an entry logged from this tab is stamped: now for today, otherwise the meal's usual time on the chosen day. */
    fun loggingMillis(slot: MealSlot): Long = NutritionDay.loggingMillis(selectedDate.value, slot, clock.nowMillis(), clock.zone())

    /** The chosen day as an epoch day, for routes that carry it. */
    fun selectedEpochDay(): Long = selectedDate.value.toEpochDay()
}
