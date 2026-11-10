package com.zeroindex.intake.ui.screens.drinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.DrinkRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.addfood.RowAllergen
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.verdict
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One drink in the list: the food row and the engine's verdict (null when the person avoids nothing). */
data class DrinkRowUi(val food: FoodEntity, val allergen: RowAllergen?)

/** One entry of today's drinks: the water row, the drink's name and what it added. */
data class DrinkEntryUi(val log: HydrationLogEntity, val name: String, val kcal: Double?)

/** A tap the allergen check stopped, waiting for the red sheet's answer. */
data class PendingDrink(val food: FoodEntity, val ml: Double, val scan: AllergenScanResult)

data class DrinksUiState(
    val day: HydrationDay? = null,
    val presets: List<HydrationPresetEntity> = emptyList(),
    val volumeUnit: VolumeUnit = VolumeUnit.US_FLUID_OUNCES,
    val category: DrinkCategory = DrinkCategory.WATER,
    val drinks: List<DrinkRowUi> = emptyList(),
    val selected: FoodEntity? = null,
    val entries: List<DrinkEntryUi> = emptyList(),
    /** Sugar and calories of today's drinks, from the food-log rows they wrote. */
    val sugarG: Double = 0.0,
    val kcal: Double = 0.0,
    val pending: PendingDrink? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class DrinksViewModel @Inject constructor(
    private val drinks: DrinkRepository,
    private val hydration: HydrationRepository,
    private val foodLog: FoodLogRepository,
    private val allergens: AllergenRepository,
    private val settingsRepository: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val category = MutableStateFlow(DrinkCategory.WATER)
    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow<PendingDrink?>(null)

    init {
        // The generic drinks are food rows; make sure they exist before the first list is read.
        viewModelScope.launch { runCatching { drinks.ensureSeeded() } }
    }

    private val list = combine(category, query.debounce(200)) { c, q -> c to q.trim() }.flatMapLatest { (c, q) ->
        if (q.isEmpty()) drinks.observeDrinks(c) else drinks.searchDrinks(q)
    }

    private val rows = combine(list, allergens.observeAvoidedAllergens(), allergens.observeAliases()) { l, _, _ -> l }.mapLatest { foods ->
        if (allergens.avoidedAllergens().isEmpty()) {
            foods.map { DrinkRowUi(it, null) }
        } else {
            val scan = allergens.foodScanner()
            foods.map { f -> scan(f).let { DrinkRowUi(f, RowAllergen(it.verdict(), it.chipDetail())) } }
        }
    }

    private val entries = combine(
        hydration.observeDay(clock.today()),
        foodLog.observeDay(clock.today()),
    ) { day, foods ->
        val byId = foods.associateBy { it.id }
        val rowsUi = day.logs.sortedByDescending { it.loggedAt }.map { log ->
            val f = log.foodLogId?.let(byId::get)
            DrinkEntryUi(log, f?.foodNameAtLog ?: log.drinkType.label, f?.kcal)
        }
        val linked = day.logs.mapNotNull { it.foodLogId?.let(byId::get) }
        Triple(day, rowsUi, linked)
    }

    val state: StateFlow<DrinksUiState> = combine(
        combine(entries, hydration.observePresets(), settingsRepository.settings) { e, p, s -> Triple(e, p, s) },
        combine(category, rows, selectedId, pending) { c, r, sel, p -> Quad(c, r, sel, p) },
    ) { (e, presets, settings), q ->
        val (day, entryRows, linked) = e
        DrinksUiState(
            day = day,
            presets = presets,
            volumeUnit = settings.volumeUnit,
            category = q.category,
            drinks = q.rows,
            selected = q.rows.firstOrNull { it.food.id == q.selectedId }?.food ?: q.rows.firstOrNull()?.food,
            entries = entryRows,
            sugarG = linked.sumOf { it.sugar },
            kcal = linked.sumOf { it.kcal },
            pending = q.pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrinksUiState())

    private data class Quad(val category: DrinkCategory, val rows: List<DrinkRowUi>, val selectedId: String?, val pending: PendingDrink?)

    fun selectCategory(c: DrinkCategory) { category.value = c; selectedId.value = null }

    fun setQuery(q: String) { query.value = q }

    fun select(id: String) { selectedId.value = id }

    /** One tap on a size: the allergen check first, then the one drink flow (water row and food row together). */
    fun log(food: FoodEntity, ml: Double) {
        viewModelScope.launch {
            val scan = allergens.scan(food)
            if (scan.hasAnything) {
                pending.value = PendingDrink(food, ml, scan)
            } else {
                write(food, ml)
            }
        }
    }

    fun dismissPending() { pending.value = null }

    fun confirmPending() {
        val p = pending.value ?: return
        pending.value = null
        viewModelScope.launch { write(p.food, p.ml) }
    }

    private suspend fun write(food: FoodEntity, ml: Double) {
        val logged = drinks.log(food, ml)
        messages.post(
            AppMessage(
                text = "Added ${settingsRepository.settings.first().volumeUnit.format(ml)} ${food.name}",
                actionLabel = "Undo",
                onAction = { drinks.delete(logged.hydration.id) },
            ),
        )
    }

    /** Soft delete of the entry and its food-log row, with an Undo. */
    fun remove(hydrationId: String) {
        viewModelScope.launch {
            val name = state.value.entries.firstOrNull { it.log.id == hydrationId }?.name ?: "drink"
            drinks.delete(hydrationId)
            messages.post(AppMessage("Removed $name", "Undo") { drinks.restore(hydrationId) })
        }
    }
}
