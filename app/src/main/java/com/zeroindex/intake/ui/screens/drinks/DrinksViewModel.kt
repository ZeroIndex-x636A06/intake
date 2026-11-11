package com.zeroindex.intake.ui.screens.drinks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.repository.DrinkRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.FoodSearchRepository
import com.zeroindex.intake.data.repository.OffSearchOutcome
import com.zeroindex.intake.data.repository.OffSearchRepository
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.domain.drinks.DrinkRule
import com.zeroindex.intake.domain.drinks.SearchScope
import com.zeroindex.intake.ui.addfood.rowAllergenOf
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One drink in the list: the food row and the engine's verdict (null when the person avoids nothing). */
data class DrinkRowUi(val food: FoodEntity, val allergen: RowAllergen?)

/** Where a drinks-search result comes from: the same four sources as Log food, in the same order. */
enum class DrinkOrigin { MINE, USDA_OFFLINE, OPEN_FOOD_FACTS, USDA_ONLINE }

/**
 * One result of the drinks search. [food] is the row as it is, or would be, on the phone (a bundled USDA row is shown
 * as the copy it becomes); its nutrition columns are read per 100 ml. [ref] is set for a bundled USDA row.
 */
data class DrinkSearchRow(
    val key: String,
    val food: FoodEntity,
    val origin: DrinkOrigin,
    val allergen: RowAllergen?,
    val ref: RefFood? = null,
)

enum class OffDrinksStatus { HIDDEN, LOADING, DONE, FAILED }

sealed interface OnlineDrinksStatus {
    data object Idle : OnlineDrinksStatus
    data object Loading : OnlineDrinksStatus
    data object Done : OnlineDrinksStatus
    data object Nothing : OnlineDrinksStatus
    data class Failed(val message: String) : OnlineDrinksStatus
}

/** The search under the predefined drinks: every source Log food searches, drinks only. */
data class DrinkSearchUi(
    val query: String = "",
    val mine: List<DrinkSearchRow> = emptyList(),
    val usda: List<DrinkSearchRow> = emptyList(),
    val off: List<DrinkSearchRow> = emptyList(),
    val offStatus: OffDrinksStatus = OffDrinksStatus.HIDDEN,
    val online: List<DrinkSearchRow> = emptyList(),
    val onlineStatus: OnlineDrinksStatus = OnlineDrinksStatus.Idle,
    val hasUsdaKey: Boolean = false,
) {
    val active: Boolean get() = query.isNotBlank()
    val isEmpty: Boolean get() = mine.isEmpty() && usda.isEmpty() && off.isEmpty() && online.isEmpty()
}

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
    /** True when [selected] was chosen from the search results rather than the category list. */
    val selectedFromSearch: Boolean = false,
    val search: DrinkSearchUi = DrinkSearchUi(),
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
    private val foodRepository: FoodRepository,
    private val foodRefRepository: FoodRefRepository,
    private val offSearchRepository: OffSearchRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val category = MutableStateFlow(DrinkCategory.WATER)
    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<String?>(null)
    private val pending = MutableStateFlow<PendingDrink?>(null)

    /** The drink chosen from the search results, already on the phone with its category. Null: the category list's choice. */
    private val picked = MutableStateFlow<FoodEntity?>(null)

    private data class Online(val status: OnlineDrinksStatus = OnlineDrinksStatus.Idle, val foods: List<FoodEntity> = emptyList())
    private val online = MutableStateFlow(Online())

    init {
        // The generic drinks are food rows; make sure they exist before the first list is read.
        viewModelScope.launch { runCatching { drinks.ensureSeeded() } }
    }

    // The predefined list: the chosen category's drinks. The search no longer replaces it; it sits underneath.
    private val list = category.flatMapLatest { c -> drinks.observeDrinks(c) }

    private val rows = combine(list, allergens.observeAvoidedAllergens(), allergens.observeAliases()) { l, _, _ -> l }.mapLatest { foods ->
        if (allergens.avoidedAllergens().isEmpty()) {
            foods.map { DrinkRowUi(it, null) }
        } else {
            val scan = allergens.foodScanner()
            withContext(Dispatchers.Default) { foods.map { f -> DrinkRowUi(f, rowAllergenOf(f, scan(f))) } }
        }
    }

    // ---- The drinks search: the person's own drinks, the offline USDA database, Open Food Facts, the USDA API ----

    private val mineHits = query.debounce(SEARCH_DEBOUNCE_MS).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList()) else drinks.searchDrinks(q)
    }

    private val usdaHits = query.debounce(SEARCH_DEBOUNCE_MS).mapLatest { q ->
        if (q.isBlank()) emptyList() else foodRefRepository.search(q, limit = USDA_LIMIT, scope = SearchScope.DRINKS)
    }

    private data class OffHits(val status: OffDrinksStatus = OffDrinksStatus.HIDDEN, val foods: List<FoodEntity> = emptyList())

    /** Only the words typed are sent, plus the fixed beverages filter; nothing is saved until a result is chosen. */
    private val offHits = query.debounce(OFF_DEBOUNCE_MS).flatMapLatest { q ->
        flow {
            if (q.trim().length < OffSearchRepository.MIN_QUERY) {
                emit(OffHits())
            } else {
                emit(OffHits(OffDrinksStatus.LOADING))
                emit(
                    when (val outcome = offSearchRepository.search(q, SearchScope.DRINKS)) {
                        is OffSearchOutcome.Results -> OffHits(OffDrinksStatus.DONE, outcome.foods)
                        OffSearchOutcome.Unavailable -> OffHits()
                        OffSearchOutcome.Failed -> OffHits(OffDrinksStatus.FAILED)
                    },
                )
            }
        }
    }

    private val search = combine(
        combine(query, mineHits, usdaHits, offHits, online) { q, m, u, o, on -> SearchInput(q, m, u, o, on) },
        allergens.observeAvoidedAllergens(),
        allergens.observeAliases(),
    ) { input, _, _ -> input }.mapLatest { input ->
        val scan = if (allergens.avoidedAllergens().isEmpty()) null else allergens.foodScanner()
        // Sorting out duplicates and scanning every row is text work over up to a hundred rows: off the main thread.
        withContext(Dispatchers.Default) {
            fun row(food: FoodEntity, origin: DrinkOrigin, ref: RefFood? = null) =
                DrinkSearchRow("${origin.name}-${food.id}", food, origin, scan?.let { rowAllergenOf(food, it(food)) }, ref)
            // A result already on the phone is listed once, as the person's own.
            val onlineIds = input.online.foods.map { it.id }.toSet()
            val mine = input.mine.filter { it.id !in onlineIds }
            val mineIds = mine.map { it.id }.toSet()
            val mineBarcodes = mine.mapNotNull { it.barcode }.toSet()
            DrinkSearchUi(
                query = input.query,
                mine = mine.map { row(it, DrinkOrigin.MINE) },
                usda = input.usda.filter { FoodRefRepository.foodId(it.id) !in mineIds }
                    .map { row(FoodRefRepository.toFoodEntity(it), DrinkOrigin.USDA_OFFLINE, it) },
                off = input.off.foods.filter { it.barcode == null || it.barcode !in mineBarcodes }.map { row(it, DrinkOrigin.OPEN_FOOD_FACTS) },
                offStatus = input.off.status,
                online = input.online.foods.map { row(it, DrinkOrigin.USDA_ONLINE) },
                onlineStatus = input.online.status,
                hasUsdaKey = apiKeyStore.usdaKey() != null,
            )
        }
    }

    private data class SearchInput(val query: String, val mine: List<FoodEntity>, val usda: List<RefFood>, val off: OffHits, val online: Online)

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
        search,
        picked,
    ) { (e, presets, settings), q, found, chosen ->
        val (day, entryRows, linked) = e
        DrinksUiState(
            day = day,
            presets = presets,
            volumeUnit = settings.volumeUnit,
            category = q.category,
            drinks = q.rows,
            selected = chosen ?: q.rows.firstOrNull { it.food.id == q.selectedId }?.food ?: q.rows.firstOrNull()?.food,
            selectedFromSearch = chosen != null,
            search = found,
            entries = entryRows,
            sugarG = linked.sumOf { it.sugar },
            kcal = linked.sumOf { it.kcal },
            pending = q.pending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrinksUiState())

    private data class Quad(val category: DrinkCategory, val rows: List<DrinkRowUi>, val selectedId: String?, val pending: PendingDrink?)

    fun selectCategory(c: DrinkCategory) { category.value = c; selectedId.value = null; picked.value = null }

    fun setQuery(q: String) {
        if (q.trim() != query.value.trim()) online.value = Online()
        query.value = q
    }

    fun select(id: String) { selectedId.value = id; picked.value = null }

    /**
     * A search result was tapped. A result from USDA or Open Food Facts is copied to the phone first, with its drink
     * category set, so the size buttons log it through the one drink flow (water and calories together).
     */
    fun choose(row: DrinkSearchRow) {
        viewModelScope.launch {
            val food = when (row.origin) {
                DrinkOrigin.MINE, DrinkOrigin.USDA_ONLINE -> row.food
                DrinkOrigin.USDA_OFFLINE -> row.ref?.let { foodRefRepository.copyDrinkToMyFoods(it) } ?: row.food
                DrinkOrigin.OPEN_FOOD_FACTS -> offSearchRepository.adopt(row.food)
            }
            // A copy made before it was known to be a drink has no category yet: it gets the result's.
            picked.value = if (food.drinkCategory != null) {
                food
            } else {
                foodRepository.save(food.copy(drinkCategory = row.food.drinkCategory ?: DrinkCategory.OTHER.name))
            }
        }
    }

    /** The USDA API, for people who saved a key: asked for explicitly, and only its drinks are listed. */
    fun searchUsdaOnline() {
        val q = query.value.trim()
        if (q.isEmpty()) return
        online.value = Online(OnlineDrinksStatus.Loading)
        viewModelScope.launch {
            online.value = when (val result = foodSearchRepository.searchRemote(q)) {
                is RemoteResult.Success -> {
                    val found = result.value.filter { SearchScope.DRINKS.keeps(DrinkRule.isStoredDrink(it.drinkCategory)) }
                    if (found.isEmpty()) Online(OnlineDrinksStatus.Nothing) else Online(OnlineDrinksStatus.Done, found)
                }
                RemoteResult.MissingKey, RemoteResult.NotFound -> Online(OnlineDrinksStatus.Nothing)
                is RemoteResult.Failed -> Online(OnlineDrinksStatus.Failed(result.message))
            }
        }
    }

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

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val OFF_DEBOUNCE_MS = 600L
        const val USDA_LIMIT = 25
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
