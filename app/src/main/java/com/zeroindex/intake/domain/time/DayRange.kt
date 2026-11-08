package com.zeroindex.intake.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * SPEC §3.4: everything is stored as UTC epoch millis and every date the user
 * sees is derived from their zone at render time. A local date string is never
 * stored, so "what did I eat today" becomes a half-open millis range computed
 * here, once, rather than string comparisons scattered through the DAOs.
 */
data class DayRange(
    val startInclusive: Long,
    val endExclusive: Long,
) {
    init {
        require(endExclusive > startInclusive) {
            "endExclusive ($endExclusive) must be after startInclusive ($startInclusive)"
        }
    }

    operator fun contains(epochMillis: Long): Boolean =
        epochMillis >= startInclusive && epochMillis < endExclusive

    companion object {

        /** The local day [date] occupies, in UTC millis, for [zone]. */
        fun of(date: LocalDate, zone: ZoneId): DayRange {
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return DayRange(start, end)
        }

        /** The local day containing [epochMillis]. */
        fun containing(epochMillis: Long, zone: ZoneId): DayRange =
            of(localDateOf(epochMillis, zone), zone)

        /**
         * A half-open range covering [days] local days ending with [lastDay],
         * used by the rolling windows in SPEC §8 and the charts in SPEC §11.
         */
        fun lastDays(days: Int, lastDay: LocalDate, zone: ZoneId): DayRange {
            require(days >= 1) { "days must be at least 1, was $days" }
            val first = lastDay.minusDays((days - 1).toLong())
            return DayRange(
                startInclusive = of(first, zone).startInclusive,
                endExclusive = of(lastDay, zone).endExclusive,
            )
        }
    }
}

/** The local calendar date [epochMillis] falls on in [zone]. */
fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/** Start-of-day UTC millis for the local date [epochMillis] falls on. */
fun startOfLocalDay(epochMillis: Long, zone: ZoneId): Long =
    DayRange.containing(epochMillis, zone).startInclusive
