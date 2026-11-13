package com.zeroindex.intake.domain.health

import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import org.junit.Test

class HealthInsightsTest {
    private val min = 60_000L
    private val hour = 3_600_000L
    private val dayStart = 20_000L * 86_400_000L // a UTC midnight
    private val dayEnd = dayStart + 24 * hour

    // ----- sleep ------------------------------------------------------------

    @Test fun `overlapping sleep from a phone and a watch counts once`() {
        val phone = SleepSpan(dayStart - 2 * hour, dayStart + 6 * hour) // 22:00 to 06:00
        val watch = SleepSpan(dayStart - 90 * min, dayStart + 6 * hour + 15 * min)
        assertThat(SleepMath.unionMinutes(listOf(phone, watch))).isEqualTo(8 * 60 + 15)
    }

    @Test fun `separate sleep spans add up and empty is zero`() {
        val a = SleepSpan(0, 2 * hour)
        val b = SleepSpan(3 * hour, 4 * hour)
        assertThat(SleepMath.unionMinutes(listOf(b, a))).isEqualTo(180)
        assertThat(SleepMath.unionMinutes(emptyList())).isEqualTo(0)
    }

    @Test fun `last night is the sleep that ended today, naps left out`() {
        val night = SleepSpan(dayStart - hour, dayStart + 7 * hour)
        val nap = SleepSpan(dayStart + 14 * hour, dayStart + 15 * hour)
        val yesterdaysNight = SleepSpan(dayStart - 25 * hour, dayStart - 17 * hour)
        val result = SleepMath.lastNight(listOf(night, nap, yesterdaysNight), dayStart, dayEnd, ZoneOffset.UTC)
        assertThat(result).isEqualTo(8 * 60)
    }

    @Test fun `a nap is kept when it is all there is, and no sleep is null`() {
        val nap = SleepSpan(dayStart + 14 * hour, dayStart + 15 * hour)
        assertThat(SleepMath.lastNight(listOf(nap), dayStart, dayEnd, ZoneOffset.UTC)).isEqualTo(60)
        assertThat(SleepMath.lastNight(emptyList(), dayStart, dayEnd, ZoneOffset.UTC)).isNull()
    }

    // ----- workout windows --------------------------------------------------

    @Test fun `two apps recording the same workout merge into one window`() {
        val merged = SessionMath.merge(
            listOf(SessionWindow(10 * hour, 11 * hour), SessionWindow(10 * hour + 5 * min, 11 * hour + 10 * min), SessionWindow(15 * hour, 16 * hour)),
        )
        assertThat(merged).containsExactly(SessionWindow(10 * hour, 11 * hour + 10 * min), SessionWindow(15 * hour, 16 * hour)).inOrder()
    }

    @Test fun `merged windows do not count the same burn twice`() {
        val burn = listOf(EnergySample(10 * hour, 11 * hour, 600.0))
        val doubled = ActivityNetting.net(
            sessions = listOf(SessionWindow(10 * hour, 11 * hour), SessionWindow(10 * hour, 11 * hour)),
            totalSamples = burn, activeSamples = emptyList(), bmrKcalPerDay = 1_440.0,
        )
        val merged = ActivityNetting.net(
            sessions = SessionMath.merge(listOf(SessionWindow(10 * hour, 11 * hour), SessionWindow(10 * hour, 11 * hour))),
            totalSamples = burn, activeSamples = emptyList(), bmrKcalPerDay = 1_440.0,
        )
        assertThat(doubled.netKcal).isEqualTo(2 * merged.netKcal)
        assertThat(merged.netKcal).isWithin(0.001).of(600.0 - 60.0)
    }

    // ----- weigh-in de-duplication -----------------------------------------

    private fun key(id: String) = "hc-weight-$id"

    @Test fun `a weigh-in near one the person logged is left out`() {
        val known = listOf(KnownWeighIn("mine", 1_000_000, deleted = false))
        val out = WeighInDedupe.toImport(
            listOf(OfferedWeighIn("a", 1_000_000 + 5 * min), OfferedWeighIn("b", 1_000_000 + 30 * min)), known, ::key,
        )
        assertThat(out.map { it.id }).containsExactly("b")
    }

    @Test fun `a deleted row blocks its own id but not a new reading near it`() {
        val known = listOf(KnownWeighIn("hc-weight-a", 5_000_000, deleted = true))
        val out = WeighInDedupe.toImport(listOf(OfferedWeighIn("a", 5_000_000), OfferedWeighIn("z", 5_000_000 + hour)), known, ::key)
        assertThat(out.map { it.id }).containsExactly("z")
    }

    @Test fun `a deleted row near a different reading does not suppress it`() {
        val known = listOf(KnownWeighIn("old", 5_000_000, deleted = true))
        val out = WeighInDedupe.toImport(listOf(OfferedWeighIn("n", 5_000_000 + 2 * min)), known, ::key)
        assertThat(out.map { it.id }).containsExactly("n")
    }

    // ----- the Today card and the charts -----------------------------------

    private fun day(steps: Long? = null, sleep: Long? = null, kcal: Double? = null, hr: Double? = null, start: Long = dayStart) =
        DailyHealth(start, steps, sleep, kcal, hr)

    @Test fun `the activity card is absent with no data, and zeros are not data`() {
        assertThat(ActivityToday.from(null)).isNull()
        assertThat(ActivityToday.from(day())).isNull()
        assertThat(ActivityToday.from(day(steps = 0, sleep = 0, kcal = 0.0))).isNull()
    }

    @Test fun `the activity card keeps only the figures that exist`() {
        val card = ActivityToday.from(day(steps = 6_400, sleep = 415, kcal = 312.6))!!
        assertThat(card.steps).isEqualTo(6_400)
        assertThat(card.sleepMinutes).isEqualTo(415)
        assertThat(card.activeKcal).isEqualTo(313)
        val sleepOnly = ActivityToday.from(day(sleep = 400))!!
        assertThat(sleepOnly.steps).isNull()
        assertThat(sleepOnly.activeKcal).isNull()
    }

    @Test fun `chart series leave out days with no reading instead of drawing zero`() {
        val days = listOf(
            day(steps = 5_000, sleep = 420, hr = 55.0, start = 3),
            day(start = 2),
            day(steps = 0, sleep = null, hr = 57.0, start = 1),
        )
        assertThat(HealthSeries.steps(days).map { it.dayStartMillis }).containsExactly(3L)
        assertThat(HealthSeries.sleepHours(days).single().value).isEqualTo(7.0)
        assertThat(HealthSeries.restingHeartRate(days).map { it.dayStartMillis }).containsExactly(1L, 3L).inOrder()
        assertThat(HealthSeries.steps(emptyList())).isEmpty()
    }
}
