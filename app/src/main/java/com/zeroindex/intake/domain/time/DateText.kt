package com.zeroindex.intake.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The one date style of the app (QA Q6-7): the device locale's medium date, so a screen never mixes
 * "16 Nov 2026", "Nov 16" and "2026-11-16" for the same person. en-GB gives "16 Nov 2026", en-US "Nov 16, 2026".
 * Clock times keep their own formatters; this is for calendar dates only.
 */
object DateText {

    /** "16 Nov 2026" or "Nov 16, 2026", by [locale]. */
    fun medium(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    /** The medium date with the weekday in front: "Mon 16 Nov 2026" or "Mon, Nov 16, 2026". */
    fun mediumWithWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val lead = if (dayFirst(locale)) "EEE " else "EEE, "
        return DateTimeFormatter.ofPattern(lead + mediumPattern(locale), locale).format(date)
    }

    fun medium(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        medium(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), locale)

    fun mediumWithWeekday(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        mediumWithWeekday(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), locale)

    private fun mediumPattern(locale: Locale): String =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.MEDIUM, null, IsoChronology.INSTANCE, locale)

    private fun dayFirst(locale: Locale): Boolean {
        val pattern = mediumPattern(locale)
        val day = pattern.indexOf('d')
        val month = pattern.indexOf('M')
        return day in 0 until (if (month < 0) Int.MAX_VALUE else month)
    }
}
