package com.zeroindex.intake.domain.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The date logic of Today's seven-day strip. The strip shows seven consecutive days and pages back week by week.
 * Page 0 is the last seven days ending today (the last six days and today); page 1 is the seven days before that, and so on.
 * Future days never appear and are never selectable. Pure Kotlin so it is unit-tested.
 */
object WeekStrip {
    const val DAYS = 7

    /** How many weeks the strip offers by default (about ten years). */
    const val MAX_PAGES = 530

    /** The last day shown on [page]: [today] for page 0, a week earlier for each page after. */
    fun lastDay(today: LocalDate, page: Int): LocalDate = today.minusDays(page.coerceAtLeast(0) * DAYS.toLong())

    /** The seven days of [page], oldest first. */
    fun days(today: LocalDate, page: Int): List<LocalDate> {
        val last = lastDay(today, page)
        return (DAYS - 1 downTo 0).map { last.minusDays(it.toLong()) }
    }

    /** The page whose window holds [date]. A future date counts as page 0. */
    fun pageOf(today: LocalDate, date: LocalDate): Int {
        val back = ChronoUnit.DAYS.between(date, today)
        if (back <= 0) return 0
        return (back / DAYS).toInt()
    }

    /** The number of pages a strip needs so it can show [selected] and at least [MAX_PAGES] weeks. */
    fun pageCount(today: LocalDate, selected: LocalDate): Int = maxOf(MAX_PAGES, pageOf(today, selected) + 1)

    /** A day can be selected when it is not after today. */
    fun isSelectable(today: LocalDate, date: LocalDate): Boolean = !date.isAfter(today)

    /** [date] limited to the selectable range (a future day becomes today). */
    fun clampToToday(today: LocalDate, date: LocalDate): LocalDate = if (date.isAfter(today)) today else date
}
