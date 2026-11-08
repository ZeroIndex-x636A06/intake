package com.zeroindex.intake.domain.report

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Test

class WeeklyReportTest {

    private val monday = LocalDate.parse("2026-11-02")
    private fun at(date: LocalDate, h: Int, m: Int) =
        date.atTime(h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun report(days: List<ReportDay>, sessions: List<ReportSession> = emptyList()) = WeeklyReport(
        weekStart = monday, days = days, sessions = sessions, kcalTarget = 2_040, proteinTarget = 190,
        weightTrend = listOf(monday to 84.0, monday.plusDays(6) to 83.7), zone = ZoneOffset.UTC,
    )

    @Test
    fun `averages ignore days with no data`() {
        val r = report(
            listOf(
                ReportDay(monday, kcal = 2_000.0, steps = 8_000),
                ReportDay(monday.plusDays(1), kcal = null, steps = null),
                ReportDay(monday.plusDays(2), kcal = 1_800.0, steps = 10_000),
            ),
        )
        assertThat(r.average { it.kcal }).isEqualTo(1_900.0)
        assertThat(r.average { it.steps }).isEqualTo(9_000.0)
        assertThat(r.average { it.sleepMinutes }).isNull()
        assertThat(r.daysLogged).isEqualTo(2)
    }

    @Test
    fun `bedtime spread handles midnight`() {
        val r = report(
            listOf(
                ReportDay(monday, sleepStartMillis = at(monday, 23, 30)),
                ReportDay(monday.plusDays(1), sleepStartMillis = at(monday.plusDays(2), 0, 30)),
            ),
        )
        // An hour apart: a spread of 30 minutes either side.
        assertThat(r.bedtimeSpreadMinutes).isWithin(1e-9).of(30.0)
    }

    @Test
    fun `training totals and set descriptions`() {
        val squat = ReportExercise("Back squat", List(3) { ReportSet(82.5, 5, 8.0) })
        val bench = ReportExercise("Bench press", listOf(ReportSet(60.0, 8, null), ReportSet(60.0, 7, null)))
        val r = report(emptyList(), listOf(ReportSession(monday, "Day A", 62, listOf(squat, bench), "felt strong")))
        assertThat(r.totalSets).isEqualTo(5)
        assertThat(r.totalVolumeKg).isWithin(1e-9).of(3 * 82.5 * 5 + 60.0 * 8 + 60.0 * 7)
        assertThat(squat.describe { "$it kg" }).isEqualTo("3 × 5 @ 82.5 kg")
        assertThat(bench.describe { "$it kg" }).isEqualTo("60.0 kg × 8, 60.0 kg × 7")
    }

    @Test
    fun `weight change comes from the trend ends`() {
        assertThat(report(emptyList()).weightChangeKg).isWithin(1e-9).of(-0.3)
    }
}
