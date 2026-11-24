package com.zeroindex.intake.ui.screens.today.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.time.DateText
import com.zeroindex.intake.domain.time.WeekStrip
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.NumberSmall
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * The top bar's title on Today: a small "Today" (or "Earlier") line over the date, which opens the month calendar when tapped.
 */
@Composable
fun DayHeading(date: LocalDate, isToday: Boolean, onOpenCalendar: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clickable(onClickLabel = "Open the calendar", role = Role.Button, onClick = onOpenCalendar)
            .heightIn(min = IntakeSpacing.TouchTarget),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (isToday) "TODAY" else "EARLIER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                DateText.mediumWithWeekday(date),
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false).semantics { heading() },
            )
            Icon(IntakeIcons.ChevronDown, contentDescription = null, modifier = Modifier.size(IconSize.Small))
        }
    }
}

/**
 * The seven-day strip: seven equal day buttons, today on the right, the selected day filled ink. Swiping pages back (and
 * forward again) one week at a time; the selection only changes when a day is tapped. When the selected day moves to
 * another week (the calendar, the swipe on the page) the strip follows it. Future days are not shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayStrip(today: LocalDate, selected: LocalDate, onSelect: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val pageCount = WeekStrip.pageCount(today, selected)
    val selectedPage = WeekStrip.pageOf(today, selected)
    // Page 0 (today's week) is the last page, so swiping right goes back in time.
    val last = pageCount - 1
    val pager = rememberPagerState(initialPage = last - selectedPage, pageCount = { pageCount })
    LaunchedEffect(selectedPage, pageCount) {
        val target = last - selectedPage
        if (pager.currentPage != target) pager.animateScrollToPage(target)
    }
    HorizontalPager(state = pager, modifier = modifier.fillMaxWidth(), beyondViewportPageCount = 1) { index ->
        val days = WeekStrip.days(today, last - index)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day -> DayCell(day, today, selected, Modifier.weight(1f), onSelect) }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, selected: LocalDate, modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    val isSelected = day == selected
    val letter = weekdayInitial(day)
    val fill = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val ink = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val muted = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = fill,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        modifier = modifier
            .heightIn(min = 56.dp)
            .semantics {
                role = Role.Button
                this.selected = isSelected
                contentDescription = (if (day == today) "Today, " else "") + DateText.mediumWithWeekday(day)
            },
        onClick = { onSelect(day) },
    ) {
        Column(
            Modifier.padding(vertical = 6.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(letter, style = MaterialTheme.typography.bodySmall, color = muted, textAlign = TextAlign.Center)
            Text("${day.dayOfMonth}", style = NumberSmall.copy(fontWeight = FontWeight.SemiBold), color = ink, textAlign = TextAlign.Center)
        }
    }
}

/** The weekday's one-letter initial in the phone's language. Not composable, so lint's locale rule does not apply. */
private fun weekdayInitial(day: LocalDate): String = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

/** The month calendar behind the date heading: any day up to today. Choosing a day selects it and closes the dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayCalendarDialog(today: LocalDate, selected: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = com.zeroindex.intake.ui.components.rememberMondayFirstDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    WeekStrip.isSelectable(today, Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate())

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    onDismiss()
                },
            ) { Text("Show this day") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(today); onDismiss() }) { Text("Today") }
        },
    ) { DatePicker(state = state) }
}
