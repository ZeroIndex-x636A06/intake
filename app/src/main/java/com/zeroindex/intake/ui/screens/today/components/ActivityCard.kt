package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.health.ActivityToday
import com.zeroindex.intake.ui.components.IntakeCard

/**
 * Today's steps, last night's sleep and active calories, each only when Health Connect has it: a figure with no data is
 * left out, not shown as zero. The caller shows the card only when [activity] is not null (no data, no permission, no card).
 */
@Composable
fun ActivityCard(activity: ActivityToday, modifier: Modifier = Modifier) {
    IntakeCard(modifier) {
        Text("Activity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            activity.steps?.let { Figure("%,d".format(it), "steps", Modifier.weight(1f)) }
            activity.sleepMinutes?.let { Figure("${it / 60} h ${it % 60} min", "slept last night", Modifier.weight(1f)) }
            activity.activeKcal?.let { Figure("%,d".format(it), "active kcal", Modifier.weight(1f)) }
        }
        Text(
            "from Health Connect",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
