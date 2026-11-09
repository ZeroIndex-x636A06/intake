package com.zeroindex.intake.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.remote.openmeteo.GeoPlace
import com.zeroindex.intake.data.settings.HydrationPrefs
import com.zeroindex.intake.data.settings.HydrationSettings
import com.zeroindex.intake.data.settings.WeatherLocationMode
import com.zeroindex.intake.data.weather.CoarseLocationProvider
import com.zeroindex.intake.data.weather.WeatherRepository
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherSettingsUi(
    val hasPermission: Boolean = false,
    val busy: Boolean = false,
    val results: List<GeoPlace> = emptyList(),
    val message: String? = null,
)

@HiltViewModel
class WeatherSettingsViewModel @Inject constructor(
    private val prefs: HydrationPrefs,
    private val weather: WeatherRepository,
    private val location: CoarseLocationProvider,
) : ViewModel() {
    val settings: StateFlow<HydrationSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HydrationSettings())

    private val _ui = MutableStateFlow(WeatherSettingsUi(hasPermission = location.hasPermission()))
    val ui: StateFlow<WeatherSettingsUi> = _ui.asStateFlow()

    fun refreshPermission() = _ui.update { it.copy(hasPermission = location.hasPermission()) }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        prefs.setWeatherEnabled(on)
        if (on) weather.refreshIfStale()
    }

    fun setMode(mode: WeatherLocationMode) = viewModelScope.launch {
        prefs.setLocationMode(mode)
        _ui.update { it.copy(results = emptyList(), message = null) }
    }

    /** One approximate fix, rounded to 0.1 degree in [CoarseLocationProvider], then the forecast. */
    fun useApproximateLocation() = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null) }
        val fix = location.currentFix()
        if (fix == null) {
            _ui.update { it.copy(busy = false, message = "Could not get an approximate location. You can type a city or ZIP code instead.") }
        } else {
            prefs.setLocation(fix.lat, fix.lon, null)
            weather.refreshIfStale()
            _ui.update { it.copy(busy = false) }
        }
    }

    fun search(query: String) = viewModelScope.launch {
        _ui.update { it.copy(busy = true, message = null, results = emptyList()) }
        val found = weather.geocode(query)
        _ui.update {
            it.copy(
                busy = false,
                results = found,
                message = if (found.isEmpty()) "No place found. Check the spelling, or check that you are online." else null,
            )
        }
    }

    fun choose(place: GeoPlace) = viewModelScope.launch {
        prefs.setLocation(place.lat, place.lon, place.label)
        _ui.update { it.copy(results = emptyList(), message = null) }
        weather.refreshIfStale()
    }

    fun clearLocation() = viewModelScope.launch { prefs.clearLocation() }
}

/**
 * Settings → "Adjust water for weather". Off by default. The two ways to give a location, and exactly what is
 * sent (docs/PRIVACY.md), are spelled out here, next to the switch.
 */
@Composable
fun WeatherSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: WeatherSettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission()
        if (granted) viewModel.useApproximateLocation()
    }

    Column(modifier) {
        SettingRow(
            title = "Adjust water for weather",
            subtitle = "Adds a little water to your target on hot days. Off until you turn it on.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoTip(
                    "Uses the day's forecast high (and humidity when it is hot): 25 to 30 °C adds 250 ml, 30 to 35 °C 500 ml, " +
                        "above 35 °C 750 ml, and a humid day a little more, up to 1 litre. Cooler days add nothing.",
                    label = "How weather changes the target",
                )
                Switch(checked = settings.weatherEnabled, onCheckedChange = viewModel::setEnabled)
            }
        }
        if (!settings.weatherEnabled) return@Column

        Eyebrow("Where")
        Segmented(
            options = listOf("Approximate location", "City or ZIP"),
            selectedIndex = if (settings.locationMode == WeatherLocationMode.APPROXIMATE) 0 else 1,
            onSelect = { viewModel.setMode(if (it == 0) WeatherLocationMode.APPROXIMATE else WeatherLocationMode.CITY) },
            modifier = Modifier.padding(vertical = 6.dp),
        )

        if (settings.locationMode == WeatherLocationMode.APPROXIMATE) {
            Text(
                "Uses the coarse permission only. The app takes one fix when Today opens and the forecast is a day old, " +
                    "rounds it to about 10 km, and sends only that.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (ui.hasPermission) viewModel.useApproximateLocation() else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                enabled = !ui.busy,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) { Text(if (ui.hasPermission) "Use my approximate location" else "Allow approximate location") }
        } else {
            Text(
                "Type a city or ZIP code. Only that text is sent, once, to find coordinates; the app keeps them rounded to about 10 km.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                LabeledField(
                    label = "City or ZIP code",
                    value = query,
                    onValueChange = { query = it.take(60) },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.search(query) },
                    enabled = query.trim().length >= 2 && !ui.busy,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Find") }
            }
            ui.results.forEach { place ->
                TextButton(onClick = { viewModel.choose(place) }, modifier = Modifier.fillMaxWidth()) {
                    Text(place.label, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        ui.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
        }

        if (settings.hasLocation) {
            Text(
                buildString {
                    append("Using ")
                    settings.placeName?.let { append("$it, ") }
                    append("%.1f, %.1f (rounded to about 10 km).".format(settings.lat, settings.lon))
                    settings.weatherCache?.let { append(" Today's high: ${it.day.tempMaxC.toInt()} °C.") }
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = viewModel::clearLocation) { Text("Forget this location") }
        } else {
            SandNote(body = "No location yet, so there is no weather adjustment.")
        }
        Text(
            "Weather data by Open-Meteo.com (CC BY 4.0). If you are offline the target simply has no weather line.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
