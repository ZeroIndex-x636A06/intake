package com.zeroindex.intake.ui.screens.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.swap.PlannedMeal
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapLogResult
import com.zeroindex.intake.data.swap.SwapRepository
import com.zeroindex.intake.data.swap.SwapSource
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.SwapSection
import com.zeroindex.intake.domain.swap.SwapSections
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The nearby search's state. It starts idle and only ever runs on a tap. */
data class NearbyState(
    val hasKey: Boolean = false,
    val hasLocation: Boolean = false,
    val radiusMi: Double = 1.0,
    val searching: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    /** When the last search finished, UTC millis, or null if there has not been one. */
    val foundAt: Long? = null,
    val costUsd: Double? = null,
    val resultCount: Int = 0,
)

data class SwapUiState(
    val loading: Boolean = true,
    val slot: MealSlot = MealSlot.LUNCH,
    val planned: PlannedMeal? = null,
    val sections: List<SwapSection> = emptyList(),
    val nearby: NearbyState = NearbyState(),
    /** A one-line refusal ("Contains sesame") after a blocked tap; cleared on the next action. */
    val notice: String? = null,
    val choosing: Boolean = false,
    val savedKeys: Set<String> = emptySet(),
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

/** A planned meal to compare to when nothing is planned: a typical meal, so options still sort sensibly. */
private val TYPICAL_MEAL = Macros(kcal = 600.0)

@HiltViewModel
class SwapViewModel @Inject constructor(
    private val repo: SwapRepository,
    private val finder: PlanBFinder,
    private val gateway: AiGateway,
    private val settings: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SwapUiState(zone = clock.zone()))
    val state: StateFlow<SwapUiState> = _state.asStateFlow()

    private var offline: List<SwapEntry> = emptyList()
    private var found: List<SwapEntry> = emptyList()
    private var loadedFor: String? = "\u0000"

    /** Reads today's planned meal and the options on this phone. Safe to call again with the same slot. */
    fun load(slotName: String?) {
        if (loadedFor == slotName) return
        loadedFor = slotName
        val slot = slotName?.let { n -> MealSlot.entries.firstOrNull { it.name == n } }
            ?: TemplateLink.slotFor(java.time.LocalTime.now(clock.zone()))
        _state.update { it.copy(slot = slot, loading = true) }
        viewModelScope.launch {
            val planned = repo.planned()
            offline = repo.offlineEntries(planned)
            val radius = settings.settings.first().eatOutRadiusMi
            _state.update { it.copy(planned = planned, loading = false, nearby = it.nearby.copy(radiusMi = radius)) }
            refreshPermissions()
            publish()
        }
    }

    fun refreshPermissions() = _state.update {
        it.copy(nearby = it.nearby.copy(hasKey = gateway.isConfigured(), hasLocation = finder.hasLocationPermission()))
    }

    private fun publish() = _state.update { s ->
        s.copy(sections = SwapSections.group((offline + found).map { it.option }, s.planned?.macros ?: TYPICAL_MEAL))
    }

    private fun entryOf(key: String): SwapEntry? = (offline + found).firstOrNull { it.option.key == key }

    /** "Search near me": one location fix, rounded to about a kilometre, used for this search only. */
    fun searchNearby() {
        _state.update { it.copy(notice = null, nearby = it.nearby.copy(searching = true, error = null, status = "Finding where you are…")) }
        viewModelScope.launch {
            val area = finder.currentArea()
            if (area == null) {
                _state.update { it.copy(nearby = it.nearby.copy(searching = false, status = null, error = "Could not get a location fix. Try a zip code instead.")) }
                return@launch
            }
            run(area)
        }
    }

    fun searchPostalCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.length < 3) return
        _state.update { it.copy(notice = null, nearby = it.nearby.copy(searching = true, error = null)) }
        viewModelScope.launch { run(SearchArea.PostalCode(trimmed)) }
    }

    private suspend fun run(area: SearchArea) {
        val radius = _state.value.nearby.radiusMi
        _state.update { it.copy(nearby = it.nearby.copy(status = "Searching nearby menus and checking them against your allergens…")) }
        runCatching { finder.search(area, radius) }
            .onSuccess { (options, cost) ->
                found = options.map(repo::foundEntry)
                _state.update {
                    it.copy(nearby = it.nearby.copy(searching = false, status = null, foundAt = clock.nowMillis(), costUsd = cost, resultCount = options.size))
                }
                publish()
            }
            .onFailure { e ->
                _state.update { it.copy(nearby = it.nearby.copy(searching = false, status = null, error = (e as? AiError)?.message ?: "The search failed.")) }
            }
    }

    /** Keeps a nearby option as a saved eat-out option (works offline from then on). */
    fun saveForOffline(key: String) {
        val src = entryOf(key)?.source as? SwapSource.Found ?: return
        viewModelScope.launch {
            repo.saveForOffline(src.option)
            _state.update { it.copy(savedKeys = it.savedKeys + key) }
        }
    }

    /** Logs the option in place of the planned meal, then closes the sheet. A blocked option never gets this far. */
    fun choose(key: String, onDone: () -> Unit) {
        val entry = entryOf(key) ?: return
        if (_state.value.choosing) return
        _state.update { it.copy(choosing = true, notice = null) }
        viewModelScope.launch {
            when (val r = repo.choose(entry, _state.value.slot)) {
                is SwapLogResult.Logged -> {
                    val planned = _state.value.planned
                    messages.post(
                        AppMessage(
                            text = r.text + (planned?.let { " instead of ${it.title}" } ?: ""),
                            actionLabel = "Undo",
                            onAction = r.undo,
                        ),
                    )
                    _state.update { it.copy(choosing = false) }
                    onDone()
                }
                is SwapLogResult.Blocked -> _state.update { it.copy(choosing = false, notice = "Not logged. ${r.reason}.") }
            }
        }
    }
}
