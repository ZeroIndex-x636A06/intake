package com.zeroindex.intake.domain.tdee

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.TdeeConfidence
import org.junit.Test

class WeightTrendTest {

    @Test
    fun `a single weigh-in has a trend equal to itself`() {
        val trend = WeightTrend.smooth(listOf(WeightPoint(0, 90.0)))
        assertThat(trend).hasSize(1)
        assertThat(trend.single().trendKg).isWithin(1e-9).of(90.0)
    }

    @Test
    fun `the trend lags a step change rather than jumping to it`() {
        // SPEC §8: alpha 0.1 exists to strip out water fluctuation.
        val points = (0..9).map { WeightPoint(it.toLong(), if (it == 0) 90.0 else 95.0) }
        val trend = WeightTrend.smooth(points)
        assertThat(trend.first().trendKg).isWithin(1e-9).of(90.0)
        // Still well below the new raw value after nine days.
        assertThat(trend.last().trendKg).isLessThan(94.0)
        assertThat(trend.last().trendKg).isGreaterThan(90.0)
    }

    @Test
    fun `a single spike barely moves the trend`() {
        val steady = (0..9).map { WeightPoint(it.toLong(), 90.0) }
        val spiked = steady.toMutableList().also { it[5] = WeightPoint(5, 93.0) }
        val steadyEnd = WeightTrend.smooth(steady).last().trendKg
        val spikedEnd = WeightTrend.smooth(spiked).last().trendKg
        assertThat(spikedEnd - steadyEnd).isLessThan(0.25)
    }

    @Test
    fun `multiple weigh-ins on one day are averaged first`() {
        val twice = WeightTrend.smooth(
            listOf(WeightPoint(0, 90.0), WeightPoint(0, 92.0), WeightPoint(1, 91.0)),
        )
        // Day 0 becomes one point at 91.0, so there are two points, not three.
        assertThat(twice).hasSize(2)
        assertThat(twice.first().rawKg).isWithin(1e-9).of(91.0)
    }

    @Test
    fun `slope is negative while losing`() {
        val losing = (0..27).map { WeightPoint(it.toLong(), 95.0 - it * 0.1) }
        val slope = WeightTrend.slopeKgPerDay(WeightTrend.smooth(losing))
        assertThat(slope).isNotNull()
        assertThat(slope!!).isLessThan(0.0)
    }

    @Test
    fun `slope needs at least two days`() {
        assertThat(WeightTrend.slopeKgPerDay(WeightTrend.smooth(listOf(WeightPoint(0, 90.0)))))
            .isNull()
    }

    @Test
    fun `a flat series has no meaningful slope`() {
        val flat = (0..20).map { WeightPoint(it.toLong(), 88.0) }
        assertThat(WeightTrend.slopeKgPerDay(WeightTrend.smooth(flat))!!).isWithin(1e-9).of(0.0)
    }
}

class TdeeCalculatorTest {

    /** 28 days of weigh-ins losing a steady rate, with intake logged each day. */
    private fun series(
        days: Int,
        startKg: Double,
        kgPerDay: Double,
        kcal: Double,
    ): Pair<List<WeightPoint>, List<IntakeDay>> {
        val weights = (0 until days).map { WeightPoint(it.toLong(), startKg + kgPerDay * it) }
        val intake = (0 until days).map { IntakeDay(it.toLong(), kcal) }
        return weights to intake
    }

    @Test
    fun `too little data returns null rather than a confident number`() {
        // SPEC §8: a wrong TDEE presented confidently is worse than no TDEE.
        val (weights, intake) = series(days = 10, startKg = 90.0, kgPerDay = -0.02, kcal = 2200.0)
        assertThat(TdeeCalculator.estimate(weights, intake)).isNull()
    }

    @Test
    fun `no intake at all returns null`() {
        val (weights, _) = series(28, 90.0, -0.02, 2200.0)
        assertThat(TdeeCalculator.estimate(weights, emptyList())).isNull()
    }

    @Test
    fun `holding weight makes maintenance equal to intake`() {
        val (weights, intake) = series(days = 28, startKg = 90.0, kgPerDay = 0.0, kcal = 2500.0)
        val result = TdeeCalculator.estimate(weights, intake)
        assertThat(result).isNotNull()
        assertThat(result!!.estimatedTdee).isWithin(1.0).of(2500.0)
    }

    @Test
    fun `losing weight puts maintenance above intake`() {
        // 0.1 kg/day lost on 2000 kcal implies maintenance near 2000 + 770.
        val (weights, intake) = series(days = 28, startKg = 95.0, kgPerDay = -0.1, kcal = 2000.0)
        val result = TdeeCalculator.estimate(weights, intake)!!
        assertThat(result.estimatedTdee).isGreaterThan(2000.0)
        assertThat(result.weightTrendSlopeKgPerDay).isLessThan(0.0)
        // The EWMA lags the raw series, so the implied slope is shallower than
        // the true one; the estimate should still land in the right region.
        assertThat(result.estimatedTdee).isLessThan(2800.0)
    }

    @Test
    fun `gaining weight puts maintenance below intake`() {
        val (weights, intake) = series(days = 28, startKg = 80.0, kgPerDay = 0.05, kcal = 3000.0)
        val result = TdeeCalculator.estimate(weights, intake)!!
        assertThat(result.estimatedTdee).isLessThan(3000.0)
    }

    @Test
    fun `confidence follows data density`() {
        // SPEC §8: under 14 LOW, 14 to 28 MEDIUM, over 28 with few gaps HIGH.
        assertThat(TdeeCalculator.confidenceFor(10, 0)).isEqualTo(TdeeConfidence.LOW)
        assertThat(TdeeCalculator.confidenceFor(14, 0)).isEqualTo(TdeeConfidence.MEDIUM)
        assertThat(TdeeCalculator.confidenceFor(28, 0)).isEqualTo(TdeeConfidence.MEDIUM)
        assertThat(TdeeCalculator.confidenceFor(35, 1)).isEqualTo(TdeeConfidence.HIGH)
        assertThat(TdeeCalculator.confidenceFor(35, 5)).isEqualTo(TdeeConfidence.MEDIUM)
    }

    @Test
    fun `only days with both a weigh-in and a log count`() {
        val weights = (0 until 28).map { WeightPoint(it.toLong(), 90.0) }
        // Intake on only ten of those days: not enough overlap.
        val intake = (0 until 10).map { IntakeDay(it.toLong(), 2400.0) }
        assertThat(TdeeCalculator.estimate(weights, intake)).isNull()
    }

    @Test
    fun `days with zero intake are not counted as logged`() {
        val weights = (0 until 28).map { WeightPoint(it.toLong(), 90.0) }
        val intake = (0 until 28).map { IntakeDay(it.toLong(), if (it < 20) 2400.0 else 0.0) }
        val result = TdeeCalculator.estimate(weights, intake)!!
        assertThat(result.daysOfData).isEqualTo(20)
    }

    @Test
    fun `recent days are weighted more heavily than old ones`() {
        val weights = (0 until 28).map { WeightPoint(it.toLong(), 90.0) }
        // Intake steps up in the second half; a recency-weighted mean should sit
        // above the flat arithmetic mean of 2250.
        val intake = (0 until 28).map {
            IntakeDay(it.toLong(), if (it < 14) 2000.0 else 2500.0)
        }
        val result = TdeeCalculator.estimate(weights, intake)!!
        assertThat(result.rollingIntakeAvg).isGreaterThan(2250.0)
    }

    @Test
    fun `a rate goal derives the target from measured maintenance`() {
        // Losing 0.5 lb a week is about 0.227 kg, so roughly 250 kcal a day.
        val target = TdeeCalculator.targetForRate(
            tdee = 2600.0,
            rateKgPerWeek = -0.2268,
            goalWeightKg = 80.0,
        )
        assertThat(target.kcal).isIn(2340..2360)
        assertThat(target.proteinG).isEqualTo(160)
    }

    @Test
    fun `an absurd rate cannot drive the target below the floor`() {
        val target = TdeeCalculator.targetForRate(
            tdee = 2200.0,
            rateKgPerWeek = -2.0,
            goalWeightKg = 80.0,
        )
        assertThat(target.kcal).isEqualTo(TdeeCalculator.MIN_SAFE_KCAL)
    }

    @Test
    fun `gaining raises the target above maintenance`() {
        val target = TdeeCalculator.targetForRate(2600.0, 0.25, 80.0)
        assertThat(target.kcal).isGreaterThan(2600)
    }

    @Test
    fun `a shift over 100 kcal is worth a notification`() {
        // SPEC §8: notify when the target moves more than 100 kcal.
        assertThat(TdeeCalculator.shouldNotifyShift(2400, 2550)).isTrue()
        assertThat(TdeeCalculator.shouldNotifyShift(2400, 2450)).isFalse()
        assertThat(TdeeCalculator.shouldNotifyShift(2400, 2250)).isTrue()
        assertThat(TdeeCalculator.shouldNotifyShift(2400, 2400)).isFalse()
    }

    @Test
    fun `the formula seed is in a sane range and kept separate from measurement`() {
        val seed = TdeeCalculator.seedEstimate(
            weightKg = 85.0,
            heightCm = 180.0,
            ageYears = 30,
            isMale = true,
        )
        assertThat(seed).isGreaterThan(2000.0)
        assertThat(seed).isLessThan(3200.0)
    }
}
