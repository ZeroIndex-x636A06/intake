package com.zeroindex.intake.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.onboarding.TipKey
import com.zeroindex.intake.domain.onboarding.TipsSeen
import com.zeroindex.intake.ui.theme.IntakeShape

/** The two-sentence copy shown the first time each tab is opened. */
object TabTipCopy {
    const val FOOD = "Log what you eat, plan meals and check allergens here. Start by logging your next meal."
    const val TRAIN = "Build, generate or pick a program, or just start an empty workout."
    const val PROGRESS = "Weigh in a few mornings a week; your trend appears after a few days."
    const val MORE = "Profile & goals, settings, backups and help live here."
}

/**
 * A dismissible first-visit note at the top of a tab. Dismissing, or tapping its button, stores
 * that it was seen for this tab only. Nothing shows until the stored flags have loaded.
 */
@Composable
fun TabTip(
    key: TipKey,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TipsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    // A person still in the welcome flow gets the flow first, not tips.
    if (!prefs.loaded || !prefs.done || !TipsSeen.shouldShow(key, prefs.tipsSeen)) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = IntakeShape.Banner,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.dismiss(key) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Got it")
                }
                TextButton(
                    onClick = {
                        viewModel.dismiss(key)
                        onAction()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(actionLabel) }
            }
        }
    }
}
