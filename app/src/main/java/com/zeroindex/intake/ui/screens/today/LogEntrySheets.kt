@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.today

import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.Unlisted
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.model.MealSlot
import kotlin.math.roundToInt

/** SPEC §5: long-press a food to edit or delete it. */
@Composable
fun LogEntryActionsSheet(
    entry: FoodLogEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onQuantityChange: (Double) -> Unit,
    onMoveToSlot: (MealSlot) -> Unit,
    onCopyToSlot: (MealSlot) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picking by remember { mutableStateOf<PickMode?>(null) }
    var grams by remember {
        mutableStateOf(entry.quantityGrams?.let { it.roundToInt().toString() } ?: "")
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        ) {
            Text(
                text = entry.foodNameAtLog,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
            )
            Text(
                text = run {
                    val unknown = Unlisted.decodeMacros(entry.unlistedNutrients)
                    fun g(v: Double, m: CoreMacro) = if (m in unknown) "${m.label} not listed" else "${v.roundToInt()} g ${m.label}"
                    "${entry.kcal.roundToInt()} kcal · ${g(entry.protein, CoreMacro.PROTEIN)} · " +
                        "${g(entry.carbs, CoreMacro.CARBS)} · ${g(entry.fat, CoreMacro.FAT)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))

            if (entry.quantityGrams != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = grams,
                        onValueChange = { grams = it.filter(Char::isDigit) },
                        label = { Text("Grams") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { grams.toDoubleOrNull()?.let(onQuantityChange) },
                        enabled = grams.toDoubleOrNull()?.let { it > 0 } == true,
                        modifier = Modifier.height(56.dp),
                    ) { Text("Update") }
                }
                Spacer(Modifier.height(8.dp))
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Copy to another meal") },
                leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                modifier = Modifier.clickableRow { picking = PickMode.COPY },
            )
            ListItem(
                headlineContent = { Text("Move to another meal") },
                leadingContent = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                modifier = Modifier.clickableRow { picking = PickMode.MOVE },
            )
            ListItem(
                headlineContent = { Text("Delete this entry") },
                leadingContent = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickableRow(onDelete),
            )

            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Cancel")
            }
        }
    }

    picking?.let { mode ->
        SlotPickerSheet(
            title = if (mode == PickMode.COPY) "Copy to" else "Move to",
            onDismiss = { picking = null },
            onPick = { slot ->
                if (mode == PickMode.COPY) onCopyToSlot(slot) else onMoveToSlot(slot)
                picking = null
            },
        )
    }
}

private enum class PickMode { COPY, MOVE }

@Composable
fun SlotPickerSheet(
    title: String,
    onDismiss: () -> Unit,
    onPick: (MealSlot) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
            MealSlot.entries.forEach { slot ->
                ListItem(
                    headlineContent = { Text(slot.displayName()) },
                    modifier = Modifier.clickableRow { onPick(slot) },
                )
            }
        }
    }
}

/** Rows stay at or above the 48dp minimum touch target (SPEC §3.7). */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick)
