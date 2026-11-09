@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.size
import com.zeroindex.intake.domain.hydration.CaffeineAlcohol
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.hydration.TargetLine
import com.zeroindex.intake.domain.hydration.WeatherStatus
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.EquationStrip
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.screens.today.components.icon
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.WaterAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Mockup v2's "Water & presets" sheet: a custom amount, today's entries with
 * Undo, the daily target (base and training-day increase) and the quick-add
 * preset sizes, all saved with one button. Removing an entry is a soft delete
 * and can be restored until the sheet closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HydrationSheet(
    day: HydrationDay,
    presets: List<HydrationPresetEntity>,
    unit: VolumeUnit,
    weightUnit: WeightUnit,
    manualBaseMl: Double?,
    dynamic: Boolean,
    trainingBonusMl: Double,
    onDismiss: () -> Unit,
    onLog: (Double, DrinkType) -> Unit,
    onDelete: (HydrationLogEntity) -> Unit,
    onRestore: (HydrationLogEntity) -> Unit,
    onSavePreset: (String, Double, HydrationPresetEntity?, DrinkType?) -> Unit,
    onDeletePreset: (HydrationPresetEntity) -> Unit,
    onSetUnit: (VolumeUnit) -> Unit,
    onSetBase: (Double?) -> Unit,
    onSetDynamic: (Boolean) -> Unit,
    onSetTrainingBonus: (Double) -> Unit,
    extras: HydrationExtrasViewModel = hiltViewModel(),
) {
    val hydrationSettings by extras.settings.collectAsStateWithLifecycle()
    var drink by remember { mutableStateOf(DrinkType.WATER) }
    var newDrink by remember { mutableStateOf(DrinkType.WATER) }
    var showAdvanced by remember { mutableStateOf(false) }
    val edits = remember(hydrationSettings.drinkOverrides, hydrationSettings.sweat, unit, weightUnit) {
        AdvancedEdits(hydrationSettings, weightUnit, unit)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    var lastDeleted by remember { mutableStateOf<HydrationLogEntity?>(null) }
    val time = remember { DateTimeFormatter.ofPattern("h:mm a") }

    fun shown(ml: Double, places: Int = 0): String {
        val v = UnitConverter.round(unit.fromMl(ml), places)
        return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    }
    fun digits(text: String) = text.filter { it.isDigit() || it == '.' }

    var baseText by remember(manualBaseMl, unit) { mutableStateOf(manualBaseMl?.let { shown(it) } ?: "") }
    var bonusText by remember(trainingBonusMl, unit) { mutableStateOf(shown(trainingBonusMl)) }
    val presetEdits = remember(presets, unit) {
        mutableStateMapOf<String, String>().apply { presets.forEach { put(it.id, shown(it.volumeMl, 1)) } }
    }
    var newName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Water & drinks", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("Today", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${unit.formatNumber(day.consumedMl)} / ${unit.format(day.target.totalMl)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(day.progress.coerceIn(0f, 1f))
                        .background(WaterAccent, CircleShape),
                )
            }
            if (kotlin.math.abs(day.rawMl - day.consumedMl) >= 1.0) {
                Text(
                    "Poured ${unit.format(day.rawMl)} · counts as ${unit.format(day.consumedMl)} toward your target",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.totals.caffeineMg > 0) {
                Text(
                    "Caffeine today: about ${day.totals.caffeineMg.toInt()} mg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (day.totals.standardDrinks >= 0.1) {
                Text(
                    "Alcohol today: ${"%.1f".format(day.totals.standardDrinks)} standard drinks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Eyebrow("Add a drink")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                DrinkType.entries.forEach { type ->
                    FilterChip(
                        selected = drink == type,
                        onClick = { drink = type },
                        label = { Text(type.label) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            val profile = Drinks.profile(drink, hydrationSettings.drinkOverrides[drink])
            val typedMl = custom.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
            Text(
                drinkHint(drink, profile, typedMl, unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "Amount (${unit.label})",
                    value = custom,
                    onValueChange = { custom = digits(it) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        typedMl?.let { onLog(it, drink) }
                        custom = ""
                    },
                    enabled = typedMl != null,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Add") }
            }

            SectionLine(title = "How today's target adds up")
            val lines = listOf(day.breakdown.base) + day.breakdown.adjustments
            lines.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(line.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        (if (line.id == TargetLine.Id.BASE) "" else "+") + unit.format(line.ml),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    InfoTip(line.why, label = line.title)
                }
            }
            val extrasMl = day.breakdown.adjustments.sumOf { it.ml }
            EquationStrip(
                terms = listOf(
                    unit.formatNumber(day.breakdown.baseMl) to "base",
                    unit.formatNumber(extrasMl) to "added today",
                    unit.formatNumber(day.target.totalMl) to "target (${unit.label})",
                ),
                operators = listOf("+", "="),
            )
            if (!dynamic) {
                Text(
                    "Adjustments are switched off below, so the target is just the base.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                weatherSentence(day.weather, day.breakdown.adjustments.any { it.id == TargetLine.Id.WEATHER }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (day.sweatRateMeasured) {
                Text(
                    "Workout water uses your measured sweat rate.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SandNote(body = CaffeineAlcohol.TEA_COFFEE_COUNT)
            day.notes.forEach { SandNote(body = it) }

            Spacer(Modifier.height(14.dp))
            Eyebrow("Today's entries · tap undo to remove")
            if (day.logs.isEmpty()) {
                Text(
                    "Nothing logged yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            day.logs.forEach { log ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val preset = presets.firstOrNull { it.id == log.presetId }?.name
                    Text(
                        text = listOfNotNull(
                            time.format(Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault())),
                            unit.format(log.volumeMl),
                            log.drinkType.label.lowercase().takeIf { log.drinkType != DrinkType.WATER },
                            preset?.lowercase()?.takeIf { !it.equals(log.drinkType.label, ignoreCase = true) },
                            if (kotlin.math.abs(log.hydrationFactor - 1.0) > 0.001) {
                                "counts as ${unit.format(log.effectiveMl)}"
                            } else {
                                null
                            },
                            if (log.caffeineMg >= 1) "${log.caffeineMg.toInt()} mg caffeine" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onDelete(log)
                        lastDeleted = log
                    }) { Text("Undo", fontWeight = FontWeight.ExtraBold) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            lastDeleted?.let { removed ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Removed ${unit.format(removed.volumeMl)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        onRestore(removed)
                        lastDeleted = null
                    }) { Text("Put back") }
                }
            }

            SectionLine(title = "Daily target settings") {
                StatusPill(
                    if (dynamic) "Dynamic · +${unit.format(trainingBonusMl)} on training days" else "Fixed",
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "Base target (${unit.label})",
                    value = baseText,
                    onValueChange = { baseText = digits(it) },
                    placeholder = "From bodyweight",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                LabeledField(
                    label = "Training-day increase",
                    value = bonusText,
                    onValueChange = { bonusText = digits(it) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "Leave the base blank to use 35 ml per kg of your latest weigh-in. The training-day amount is used for a training day with no timed workout yet; a timed workout replaces it with its sweat estimate.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            SettingRow(
                title = "Adjust the target for the day",
                subtitle = "Adds water for workouts, activity, hot weather, and for lots of caffeine or alcohol",
            ) {
                Switch(checked = dynamic, onCheckedChange = onSetDynamic)
            }
            SettingRow(title = "Unit", showDivider = false) {
                Segmented(
                    options = VolumeUnit.entries.map { it.label },
                    selectedIndex = VolumeUnit.entries.indexOf(unit),
                    onSelect = { onSetUnit(VolumeUnit.entries[it]) },
                )
            }

            SectionLine(title = "Quick-add drinks", note = "The first six show on Today")
            presets.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    pair.forEach { preset ->
                        Column(Modifier.weight(1f)) {
                            LabeledField(
                                label = "${preset.name}${if (preset.drinkType != DrinkType.WATER) " · ${preset.drinkType.label.lowercase()}" else ""} (${unit.label})",
                                value = presetEdits[preset.id].orEmpty(),
                                onValueChange = { presetEdits[preset.id] = digits(it) },
                                keyboardType = KeyboardType.Decimal,
                            )
                            TextButton(onClick = { onDeletePreset(preset) }) { Text("Remove ${preset.name.lowercase()}") }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "New preset name",
                    value = newName,
                    onValueChange = { newName = it.take(24) },
                    placeholder = "e.g. Flask",
                    modifier = Modifier.weight(1f),
                )
                LabeledField(
                    label = "Amount (${unit.label})",
                    value = newAmount,
                    onValueChange = { newAmount = digits(it) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                DrinkType.entries.forEach { type ->
                    FilterChip(
                        selected = newDrink == type,
                        onClick = { newDrink = type },
                        label = { Text(type.label) },
                        leadingIcon = { Icon(type.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Advanced", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(
                        if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showAdvanced) "Hide advanced" else "Show advanced",
                    )
                }
            }
            if (showAdvanced) HydrationAdvanced(edits, weightUnit, unit)

            Button(
                onClick = {
                    val base = baseText.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                    if (base != manualBaseMl) onSetBase(base)
                    bonusText.toDoubleOrNull()?.let(unit::toMl)?.let { if (it != trainingBonusMl) onSetTrainingBonus(it) }
                    presets.forEach { preset ->
                        val ml = presetEdits[preset.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                        if (ml != null && kotlin.math.abs(ml - preset.volumeMl) > 0.5) onSavePreset(preset.name, ml, preset, null)
                    }
                    val addMl = newAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                    if (newName.isNotBlank() && addMl != null) {
                        onSavePreset(newName.trim(), addMl, null, newDrink)
                        newName = ""
                        newAmount = ""
                    }
                    if (showAdvanced) {
                        DrinkType.entries.forEach { type ->
                            val now = edits.overrideFor(type).takeIf { !it.isEmpty }
                            if (now != hydrationSettings.drinkOverrides[type]) extras.setOverride(type, now)
                        }
                        extras.saveSweat(edits.measurement(weightUnit, unit), edits.useMeasured)
                    }
                    onDismiss()
                },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).heightIn(min = 52.dp),
            ) { Text("Save water settings", fontWeight = FontWeight.Bold) }
        }
    }
}

/** What the chosen drink does, in a sentence, with the caffeine or alcohol for the typed amount. */
private fun drinkHint(
    type: DrinkType,
    profile: com.zeroindex.intake.domain.hydration.DrinkProfile,
    typedMl: Double?,
    unit: VolumeUnit,
): String {
    val parts = mutableListOf<String>()
    parts += when {
        type == DrinkType.TEA || type == DrinkType.COFFEE -> CaffeineAlcohol.TEA_COFFEE_COUNT
        profile.factor < 0 -> "Counts against your water: minus ${"%.0f".format(-profile.factor * 100)}% of the amount, the alcohol in it."
        kotlin.math.abs(profile.factor - 1.0) < 0.005 -> "Counts in full toward your water."
        profile.factor > 1.0 -> "Counts as ${"%.1f".format(profile.factor)} times its amount: it holds on to fluid a little longer."
        else -> "Counts as ${"%.0f".format(profile.factor * 100)}% of its amount."
    }
    if (typedMl != null) {
        val entry = Drinks.entry(typedMl, profile)
        if (entry.caffeineMg >= 1) parts += "About ${entry.caffeineMg.toInt()} mg caffeine."
        if (entry.standardDrinks >= 0.05) parts += "About ${"%.1f".format(entry.standardDrinks)} standard drinks."
        if (kotlin.math.abs(profile.factor - 1.0) > 0.005) parts += "Counts as ${unit.format(entry.effectiveMl)}."
    }
    return parts.joinToString(" ")
}

private fun weatherSentence(status: WeatherStatus, hasLine: Boolean): String = when (status) {
    WeatherStatus.Off -> "Weather: off. Turn it on in Settings, Adjust water for weather."
    WeatherStatus.NeedsLocation -> "Weather: no location set, so no weather adjustment. Choose one in Settings."
    WeatherStatus.Unavailable -> "Weather: today's forecast could not be loaded (no network?), so no weather adjustment."
    is WeatherStatus.Ready ->
        if (hasLine) "Weather: ${status.day.tempMaxC.toInt()} \u00B0C high today${status.place?.let { " near $it" } ?: ""}."
        else "Weather: ${status.day.tempMaxC.toInt()} \u00B0C high today, so nothing extra."
}
