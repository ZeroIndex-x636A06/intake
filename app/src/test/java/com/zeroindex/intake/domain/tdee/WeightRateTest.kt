package com.zeroindex.intake.domain.tdee

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class WeightRateTest {
    private val today = 20_000L

    private fun daily(from: Long, to: Long, kgAt: (Long) -> Double) = (from..to).map { WeightPoint(it, kgAt(it)) }

    @Test fun `a steady fall of a tenth of a kilo a day is 0 point 7 kg a week`() {
        val points = daily(today - 55, today) { 90.0 - 0.1 * (it - (today - 55)) }
        assertThat(WeightRate.weeklyKg(points, today)!!).isWithin(0.1).of(-0.7)
    }

    @Test fun `one noisy day does not decide the rate`() {
        val points = daily(today - 40, today - 1) { 87.0 } + WeightPoint(today, 85.1)
        assertThat(WeightRate.weeklyKg(points, today)!!).isGreaterThan(-0.5)
    }

    @Test fun `it does not depend on how much history the caller passes`() {
        val long = daily(today - 200, today) { 90.0 - 0.05 * (it - (today - 200)) }
        val short = long.filter { it.dayIndex >= today - WeightRate.SMOOTHING_DAYS + 1 }
        assertThat(WeightRate.weeklyKg(long, today)).isEqualTo(WeightRate.weeklyKg(short, today))
    }

    @Test fun `too few weigh-ins or too short a span gives no rate`() {
        assertThat(WeightRate.weeklyKg(listOf(WeightPoint(today, 80.0), WeightPoint(today - 10, 81.0)), today)).isNull()
        assertThat(WeightRate.weeklyKg(daily(today - 3, today) { 80.0 }, today)).isNull()
        assertThat(WeightRate.weeklyKg(emptyList(), today)).isNull()
    }

    @Test fun `old weigh-ins outside the window are ignored`() {
        assertThat(WeightRate.weeklyKg(daily(today - 200, today - 100) { 100.0 }, today)).isNull()
    }

    @Test fun `text always says per week, with a real minus and no sign at zero`() {
        assertThat(WeightRate.text(-0.136, WeightUnit.POUNDS)).isEqualTo("−0.3 lb/week")
        assertThat(WeightRate.text(0.4, WeightUnit.KILOGRAMS)).isEqualTo("+0.4 kg/week")
        assertThat(WeightRate.text(0.001, WeightUnit.POUNDS)).isEqualTo("0.0 lb/week")
    }
}
