package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.training.history.TrainingCalendar
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalConfiguration

/**
 * A month of the Training log: days with training are filled, today has a ring, the day tapped is outlined. The month starts
 * on the newest session's month, and the arrows move a month at a time (forwards stops at this month).
 */
@Composable
fun TrainingCalendarView(
    sessionStarts: List<Long>,
    zone: ZoneId,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opening = TrainingCalendar.openingMonth(sessionStarts, zone, YearMonth.from(today))
    var monthText by rememberSaveable { mutableStateOf(opening.toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrDefault(opening)
    val weeks = TrainingCalendar.month(month, sessionStarts, zone)
    val trained = weeks.flatten().count { it.inMonth && it.sessions > 0 }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(IntakeSpacing.CardPadCompact)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(IntakeIcons.ChevronLeft, contentDescription = "Previous month", modifier = Modifier.size(IconSize.Default))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (trained == 0) "No training days" else "$trained training ${if (trained == 1) "day" else "days"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }, enabled = month < YearMonth.from(today)) {
                    Icon(IntakeIcons.ChevronRight, contentDescription = "Next month", modifier = Modifier.size(IconSize.Default))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                java.time.DayOfWeek.entries.forEach { d ->
                    Text(
                        d.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { day ->
                        DayCell(day.date, day.inMonth, day.sessions, day.date == today, day.date == selected, onSelect, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    sessions: Int,
    isToday: Boolean,
    isSelected: Boolean,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier,
) {
    val spoken = DateTimeFormatter.ofPattern("EEEE d MMMM").format(date) + when (sessions) {
        0 -> ", no training"
        1 -> ", 1 session"
        else -> ", $sessions sessions"
    }
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Show this day") { onSelect(date) }
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val mark = Modifier
            .size(38.dp)
            .clip(IntakeShape.Pill)
            .let { if (sessions > 0) it.background(primary) else it }
            .let {
                when {
                    isSelected -> it.border(2.dp, MaterialTheme.colorScheme.onSurface, IntakeShape.Pill)
                    isToday -> it.border(1.5.dp, primary, IntakeShape.Pill)
                    else -> it
                }
            }
        Box(mark, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    sessions > 0 -> MaterialTheme.colorScheme.onPrimary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}
