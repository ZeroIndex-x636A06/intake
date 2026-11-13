package com.zeroindex.intake.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.data.foodref.RefPortion
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.repository.OffSearchOutcome
import com.zeroindex.intake.data.repository.OffSearchRepository
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.domain.barcode.Barcodes
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.DrinkRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.allergen.chipDetail
import com.zeroindex.intake.ui.allergen.verdict
import com.zeroindex.intake.data.repository.BarcodeRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.FoodSearchRepository
import com.zeroindex.intake.data.repository.MenuHit
import com.zeroindex.intake.data.repository.MenuLogOutcome
import com.zeroindex.intake.data.repository.MenuLogRepository
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.data.repository.ScannedProduct
import com.zeroindex.intake.domain.allergen.AllergenEvidence
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.drinks.DrinkRule
import com.zeroindex.intake.domain.drinks.SearchScope
import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.nutrition.NutrientKey
import com.zeroindex.intake.domain.nutrition.QuickAddLimits
import com.zeroindex.intake.domain.nutrition.Unlisted
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The modes of the Log food screen: Search, Scan and Photo are the three equal primary modes (R3-4), Mine replaces
 * "My foods", and Quick add is the by-hand entry. Recents are the Search mode's list before anything is typed.
 */
enum class AddFoodTab(val label: String) {
    SEARCH("Search"),
    BARCODE("Scan"),
    PHOTO("Photo"),
    MY_FOODS("Mine"),
    QUICK_ADD("Quick add"),
    ;

    companion object {
        /** Reads a route argument; anything unknown opens Search. */
        fun fromName(name: String?): AddFoodTab = entries.firstOrNull { it.name == name } ?: SEARCH
    }
}

/**
 * One result row's allergen chip: the local engine's verdict and the words it matched ("milk and peanuts").
 * [nameOnly] marks a clear result on a packaged product that carries no ingredient list and no allergen tags: the
 * chip then says so instead of "No allergens" (`AllergenEvidence`).
 */
data class RowAllergen(val verdict: SwapAllergen, val detail: String?, val nameOnly: Boolean = false)

/** The chip for a food row, from the engine's scan of everything the row carries. */
fun rowAllergenOf(food: FoodEntity, scan: AllergenScanResult): RowAllergen {
    val verdict = scan.verdict()
    return RowAllergen(
        verdict = verdict,
        detail = scan.chipDetail(),
        nameOnly = verdict == SwapAllergen.CLEAR && AllergenEvidence.isNameOnlyProduct(food.source, food.isVerified, food.allergenTags),
    )
}

/** One imported restaurant-menu row in search: the item, and its allergen chip when the person avoids anything. */
data class MenuRow(val hit: MenuHit, val allergen: RowAllergen?)

/** A save waiting on the allergen sheet's explicit confirmation (SPEC §7.2). */
data class PendingLog(
    val food: FoodEntity?,
    val name: String,
    val grams: Double?,
    val macros: Macros?,
    val scan: AllergenScanResult,
)

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Remote(val results: List<FoodEntity>) : SearchState
    data object NeedsKey : SearchState
    data object NoResults : SearchState
    data class Failed(val message: String) : SearchState
}

/** The "Packaged foods (Open Food Facts)" section of search. */
sealed interface OffUiState {
    /** Not shown: switched off in settings, offline, or the query is too short. */
    data object Hidden : OffUiState
    data object Loading : OffUiState
    /** [drinksLeftOut]: how many of the answers were drinks, which belong to the Drinks search. */
    data class Results(val foods: List<FoodEntity>, val drinksLeftOut: Int = 0) : OffUiState
    data object Failed : OffUiState
}

sealed interface BarcodeState {
    data object Idle : BarcodeState
    data object Scanning : BarcodeState
    data object LookingUp : BarcodeState
    data class Found(val food: FoodEntity, val scan: AllergenScanResult, val fromCache: Boolean) :
        BarcodeState
    /** Open Food Facts answered, and has no usable record: offer "Add this product". */
    data class Unknown(val barcode: String, val reason: String?) : BarcodeState

    /** The lookup itself failed (offline, server error): offer retry as well as adding by hand. */
    data class LookupFailed(val barcode: String, val message: String) : BarcodeState
}

/**
 * Where a lookup result goes. Pure, so the routing is tested without a camera: a
 * product is shown, a missing one offers "Add this product" with the barcode kept,
 * and a failed lookup offers a retry.
 */
internal fun routeBarcodeResult(barcode: String, result: RemoteResult<ScannedProduct>): BarcodeState =
    when (result) {
        is RemoteResult.Success -> BarcodeState.Found(
            food = result.value.food,
            scan = result.value.scan,
            fromCache = result.value.fromCache,
        )
        RemoteResult.NotFound, RemoteResult.MissingKey -> BarcodeState.Unknown(barcode, null)
        is RemoteResult.Failed -> BarcodeState.LookupFailed(barcode, result.message)
    }

data class AddFoodUiState(
    val tab: AddFoodTab = AddFoodTab.SEARCH,
    /** Portions are shown and typed in this unit (Settings > Appearance and units). */
    val portionUnit: PortionUnit = PortionUnit.GRAMS,
    /** The engine's verdict per food id, for the rows on screen. Empty when the person avoids nothing. */
    val rowAllergens: Map<String, RowAllergen> = emptyMap(),
    /** The same for the bundled USDA rows, keyed by `RefFood.id`. */
    val refAllergens: Map<Int, RowAllergen> = emptyMap(),
    val query: String = "",
    val recents: List<FoodEntity> = emptyList(),
    val localResults: List<FoodEntity> = emptyList(),
    val myFoods: List<FoodEntity> = emptyList(),
    val favorites: List<FoodEntity> = emptyList(),
    val safeFoodIds: Set<String> = emptySet(),
    val searchState: SearchState = SearchState.Idle,
    val barcodeState: BarcodeState = BarcodeState.Idle,
    val pendingLog: PendingLog? = null,
    val hasUsdaKey: Boolean = false,
    /** The offline USDA database matches, ranked (docs/FOOD-DATA.md). */
    val refResults: List<RefFood> = emptyList(),
    val offState: OffUiState = OffUiState.Hidden,
    /** False once the debounce has settled and every local source has answered. */
    val offSearchEnabled: Boolean = true,
    /** Soft "Check the label" terms in force, for list rows and the portion step. */
    val checkLabelTerms: List<CheckLabelTerm> = emptyList(),
    /** True when the words typed match drinks (on this phone, in the offline database or in Open Food Facts). */
    val queryMatchesDrinks: Boolean = false,
    /** Imported restaurant menu items matching the query (B1), scanned on this phone. */
    val menuRows: List<MenuRow> = emptyList(),
)

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val foodLogRepository: FoodLogRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val barcodeRepository: BarcodeRepository,
    private val allergenRepository: AllergenRepository,
    private val apiKeyStore: ApiKeyStore,
    private val foodRefRepository: FoodRefRepository,
    private val offSearchRepository: OffSearchRepository,
    private val drinkRepository: DrinkRepository,
    private val menuLogRepository: MenuLogRepository,
    private val messages: AppMessages,
    private val settingsRepository: SettingsRepository,
    foodDataPrefs: FoodDataPrefs,
) : ViewModel() {

    private val tab = MutableStateFlow(AddFoodTab.SEARCH)
    private val query = MutableStateFlow("")
    private val searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    private val barcodeState = MutableStateFlow<BarcodeState>(BarcodeState.Idle)
    private val pendingLog = MutableStateFlow<PendingLog?>(null)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val localResults = query
        // SPEC §5.1: 300ms debounce.
        .debounce(300)
        .flatMapLatest { q ->
            // Food search lists foods only: a drink on this phone is found in Drinks (one rule, DrinkRule).
            if (q.isBlank()) flowOf(emptyList()) else foodRepository.search(q.trim()).map { found ->
                found.filter { SearchScope.FOODS.keeps(DrinkRule.isStoredDrink(it.drinkCategory)) }
            }
        }

    /**
     * Imported restaurant menu items for the query. Scanned again here, off the main thread, so the chip is today's
     * verdict and not the one stored at import; it is also redone when the person changes what they avoid.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val menuRows: kotlinx.coroutines.flow.Flow<List<MenuRow>> = combine(
        query.debounce(300),
        allergenRepository.observeAvoidedAllergens(),
        allergenRepository.observeAliases(),
    ) { q, _, _ -> q }.mapLatest { q ->
        if (q.trim().length < 2) {
            emptyList()
        } else {
            val avoidsSomething = allergenRepository.avoidedAllergens().isNotEmpty()
            menuLogRepository.search(q.trim()).map { hit ->
                val verdict = hit.scan.verdict()
                MenuRow(
                    hit,
                    if (avoidsSomething) {
                        RowAllergen(verdict, hit.scan.chipDetail(), nameOnly = verdict == SwapAllergen.CLEAR && hit.noIngredientInfo)
                    } else {
                        null
                    },
                )
            }
        }
    }

    /** The offline database's foods for the query, and whether the same words also find drinks. */
    private data class RefAnswer(val foods: List<RefFood> = emptyList(), val drinksExist: Boolean = false)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val refResults = query
        .debounce(300)
        .flatMapLatest { q ->
            flow {
                if (q.isBlank()) {
                    emit(RefAnswer())
                } else {
                    val foods = foodRefRepository.search(q, scope = SearchScope.FOODS)
                    emit(RefAnswer(foods))
                    val drinks = drinkRepository.searchDrinks(q).first().isNotEmpty() ||
                        foodRefRepository.search(q, limit = 1, scope = SearchScope.DRINKS).isNotEmpty()
                    if (drinks) emit(RefAnswer(foods, drinksExist = true))
                }
            }
        }

    /**
     * Open Food Facts runs on a longer debounce than the on-phone sources, so a word
     * being typed does not become a request per keystroke. Only the query text is sent.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val offState = query
        .debounce(OFF_DEBOUNCE_MS)
        .flatMapLatest { q ->
            flow {
                if (q.trim().length < OffSearchRepository.MIN_QUERY) {
                    emit(OffUiState.Hidden)
                } else {
                    emit(OffUiState.Loading)
                    emit(
                        when (val outcome = offSearchRepository.search(q, SearchScope.FOODS)) {
                            is OffSearchOutcome.Results -> OffUiState.Results(outcome.foods, outcome.otherKind)
                            OffSearchOutcome.Unavailable -> OffUiState.Hidden
                            OffSearchOutcome.Failed -> OffUiState.Failed
                        },
                    )
                }
            }
        }

    private data class Extras(
        val barcode: BarcodeState,
        val pending: PendingLog?,
        val refResults: List<RefFood>,
        val off: OffUiState,
        val offEnabled: Boolean,
        val drinksExist: Boolean,
    )

    // Shared, because both the screen state and the allergen chips read these. Collected twice, each collector
    // would run its own Open Food Facts search and get rows with different ids, and the chips, which are keyed by
    // id, would then belong to rows that are not on screen.
    private val extras = combine(
        barcodeState,
        pendingLog,
        refResults,
        offState,
        foodDataPrefs.settings,
    ) { barcode, pending, ref, off, prefs ->
        Extras(barcode, pending, ref.foods, if (prefs.offSearch) off else OffUiState.Hidden, prefs.offSearch, ref.drinksExist)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val lists = combine(
        foodRepository.observeRecents(),
        foodRepository.observeFavorites(),
        allergenRepository.observeSafeFoodIds(),
        localResults,
        allergenRepository.observeCheckLabelTerms(),
    ) { recents, favorites, safeIds, results, terms ->
        Lists(recents, favorites, safeIds, results, terms)
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private data class RowAllergens(val rows: Map<String, RowAllergen>, val refs: Map<Int, RowAllergen>)

    /**
     * The local engine's verdict for every row on screen, so a result list shows "Contains milk" before anything is
     * tapped. One engine setup per list. Empty when the person avoids nothing (no chips then).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rowAllergens = combine(
        combine(lists, extras, searchState, tab) { l, e, s, t -> Triple(l, e, s) to t },
        allergenRepository.observeAvoidedAllergens(),
        allergenRepository.observeAliases(),
        foodRepository.observeAll(),
    ) { t, _, _, mine -> t to mine }.mapLatest { (t, mine) ->
        val (l, e, s) = t.first
        if (allergenRepository.avoidedAllergens().isEmpty()) {
            RowAllergens(emptyMap(), emptyMap())
        } else {
            val scan = allergenRepository.foodScanner()
            // Every row on screen, from every source: this phone, Open Food Facts, the USDA API. The phone's whole
            // food list is only read for the Mine tab.
            val foods = l.recents + l.favorites + l.localResults +
                (if (t.second == AddFoodTab.MY_FOODS) mine.take(300) else emptyList()) +
                (e.off as? OffUiState.Results)?.foods.orEmpty() + (s as? SearchState.Remote)?.results.orEmpty()
            // Keyword matching over a few hundred rows is text work: never on the main thread.
            withContext(Dispatchers.Default) {
                RowAllergens(
                    foods.associate { it.id to rowAllergenOf(it, scan(it)) },
                    // A bundled USDA row is scanned as the food it becomes when chosen (its name: it is a generic food).
                    e.refResults.associate { ref ->
                        FoodRefRepository.toFoodEntity(ref).let { ref.id to rowAllergenOf(it, scan(it)) }
                    },
                )
            }
        }
    }

    val uiState: StateFlow<AddFoodUiState> = combine(
        combine(tab, query, lists, searchState, extras) { a, b, c, d, f -> Quint(a, b, c, d, f) },
        rowAllergens,
        settingsRepository.settings,
        foodRepository.observeAll(),
        menuRows,
    ) { core, allergens, settings, everything, menu ->
        val (tab, query, lists, search, extras) = core
        AddFoodUiState(
            portionUnit = settings.portionUnit,
            rowAllergens = allergens.rows,
            refAllergens = allergens.refs,
            tab = tab,
            query = query,
            recents = lists.recents,
            localResults = lists.localResults,
            // Mine: every food on this phone (searched, scanned or made by hand) but the built-in drinks nobody touched.
            myFoods = everything.filter { it.createdAt != com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP },
            favorites = lists.favorites,
            safeFoodIds = lists.safeIds,
            searchState = search,
            barcodeState = extras.barcode,
            pendingLog = extras.pending,
            hasUsdaKey = apiKeyStore.usdaKey() != null,
            refResults = extras.refResults,
            offState = extras.off,
            offSearchEnabled = extras.offEnabled,
            checkLabelTerms = lists.checkLabelTerms,
            menuRows = menu,
            queryMatchesDrinks = extras.drinksExist || ((extras.off as? OffUiState.Results)?.drinksLeftOut ?: 0) > 0,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddFoodUiState(),
    )

    private data class Quint(
        val tab: AddFoodTab,
        val query: String,
        val lists: Lists,
        val search: SearchState,
        val extras: Extras,
    )

    private data class Lists(
        val recents: List<FoodEntity>,
        val favorites: List<FoodEntity>,
        val safeIds: Set<String>,
        val localResults: List<FoodEntity>,
        val checkLabelTerms: List<CheckLabelTerm>,
    )

    fun selectTab(next: AddFoodTab) {
        tab.value = next
        if (next == AddFoodTab.BARCODE && barcodeState.value is BarcodeState.Idle) {
            barcodeState.value = BarcodeState.Scanning
        }
    }

    fun setQuery(next: String) {
        query.value = next
        if (next.isBlank()) searchState.value = SearchState.Idle
    }

    /** SPEC §5.1: local results show above remote ones; remote is explicit. */
    fun searchRemote() = viewModelScope.launch {
        val q = query.value.trim()
        if (q.isBlank()) return@launch
        searchState.value = SearchState.Loading
        searchState.value = when (val result = foodSearchRepository.searchRemote(q)) {
            is RemoteResult.Success -> {
                // Drinks the USDA API returned are saved like the rest, and are found in Drinks, not here.
                val foods = result.value.filter { SearchScope.FOODS.keeps(DrinkRule.isStoredDrink(it.drinkCategory)) }
                if (foods.isEmpty()) SearchState.NoResults else SearchState.Remote(foods)
            }
            RemoteResult.MissingKey -> SearchState.NeedsKey
            RemoteResult.NotFound -> SearchState.NoResults
            is RemoteResult.Failed -> SearchState.Failed(result.message)
        }
    }

    fun onBarcodeScanned(barcode: String) {
        // The scanner and the typed field both deliver a normalized code; this is the
        // last line of defence for a caller that did not.
        val code = Barcodes.normalize(barcode) ?: return
        val current = barcodeState.value
        if (current !is BarcodeState.Scanning && current !is BarcodeState.Idle) return
        lookUp(code)
    }

    fun retryBarcode(barcode: String) = lookUp(barcode)

    private fun lookUp(code: String) {
        barcodeState.value = BarcodeState.LookingUp
        viewModelScope.launch {
            barcodeState.value = routeBarcodeResult(code, barcodeRepository.lookup(code))
        }
    }

    /** Copies a bundled USDA food into the user's foods and hands it back for the portion step. */
    fun chooseRef(ref: RefFood, onReady: (FoodEntity, List<RefPortion>) -> Unit) = viewModelScope.launch {
        onReady(foodRefRepository.copyToMyFoods(ref), ref.portions)
    }

    /** Saves a chosen Open Food Facts result and hands it back for the portion step. */
    fun chooseOff(food: FoodEntity, onReady: (FoodEntity) -> Unit) = viewModelScope.launch {
        onReady(offSearchRepository.adopt(food))
    }

    fun rescanBarcode() { barcodeState.value = BarcodeState.Scanning }

    /**
     * SPEC §7: every path runs the engine before anything is written. A match
     * stages the log behind the red sheet instead of saving.
     */
    fun requestLogFood(
        food: FoodEntity,
        grams: Double,
        slot: MealSlot,
        at: Long,
        onLogged: () -> Unit,
    ) = viewModelScope.launch {
        val scan = allergenRepository.scan(food)
        if (scan.hasAnything) {
            pendingLog.value = PendingLog(food, food.name, grams, null, scan)
        } else {
            logOne(food, grams, slot, at)
            onLogged()
        }
    }

    fun requestQuickAdd(
        name: String,
        macros: Macros,
        slot: MealSlot,
        at: Long,
        onLogged: () -> Unit,
    ) = viewModelScope.launch {
        // The form disables Save for an out-of-range value; this keeps the save path honest too.
        if (QuickAddLimits.firstProblem(macros) != null) return@launch
        val label = name.ifBlank { "Quick add" }
        val scan = allergenRepository.scan(name = label)
        if (scan.hasAnything) {
            pendingLog.value = PendingLog(null, label, null, macros, scan)
        } else {
            foodLogRepository.quickAdd(label, macros, slot, at)
            onLogged()
        }
    }

    /**
     * The "+" on a restaurant-menu row: one serving as a quick add with the item's own numbers. The item is scanned
     * again first (the stored verdict can be stale); anything the person avoids stages it behind the warning sheet.
     * A row that says "ingredients not listed" is logged like any other: the chip on the row was the warning.
     */
    fun logMenuItem(row: MenuRow, slot: MealSlot, at: Long) = viewModelScope.launch {
        when (val outcome = menuLogRepository.log(row.hit.item.id, slot, at)) {
            null -> messages.post(AppMessage("That menu item is no longer saved"))
            is MenuLogOutcome.NeedsConfirmation -> pendingLog.value = PendingLog(null, outcome.name, null, outcome.macros, outcome.scan)
            is MenuLogOutcome.Logged -> messages.post(
                AppMessage("Logged ${row.hit.item.name} to ${slot.name.lowercase()}", "Undo") { foodLogRepository.softDelete(outcome.log.id) },
            )
        }
    }

    fun confirmPendingLog(slot: MealSlot, at: Long, onLogged: () -> Unit) {
        val pending = pendingLog.value ?: return
        pendingLog.value = null
        viewModelScope.launch {
            when {
                pending.food != null && pending.grams != null ->
                    logOne(pending.food, pending.grams, slot, at)
                pending.macros != null ->
                    foodLogRepository.quickAdd(pending.name, pending.macros, slot, at)
            }
            onLogged()
        }
    }

    fun dismissPendingLog() { pendingLog.value = null }

    /** A drink goes through the one drink flow (hydration row and food-log row together); everything else is a food log. */
    private suspend fun logOne(food: FoodEntity, amount: Double, slot: MealSlot, at: Long) {
        if (food.drinkCategory != null) {
            val logged = drinkRepository.log(food, amount, at, slot)
            messages.post(AppMessage("Added ${settingsRepository.settings.first().volumeUnit.format(amount)} ${food.name}", "Undo") { drinkRepository.delete(logged.hydration.id) })
        } else {
            val entry = foodLogRepository.logFood(food, amount, slot, at)
            messages.post(AppMessage("Logged ${food.name} to ${slot.name.lowercase()}", "Undo") { foodLogRepository.softDelete(entry.id) })
        }
    }

    /** The "+" on a result row: logs the food's own serving (after the allergen check) and stays on the screen. */
    fun quickAddDefault(food: FoodEntity, slot: MealSlot, at: Long) =
        requestLogFood(food, food.servingSizeGrams.coerceAtLeast(1.0), slot, at) { }

    /**
     * Creates a food by hand. The numbers the user types are for the serving
     * they described, so they are converted to per-100g before storing
     * (SPEC §4).
     */
    fun createFood(
        name: String,
        brand: String?,
        servingGrams: Double,
        servingLabel: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        barcode: String? = null,
        onCreated: (FoodEntity) -> Unit,
    ) = viewModelScope.launch {
        val per100 = 100.0 / servingGrams
        val food = foodRepository.create(
            name = name,
            brand = brand?.takeIf { it.isNotBlank() },
            barcode = barcode,
            servingSizeGrams = servingGrams,
            servingLabel = servingLabel.ifBlank { "1 serving" },
            kcalPer100g = kcal * per100,
            proteinPer100g = protein * per100,
            carbsPer100g = carbs * per100,
            fatPer100g = fat * per100,
            source = if (barcode.isNullOrBlank()) FoodSource.MANUAL else FoodSource.BARCODE,
            // The form asks for energy and the three macros only: the rest is not listed, not zero.
            unlistedNutrients = Unlisted.encode(NutrientKey.entries.toSet()),
        )
        onCreated(food)
    }

    fun toggleFavorite(food: FoodEntity) = viewModelScope.launch {
        foodRepository.toggleFavorite(food.id, !food.isFavorite)
    }

    private companion object {
        const val OFF_DEBOUNCE_MS = 600L
    }
}
