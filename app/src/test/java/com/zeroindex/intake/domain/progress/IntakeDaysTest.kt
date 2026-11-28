package com.zeroindex.intake.domain.progress

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class IntakeDaysTest {
    private val la = ZoneId.of("America/Los_Angeles")

    private fun row(date: String, h: Int, kcal: Double, protein: Double = 0.0) =
        IntakeRow(
            ZonedDateTime.of(LocalDate.parse(date).atTime(h, 0), la).toInstant().toEpochMilli(),
            kcal,
            protein,
        )

    private fun nov(d: Int) = "2026-11-%02d".format(d)

    @Test
    fun lateEveningAfterFallBackStaysOnItsOwnDay() {
        // 1 Nov 2026 is the day the clocks go back in Los Angeles. A fixed offset taken on 26 Oct (-7h)
        // put 23:00 PST on 5 Nov on the 6th.
        val byDay = IntakeDays.byDay(
            listOf(row("2026-10-26", 12, 500.0), row(nov(5), 23, 700.0), row(nov(6), 8, 300.0)),
            la,
        )
        assertThat(byDay.getValue(LocalDate.parse(nov(5))).kcal).isEqualTo(700.0)
        assertThat(byDay.getValue(LocalDate.parse(nov(6))).kcal).isEqualTo(300.0)
        assertThat(byDay).hasSize(3)
    }

    @Test
    fun everyLoggedDayCountsAcrossDstAndTodayIsIncluded() {
        val days = (26..31).map { "2026-10-$it" } + (1..15).map { nov(it) }
        val byDay = IntakeDays.byDay(days.map { row(it, 23, 2000.0) }, la)
        val today = LocalDate.parse(nov(15))
        val series = IntakeDays.series(byDay, LocalDate.parse("2026-10-26"), today)
        assertThat(series).hasSize(21)
        assertThat(series.count { it != null }).isEqualTo(21)
        assertThat(series.last()).isNotNull()
        val stats = IntakeDays.stats(series, 1900)
        assertThat(stats.loggedDays).isEqualTo(21)
        assertThat(stats.averageKcal).isEqualTo(2000.0)
        assertThat(stats.daysOverTarget).isEqualTo(21)
    }

    @Test
    fun averageAndCountsAreTheSameInEveryRangeThatHoldsTheSameDays() {
        val days = (26..31).map { "2026-10-$it" } + (1..15).map { nov(it) }
        val byDay = IntakeDays.byDay(days.map { row(it, 22, 2100.0) }, la)
        val today = LocalDate.parse(nov(15))
        val month = IntakeDays.stats(IntakeDays.series(byDay, today.minusDays(29), today), 2060)
        val all = IntakeDays.stats(IntakeDays.series(byDay, LocalDate.parse("2026-10-26"), today), 2060)
        assertThat(month.averageKcal).isEqualTo(all.averageKcal)
        assertThat(month.loggedDays).isEqualTo(all.loggedDays)
        assertThat(month.daysOverTarget).isEqualTo(all.daysOverTarget)
        assertThat(month.loggedDays).isEqualTo(21)
    }

    @Test
    fun springForwardDayIsOneDay() {
        val byDay = IntakeDays.byDay(
            listOf(row("2026-03-08", 1, 100.0), row("2026-03-08", 23, 200.0), row("2026-03-09", 0, 50.0)),
            la,
        )
        assertThat(byDay.getValue(LocalDate.parse("2026-03-08")).kcal).isEqualTo(300.0)
        assertThat(byDay.getValue(LocalDate.parse("2026-03-09")).kcal).isEqualTo(50.0)
    }

    @Test
    fun proteinHitRateUsesLoggedDaysInTheSameWindow() {
        val byDay = IntakeDays.byDay(
            listOf(row(nov(13), 9, 2000.0, 150.0), row(nov(14), 9, 2000.0, 90.0), row(nov(1), 9, 2000.0, 150.0)),
            la,
        )
        val last = LocalDate.parse(nov(15))
        assertThat(IntakeDays.proteinHitRate(byDay, last.minusDays(6), last, 150)).isEqualTo(0.5)
        assertThat(IntakeDays.proteinHitRate(byDay, last.minusDays(6), last, null)).isNull()
        assertThat(IntakeDays.proteinHitRate(emptyMap(), last.minusDays(6), last, 150)).isNull()
    }
}
