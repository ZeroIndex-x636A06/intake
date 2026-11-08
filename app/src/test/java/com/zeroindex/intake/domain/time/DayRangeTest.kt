package com.zeroindex.intake.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/**
 * SPEC §3.4: timestamps are UTC epoch millis and user-visible dates come from
 * the local zone at render time. These tests pin the boundaries, because an
 * off-by-one-hour bug here silently files food under the wrong day.
 */
class DayRangeTest {

    private val newYork = ZoneId.of("America/New_York")
    private val utc = ZoneId.of("UTC")

    @Test
    fun `a day is a half-open range of exactly 24 hours in a stable zone`() {
        val range = DayRange.of(LocalDate.of(2026, 3, 10), utc)
        assertThat(range.endExclusive - range.startInclusive).isEqualTo(86_400_000L)
    }

    @Test
    fun `day boundaries follow the local zone not UTC`() {
        val range = DayRange.of(LocalDate.of(2026, 1, 15), newYork)
        assertThat(range.startInclusive)
            .isEqualTo(Instant.parse("2026-01-15T05:00:00Z").toEpochMilli())
    }

    @Test
    fun `the spring forward day is 23 hours long`() {
        val range = DayRange.of(LocalDate.of(2026, 3, 8), newYork)
        assertThat(range.endExclusive - range.startInclusive).isEqualTo(23 * 3_600_000L)
    }

    @Test
    fun `the fall back day is 25 hours long`() {
        val range = DayRange.of(LocalDate.of(2026, 11, 1), newYork)
        assertThat(range.endExclusive - range.startInclusive).isEqualTo(25 * 3_600_000L)
    }

    @Test
    fun `range is inclusive of its start and exclusive of its end`() {
        val range = DayRange.of(LocalDate.of(2026, 5, 4), newYork)
        assertThat(range.startInclusive in range).isTrue()
        assertThat(range.endExclusive - 1 in range).isTrue()
        assertThat(range.endExclusive in range).isFalse()
        assertThat(range.startInclusive - 1 in range).isFalse()
    }

    @Test
    fun `containing finds the local day for an instant`() {
        val millis = Instant.parse("2026-01-15T02:30:00Z").toEpochMilli()
        assertThat(localDateOf(millis, newYork)).isEqualTo(LocalDate.of(2026, 1, 14))
        assertThat(millis in DayRange.containing(millis, newYork)).isTrue()
        assertThat(DayRange.containing(millis, newYork).startInclusive)
            .isEqualTo(startOfLocalDay(millis, newYork))
    }

    @Test
    fun `lastDays covers the requested number of local days`() {
        val range = DayRange.lastDays(7, LocalDate.of(2026, 6, 10), utc)
        assertThat(range.startInclusive)
            .isEqualTo(DayRange.of(LocalDate.of(2026, 6, 4), utc).startInclusive)
        assertThat(range.endExclusive - range.startInclusive).isEqualTo(7 * 86_400_000L)
    }

    @Test
    fun `lastDays of one is a single day`() {
        val day = LocalDate.of(2026, 6, 10)
        assertThat(DayRange.lastDays(1, day, utc)).isEqualTo(DayRange.of(day, utc))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lastDays rejects a non positive window`() {
        DayRange.lastDays(0, LocalDate.of(2026, 6, 10), utc)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a range cannot be empty`() {
        DayRange(10, 10)
    }
}
