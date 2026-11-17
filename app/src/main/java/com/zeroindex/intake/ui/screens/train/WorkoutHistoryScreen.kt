package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.theme.IntakeShape
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
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "controls") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !calendar, onClick = { calendar = false }, label = { Text("List") })
                FilterChip(selected = calendar, onClick = { calendar = true }, label = { Text("Calendar") })
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { logging = true }, shape = IntakeShape.Button, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Log a past workout")
                }
            }
        }
        if (calendar) {
            item(key = "calendar") {
                TrainingCalendarView(
                    sessionStarts = state.rows.map { it.startedAt },
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
                            Eyebrow(DAY_TITLE.format(day), color = MaterialTheme.colorScheme.primary)
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = IntakeShape.Card,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    onDay.forEachIndexed { i, s ->
                                        SessionRow(s, unit) { onOpenSession(s.sessionId) }
                                        if (i != onDay.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    title = "No sessions yet",
                    body = "Open the Train tab and tap Start workout. Finished sessions, including ones you start from your watch, show up here.",
                )
            }
        }
        if (!calendar) state.weeks.forEach { week ->
            item(key = "week-${week.weekStart}") {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow(week.label, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Text(
                        "${week.sessions.size} ${if (week.sessions.size == 1) "session" else "sessions"} · ${TrainText.thousands(unit.fromKg(week.volumeKg))} ${unit.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "list-${week.weekStart}") {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        week.sessions.forEachIndexed { i, s ->
                            SessionRow(s, unit) { onOpenSession(s.sessionId) }
                            if (i != week.sessions.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val date = DATE.format(Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault()))
    val facts = buildList {
        add(if (s.unfinished) "Unfinished" else s.minutes?.let { "$it min" } ?: "")
        add("${s.workingSets} ${if (s.workingSets == 1) "set" else "sets"}")
        if (s.volumeKg > 0) add("${TrainText.thousands(unit.fromKg(s.volumeKg))} ${unit.label}")
    }.filter { it.isNotEmpty() }.joinToString(" · ")
    val spoken = "${s.title}, $date, $facts" + if (s.prCount > 0) ", ${s.prCount} ${if (s.prCount == 1) "record" else "records"}" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open this session", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = IntakeShape.Button) {
                Text(
                    "${s.prCount} PR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
