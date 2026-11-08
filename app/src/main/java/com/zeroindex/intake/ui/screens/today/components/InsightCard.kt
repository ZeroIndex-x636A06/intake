package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard

/**
 * Amendment §3's daily insight, as mockup v2's sand `.insight` card: marked
 * optional, dismissable in words, and with a disclosure that shows exactly the
 * digest that was sent, so what left the phone is never a mystery.
 */
@Composable
fun InsightCard(insight: AiInsightEntity, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showWhy by rememberSaveable(insight.id) { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onSecondaryContainer
    IntakeCard(modifier = modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Today's insight · optional", color = ink, modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Dismiss today's insight" },
            ) { Text("Dismiss", fontWeight = FontWeight.Bold) }
        }
        Text(
            insight.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        TextButton(
            onClick = { showWhy = !showWhy },
            modifier = Modifier
                .heightIn(min = 44.dp)
                .semantics { stateDescription = if (showWhy) "Expanded" else "Collapsed" },
        ) {
            Text(
                if (showWhy) "Why this suggestion?  ⌃" else "Why this suggestion?  ⌄",
                color = ink,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AnimatedVisibility(showWhy) {
            Column(Modifier.padding(bottom = 4.dp)) {
                HorizontalDivider(color = ink.copy(alpha = 0.13f))
                insight.rationale.lines().filter { it.isNotBlank() }.forEach {
                    Text("· $it", style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "Sent as numbers only to ${insight.model}. No food names, notes or location.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
