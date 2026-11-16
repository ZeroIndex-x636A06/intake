package com.zeroindex.intake.domain.nutrition

import com.zeroindex.intake.domain.model.MealSlot
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The date maths behind the Nutrition tab's day switcher and the per-meal screen. Pure, so it is tested without a clock.
 *
 * **Future days are blocked.** The tab shows what was eaten, and a future day has nothing eaten. The next arrow stops at
 * today, the date picker greys out later days, and anything that still asks for one is clamped to today. The plan for
 * the days ahead lives in Plan, which is where [FUTURE_NOTE] points.
 *
 * Dates are local calendar days. A day runs from local midnight to the next local midnight (`DayRange`), and what is
 * stored stays UTC epoch millis: a local date is never stored.
 */
object NutritionDay {

    const val FUTURE_NOTE = "Future days are not shown here. Plan ahead in Plan."

    fun isFuture(date: LocalDate, today: LocalDate): Boolean = date.isAfter(today)

    /** [date], or [today] when it is later than today. */
    fun clamp(date: LocalDate, today: LocalDate): LocalDate = if (isFuture(date, today)) today else date

    /** [days] from [selected] (negative goes back), never later than today. */
    fun shift(selected: LocalDate, days: Long, today: LocalDate): LocalDate = clamp(selected.plusDays(days), today)

    fun canGoNext(selected: LocalDate, today: LocalDate): Boolean = selected.isBefore(today)

    /**
     * The material date picker reports a day as the UTC midnight of that calendar date, whatever the phone's zone.
     * Reads it back as that calendar date (so no zone can move it a day), clamped to today.
     */
    fun fromPickerMillis(utcMillis: Long, today: LocalDate): LocalDate =
        clamp(java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate(), today)

    /** The picker's initial selection for [date]: that calendar date's UTC midnight. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    /** "Today", "Yesterday", "Tue, Nov 10", or with the year when it is not this year. */
    fun label(date: LocalDate, today: LocalDate): String = when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }

    /** When a meal is usually eaten: the time a past day's entry lands at when nobody entered one. */
    fun usualTime(slot: MealSlot): LocalTime = when (slot) {
        MealSlot.BREAKFAST -> LocalTime.of(8, 0)
        MealSlot.LUNCH -> LocalTime.of(12, 30)
        MealSlot.DINNER -> LocalTime.of(18, 30)
        MealSlot.SNACK -> LocalTime.of(15, 30)
        MealSlot.DRINKS -> LocalTime.of(12, 0)
    }

    /**
     * The moment (UTC epoch millis) an entry logged on [date] gets. An entered [time] wins. Otherwise today is "now" and
     * any other day is the meal's usual time on that day, so the entry always lands on the day being viewed.
     */
    fun loggingMillis(date: LocalDate, slot: MealSlot, nowMillis: Long, zone: ZoneId, time: LocalTime? = null): Long {
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val at = time ?: if (date == today) return nowMillis else usualTime(slot)
        return date.atTime(at).atZone(zone).toInstant().toEpochMilli()
    }

    /** The local time of day of [millis], seconds dropped: what the time pill shows. */
    fun timeOf(millis: Long, zone: ZoneId): LocalTime =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().withSecond(0).withNano(0)
}
