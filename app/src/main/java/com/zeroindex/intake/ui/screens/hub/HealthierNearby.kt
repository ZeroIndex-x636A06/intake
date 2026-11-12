package com.zeroindex.intake.ui.screens.hub

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.planb.PlanBFinder
import com.zeroindex.intake.data.ai.planb.SearchArea
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.swap.SwapEntry
import com.zeroindex.intake.data.swap.SwapLogResult
import com.zeroindex.intake.data.swap.SwapRepository
import com.zeroindex.intake.data.swap.SwapSource
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.swap.NearbyMode
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.swap.NearbyCard
import com.zeroindex.intake.ui.screens.swap.NearbyState
import com.zeroindex.intake.ui.screens.swap.OptionCard
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthierUiState(
    /** False until the person taps "Find healthier options nearby". Nothing is read or sent before that. */
    val open: Boolean = false,
    val loading: Boolean = false,
    /** Saved eat-out options and imported menus, healthiest first. No network. */
    val local: List<SwapEntry> = emptyList(),
    /** The AI nearby search's results, healthiest first. Empty until the person taps Search. */
    val found: List<SwapEntry> = emptyList(),
    val nearby: NearbyState = NearbyState(),
    val notice: String? = null,
    val savedKeys: Set<String> = emptySet(),
    val choosing: Boolean = false,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
)

/**
 * Eating out > "Find healthier options nearby": the finder in [NearbyMode.HEALTHIER]. With no planned meal to compare
 * with, options are ranked by how healthy they are for what is left today. The restaurant menus already imported come
 * first (on this phone, no AI); the AI nearby search runs only on a tap on its own Search button. Every option goes
 * through the local allergen engine; a "contains" option is shown disabled with its reason.
 */
@HiltViewModel
class HealthierNearbyViewModel @Inject constructor(
    private val repo: SwapRepository,
    private val finder: PlanBFinder,
    private val gateway: AiGateway,
    private val settings: SettingsRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(HealthierUiState(zone = clock.zone()))
    val state: StateFlow<HealthierUiState> = _state.asStateFlow()

    /** The button: shows the healthier options from imported menus and saved options. Offline; no AI. */
    fun open() {
        if (_state.value.open) return
        _state.update { it.copy(open = true, loading = true, notice = null) }
        viewModelScope.launch {
            val radius = settings.settings.first().eatOutRadiusMi
            val local = repo.healthierOffline()
            _state.update { it.copy(local = local, loading = false, nearby = it.nearby.copy(radiusMi = radius)) }
            refreshPermissions()
        }
    }

    fun refreshPermissions() = _state.update {
        it.copy(nearby = it.nearby.copy(hasKey = gateway.isConfigured(), hasLocation = finder.hasLocationPermission()))
    }

    /** One location fix, rounded to about a kilometre, used for this search only. */
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
        _state.update { it.copy(nearby = it.nearby.copy(status = "Searching nearby menus for healthier orders and checking your allergens…")) }
        runCatching { finder.search(area, radius, NearbyMode.HEALTHIER, planned = null) }
            .onSuccess { (options, cost) ->
                val ranked = repo.healthierFound(options)
                _state.update {
                    it.copy(
                        found = ranked,
                        nearby = it.nearby.copy(searching = false, status = null, foundAt = clock.nowMillis(), costUsd = cost, resultCount = ranked.size),
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(nearby = it.nearby.copy(searching = false, status = null, error = (e as? AiError)?.message ?: "The search failed.")) }
            }
    }

    private fun entryOf(key: String): SwapEntry? = (_state.value.local + _state.value.found).firstOrNull { it.option.key == key }

    fun saveForOffline(key: String) {
        val src = entryOf(key)?.source as? SwapSource.Found ?: return
        viewModelScope.launch {
            repo.saveForOffline(src.option)
            _state.update { it.copy(savedKeys = it.savedKeys + key) }
        }
    }

    /** Logs the option to the meal slot for this time of day. A blocked option never gets this far. */
    fun log(key: String) {
        val entry = entryOf(key) ?: return
        if (_state.value.choosing) return
        _state.update { it.copy(choosing = true, notice = null) }
        viewModelScope.launch {
            when (val r = repo.choose(entry, TemplateLink.slotFor(java.time.LocalTime.now(clock.zone())))) {
                is SwapLogResult.Logged -> {
                    messages.post(AppMessage(text = r.text, actionLabel = "Undo", onAction = r.undo))
                    _state.update { it.copy(choosing = false) }
                }
                is SwapLogResult.Blocked -> _state.update { it.copy(choosing = false, notice = "Not logged. ${r.reason}.") }
            }
        }
    }
}

/** The "Find healthier options nearby" block of the Eating out screen, as items of its list. */
@Composable
fun rememberHealthierBlock(
    onOpenAiSettings: () -> Unit,
    onOpenRadiusSetting: () -> Unit,
    viewModel: HealthierNearbyViewModel = hiltViewModel(),
): LazyListScope.() -> Unit {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermissions()
        if (granted) viewModel.searchNearby()
    }
    val timeFmt = remember(state.zone) { DateTimeFormatter.ofPattern("h:mm a").withZone(state.zone) }
    return {
        item(key = "healthier-head") { SectionLine("Healthier options", note = "your menus first, AI only on tap") }
        if (!state.open) {
            item(key = "healthier-button") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = viewModel::open,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Find healthier options nearby")
                    }
                    Text(
                        "Ranks the restaurant menus you imported by protein for the calories, fibre, and less saturated fat, sugar and salt, for what is left today. Your allergens are checked on this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            state.notice?.let { n -> item(key = "healthier-notice") { SandNote(n) } }
            if (state.loading) {
                item(key = "healthier-loading") { Text("Reading your menus…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (state.local.isEmpty()) {
                item(key = "healthier-empty") {
                    EmptyState(
                        title = "No imported menus yet",
                        body = "Import a restaurant menu and its healthier items show up here with no signal. Or search nearby below.",
                    )
                }
            } else {
                item(key = "healthier-local-head") { Text("From your menus and saved options", style = MaterialTheme.typography.titleMedium) }
                items(state.local, key = { "hl-" + it.option.key }) { e ->
                    OptionCard(
                        e.option, planned = null, choosing = state.choosing, saved = e.option.key in state.savedKeys, saveable = false,
                        onChoose = { viewModel.log(e.option.key) }, onSave = {}, chooseLabel = "Log",
                    )
                }
            }
            item(key = "healthier-nearby-card") {
                NearbyCard(
                    nearby = state.nearby,
                    title = "Find healthier options nearby",
                    blurb = " Healthier orders on the menus near you, by protein for the calories, fibre, saturated fat, sugar and salt.",
                    foundTime = state.nearby.foundAt?.let { timeFmt.format(Instant.ofEpochMilli(it)) },
                    onUseLocation = {
                        if (state.nearby.hasLocation) viewModel.searchNearby() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onPostalCode = viewModel::searchPostalCode,
                    onOpenAiSettings = onOpenAiSettings,
                    onOpenRadiusSetting = onOpenRadiusSetting,
                )
            }
            if (state.found.isNotEmpty()) {
                item(key = "healthier-found-head") { Text("Found nearby", style = MaterialTheme.typography.titleMedium) }
                items(state.found, key = { "hf-" + it.option.key }) { e ->
                    OptionCard(
                        e.option, planned = null, choosing = state.choosing, saved = e.option.key in state.savedKeys, saveable = true,
                        onChoose = { viewModel.log(e.option.key) }, onSave = { viewModel.saveForOffline(e.option.key) }, chooseLabel = "Log",
                    )
                }
            }
        }
    }
}
