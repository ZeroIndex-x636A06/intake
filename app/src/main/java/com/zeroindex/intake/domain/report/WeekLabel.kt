package com.zeroindex.intake.domain.report

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The period a weekly report covers, as a short label for its chip: "Nov 9–15", "Oct 30 – Nov 5" across a
 * month, and with the year on both ends across a year ("Dec 29, 2025 – Jan 4, 2026"). The report's week is
 * Monday to Sunday (`WeeklyReportRepository.mondayOf`), so a week starting [start] ends [start] + 6 days.
 *
 * Month names and the day-before-month order follow [locale] ("9–15 Nov" for en-GB).
 */
object WeekLabel {

    const val DAYS = 7L

    fun forWeek(start: LocalDate, locale: Locale = Locale.getDefault()): String =
        range(start, start.plusDays(DAYS - 1), locale)

    fun range(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
        val month = DateTimeFormatter.ofPattern("MMM", locale)
        val dayFirst = isDayFirst(locale)
        fun md(d: LocalDate) = if (dayFirst) "${d.dayOfMonth} ${d.format(month)}" else "${d.format(month)} ${d.dayOfMonth}"
        fun mdy(d: LocalDate) = if (dayFirst) "${md(d)} ${d.year}" else "${md(d)}, ${d.year}"
        return when {
            start.year != end.year -> "${mdy(start)} – ${mdy(end)}"
            start.month != end.month -> "${md(start)} – ${md(end)}"
            dayFirst -> "${start.dayOfMonth}–${end.dayOfMonth} ${start.format(month)}"
            else -> "${start.format(month)} ${start.dayOfMonth}–${end.dayOfMonth}"
        }
    }

    /** True when the locale writes the day before the month ("9 Nov 2026"), judged from its medium date pattern. */
    internal fun isDayFirst(locale: Locale): Boolean {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)
        val day = pattern.indexOf('d')
        val month = pattern.indexOf('M')
        return day in 0 until (if (month < 0) Int.MAX_VALUE else month)
    }
}
