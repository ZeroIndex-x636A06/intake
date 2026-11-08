package com.zeroindex.intake.domain.health

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActivityNettingTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val run = SessionWindow(0, hour)
    // 1,800 kcal/day BMR is 1.25 kcal a minute, 75 kcal an hour.
    private val bmr = 1_800.0

    @Test
    fun `total burn has the bmr for the session taken out`() {
        val n = ActivityNetting.net(listOf(run), listOf(EnergySample(0, hour, 600.0)), emptyList(), bmr)
        assertThat(n.rawKcal).isEqualTo(600.0)
        assertThat(n.bmrKcal).isWithin(1e-9).of(75.0)
        assertThat(n.netKcal).isWithin(1e-9).of(525.0)
    }

    @Test
    fun `active burn is already net of bmr and is not reduced again`() {
        val n = ActivityNetting.net(listOf(run), emptyList(), listOf(EnergySample(0, hour, 525.0)), bmr)
        assertThat(n.netKcal).isEqualTo(525.0)
        assertThat(n.bmrKcal).isEqualTo(0.0)
    }

    @Test
    fun `when both kinds cover a session only total minus bmr counts`() {
        val n = ActivityNetting.net(
            listOf(run),
            listOf(EnergySample(0, hour, 600.0)),
            listOf(EnergySample(0, hour, 525.0)),
            bmr,
        )
        assertThat(n.netKcal).isWithin(1e-9).of(525.0)
    }

    @Test
    fun `burn outside any tracked session is never added`() {
        val n = ActivityNetting.net(
            listOf(run),
            listOf(EnergySample(2 * hour, 3 * hour, 400.0)),
            listOf(EnergySample(5 * hour, 6 * hour, 300.0)),
            bmr,
        )
        assertThat(n.netKcal).isEqualTo(0.0)
    }

    @Test
    fun `a sample straddling the session counts only the overlapping part`() {
        // 2-hour sample of 800 kcal, half inside the 1-hour run.
        val n = ActivityNetting.net(listOf(run), listOf(EnergySample(-hour, hour, 800.0)), emptyList(), bmr)
        assertThat(n.rawKcal).isWithin(1e-9).of(400.0)
        assertThat(n.bmrKcal).isWithin(1e-9).of(75.0)
        assertThat(n.netKcal).isWithin(1e-9).of(325.0)
    }

    @Test
    fun `a gentle session never produces negative calories`() {
        val n = ActivityNetting.net(listOf(run), listOf(EnergySample(0, hour, 60.0)), emptyList(), bmr)
        assertThat(n.netKcal).isEqualTo(0.0)
    }

    @Test
    fun `bmr comes from the best source available`() {
        assertThat(ActivityNetting.bmrPerDay(1_750.0, 84.0, 18.0)).isEqualTo(1_750.0 to BmrBasis.HEALTH_CONNECT)
        val (katch, basis) = ActivityNetting.bmrPerDay(null, 84.0, 20.0)!!
        assertThat(basis).isEqualTo(BmrBasis.KATCH_MCARDLE)
        assertThat(katch).isWithin(1e-9).of(370 + 21.6 * 84.0 * 0.8)
        assertThat(ActivityNetting.bmrPerDay(null, 84.0, null)).isEqualTo(84.0 * 22.0 to BmrBasis.BODYWEIGHT)
        assertThat(ActivityNetting.bmrPerDay(null, null, null)).isNull()
    }
}
