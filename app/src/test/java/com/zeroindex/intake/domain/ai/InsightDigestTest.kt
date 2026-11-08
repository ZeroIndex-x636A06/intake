package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InsightDigestTest {

    private val base = InsightInputs(
        days = 14,
        kcalTarget = 2_040,
        proteinTarget = 190,
        dailyKcal = List(11) { 2_000.0 } + listOf(null, 1_900.0, 1_950.0),
        dailyProtein = List(11) { 185.0 } + listOf(null, 140.0, 150.0),
        weightTrendKgPerWeek = -0.31,
        tdeeKcal = 2_540.0,
        tdeeConfidence = "medium",
        bodyFatPercent = 18.24,
        leanMassKg = null,
        sessionsLastWeek = 3,
        sessionsWeekBefore = 4,
        weeklyAvgRpe = listOf(7.5, 8.5),
        sleepMinutesAvg = 400.0,
        restingHrFirstWeek = 56.0,
        restingHrLastWeek = 61.0,
        stepsAvg = 8_123.0,
        waterMlAvg = 2_100.0,
        waterTargetMl = 3_000.0,
    )

    @Test
    fun `the digest carries numbers and trends only`() {
        val text = InsightDigest.lines(base).joinToString("\n")
        assertThat(text).contains("Days logged: 13 of 14")
        assertThat(text).contains("Protein under 90% of target for the last 2 logged days")
        assertThat(text).contains("-0.31 kg per week")
        assertThat(text).contains("7.5 → 8.5")
        assertThat(text).contains("56 → 61 bpm")
        assertThat(text).contains("8,123")
    }

    @Test
    fun `a missing day ends a short-protein run rather than extending it`() {
        val i = base.copy(dailyProtein = listOf(140.0, 140.0, null, 150.0))
        assertThat(InsightDigest.lines(i).none { it.startsWith("Protein under") }).isTrue()
    }

    @Test
    fun `absent sources are simply left out`() {
        val i = base.copy(sleepMinutesAvg = null, restingHrFirstWeek = null, stepsAvg = null, bodyFatPercent = null, weeklyAvgRpe = listOf(8.0))
        val text = InsightDigest.lines(i).joinToString("\n")
        assertThat(text).doesNotContain("Sleep")
        assertThat(text).doesNotContain("Resting heart rate")
        assertThat(text).doesNotContain("RPE")
    }

    @Test
    fun `fewer than three logged days is not enough to say anything`() {
        assertThat(InsightDigest.hasEnough(base.copy(dailyKcal = listOf(2_000.0, null, 1_900.0, null)))).isFalse()
        assertThat(InsightDigest.hasEnough(base)).isTrue()
    }
}
