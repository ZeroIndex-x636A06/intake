package com.zeroindex.intake.ui.screens.train

import com.zeroindex.intake.domain.time.DateText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Spacer
import com.zeroindex.intake.domain.training.history.TrainingCalendar
import java.time.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.domain.training.log.SessionLogRow
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Training log: every session, newest first, grouped by week. A row is the date, the program or day, the length, sets,
 * volume and records; tapping it opens the session (every set, notes, Repeat this workout). Per-exercise stats are on the
 * exercise's own page, under Train > Exercises.
 */
@Composable
fun WorkoutHistoryScreen(
    contentPadding: PaddingValues,
    onOpenSession: (sessionId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayChoices by viewModel.dayChoices.collectAsStateWithLifecycle()
    val unit = state.settings.weightUnit
    var calendar by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var logging by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = trainPadding(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "controls") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IntakeChip("List", tone = if (!calendar) ChipTone.Ink else ChipTone.Neutral, onClick = { calendar = false })
                IntakeChip("Calendar", tone = if (calendar) ChipTone.Ink else ChipTone.Neutral, onClick = { calendar = true })
                Spacer(Modifier.weight(1f))
                LineButton(onClick = { logging = true }, small = true) {
                    Text("Log a past workout")
                }
            }
        }
        if (calendar) {
            item(key = "calendar") {
                TrainingCalendarView(
                    sessionStarts = remember(state.rows) { state.rows.map { it.startedAt } },
                    zone = zone,
                    today = viewModel.today(),
                    selected = selectedDay?.let { LocalDate.parse(it) },
                    onSelect = { selectedDay = it.toString() },
                )
            }
            item(key = "day") {
                val day = selectedDay?.let { LocalDate.parse(it) }
                val onDay = if (day == null) emptyList() else TrainingCalendar.on(day, state.rows, zone) { it.startedAt }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        day == null -> Text(
                            "Tap a day to see its sessions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        onDay.isEmpty() -> Text(
                            DAY_TITLE.format(day) + ": no training logged.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            Eyebrow(DAY_TITLE.format(day))
                            ListCard {
                                Column {
                                    onDay.forEachIndexed { i, s ->
                                        SessionRow(s, unit) { onOpenSession(s.sessionId) }
                                        if (i != onDay.lastIndex) ListDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.loaded && state.weeks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = IntakeIcons.History,
                    title = "No sessions yet",
                    body = "Open the Train tab and tap Start workout. Finished sessions, including ones you start from your watch, show up here.",
                )
            }
        }
        if (!calendar) state.weeks.forEach { week ->
            item(key = "week-${week.weekStart}") {
                SectionLine(
                    week.label,
                    note = "${week.sessions.size} ${if (week.sessions.size == 1) "session" else "sessions"} · ${TrainText.thousands(unit.fromKg(week.volumeKg))} ${unit.label}",
                )
            }
            item(key = "list-${week.weekStart}") {
                ListCard {
                    Column {
                        week.sessions.forEachIndexed { i, s ->
                            SessionRow(s, unit) { onOpenSession(s.sessionId) }
                            if (i != week.sessions.lastIndex) ListDivider()
                        }
                    }
                }
            }
        }
    }

    if (logging) {
        PastWorkoutDialog(
            today = viewModel.today(),
            days = dayChoices,
            problemWith = viewModel::problemWith,
            onCreate = { date, time, minutes, dayId ->
                logging = false
                viewModel.logPast(date, time, minutes, dayId, onOpenSession)
            },
            onDismiss = { logging = false },
        )
    }
}

@Composable
private fun SessionRow(s: SessionLogRow, unit: WeightUnit, onClick: () -> Unit) {
    val date = DateText.mediumWithWeekday(s.startedAt)
    val facts = buildList {
        add(if (s.unfinished) "Unfinished" else s.durationMs?.let { com.zeroindex.intake.domain.training.WorkoutDuration.short(it) } ?: "")
        add("${s.workingSets} ${if (s.workingSets == 1) "set" else "sets"}")
        if (s.volumeKg > 0) add("${TrainText.thousands(unit.fromKg(s.volumeKg))} ${unit.label}")
    }.filter { it.isNotEmpty() }.joinToString(" · ")
    val spoken = "${s.title}, $date, $facts" + if (s.prCount > 0) ", ${s.prCount} ${if (s.prCount == 1) "record" else "records"}" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open this session", onClick = onClick)
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.title, style = MaterialTheme.typography.titleMedium)
            s.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s.prCount > 0) {
            IntakeChip("${s.prCount} PR", tone = ChipTone.Lime, icon = IntakeIcons.Trophy)
        }
    }
}

private val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
