@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.equipment.EquipmentGroup
import com.zeroindex.intake.domain.training.equipment.EquipmentItem
import com.zeroindex.intake.domain.training.equipment.EquipmentItems
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit

/** The quick picks (Full gym, Home dumbbells, Bodyweight only); one tap fills the item list. */
@Composable
fun EquipmentPresetChips(selected: EquipmentPreset, onSelect: (EquipmentPreset) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        EquipmentPreset.entries.filter { it != EquipmentPreset.CUSTOM }.forEach { p -> ChoiceChip(p.label, p == selected, { onSelect(p) }) }
        if (selected == EquipmentPreset.CUSTOM) ChoiceChip("My own list", true, { })
    }
}

/**
 * The grouped checklist of every piece of equipment (free weights, racks and benches, machines, other), with all / none per
 * group and the heaviest dumbbell. Used by Settings > Training > Equipment, the welcome flow and the program wizard.
 */
@Composable
fun EquipmentChecklist(
    selected: Set<String>,
    onChange: (Set<String>) -> Unit,
    dumbbellMaxKg: Double?,
    onDumbbellMax: (Double?) -> Unit,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    showDumbbellMax: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        EquipmentGroup.entries.forEach { group ->
            val items = EquipmentItems.inGroup(group)
            val ids = items.map { it.id }.toSet()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(selected + ids) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("All") }
                TextButton(onClick = { onChange(selected - ids) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("None") }
            }
            items.forEach { item -> ItemRow(item, item.id in selected) { on -> onChange(if (on) selected + item.id else selected - item.id) } }
            if (showDumbbellMax && group == EquipmentGroup.FREE_WEIGHTS && selected.any { it == EquipmentItem.DUMBBELLS_FIXED.id || it == EquipmentItem.DUMBBELLS_ADJUSTABLE.id }) {
                DumbbellMax(dumbbellMaxKg, onDumbbellMax, unit)
            }
        }
        Text(
            "${selected.size} of ${EquipmentItems.ALL.size} ticked. Exercises that need something you have not ticked are left out of plans and ranked lower in swaps.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ItemRow(item: EquipmentItem, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
            if (item.hint.isNotEmpty()) Text(item.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DumbbellMax(maxKg: Double?, onChange: (Double?) -> Unit, unit: WeightUnit) {
    var text by remember(unit) { mutableStateOf(maxKg?.let { UnitConverter.round(unit.fromKg(it), 1).toString().removeSuffix(".0") }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val clean = v.filter { it.isDigit() || it == '.' }.take(6)
            text = clean
            onChange(clean.toDoubleOrNull()?.takeIf { it > 0 }?.let { unit.toKg(it) })
        },
        label = { Text("Heaviest dumbbell (${unit.label})") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
    )
}
