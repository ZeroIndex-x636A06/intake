package com.zeroindex.intake.ui.addfood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.BarcodeRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.FoodSearchRepository
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AddFoodTab(val label: String) {
    RECENTS("Recents"),
    SEARCH("Search"),
    BARCODE("Barcode"),
    QUICK_ADD("Quick add"),
    MY_FOODS("My foods"),
}

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

sealed interface BarcodeState {
    data object Idle : BarcodeState
    data object Scanning : BarcodeState
    data object LookingUp : BarcodeState
    data class Found(val food: FoodEntity, val scan: AllergenScanResult, val fromCache: Boolean) :
        BarcodeState
    data class Unknown(val barcode: String, val reason: String?) : BarcodeState
}

data class AddFoodUiState(
    val tab: AddFoodTab = AddFoodTab.RECENTS,
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
)

@HiltViewModel
class AddFoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val foodLogRepository: FoodLogRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val barcodeRepository: BarcodeRepository,
    private val allergenRepository: AllergenRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val tab = MutableStateFlow(AddFoodTab.RECENTS)
    private val query = MutableStateFlow("")
    private val searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    private val barcodeState = MutableStateFlow<BarcodeState>(BarcodeState.Idle)
    private val pendingLog = MutableStateFlow<PendingLog?>(null)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val localResults = query
        // SPEC §5.1: 300ms debounce.
        .debounce(300)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else foodRepository.search(q.trim())
        }

    private val lists = combine(
        foodRepository.observeRecents(),
        foodRepository.observeFavorites(),
        allergenRepository.observeSafeFoodIds(),
        localResults,
    ) { recents, favorites, safeIds, results ->
        Lists(recents, favorites, safeIds, results)
    }

    val uiState: StateFlow<AddFoodUiState> = combine(
        tab,
        query,
        lists,
        searchState,
        combine(barcodeState, pendingLog) { b, p -> b to p },
    ) { tab, query, lists, search, barcodeAndPending ->
        AddFoodUiState(
            tab = tab,
            query = query,
            recents = lists.recents,
            localResults = lists.localResults,
            // "My foods" is what the user entered by hand, as opposed to
            // anything cached from a remote lookup.
            myFoods = lists.recents.filter { it.source == FoodSource.MANUAL },
            favorites = lists.favorites,
            safeFoodIds = lists.safeIds,
            searchState = search,
            barcodeState = barcodeAndPending.first,
            pendingLog = barcodeAndPending.second,
            hasUsdaKey = apiKeyStore.usdaKey() != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddFoodUiState(),
    )

    private data class Lists(
        val recents: List<FoodEntity>,
        val favorites: List<FoodEntity>,
        val safeIds: Set<String>,
        val localResults: List<FoodEntity>,
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
            is RemoteResult.Success ->
                if (result.value.isEmpty()) SearchState.NoResults else SearchState.Remote(result.value)
            RemoteResult.MissingKey -> SearchState.NeedsKey
            RemoteResult.NotFound -> SearchState.NoResults
            is RemoteResult.Failed -> SearchState.Failed(result.message)
        }
    }

    fun onBarcodeScanned(barcode: String) {
        if (barcodeState.value !is BarcodeState.Scanning) return
        barcodeState.value = BarcodeState.LookingUp
        viewModelScope.launch {
            barcodeState.value = when (val result = barcodeRepository.lookup(barcode)) {
                is RemoteResult.Success -> BarcodeState.Found(
                    food = result.value.food,
                    scan = result.value.scan,
                    fromCache = result.value.fromCache,
                )
                RemoteResult.NotFound -> BarcodeState.Unknown(barcode, null)
                RemoteResult.MissingKey -> BarcodeState.Unknown(barcode, null)
                is RemoteResult.Failed -> BarcodeState.Unknown(barcode, result.message)
            }
        }
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
            foodLogRepository.logFood(food, grams, slot, at)
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
        val label = name.ifBlank { "Quick add" }
        val scan = allergenRepository.scan(name = label)
        if (scan.hasAnything) {
            pendingLog.value = PendingLog(null, label, null, macros, scan)
        } else {
            foodLogRepository.quickAdd(label, macros, slot, at)
            onLogged()
        }
    }

    fun confirmPendingLog(slot: MealSlot, at: Long, onLogged: () -> Unit) {
        val pending = pendingLog.value ?: return
        pendingLog.value = null
        viewModelScope.launch {
            when {
                pending.food != null && pending.grams != null ->
                    foodLogRepository.logFood(pending.food, pending.grams, slot, at)
                pending.macros != null ->
                    foodLogRepository.quickAdd(pending.name, pending.macros, slot, at)
            }
            onLogged()
        }
    }

    fun dismissPendingLog() { pendingLog.value = null }

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
        )
        onCreated(food)
    }

    fun toggleFavorite(food: FoodEntity) = viewModelScope.launch {
        foodRepository.toggleFavorite(food.id, !food.isFavorite)
    }
}
