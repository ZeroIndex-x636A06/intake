package com.zeroindex.intake.ui.screens.coach

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Column
import com.zeroindex.intake.ui.motion.MoreDetail
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.SuggestionStatus
import com.zeroindex.intake.domain.coach.WeekSummary
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.SwitchRow
import com.zeroindex.intake.domain.units.WeightUnit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The weekly check-in: the week at a glance, then each suggestion with Apply / Dismiss and an
 * expandable "why". Nothing changes until a button is tapped. Not seen on a device yet.
 */
@Composable
fun CoachCheckInScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: CoachCheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("Weekly check-in", "Your coach looks at the week and suggests small changes. You decide.")
        }
        state.message?.let { msg ->
            item(key = "message") {
                SandNote(msg)
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            }
        }
        state.summary?.let { s -> item(key = "summary") { WeekCard(s, state.weightUnit) } }

        val latest = state.latest
        if (latest == null) {
            item(key = "none") {
                IntakeCard {
                    Text("No check-in yet", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Log food and weigh in for a few days and your first check-in appears on its day. You can also run one now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::checkInNow, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Check in now")
                    }
                }
            }
        } else {
            item(key = "latest-head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Check-in of ${dayLabel(latest.record.day)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusPill(
                        if (latest.rows.isEmpty()) "All on track" else "${latest.rows.count { it.status == SuggestionStatus.PENDING }} to review",
                        tone = if (latest.rows.any { it.status == SuggestionStatus.PENDING }) PillTone.Wait else PillTone.Ok,
                    )
                }
            }
            if (latest.rows.isEmpty()) {
                item(key = "on-track") {
                    SandNote("Nothing to change this week. Your numbers are where they should be, so the best move is to keep going.")
                }
            }
            items(latest.rows, key = { it.id }) { row ->
                SuggestionCard(row, enabled = !state.working, onApply = { viewModel.apply(row) }, onDismiss = { viewModel.dismiss(row) })
            }
            item(key = "now") {
                TextButton(onClick = viewModel::checkInNow, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Check in now")
                }
            }
        }

        // Set once and forgotten, so folded away.
        item(key = "settings") {
            MoreDetail(title = "Check-in settings") { CheckInSettings(state, viewModel) }
        }

        if (state.past.isNotEmpty()) {
            item(key = "past") {
              MoreDetail(title = "Past check-ins (${state.past.size})") {
               Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.past.forEach { past ->
                IntakeCard {
                    Text(dayLabel(past.record.day), style = MaterialTheme.typography.titleSmall)
                    if (past.rows.isEmpty()) {
                        Text("All on track", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    past.rows.forEach { row ->
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.aiTitle ?: row.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            StatusPill(
                                when (row.status) {
                                    SuggestionStatus.APPLIED -> "Applied"
                                    SuggestionStatus.DISMISSED -> "Dismissed"
                                    SuggestionStatus.PENDING -> "Not decided"
                                },
                                tone = when (row.status) {
                                    SuggestionStatus.APPLIED -> PillTone.Ok
                                    else -> PillTone.Wait
                                },
                            )
                        }
                    }
                }
                }
               }
              }
            }
        }
    }
}

@Composable
private fun WeekCard(s: WeekSummary, unit: WeightUnit) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            InfoTip(
                text = "Weight uses your smoothed trend, not a single scale reading, so water and salt do not move it. " +
                    "Averages count only days with food logged.",
                label = "Your week",
            )
        }
        val change = s.trendChangeKg?.let {
            val v = unit.fromKg(it)
            (if (v >= 0) "+" else "−") + "%.1f".format(Locale.US, abs(v)) + " ${unit.label}"
        } ?: "not enough weigh-ins"
        val pct = s.trendPctPerWeek?.let { " (${if (it >= 0) "+" else "−"}${"%.1f".format(Locale.US, abs(it))}% a week)" }.orEmpty()
        SummaryLine("Weight trend", change + pct)
        SummaryLine("Calories (average)", s.avgKcal?.let { "%,d kcal".format(Locale.US, it) } ?: "no days logged")
        SummaryLine("Protein (average)", s.avgProteinG?.let { "$it g" } ?: "no days logged")
        SummaryLine("Days logged", "${s.daysLogged} of ${s.daysInWindow}")
        SummaryLine("Weigh-ins", "${s.weighIns}")
        SummaryLine("Workouts", "${s.workouts}")
        SummaryLine("Sleep (average)", s.avgSleepHours?.let { "%.1f h".format(Locale.US, it) } ?: "no data")
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SuggestionCard(
    row: CoachSuggestionEntity,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    val action = runCatching { kotlinx.serialization.json.Json.decodeFromString(CoachAction.serializer(), row.actionJson) }.getOrNull()
    val reworded = row.aiTitle != null || row.aiWhy != null
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(row.kind.label, modifier = Modifier.weight(1f))
            if (row.status != SuggestionStatus.PENDING) {
                StatusPill(if (row.status == SuggestionStatus.APPLIED) "Applied" else "Dismissed", tone = PillTone.Wait)
            }
        }
        Text(row.aiTitle ?: row.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(row.aiWhy ?: row.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && action !is CoachAction.Acknowledge) {
            Spacer(Modifier.height(4.dp))
            Text("Apply does: ${CoachCheckInViewModel.describe(action)}", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (open) "Hide the why" else "Why this?")
        }
        if (open) {
            row.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (reworded) {
                SandNote("Reworded with AI. The numbers and the action are the rule's own.\nOriginal: ${row.title}. ${row.why}")
            }
        }
        if (row.status == SuggestionStatus.PENDING) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(if (action == null || action is CoachAction.Acknowledge) "Got it" else "Apply")
                }
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun CheckInSettings(state: CheckInUiState, viewModel: CoachCheckInViewModel) {
    IntakeCard {
        Eyebrow("Check-in settings")
        Spacer(Modifier.height(6.dp))
        Text("Day of the week", style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            (1..7).forEach { d ->
                ChoiceChip(
                    label = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, androidx.compose.ui.text.intl.Locale.current.platformLocale),
                    selected = state.weekday == d,
                    onClick = { viewModel.setWeekday(d) },
                )
            }
        }
        if (!state.weekdayChosen) {
            Text(
                "Starts on the day your baseline week began, or Monday.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchRow(
            title = "Weekly notification",
            subtitle = "\"Your weekly check-in is ready\", once a week",
            on = state.notify,
            onToggle = { viewModel.setNotify(!state.notify) },
            onLabel = "On",
            offLabel = "Off",
        )
        SwitchRow(
            title = "Reword my check-in with AI",
            subtitle = if (state.aiConfigured) {
                "Off by default. Sends only the suggestion texts and their numbers to OpenRouter. The numbers and actions never change."
            } else {
                "Needs an OpenRouter key in Settings → AI. Everything else works without one."
            },
            on = state.aiReword && state.aiConfigured,
            onToggle = { viewModel.setAiReword(!state.aiReword) },
            onLabel = "On",
            offLabel = "Off",
            enabled = state.aiConfigured,
        )
        Text(
            "This is not medical advice. If something feels wrong, rest and talk to a doctor.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
