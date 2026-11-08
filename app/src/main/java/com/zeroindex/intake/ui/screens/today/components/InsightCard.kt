package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard

/**
 * Amendment §3's daily insight. The "why" shows exactly the digest that was
 * sent, so what left the phone is never a mystery.
 */
@Composable
fun InsightCard(insight: AiInsightEntity, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showWhy by rememberSaveable(insight.id) { mutableStateOf(false) }
    IntakeCard(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Today's suggestion", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss today's suggestion", tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        TextButton(onClick = { showWhy = !showWhy }) { Text(if (showWhy) "Hide what it was based on" else "Why this?") }
        AnimatedVisibility(showWhy) {
            Column(Modifier.padding(bottom = 4.dp)) {
                insight.rationale.lines().forEach {
                    Text("· $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    "Sent as numbers only to ${insight.model}. No food names, notes or location.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
