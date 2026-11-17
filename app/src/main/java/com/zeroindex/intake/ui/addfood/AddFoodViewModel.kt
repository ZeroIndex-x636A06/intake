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
import com.zeroindex.intake.ui.allergen.recipeChipDetail
import com.zeroindex.intake.ui.allergen.traceChipLabel
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
import com.zeroindex.intake.domain.allergen.AllergenOverride
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.ensureActive
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
 * [basis] says what a clear result rests on (a packaged product with no ingredient list and no allergen tags: the
 * chip then says so instead of "No allergens", `AllergenEvidence`).
 */
data class RowAllergen(
    val verdict: SwapAllergen,
    val detail: String?,
    /** What a clear verdict rests on; only read when [verdict] is clear. */
    val basis: AllergenEvidence.ClearBasis = AllergenEvidence.ClearBasis.LABEL_READ,
    /** The whole verdict is a label's "may contain" trace line: worded as a trace, still a warning. */
    val traceLabel: String? = null,
    /** True for a row whose verdict is still being worked out. It is shown as "Checking..." and is never a clear. */
    val checking: Boolean = false,
) {
    /** True when this is a clear result that must not read as clear: the chip says what was (not) checked. */
    val cautionClear: Boolean get() = verdict == SwapAllergen.CLEAR && basis.isCaution

    /**
     * True when the green "logged before, no flag" tick may sit beside the name: only a plain clear that rests on a read
     * label. Never beside a name-only or no-list chip, a flag or a placeholder (Q5a-24: one signal per row).
     */
    val allowsSafeTick: Boolean get() = !checking && !cautionClear && verdict == SwapAllergen.CLEAR

    companion object {
        /** The neutral placeholder for a row that has no verdict yet while the person avoids something. */
        val Checking = RowAllergen(SwapAllergen.CHECK_LABEL, null, checking = true)
    }
}

/** The chip for a food row, from the engine's scan of everything the row carries. */
fun rowAllergenOf(food: FoodEntity, scan: AllergenScanResult): RowAllergen {
    val verdict = scan.verdict()
    return RowAllergen(
        verdict = verdict,
        detail = scan.chipDetail(),
        basis = AllergenEvidence.clearBasis(food.source, food.isVerified, food.allergenTags),
        traceLabel = scan.traceChipLabel(),
    )
}

/**
 * The chip for a saved recipe or a template: THE one mapper every recipe surface uses (Recipes list, Log food > Mine,
 * recipe page, plan picker, templates), so one recipe never reads differently in two places. [ingredientLineCount] is
 * how many ingredient lines (foods, for a template) it has; [readLineCount] how many of them were read from a label or
 * allergen tags. A clear verdict is green only when every line was read (`AllergenEvidence.recipeClearBasis`).
 */
fun recipeRowAllergenOf(scan: AllergenScanResult, ingredientLineCount: Int, readLineCount: Int): RowAllergen =
    recipeRowAllergenOf(scan.verdict(), scan.recipeChipDetail(), scan.traceChipLabel(), ingredientLineCount, readLineCount)

/**
 * The same recipe chip from an already-decided verdict (the Swap sheet, Today's planned card and the plan screen carry a
 * verdict and matched terms rather than a scan). The basis is always `AllergenEvidence.recipeClearBasis`, so a recipe that
 * was only name-checked is never green anywhere (QA Q5b-2).
 */
fun recipeRowAllergenOf(
    verdict: SwapAllergen,
    detail: String?,
    traceLabel: String?,
    ingredientLineCount: Int,
    readLineCount: Int,
): RowAllergen = RowAllergen(
    verdict = verdict,
    detail = detail,
    traceLabel = traceLabel,
    basis = AllergenEvidence.recipeClearBasis(ingredientLineCount, readLineCount),
)

/**
 * The chip for an imported menu item, from today's scan. [statesNothing] is `MenuAllergenEvidence.statesNothing`: an
 * estimate, or an item with no ingredient text and no stated allergens, is never clear ("No ingredient list").
 */
fun menuRowAllergenOf(scan: AllergenScanResult, statesNothing: Boolean): RowAllergen = RowAllergen(
    verdict = scan.verdict(),
    detail = scan.chipDetail(),
    traceLabel = scan.traceChipLabel(),
    basis = if (statesNothing) AllergenEvidence.ClearBasis.NO_LIST else AllergenEvidence.ClearBasis.LABEL_READ,
)

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
    /**
     * True when the person avoids something, so a row with no verdict yet is "Checking...", not blank. Rows and
     * verdicts come from different flows; a row must never look clear only because its verdict has not arrived.
     */
    val avoidsAnything: Boolean = false,
    /** False until the first real read of the phone's lists: the screen shows nothing, not "Nothing logged yet". */
    val loaded: Boolean = false,
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
) {
    /** The chip for a phone or Open Food Facts row: the verdict, a "Checking..." placeholder, or none when nothing is avoided. */
    fun chipFor(foodId: String): RowAllergen? = rowAllergens[foodId] ?: RowAllergen.Checking.takeIf { avoidsAnything }

    /** The same for a bundled USDA row. */
    fun refChipFor(refId: Int): RowAllergen? = refAllergens[refId] ?: RowAllergen.Checking.takeIf { avoidsAnything }
}

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
                // Word-start matches first ("almo" before Salmon), the database's own order inside a tier.
                com.zeroindex.intake.domain.food.NameMatchRank.rank(
                    found.filter { SearchScope.FOODS.keeps(DrinkRule.isStoredDrink(it.drinkCategory)) },
                    q.trim(), { it.name }, { it.brand.orEmpty() },
                )
            }
        }
        // Building the Room query flow and ranking its rows were running on the main thread, once per settled query
        // (seen in a main-thread stack sample while typing: FoodDao_Impl.searchByName).
        .flowOn(Dispatchers.Default)

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
                        RowAllergen(
                            verdict,
                            hit.scan.chipDetail(),
                            traceLabel = hit.scan.traceChipLabel(),
                            // A menu item whose source states nothing is "no list"; one with stated text was read.
                            basis = if (hit.noIngredientInfo) AllergenEvidence.ClearBasis.NO_LIST else AllergenEvidence.ClearBasis.LABEL_READ,
                        )
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
                    // The pointer to Drinks shows when a drink name starts with the words, or has a word that does
                    // ("almo" finds Almond milk); a drink that only holds them inside a word does not count. The drinks
                    // on this phone are checked first, because that is quick, so the pointer comes with the foods.
                    val trimmed = q.trim()
                    val localDrink = drinkRepository.searchDrinks(trimmed).first().any {
                        com.zeroindex.intake.domain.food.NameMatchRank.startsAWord(it.name, it.brand.orEmpty(), trimmed)
                    }
                    val foods = foodRefRepository.search(q, scope = SearchScope.FOODS)
                    emit(RefAnswer(foods, drinksExist = localDrink))
                    if (!localDrink) {
                        val tokens = com.zeroindex.intake.data.foodref.FoodRefSearch.queryTokens(q)
                        val refDrink = foodRefRepository.search(q, limit = 1, scope = SearchScope.DRINKS).firstOrNull()
                        if (refDrink != null && com.zeroindex.intake.data.foodref.FoodRefSearch.tier(refDrink.name, tokens) < 3) {
                            emit(RefAnswer(foods, drinksExist = true))
                        }
                    }
                }
            }
        }
        .flowOn(Dispatchers.Default)

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

    private data class RowAllergens(val rows: Map<String, RowAllergen>, val refs: Map<Int, RowAllergen>, val avoids: Boolean)

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
    ) { t, _, _, mine -> t to mine }.transformLatest { (t, mine) ->
        val (l, e, s) = t.first
        if (allergenRepository.avoidedAllergens().isEmpty()) {
            emit(RowAllergens(emptyMap(), emptyMap(), avoids = false))
        } else {
            val scan = allergenRepository.foodScanner()
            // Every row on screen, from every source: this phone, Open Food Facts, the USDA API. The phone's whole
            // food list is only read for the Mine tab.
            val foods = (
                l.recents + l.favorites + l.localResults +
                    (if (t.second == AddFoodTab.MY_FOODS) mine else emptyList()) +
                    (e.off as? OffUiState.Results)?.foods.orEmpty() + (s as? SearchState.Remote)?.results.orEmpty()
                ).distinctBy { it.id }
            val done = LinkedHashMap<String, RowAllergen>()
            // A row not scanned yet has no entry and shows "Checking...", never a clear (chipFor). The first rows are
            // scanned and shown at once; the rest follow, on two threads, and stop as soon as the lists change.
            withContext(allergenRepository.scanDispatcher) {
                for (food in foods.take(FIRST_SCAN_ROWS)) {
                    ensureActive()
                    done[food.id] = rowAllergenOf(food, scan(food))
                }
            }
            if (foods.size > FIRST_SCAN_ROWS || e.refResults.isNotEmpty()) {
                emit(RowAllergens(LinkedHashMap(done), emptyMap(), avoids = true))
            }
            val refs = LinkedHashMap<Int, RowAllergen>()
            withContext(allergenRepository.scanDispatcher) {
                for (food in foods.drop(FIRST_SCAN_ROWS)) {
                    ensureActive()
                    done[food.id] = rowAllergenOf(food, scan(food))
                }
                // A bundled USDA row is scanned as the food it becomes when chosen (its name: it is a generic food).
                for (ref in e.refResults) {
                    ensureActive()
                    FoodRefRepository.toFoodEntity(ref).let { refs[ref.id] = rowAllergenOf(it, scan(it)) }
                }
            }
            emit(RowAllergens(done, refs, avoids = true))
        }
    }

    val uiState: StateFlow<AddFoodUiState> = combine(
        combine(tab, lists, searchState, extras) { a, c, d, f -> Quad(a, c, d, f) },
        rowAllergens,
        settingsRepository.settings,
        foodRepository.observeAll(),
        menuRows,
    ) { core, allergens, settings, everything, menu ->
        val (tab, lists, search, extras) = core
        AddFoodUiState(
            portionUnit = settings.portionUnit,
            rowAllergens = allergens.rows,
            refAllergens = allergens.refs,
            tab = tab,
            avoidsAnything = allergens.avoids,
            loaded = true,
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
    }
        // The assembling (a filter over every food on the phone, among others) is not main-thread work.
        .flowOn(Dispatchers.Default)
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddFoodUiState(),
    )

    private data class Quad(
        val tab: AddFoodTab,
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

    /** The words last sent, so the screen's own text field can start from them after a rotation. */
    val currentQuery: String get() = query.value

    /**
     * The words typed. The text field itself keeps its own state in the screen, so a keystroke never waits on the
     * flows below (Q1-3); each source debounces this on its own.
     */
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
            // Every override path keeps "Logged despite: ..." on the entry (the warning itself is never cleared).
            val note = AllergenOverride.noteFor(pending.scan)
            when {
                pending.food != null && pending.grams != null ->
                    logOne(pending.food, pending.grams, slot, at, note)
                pending.macros != null ->
                    foodLogRepository.markOverridden(foodLogRepository.quickAdd(pending.name, pending.macros, slot, at), note)
            }
            onLogged()
        }
    }

    fun dismissPendingLog() { pendingLog.value = null }

    /** A drink goes through the one drink flow (hydration row and food-log row together); everything else is a food log. */
    private suspend fun logOne(food: FoodEntity, amount: Double, slot: MealSlot, at: Long, overrideNote: String? = null) {
        if (food.drinkCategory != null) {
            val logged = drinkRepository.log(food, amount, at, slot)
            if (overrideNote != null) logged.foodLog?.let { foodLogRepository.markOverridden(it, overrideNote) }
            messages.post(AppMessage("Added ${settingsRepository.settings.first().volumeUnit.format(amount)} ${food.name}", "Undo") { drinkRepository.delete(logged.hydration.id) })
        } else {
            val entry = foodLogRepository.logFood(food, amount, slot, at)
            if (overrideNote != null) foodLogRepository.markOverridden(entry, overrideNote)
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
        const val FIRST_SCAN_ROWS = 24
    }
}
