package com.zeroindex.intake.ui.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.zeroindex.intake.domain.nutrition.Unlisted
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.screens.today.NextMealUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

data class NutritionHomeState(
    /** False until the first read of the day finishes, so the card does not flash an empty day. */
    val ready: Boolean = false,
    val dashboard: DashboardModel? = null,
    val hydration: HydrationDay? = null,
    val settings: AppSettings = AppSettings(),
    val next: NextMealUi? = null,
    val sections: SectionsUi = SectionsUi(),
    /** Today's drinks in a phrase: "water, coffee, tea". */
    val drinkNames: String = "",
)

/**
 * The Nutrition tab's data (IA 5a, Option A): today's whole nutrition, the planned meal with Log it, the hydration line and
 * the counts behind the sections. Today only; the Today tab has the day arrows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionHomeViewModel @Inject constructor(
    foodLog: FoodLogRepository,
    targets: TargetRepository,
    hydration: HydrationRepository,
    private val plans: MealPlanRepository,
    templates: MealTemplateRepository,
    settingsRepository: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val today = clock.today()

    private val dayEntries = foodLog.observeDay(today).map { rows ->
        rows.map { r ->
            DayEntry(
                Macros(r.kcal, r.protein, r.carbs, r.fat, r.fiber, r.sugar, r.sodium, r.saturatedFat),
                Unlisted.decode(r.unlistedNutrients),
            )
        }
    }

    private val dashboard: Flow<DashboardModel?> = combine(dayEntries, targets.observeTargetFor(today), settingsRepository.settings) { entries, target, s ->
        NutritionDashboard.build(DayNutrition.aggregate(entries), target, saltInsteadOfSodium = s.showSodium == SodiumDisplay.SALT)
    }

    private val next = plans.observeCurrentWeekNumber().flatMapLatest { plans.observeWeekContent(it) }.map { week ->
        week?.main?.let { NextMealUi(it, TemplateLink.slotFor(java.time.LocalTime.now(clock.zone()))) }
    }

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

    private val partial = combine(dashboard, hydration.observeDay(today), settingsRepository.settings) { d, h, s -> Triple(d, h, s) }

    val state: StateFlow<NutritionHomeState> = combine(partial, next, sections) { (d, h, s), n, sec ->
        NutritionHomeState(
            ready = true,
            dashboard = d,
            hydration = h,
            settings = s,
            next = n,
            sections = sec,
            drinkNames = h.logs.map { it.drinkType.label.lowercase() }.distinct().take(3).joinToString(", "),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionHomeState())

    /** "Log it": one tap logs the planned meal. An allergen match blocks it, exactly as on Today and the Plan screen. */
    fun logNextMeal(next: NextMealUi) {
        viewModelScope.launch {
            when (val r = plans.logMeal(next.content, next.slot)) {
                is MealLogResult.Blocked -> messages.post(AppMessage("Not logged: ${r.terms.joinToString()} matches your allergen profile."))
                is MealLogResult.Logged -> messages.post(
                    AppMessage(
                        text = "Logged ${next.content.recipe.name} to ${next.slot.name.lowercase()}",
                        actionLabel = "Undo",
                        onAction = { plans.undoLog(r.log) },
                    ),
                )
            }
        }
    }

    /** The meal slot a "Log food" starts in: by the clock, since the tab has no slot of its own. */
    fun currentSlot(): MealSlot = TemplateLink.slotFor(java.time.LocalTime.now(clock.zone()))
}
