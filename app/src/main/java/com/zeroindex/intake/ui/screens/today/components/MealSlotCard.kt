package com.zeroindex.intake.ui.screens.today.components

import com.zeroindex.intake.domain.units.KcalFormat
import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.Unlisted
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.components.MacroSplitBar
import com.zeroindex.intake.ui.screens.today.MealSlotUi
import com.zeroindex.intake.ui.screens.today.displayName
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IconCircleButton
import com.zeroindex.intake.ui.components.IconCircleStyle
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

/**
 * One of the four slot cards from SPEC §5, styled as the mockup's `.meal`:
 * the slot name with its kcal total opposite, then one compact line per food.
 * Long-press an entry for edit and delete, swipe it to copy into another slot.
 *
 * The mockup gives each card an "Add food" row rather than an icon button in
 * the header. That row doubles as the empty state, so a slot with nothing in it
 * reads as an invitation instead of a blank.
 */
@Composable
fun MealSlotCard(
    slotUi: MealSlotUi,
    onAdd: () -> Unit,
    onEntryLongPress: (FoodLogEntity) -> Unit,
    onEntrySwipedToCopy: (FoodLogEntity) -> Unit,
    modifier: Modifier = Modifier,
    onSaveAsTemplate: (() -> Unit)? = null,
) {
    val slotName = slotUi.slot.displayName()
    val addLabel = if (slotUi.slot == MealSlot.DRINKS) "Log a drink" else "Log food"
    val empty = slotUi.entries.isEmpty()
    IntakeCard(modifier = modifier, contentPadding = PaddingValues(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = if (empty) "Not logged" else "${KcalFormat.format(slotUi.kcal)} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (empty) {
                    Spacer(Modifier.width(10.dp))
                    // The empty slot's invitation: a 44 dp soft round plus.
                    IconCircleButton(
                        icon = IntakeIcons.Plus,
                        contentDescription = if (slotUi.slot == MealSlot.DRINKS) "Log a drink" else "Log food in ${slotName.lowercase()}",
                        onClick = onAdd,
                        style = IconCircleStyle.Soft,
                    )
                }
            }
            if (!empty) {
                MacroSplitBar(
                    proteinGrams = slotUi.entries.sumOf { it.protein },
                    carbGrams = slotUi.entries.sumOf { it.carbs },
                    fatGrams = slotUi.entries.sumOf { it.fat },
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                slotUi.entries.forEach { entry ->
                    SwipeableEntryRow(
                        entry = entry,
                        onLongPress = { onEntryLongPress(entry) },
                        onSwipedToCopy = { onEntrySwipedToCopy(entry) },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftButton(onClick = onAdd, small = true) { Text(addLabel) }
                    Spacer(Modifier.weight(1f))
                    if (onSaveAsTemplate != null) {
                        GhostButton(onClick = onSaveAsTemplate) { Text("Save as meal template") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableEntryRow(
    entry: FoodLogEntity,
    onLongPress: () -> Unit,
    onSwipedToCopy: () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.45f },
    )

    // A swipe means "copy somewhere else", not "delete", so the row springs
    // back once the picker has been opened.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            onSwipedToCopy()
            state.reset()
        }
    }

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.intake.soft)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Copy to another meal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        content = { EntryRow(entry = entry, onLongPress = onLongPress) },
    )
}

@Composable
private fun EntryRow(entry: FoodLogEntity, onLongPress: () -> Unit) {
    // The name with its portion, then the energy and protein in a small line below (the mockup's Meals rows).
    val name = buildString {
        append(entry.foodNameAtLog)
        entry.quantityGrams?.let { append(" · ${it.roundToInt()} g") }
        if (entry.wasEdited) append(" · edited")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onLongPress,
                onLongClick = onLongPress,
                onClickLabel = "Show actions for ${entry.foodNameAtLog}",
            )
            .heightIn(min = IntakeSpacing.TouchTarget)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "${KcalFormat.format(entry.kcal)} kcal · " +
                if (CoreMacro.PROTEIN in Unlisted.decodeMacros(entry.unlistedNutrients)) "protein not listed" else "${entry.protein.roundToInt()} g protein",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
