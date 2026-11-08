@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything about water that is not a one-tap log: a custom amount, the day's
 * entries with delete and undo, the presets, and how the target is set.
 */
@Composable
fun HydrationSheet(
    day: HydrationDay,
    presets: List<HydrationPresetEntity>,
    unit: VolumeUnit,
    manualBaseMl: Double?,
    dynamic: Boolean,
    onDismiss: () -> Unit,
    onLog: (Double) -> Unit,
    onDelete: (HydrationLogEntity) -> Unit,
    onRestore: (HydrationLogEntity) -> Unit,
    onSavePreset: (String, Double, HydrationPresetEntity?) -> Unit,
    onDeletePreset: (HydrationPresetEntity) -> Unit,
    onSetUnit: (VolumeUnit) -> Unit,
    onSetBase: (Double?) -> Unit,
    onSetDynamic: (Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    var lastDeleted by remember { mutableStateOf<HydrationLogEntity?>(null) }
    var editing by remember { mutableStateOf<HydrationPresetEntity?>(null) }
    var addingPreset by remember { mutableStateOf(false) }
    val time = remember { DateTimeFormatter.ofPattern("h:mm a") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text("Water", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "${unit.format(day.consumedMl)} of ${unit.format(day.target.totalMl)} today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            Eyebrow("Log a custom amount")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = custom,
                    onValueChange = { next -> custom = next.filter { it.isDigit() || it == '.' } },
                    label = { Text(unit.label) },
                    singleLine = true,
                    shape = IntakeShape.Field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = {
                        custom.toDoubleOrNull()?.takeIf { it > 0 }?.let { onLog(unit.toMl(it)) }
                        custom = ""
                    },
                    enabled = (custom.toDoubleOrNull() ?: 0.0) > 0,
                    shape = IntakeShape.Button,
                    modifier = Modifier.heightIn(min = 52.dp),
                ) { Text("Add") }
            }

            Spacer(Modifier.height(16.dp))
            Eyebrow("Today")
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
                    Text(
                        text = time.format(Instant.ofEpochMilli(log.loggedAt).atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.35f),
                    )
                    Text(
                        text = unit.format(log.volumeMl),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(0.65f),
                    )
                    IconButton(onClick = {
                        onDelete(log)
                        lastDeleted = log
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${unit.format(log.volumeMl)}")
                    }
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
                    }) { Text("Undo") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Eyebrow("Presets")
            presets.forEach { preset ->
                if (editing?.id == preset.id) {
                    PresetEditor(
                        unit = unit,
                        initialName = preset.name,
                        initialMl = preset.volumeMl,
                        onSave = { name, ml ->
                            onSavePreset(name, ml, preset)
                            editing = null
                        },
                        onCancel = { editing = null },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(preset.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            unit.format(preset.volumeMl),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { editing = preset }) { Text("Edit") }
                        IconButton(onClick = { onDeletePreset(preset) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete preset ${preset.name}")
                        }
                    }
                }
            }
            if (addingPreset) {
                PresetEditor(
                    unit = unit,
                    initialName = "",
                    initialMl = null,
                    onSave = { name, ml ->
                        onSavePreset(name, ml, null)
                        addingPreset = false
                    },
                    onCancel = { addingPreset = false },
                )
            } else {
                OutlinedButton(
                    onClick = { addingPreset = true },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Add a preset") }
            }

            Spacer(Modifier.height(16.dp))
            Eyebrow("Target")
            SettingRow(title = "Unit") {
                Segmented(
                    options = VolumeUnit.entries.map { it.label },
                    selectedIndex = VolumeUnit.entries.indexOf(unit),
                    onSelect = { onSetUnit(VolumeUnit.entries[it]) },
                )
            }
            SettingRow(
                title = "Adjust for training and activity",
                subtitle = "Adds water on training days and for measured active calories",
            ) {
                Switch(checked = dynamic, onCheckedChange = onSetDynamic)
            }
            var baseText by remember(manualBaseMl, unit) {
                mutableStateOf(manualBaseMl?.let { UnitConverter.round(unit.fromMl(it), 0).toInt().toString() } ?: "")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(
                    value = baseText,
                    onValueChange = { next -> baseText = next.filter { it.isDigit() || it == '.' } },
                    label = { Text("Daily base (${unit.label}) · blank = from bodyweight") },
                    singleLine = true,
                    shape = IntakeShape.Field,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(
                    onClick = { onSetBase(baseText.toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toMl)) },
                    shape = IntakeShape.Chip,
                ) { Text("Set") }
            }
            Text(
                text = "Without a number here, the base is 35 ml per kg of your latest weigh-in.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun PresetEditor(
    unit: VolumeUnit,
    initialName: String,
    initialMl: Double?,
    onSave: (String, Double) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember {
        mutableStateOf(initialMl?.let { UnitConverter.round(unit.fromMl(it), 1).toString() } ?: "")
    }
    Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            shape = IntakeShape.Field,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { next -> amount = next.filter { it.isDigit() || it == '.' } },
            label = { Text("Amount (${unit.label})") },
            singleLine = true,
            shape = IntakeShape.Field,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, shape = IntakeShape.Chip) { Text("Cancel") }
            Button(
                onClick = { amount.toDoubleOrNull()?.let { onSave(name.trim(), unit.toMl(it)) } },
                enabled = name.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                shape = IntakeShape.Chip,
            ) { Text("Save") }
        }
    }
}
