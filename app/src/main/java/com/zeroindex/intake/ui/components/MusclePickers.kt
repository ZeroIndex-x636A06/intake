@file:OptIn(ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.muscles.MuscleHighlight
import com.zeroindex.intake.domain.training.muscles.MuscleMap

/**
 * Pick one main muscle and any number of "also works" muscles, by tapping the body or from the chip
 * list (the list is always there, so nothing depends on hitting a small target). A tap on the body
 * stands for the whole library muscle: tapping a lat selects "back".
 */
@Composable
fun MainAndAlsoMusclePicker(
    primary: String?,
    secondary: Set<String>,
    onPrimary: (String) -> Unit,
    onToggleSecondary: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(PickMode.MAIN) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ChoiceChip("Tap sets: main muscle", selected = mode == PickMode.MAIN, onClick = { mode = PickMode.MAIN })
            ChoiceChip("Tap sets: also works", selected = mode == PickMode.ALSO, onClick = { mode = PickMode.ALSO })
        }
        val highlight = MuscleHighlight(
            primary = primary?.let { MuscleMap.regionsOf(it) }.orEmpty(),
            secondary = secondary.flatMapTo(linkedSetOf()) { MuscleMap.regionsOf(it) } - primary?.let { MuscleMap.regionsOf(it) }.orEmpty(),
        )
        MuscleMapView(
            highlight = highlight,
            modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally),
            onRegionTap = { region ->
                if (mode == PickMode.MAIN) onPrimary(region.muscle) else if (region.muscle != primary) onToggleSecondary(region.muscle)
            },
        )
        Text("Or choose from the list", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Main muscle", style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MuscleMap.CUSTOM_MUSCLES.forEach { m -> ChoiceChip(Muscles.label(m), selected = primary == m, onClick = { onPrimary(m) }) }
        }
        Text("Also works (optional)", style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            MuscleMap.CUSTOM_MUSCLES.filter { it != primary }.forEach { m ->
                ChoiceChip(Muscles.label(m), selected = m in secondary, onClick = { onToggleSecondary(m) })
            }
        }
    }
}

private enum class PickMode { MAIN, ALSO }

/**
 * Pick the muscles to focus on (Profile & goals): tap the body or use the chips. Only the muscles the
 * generator programs can be chosen; a tap on a part with no such muscle does nothing.
 */
@Composable
fun FocusMusclePicker(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MuscleMapView(
            highlight = MuscleHighlight(primary = selected.flatMapTo(linkedSetOf()) { MuscleMap.regionsOf(it) }),
            modifier = Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally),
            onRegionTap = { region -> if (region.muscle in Muscles.PROGRAMMED) onToggle(region.muscle) },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Muscles.PROGRAMMED.forEach { m -> ChoiceChip(Muscles.label(m), selected = m in selected, onClick = { onToggle(m) }) }
        }
    }
}
