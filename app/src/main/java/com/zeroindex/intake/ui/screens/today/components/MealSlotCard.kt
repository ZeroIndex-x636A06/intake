package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.screens.today.MealSlotUi
import com.zeroindex.intake.ui.screens.today.displayName
import com.zeroindex.intake.ui.theme.IntakeShape
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.InnerCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = slotName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (slotUi.entries.isEmpty()) {
                        "Not logged"
                    } else {
                        "${slotUi.kcal.roundToInt()} kcal"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            slotUi.entries.forEach { entry ->
                SwipeableEntryRow(
                    entry = entry,
                    onLongPress = { onEntryLongPress(entry) },
                    onSwipedToCopy = { onEntrySwipedToCopy(entry) },
                )
            }

            AddFoodRow(
                slotName = slotName,
                isEmpty = slotUi.entries.isEmpty(),
                onAdd = onAdd,
            )
            if (onSaveAsTemplate != null && slotUi.entries.isNotEmpty()) {
                TextButton(onClick = onSaveAsTemplate, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Save as meal prep", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * The mockup's empty-slot row ("Ready when you are · ＋ Add food"), kept on
 * filled slots too so adding to a specific meal is always one tap.
 */
@Composable
private fun AddFoodRow(slotName: String, isEmpty: Boolean, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Log food in ${slotName.lowercase()}", onClick = onAdd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEmpty) {
            Text(
                text = "Ready when you are",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Log food",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
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
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Copy to another meal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        content = { EntryRow(entry = entry, onLongPress = onLongPress) },
    )
}

@Composable
private fun EntryRow(entry: FoodLogEntity, onLongPress: () -> Unit) {
    // The mockup puts the portion beside the name and the energy opposite, so
    // each food reads as one line rather than a stacked pair.
    val name = buildString {
        append(entry.foodNameAtLog)
        entry.quantityGrams?.let { append(" · ${it.roundToInt()} g") }
        if (entry.wasEdited) append(" · edited")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = onLongPress,
                onLongClick = onLongPress,
                onClickLabel = "Show actions for ${entry.foodNameAtLog}",
            )
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        Text(
            text = "${entry.kcal.roundToInt()} kcal · ${entry.protein.roundToInt()} g protein",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(1.dp))
}
