package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.profile.BaselineSnapshot
import com.zeroindex.intake.domain.goals.BaselineReadiness
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.rememberSweepFraction
import androidx.compose.runtime.getValue
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.screens.today.WorkoutCardState

/**
 * "Baseline: day 3 of 7". Small on purpose: what is still missing and why it matters, one tap
 * to the profile. When the rule is met it says "Ready" whether or not a notification arrived.
 */
@Composable
fun BaselineCard(
    snapshot: BaselineSnapshot,
    onOpenProfile: () -> Unit,
    onBuildPlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = snapshot.progress
    IntakeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Baseline week")
                Text(p.headline, style = MaterialTheme.typography.titleSmall)
            }
            StatusPill(if (p.ready) "Ready" else "Collecting", tone = if (p.ready) PillTone.Ok else PillTone.Wait)
            InfoTip(
                text = "For a week Intake watches your normal eating and weight so your plan comes from your own " +
                    "numbers, not a formula. Food on ${BaselineReadiness.MIN_LOGGED_DAYS}+ days and " +
                    "${BaselineReadiness.MIN_WEIGH_IN_DAYS}+ weigh-ins are needed.",
                label = "Baseline week",
            )
        }
        val fraction = (p.dayNumber / BaselineReadiness.MIN_DAYS.toFloat()).coerceIn(0f, 1f)
        val shownFraction by rememberSweepFraction(fraction)
        LinearProgressIndicator(
            progress = { shownFraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .semantics { stateDescription = "Day ${p.dayNumber} of ${BaselineReadiness.MIN_DAYS}" },
        )
        if (p.ready) {
            Text(
                "You have enough data to build your calories, macros, water and training.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBuildPlan, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale()) { Text("Build my plan") }
        } else {
            p.missing.take(2).forEach { m ->
                Text("Still needed: ${m.what}", style = MaterialTheme.typography.labelMedium)
                Text(m.why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            BaselineReadiness.extensionExplanation(p)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("See my baseline")
            }
        }
    }
}

/** A compact "Today's workout": the next day in the active program, or an invitation to make one. */
@Composable
fun WorkoutCard(
    state: WorkoutCardState,
    onStart: () -> Unit,
    onResume: (sessionId: String) -> Unit,
    onMakeProgram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state is WorkoutCardState.Loading) return
    IntakeCard(modifier) {
        Eyebrow("Today's workout")
        Spacer(Modifier.height(4.dp))
        when (state) {
            is WorkoutCardState.Next -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.dayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.programName} · day ${state.dayNumber} of ${state.daysInProgram}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp).pressScale()) { Text("Start") }
                }
            }
            is WorkoutCardState.InProgress -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Workout in progress", style = MaterialTheme.typography.titleMedium)
                        state.programName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = { onResume(state.sessionId) }, modifier = Modifier.heightIn(min = 48.dp).pressScale()) { Text("Resume") }
                }
            }
            WorkoutCardState.NoProgram -> {
                Text("No program yet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Answer a few questions and Intake builds one that fits your week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onMakeProgram, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Make one for me")
                }
            }
            WorkoutCardState.Loading -> Unit
        }
    }
}

/**
 * "One thing to do today": a short title, one line with a real number, and at most one button.
 * The on-track version has no button and sits quietly in the sand colour.
 */
@Composable
fun FocusCard(
    focus: com.zeroindex.intake.domain.coach.DailyFocus,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IntakeCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("One thing to do today")
                Text(focus.title, style = MaterialTheme.typography.titleSmall)
            }
            StatusPill(if (focus.onTrack) "On track" else "Next", tone = if (focus.onTrack) PillTone.Ok else PillTone.Wait)
            InfoTip(
                text = "Intake picks one thing from fixed rules, in this order: a waiting check-in, a missing weigh-in, " +
                    "protein far behind in the evening, today's workout, water behind, then your baseline week. No AI is used.",
                label = "One thing to do today",
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(focus.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (focus.buttonLabel != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale()) { Text(focus.buttonLabel) }
        }
    }
}
