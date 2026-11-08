@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
@Composable
fun HydrationSheet(
    day: HydrationDay,
    presets: List<HydrationPresetEntity>,
    unit: VolumeUnit,
    manualBaseMl: Double?,
    dynamic: Boolean,
    trainingBonusMl: Double,
    onDismiss: () -> Unit,
    onLog: (Double) -> Unit,
    onDelete: (HydrationLogEntity) -> Unit,
    onRestore: (HydrationLogEntity) -> Unit,
    onSavePreset: (String, Double, HydrationPresetEntity?) -> Unit,
    onDeletePreset: (HydrationPresetEntity) -> Unit,
    onSetUnit: (VolumeUnit) -> Unit,
    onSetBase: (Double?) -> Unit,
    onSetDynamic: (Boolean) -> Unit,
    onSetTrainingBonus: (Double) -> Unit,
) {
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
                Text("Water & presets", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
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
            if (day.target.reasons.isNotEmpty()) {
                Text(
                    "Dynamic target · " + day.target.reasons.joinToString(" · ") { "+${unit.format(it.ml)} ${it.label}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField(
                    label = "Custom amount (${unit.label})",
                    value = custom,
                    onValueChange = { custom = digits(it) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        custom.toDoubleOrNull()?.takeIf { it > 0 }?.let { onLog(unit.toMl(it)) }
                        custom = ""
                    },
                    enabled = (custom.toDoubleOrNull() ?: 0.0) > 0,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Add") }
            }

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
                            preset?.lowercase(),
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

            SectionLine(title = "Daily target") {
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
                "Leave the base blank to use 35 ml per kg of your latest weigh-in.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            SettingRow(
                title = "Adjust for training and activity",
                subtitle = "Adds the increase on training days, and water for measured active calories",
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

            SectionLine(title = "Quick-add presets", note = "First three show on Today")
            presets.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    pair.forEach { preset ->
                        Column(Modifier.weight(1f)) {
                            LabeledField(
                                label = "${preset.name} (${unit.label})",
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

            Button(
                onClick = {
                    val base = baseText.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                    if (base != manualBaseMl) onSetBase(base)
                    bonusText.toDoubleOrNull()?.let(unit::toMl)?.let { if (it != trainingBonusMl) onSetTrainingBonus(it) }
                    presets.forEach { preset ->
                        val ml = presetEdits[preset.id]?.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                        if (ml != null && kotlin.math.abs(ml - preset.volumeMl) > 0.5) onSavePreset(preset.name, ml, preset)
                    }
                    val addMl = newAmount.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)
                    if (newName.isNotBlank() && addMl != null) {
                        onSavePreset(newName.trim(), addMl, null)
                        newName = ""
                        newAmount = ""
                    }
                    onDismiss()
                },
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp).heightIn(min = 52.dp),
            ) { Text("Save water settings", fontWeight = FontWeight.Bold) }
        }
    }
}
