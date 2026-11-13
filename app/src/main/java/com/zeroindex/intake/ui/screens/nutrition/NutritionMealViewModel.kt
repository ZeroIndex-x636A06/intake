package com.zeroindex.intake.ui.screens.nutrition

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.MealItemFacts
import com.zeroindex.intake.domain.nutrition.MealSummary
import com.zeroindex.intake.domain.nutrition.MealTotals
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.nutrition.NutritionDay
import com.zeroindex.intake.domain.nutrition.PortionStep
import com.zeroindex.intake.domain.nutrition.QuickAddField
import com.zeroindex.intake.domain.nutrition.QuickAddLimits
import com.zeroindex.intake.domain.nutrition.Unlisted
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.verdict
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One logged item on the per-meal screen. */
data class MealItemUi(
    val entry: FoodLogEntity,
    val name: String,
    /** "150 g" or the serving label ("1 serving"). */
    val portion: String,
    val kcal: Int,
    /** True when the entry has a gram amount, so − and + can change it. A quick add has none. */
    val canStep: Boolean,
    /** Set only when the local allergen check found something (Contains, May contain, Check the label). */
    val allergen: SwapAllergen? = null,
    val allergenDetail: String? = null,
)

data class MealUi(
    val slot: MealSlot = MealSlot.BREAKFAST,
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val dateLabel: String = "",
    val items: List<MealItemUi> = emptyList(),
    val totals: MealTotals = MealSummary.totals(emptyList()),
    /** What a new entry is stamped with, as a time of day: the entered time, else now (today) or the meal's usual time. */
    val time: LocalTime = LocalTime.NOON,
    val timeEntered: Boolean = false,
    val ready: Boolean = false,
)

/**
 * The per-meal screen: one meal of one day, with what is logged in it and the ways to add more. It reuses the food log
 * repository for every write (soft deletes only, with Undo) and Log food, reached through the nav host, for adding.
 * Nothing here calls a network or a model, and the allergen chip is the local engine's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionMealViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val foodLog: FoodLogRepository,
    private val foods: FoodRepository,
    private val allergens: AllergenRepository,
    private val templates: MealTemplateRepository,
    settings: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Route.NutritionMeal>()

    /** The day this screen shows. Never later than today (see [NutritionDay]). */
    val date: LocalDate = NutritionDay.clamp(LocalDate.ofEpochDay(route.epochDay), clock.today())

    private val slot = MutableStateFlow(MealSlot.MEALS.firstOrNull { it.name == route.slot } ?: MealSlot.BREAKFAST)
    private val enteredTime = MutableStateFlow<LocalTime?>(null)

    private val items = combine(slot, foodLog.observeDay(date), settings.settings) { s, rows, st ->
        Triple(s, rows.filter { it.mealSlot == s }.sortedBy { it.loggedAt }, st.portionUnit)
    }.mapLatest { (s, rows, u) ->
        // One engine setup for the whole meal, not one per item.
        val scanner = runCatching { allergens.foodScanner() }.getOrNull()
        Triple(s, rows.map { itemOf(it, u, scanner) }, rows)
    }

    val state: StateFlow<MealUi> = combine(items, enteredTime) { (s, itemUis, rows), entered ->
        val facts = rows.map {
            MealItemFacts(it.kcal, it.protein, it.carbs, it.fat, Unlisted.decodeMacros(it.unlistedNutrients))
        }
        val defaultTime = NutritionDay.timeOf(NutritionDay.loggingMillis(date, s, clock.nowMillis(), clock.zone()), clock.zone())
        MealUi(
            slot = s,
            date = date,
            dateLabel = NutritionDay.label(date, clock.today()),
            items = itemUis,
            totals = MealSummary.totals(facts),
            time = entered ?: defaultTime,
            timeEntered = entered != null,
            ready = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealUi(slot = slot.value, date = date))

    private suspend fun itemOf(
        e: FoodLogEntity,
        u: PortionUnit,
        scanner: ((FoodEntity) -> AllergenScanResult)?,
    ): MealItemUi {
        val grams = e.quantityGrams
        val scan = runCatching {
            val food = e.foodId?.let { foods.findById(it) }
            if (food != null && scanner != null) scanner(food) else allergens.scan(name = e.foodNameAtLog)
        }.getOrNull()
        val verdict = scan?.verdict()
        return MealItemUi(
            entry = e,
            name = e.foodNameAtLog,
            portion = if (grams != null) u.format(grams) else (e.servingLabelAtLog ?: "1 serving"),
            kcal = e.kcal.roundToInt(),
            canStep = grams != null && grams > 0,
            allergen = verdict?.takeIf { it != SwapAllergen.CLEAR },
            allergenDetail = scan?.chipDetail(),
        )
    }

    fun setSlot(s: MealSlot) { slot.value = s }
    fun setTime(t: LocalTime) { enteredTime.value = t }
    fun resetTime() { enteredTime.value = null }

    /** The stamp a new entry gets, for the route to Log food: the pill's time on this screen's day. */
    fun loggingMillis(): Long {
        val s = state.value
        return NutritionDay.loggingMillis(date, s.slot, clock.nowMillis(), clock.zone(), s.time)
    }

    /** − and +: one portion step. At the smallest amount a − asks to remove instead. */
    fun step(item: MealItemUi, up: Boolean) {
        val grams = item.entry.quantityGrams ?: return
        val next = if (up) PortionStep.up(grams) else PortionStep.down(grams)
        viewModelScope.launch {
            if (next == null) {
                messages.post(AppMessage("That is the smallest portion. Swipe the item to remove it."))
            } else {
                foodLog.updateQuantity(item.entry, next)
            }
        }
    }

    fun updateGrams(item: MealItemUi, grams: Double) { viewModelScope.launch { foodLog.updateQuantity(item.entry, grams) } }
    fun moveTo(item: MealItemUi, to: MealSlot) { viewModelScope.launch { foodLog.moveToSlot(item.entry, to) } }
    fun copyTo(item: MealItemUi, to: MealSlot) { viewModelScope.launch { foodLog.copyToSlot(item.entry, to) } }

    /** Soft delete with Undo. */
    fun remove(item: MealItemUi) = removeAll(listOf(item.entry), "Removed ${item.name}")

    /** "Clear meal": every item of this meal, soft deleted, with one Undo. */
    fun clearMeal() {
        val rows = state.value.items.map { it.entry }
        if (rows.isEmpty()) return
        removeAll(rows, "Cleared ${slotName(state.value.slot)}")
    }

    private fun removeAll(rows: List<FoodLogEntity>, text: String) {
        viewModelScope.launch {
            rows.forEach { foodLog.softDelete(it.id) }
            messages.post(AppMessage(text = text, actionLabel = "Undo", onAction = { foodLog.restore(rows) }))
        }
    }

    /** What the calories dialog accepts, or the reason it does not. Blank name is fine. */
    fun quickAddProblem(kcalText: String): String? {
        val v = kcalText.trim().toDoubleOrNull() ?: return "Type the calories"
        return if (v <= 0.0) "Type the calories" else QuickAddLimits.problem(QuickAddField.KCAL, v)
    }

    /**
     * "Quick add calories": energy only. Protein, carbs and fat are recorded as not listed, never as 0 g, so the totals
     * say "at least" or "not listed" instead of claiming none.
     */
    fun quickAddCalories(name: String, kcalText: String) {
        val kcal = kcalText.trim().toDoubleOrNull() ?: return
        viewModelScope.launch {
            foodLog.logPrepared(
                name = name.trim().ifBlank { "Quick add" },
                macros = Macros(kcal, 0.0, 0.0, 0.0),
                slot = state.value.slot,
                at = loggingMillis(),
                unknownMacros = setOf(CoreMacro.PROTEIN, CoreMacro.CARBS, CoreMacro.FAT),
            )
        }
    }

    /** "Save as meal template": this meal's items under [name]. An empty meal cannot be saved. */
    fun saveAsTemplate(name: String) {
        viewModelScope.launch {
            val r = runCatching { templates.saveSlotAsTemplate(date, state.value.slot, name) }
            messages.post(AppMessage(if (r.isSuccess) "Saved \"${name.trim()}\" as a meal template" else "Log something in this meal first."))
        }
    }

    companion object {
        fun slotName(slot: MealSlot): String = when (slot) {
            MealSlot.BREAKFAST -> "Breakfast"
            MealSlot.LUNCH -> "Lunch"
            MealSlot.DINNER -> "Dinner"
            MealSlot.SNACK -> "Snacks"
            MealSlot.DRINKS -> "Drinks"
        }
    }
}
