package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.domain.health.ActivityToday

/**
 * The activity tile beside the weight tile: steps first, then active kcal and last night's sleep as small lines, each only
 * when Health Connect has it (a figure with no data is left out, not shown as zero). The caller shows it only when
 * [activity] is not null (no data, no permission, no card).
 */
@Composable
fun ActivityCard(activity: ActivityToday, modifier: Modifier = Modifier) {
    StatCard("Activity", modifier) {
        activity.steps?.let { BigFigure("%,d".format(it), "steps") }
        activity.activeKcal?.let { Text("%,d active kcal".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        activity.sleepMinutes?.let { Text("${it / 60} h ${it % 60} min slept last night", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("from Health Connect", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
