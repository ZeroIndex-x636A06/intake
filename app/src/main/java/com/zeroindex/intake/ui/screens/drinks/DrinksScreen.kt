package com.zeroindex.intake.ui.screens.drinks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SandNote
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrinksUiState(
    val day: HydrationDay? = null,
    val presets: List<HydrationPresetEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class DrinksViewModel @Inject constructor(
    private val hydration: HydrationRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<DrinksUiState> = combine(
        hydration.observeDay(clock.today()), hydration.observePresets(), settingsRepository.settings,
    ) { day, presets, settings -> DrinksUiState(day, presets, settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrinksUiState())

    fun log(volumeMl: Double, presetId: String?, drink: DrinkType) {
        viewModelScope.launch { hydration.log(volumeMl, clock.nowMillis(), presetId, drink) }
    }

    fun remove(log: HydrationLogEntity) { viewModelScope.launch { hydration.delete(log.id) } }
    fun restore(log: HydrationLogEntity) { viewModelScope.launch { hydration.restore(log) } }
}

/**
 * TEMPORARY Drinks page. Today's water card only adds or removes drinks already logged, so a new drink is logged here:
 * pick a drink, tap a size or type a custom amount. The Nutrition agent replaces this with the full page (categories, size
 * quick buttons, search over the food database). Settings for it (target, quick sizes, drink values, units) live in
 * Settings > Water and drinks and must not be added here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinksScreen(contentPadding: PaddingValues, modifier: Modifier = Modifier, viewModel: DrinksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unit = state.settings.volumeUnit
    var drink by remember { mutableStateOf(DrinkType.WATER) }
    var custom by remember { mutableStateOf("") }
    var lastRemoved by remember { mutableStateOf<HydrationLogEntity?>(null) }
    val time = remember { DateTimeFormatter.ofPattern("h:mm a") }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.day?.let { day ->
            item(key = "summary") {
                IntakeCard {
                    Text("${unit.format(day.consumedMl)} of ${unit.format(day.target.totalMl)}", style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(progress = { day.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    if (day.notes.isNotEmpty()) day.notes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item(key = "pick") {
            IntakeCard {
                Text("Log a drink", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    DrinkType.entries.forEach { d -> ChoiceChip(d.label, d == drink, { drink = d }) }
                }
                Text("Size", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.presets.forEach { p ->
                        ChoiceChip(unit.format(p.volumeMl), false, { viewModel.log(p.volumeMl, p.id, drink) })
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    LabeledField("Custom (${unit.label})", custom, { custom = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                    val ml = custom.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                    Button(onClick = { ml?.let { viewModel.log(it, null, drink); custom = "" } }, enabled = ml != null, modifier = Modifier.heightIn(min = 52.dp)) { Text("Add") }
                }
            }
        }
        item(key = "today-head") { SectionLine("Today's drinks") }
        val logs = state.day?.logs.orEmpty().sortedByDescending { it.loggedAt }
        if (logs.isEmpty()) item(key = "none") { SandNote("Nothing logged yet today.") }
        items(logs, key = { it.id }) { log ->
            IntakeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${log.drinkType.label} · ${unit.format(log.volumeMl)}", style = MaterialTheme.typography.titleSmall)
                        Text(time.format(Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { viewModel.remove(log); lastRemoved = log }) { Text("Remove") }
                }
            }
        }
        lastRemoved?.let { removed ->
            item(key = "undo") {
                TextButton(onClick = { viewModel.restore(removed); lastRemoved = null }) { Text("Undo remove of ${removed.drinkType.label.lowercase()}") }
            }
        }
    }
}
