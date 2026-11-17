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
    fun `week figures count today's partial day and match the tile averages`() {
        val r = report(
            listOf(
                ReportDay(monday, kcal = 2_449.0, protein = 150.0, weightKg = 87.0),
                ReportDay(monday.plusDays(1), kcal = 2_393.0, protein = 130.0),
                ReportDay(monday.plusDays(2), kcal = 650.0, protein = 20.0, sleepMinutes = 420),
                ReportDay(monday.plusDays(3)),
            ),
        )
        val f = r.figures
        assertThat(f.avgKcal).isEqualTo(Math.round(r.average { it.kcal }!!).toInt())
        assertThat(f.avgKcal).isEqualTo(1_831)
        assertThat(f.avgProteinG).isEqualTo(100)
        assertThat(f.daysLogged).isEqualTo(3)
        assertThat(f.daysInWeek).isEqualTo(4)
        assertThat(f.weighIns).isEqualTo(1)
        assertThat(f.avgSleepHours).isEqualTo(7.0)
        assertThat(f.weightChangeKg).isWithin(0.001).of(-0.3)
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

    @Test
    fun `protein hit rate counts only the days with food logged`() {
        // Two logged days (one hit), five days with nothing: "1 of 2 logged days", never "of 7".
        val days = listOf(
            ReportDay(monday, kcal = 2_000.0, protein = 190.0),
            ReportDay(monday.plusDays(1), kcal = 400.0, protein = 20.0),
        ) + (2..6).map { ReportDay(monday.plusDays(it.toLong())) }
        val hit = report(days).proteinHit()!!
        assertThat(hit.hitDays).isEqualTo(1)
        assertThat(hit.loggedDays).isEqualTo(2)
        assertThat(hit.label).isEqualTo("Protein target hit 1 of 2 logged days · 50%")
        assertThat(report(days).daysLogged).isEqualTo(hit.loggedDays)
    }

    @Test
    fun `no logged day gives no protein hit rate and sparse intake looks under-logged`() {
        assertThat(report(listOf(ReportDay(monday))).proteinHit()).isNull()
        val sparse = listOf(ReportDay(monday, kcal = 20.0, protein = 2.0), ReportDay(monday.plusDays(1), kcal = 400.0, protein = 10.0))
        assertThat(report(sparse).average { it.kcal }).isEqualTo(210.0)
        assertThat(report(sparse).intakeLooksUnderLogged()).isTrue()
    }

    @Test
    fun `a single weigh-in has no change and a flat change reads no change`() {
        val one = report(emptyList()).copy(weightTrend = listOf(monday to 88.0))
        assertThat(one.weightChangeKg).isNull()
        assertThat(formatWeightChange(0.0, "lb")).isEqualTo("no change")
        assertThat(formatWeightChange(0.04, "lb")).isEqualTo("no change")
        assertThat(formatWeightChange(0.4, "lb")).isEqualTo("+0.4 lb")
        assertThat(formatWeightChange(-0.4, "lb")).isEqualTo("−0.4 lb")
    }
}
