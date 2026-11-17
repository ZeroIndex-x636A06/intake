package com.zeroindex.intake.domain.coach

import com.zeroindex.intake.domain.report.WeekLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The days a stored check-in looked at: the [windowDays] days ending on its day, the same trailing window
 * [CoachEngine.review] uses. The weekly check-in screen shows calendar weeks (Monday to Sunday), so this says
 * which calendar week a check-in mostly describes and how to label its own days (Q6-4).
 */
object CheckInPeriod {

    fun start(checkInDay: Long, windowDays: Int = CoachConfig().windowDays): LocalDate =
        LocalDate.ofEpochDay(checkInDay - windowDays + 1)

    /** "10–16 Nov": the check-in's own days, in the device's date order. */
    fun label(checkInDay: Long, windowDays: Int = CoachConfig().windowDays, locale: Locale = Locale.getDefault()): String =
        WeekLabel.range(start(checkInDay, windowDays), LocalDate.ofEpochDay(checkInDay), locale)

    /** The Monday of the calendar week holding most of the check-in's days (the later week on a tie). */
    fun weekCovering(checkInDay: Long, windowDays: Int = CoachConfig().windowDays): LocalDate {
        val first = start(checkInDay, windowDays)
        val perWeek = (0 until windowDays).map { first.plusDays(it.toLong()).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .groupingBy { it }.eachCount()
        return perWeek.entries.sortedWith(compareBy({ it.value }, { it.key })).last().key
    }
}
