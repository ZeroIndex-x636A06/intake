package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.ui.screens.today.MealSlotUi
import com.zeroindex.intake.ui.screens.today.displayName
import kotlin.math.roundToInt

/**
 * One of the four slot cards from SPEC §5: its foods with kcal and protein, and
 * a plus button. Long-press an entry for edit and delete, swipe it to copy into
 * another slot.
 */
@Composable
fun MealSlotCard(
    slotUi: MealSlotUi,
    onAdd: () -> Unit,
    onEntryLongPress: (FoodLogEntity) -> Unit,
    onEntrySwipedToCopy: (FoodLogEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slotUi.slot.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (slotUi.entries.isEmpty()) {
                            "Nothing logged"
                        } else {
                            "${slotUi.kcal.roundToInt()} kcal · ${slotUi.protein.roundToInt()} g protein"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add food to ${slotUi.slot.displayName().lowercase()}",
                    )
                }
            }

            slotUi.entries.forEach { entry ->
                SwipeableEntryRow(
                    entry = entry,
                    onLongPress = { onEntryLongPress(entry) },
                    onSwipedToCopy = { onEntrySwipedToCopy(entry) },
                )
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
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "Copy to another meal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        content = { EntryRow(entry = entry, onLongPress = onLongPress) },
    )
}

@Composable
private fun EntryRow(entry: FoodLogEntity, onLongPress: () -> Unit) {
    val quantity = entry.quantityGrams
    val secondary = buildString {
        if (quantity != null) append("${quantity.roundToInt()} g")
        if (entry.wasEdited) {
            if (isNotEmpty()) append(" · ")
            append("edited")
        }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.foodNameAtLog,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${entry.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${entry.protein.roundToInt()} g protein",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(1.dp))
}
