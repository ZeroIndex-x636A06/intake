package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.ui.components.IntakeCard

/**
 * Amendment §3's daily insight, as mockup v2's sand `.insight` card: marked
 * optional, dismissable in words, and with a disclosure that shows exactly the
 * digest that was sent, so what left the phone is never a mystery.
 */
@Composable
fun InsightCard(insight: AiInsightEntity, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var showWhy by rememberSaveable(insight.id) { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onSurface
    // A white card with a 1.5 dp AI-tinted outline (`box-shadow: inset 0 0 0 1.5px var(--ai-bg)`).
    IntakeCard(modifier = modifier.border(1.5.dp, MaterialTheme.intake.aiBg, IntakeShape.Card)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                IntakeChip("Today's insight · optional", tone = ChipTone.Ai, icon = IntakeIcons.Sparkle)
            }
            GhostButton(
                onClick = onDismiss,
                modifier = Modifier.semantics { contentDescription = "Dismiss today's insight" },
            ) { Text("Dismiss") }
        }
        Text(
            insight.text,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        GhostButton(
            onClick = { showWhy = !showWhy },
            modifier = Modifier.semantics { stateDescription = if (showWhy) "Expanded" else "Collapsed" },
        ) {
            Text(if (showWhy) "Why this suggestion?  ⌃" else "Why this suggestion?  ⌄")
        }
        AnimatedVisibility(showWhy) {
            Column(Modifier.padding(bottom = 4.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
