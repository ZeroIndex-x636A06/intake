package com.zeroindex.intake.ui.screens.planb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.PlanBOption
import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.domain.model.MealSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanBUiState(
    val offline: List<PlanBOption> = emptyList(),
    val found: List<PlanBOption> = emptyList(),
    val hasKey: Boolean = false,
    val hasLocation: Boolean = false,
    val searching: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val selectedKey: String? = null,
    val savedKeys: Set<String> = emptySet(),
    val logged: Boolean = false,
    val costUsd: Double? = null,
) {
    val all: List<PlanBOption> get() = offline + found
    val selected: PlanBOption? get() = all.firstOrNull { it.key == selectedKey }
}

@HiltViewModel
class PlanBViewModel @Inject constructor(
    private val finder: PlanBFinder,
    private val keys: ApiKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanBUiState())
    val state: StateFlow<PlanBUiState> = _state.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch {
            val offline = finder.offlineOptions()
            _state.update { it.copy(offline = offline, selectedKey = it.selectedKey ?: offline.firstOrNull { o -> !o.rejected }?.key) }
        }
    }

    fun refreshPermissions() = _state.update {
        it.copy(hasKey = keys.openRouterKey() != null, hasLocation = finder.hasLocationPermission())
    }

    fun searchNearby() {
        _state.update { it.copy(searching = true, error = null, status = "Finding where you are…") }
        viewModelScope.launch {
            val area = finder.currentArea()
            if (area == null) {
                _state.update {
                    it.copy(searching = false, error = "Could not get a location fix. Try a zip code instead.", status = null)
                }
                return@launch
            }
            run(area)
        }
    }

    fun searchPostalCode(code: String) {
        val trimmed = code.trim()
        if (trimmed.length < 3) return
        _state.update { it.copy(searching = true, error = null) }
        viewModelScope.launch { run(SearchArea.PostalCode(trimmed)) }
    }

    private suspend fun run(area: SearchArea) {
        _state.update { it.copy(status = "Searching nearby menus and checking them against your allergens…") }
        runCatching { finder.search(area) }
            .onSuccess { (found, cost) ->
                _state.update {
                    it.copy(
                        found = found,
                        searching = false,
                        status = null,
                        costUsd = cost,
                        selectedKey = found.firstOrNull { o -> !o.rejected }?.key ?: it.selectedKey,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(searching = false, status = null, error = (e as? AiError)?.message ?: "The search failed.")
                }
            }
    }

    fun select(option: PlanBOption) = _state.update { it.copy(selectedKey = option.key, logged = false) }

    /** "Show another option": the next usable one after the current. */
    fun next() = _state.update { s ->
        val usable = s.all.filter { !it.rejected }
        val i = usable.indexOfFirst { it.key == s.selectedKey }
        s.copy(selectedKey = usable.getOrNull((i + 1) % usable.size.coerceAtLeast(1))?.key, logged = false)
    }

    fun save(option: PlanBOption) = viewModelScope.launch {
        finder.saveForOffline(option)
        _state.update { it.copy(savedKeys = it.savedKeys + option.key) }
    }

    fun log(option: PlanBOption, slot: MealSlot) = viewModelScope.launch {
        finder.log(option, slot)
        _state.update { it.copy(logged = true) }
    }
}
