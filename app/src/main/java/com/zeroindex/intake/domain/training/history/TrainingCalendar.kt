package com.zeroindex.intake.domain.training.history

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One cell of the month grid. [sessions] is how many sessions started that day. */
data class CalendarDay(val date: LocalDate, val inMonth: Boolean, val sessions: Int)

/** The Training log's month view: which days have training. Days are the person's local days. */
object TrainingCalendar {

    fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Whole weeks (rows of 7) covering [month], starting on [weekStart], with the sessions that began on each day. */
    fun month(month: YearMonth, sessionStarts: List<Long>, zone: ZoneId, weekStart: DayOfWeek = DayOfWeek.MONDAY): List<List<CalendarDay>> {
        val perDay = sessionStarts.groupingBy { dayOf(it, zone) }.eachCount()
        val first = month.atDay(1)
        val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val gridStart = first.minusDays(lead.toLong())
        val weeks = (lead + month.lengthOfMonth() + 6) / 7
        return (0 until weeks).map { w ->
            (0 until 7).map { d ->
                val date = gridStart.plusDays((w * 7 + d).toLong())
                CalendarDay(date, YearMonth.from(date) == month, perDay[date] ?: 0)
            }
        }
    }

    /** The items whose start falls on [date], in the order given. */
    fun <T> on(date: LocalDate, items: List<T>, zone: ZoneId, startOf: (T) -> Long): List<T> =
        items.filter { dayOf(startOf(it), zone) == date }

    /** The month to open first: that of the newest session, or [fallback] when there is none. */
    fun openingMonth(sessionStarts: List<Long>, zone: ZoneId, fallback: YearMonth): YearMonth =
        sessionStarts.maxOrNull()?.let { YearMonth.from(dayOf(it, zone)) } ?: fallback
}
