package com.zeroindex.intake.ui.addfood

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.Eyebrow

/**
 * A saved recipe in Log food: name over "420 kcal · 35 g protein" per serving (or "not listed"), the allergen chip when
 * the person avoids anything, and a "+" that logs one serving after the allergen check. A recipe with no calorie
 * figure shows "+" greyed, and says why when tapped.
 */
@Composable
internal fun RecipeLogRow(row: RecipeRowUi, onLog: (RecipeRowUi) -> Unit) {
    val r = row.recipe
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 65.dp)
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(
                    "Recipe · per serving · ${r.summary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                row.allergen?.let {
                    Spacer(Modifier.size(4.dp))
                    RowAllergenChip(it)
                }
            }
            IconButton(onClick = { onLog(row) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (r.loggable) "Log one serving of ${r.name}" else "${r.name} has no calories listed",
                    tint = if (r.loggable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun RecipeSourceHeader(text: String) {
    Eyebrow(text = text, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
}
