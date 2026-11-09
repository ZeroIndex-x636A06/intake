package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.BaselineReadiness.FoodDay
import com.zeroindex.intake.domain.model.TdeeConfidence
import com.zeroindex.intake.domain.tdee.WeightPoint
import org.junit.Test

class EnergyModelTest {

    @Test
    fun `mifflin st jeor matches known values`() {
        // Male, 80 kg, 180 cm, 30 y: 800 + 1125 - 150 + 5 = 1780.
        val male = EnergyModel.bmr(80.0, 180.0, 30, Sex.MALE, null)
        assertThat(male.kcal).isWithin(0.01).of(1_780.0)
        assertThat(male.formula).isEqualTo(BmrFormula.MIFFLIN_ST_JEOR)
        // Female, 65 kg, 165 cm, 28 y: 650 + 1031.25 - 140 - 161 = 1380.25.
        val female = EnergyModel.bmr(65.0, 165.0, 28, Sex.FEMALE, null)
        assertThat(female.kcal).isWithin(0.01).of(1_380.25)
    }

    @Test
    fun `katch mcardle matches known values and wins when body fat is known`() {
        // 75 kg at 20% fat = 60 kg lean: 370 + 21.6 x 60 = 1666.
        val bmr = EnergyModel.bmr(75.0, 180.0, 30, Sex.MALE, 20.0)
        assertThat(bmr.kcal).isWithin(0.01).of(1_666.0)
        assertThat(bmr.formula).isEqualTo(BmrFormula.KATCH_MCARDLE)
        assertThat(bmr.why).contains("Katch-McArdle")
        assertThat(bmr.why).contains("1,666")
    }

    @Test
    fun `a nonsense body fat falls back to mifflin`() {
        assertThat(EnergyModel.bmr(75.0, 180.0, 30, Sex.MALE, 1.0).formula).isEqualTo(BmrFormula.MIFFLIN_ST_JEOR)
        assertThat(EnergyModel.bmr(75.0, 180.0, 30, Sex.MALE, 80.0).formula).isEqualTo(BmrFormula.MIFFLIN_ST_JEOR)
    }

    @Test
    fun `activity factor adds training days to the base`() {
        val f = EnergyModel.activityFactor(ActivityLevel.SITTING, 3, null)
        assertThat(f.value).isWithin(1e-9).of(1.29)
        assertThat(f.why).contains("mostly sitting")
        assertThat(f.why).contains("3 training days")
        assertThat(EnergyModel.activityFactor(ActivityLevel.PHYSICAL, 0, null).value).isWithin(1e-9).of(1.55)
    }

    @Test
    fun `steps override the chosen level along a straight line`() {
        assertThat(EnergyModel.activityFactor(ActivityLevel.PHYSICAL, 0, 3_000.0).value).isWithin(1e-9).of(1.2)
        assertThat(EnergyModel.activityFactor(ActivityLevel.SITTING, 0, 14_000.0).value).isWithin(1e-9).of(1.55)
        assertThat(EnergyModel.activityFactor(ActivityLevel.SITTING, 0, 8_500.0).value).isWithin(1e-9).of(1.375)
        // Beyond the ends it is clamped.
        assertThat(EnergyModel.activityFactor(ActivityLevel.SITTING, 0, 30_000.0).value).isWithin(1e-9).of(1.55)
        assertThat(EnergyModel.activityFactor(ActivityLevel.SITTING, 0, 8_500.0).why).contains("8,500 steps")
    }

    // --- Measured TDEE ---------------------------------------------------------------------

    private val day0 = 20_000L

    /** A week of 2,300 kcal days while the true burn is 2,800 (a 500 kcal deficit). */
    private fun syntheticWeek(noise: Double = 0.0): Pair<List<WeightPoint>, List<FoodDay>> {
        val slope = -500.0 / 7_700.0
        val weights = (0L..6L).map { d ->
            val wobble = if (d % 2L == 0L) noise else -noise
            WeightPoint(day0 + d, 80.0 + slope * d + wobble)
        }
        val food = (0L..6L).map { FoodDay(day0 + it, 3, 2_300.0) }
        return weights to food
    }

    @Test
    fun `measured burn is recovered from a clean synthetic week`() {
        val (w, f) = syntheticWeek()
        val m = MeasuredEnergy.fromBaseline(w, f)!!
        assertThat(m.tdee).isWithin(1.0).of(2_800.0)
        assertThat(m.averageIntake).isWithin(0.01).of(2_300.0)
        assertThat(m.loggedDays).isEqualTo(7)
        assertThat(m.weighInDays).isEqualTo(7)
        assertThat(m.spanDays).isEqualTo(7)
        assertThat(m.kgPerWeek).isWithin(0.001).of(-500.0 * 7 / 7_700.0)
    }

    @Test
    fun `measured burn stays near the truth with weigh-in noise`() {
        val (w, f) = syntheticWeek(noise = 0.3)
        val m = MeasuredEnergy.fromBaseline(w, f)!!
        // +/-0.3 kg of water noise over a week is worth a few hundred kcal at most.
        assertThat(m.tdee).isWithin(300.0).of(2_800.0)
    }

    @Test
    fun `partial days are left out of the average intake`() {
        val (w, f) = syntheticWeek()
        val withCoffee = f.dropLast(1) + FoodDay(day0 + 6, 1, 90.0)
        val m = MeasuredEnergy.fromBaseline(w, withCoffee)!!
        assertThat(m.averageIntake).isWithin(0.01).of(2_300.0)
        assertThat(m.loggedDays).isEqualTo(6)
    }

    @Test
    fun `no measurement without two weigh-in days or any logged day`() {
        val (w, f) = syntheticWeek()
        assertThat(MeasuredEnergy.fromBaseline(w.take(1), f)).isNull()
        assertThat(MeasuredEnergy.fromBaseline(w.take(1) + w.take(1), f)).isNull()
        assertThat(MeasuredEnergy.fromBaseline(w, emptyList())).isNull()
        assertThat(MeasuredEnergy.fromBaseline(w, listOf(FoodDay(day0, 1, 100.0)))).isNull()
    }

    @Test
    fun `trust is set by the weakest link and capped for a one week baseline`() {
        val full = MeasuredTdee(2_800.0, 2_300.0, -0.06, loggedDays = 7, weighInDays = 5, spanDays = 7)
        assertThat(MeasuredEnergy.trust(full)).isWithin(1e-9).of(MeasuredEnergy.MAX_BASELINE_TRUST)
        val fewFood = full.copy(loggedDays = 3)
        assertThat(MeasuredEnergy.trust(fewFood)).isWithin(1e-9).of(0.8 * 3 / 7)
        val fewWeigh = full.copy(weighInDays = 2)
        assertThat(MeasuredEnergy.trust(fewWeigh)).isWithin(1e-9).of(0.8 * 2 / 5)
        val shortSpan = full.copy(spanDays = 3)
        assertThat(MeasuredEnergy.trust(shortSpan)).isWithin(1e-9).of(0.8 * 3 / 6)
    }

    @Test
    fun `without a baseline the formula stands and the why says so`() {
        val b = MeasuredEnergy.blend(2_600.0, 1_700.0, null, null)
        assertThat(b.source).isEqualTo(TdeeSource.ESTIMATE)
        assertThat(b.tdee).isWithin(1e-9).of(2_600.0)
        assertThat(b.measuredShare).isEqualTo(0.0)
        assertThat(b.why).contains("formula estimate")
        assertThat(b.why).contains("2,600")
    }

    @Test
    fun `a baseline is blended toward the measurement by its trust`() {
        val m = MeasuredTdee(2_800.0, 2_300.0, -0.065, 7, 5, 7)
        val b = MeasuredEnergy.blend(2_600.0, 1_700.0, m, null)
        assertThat(b.source).isEqualTo(TdeeSource.BLENDED)
        assertThat(b.measuredShare).isWithin(1e-9).of(0.8)
        assertThat(b.tdee).isWithin(1e-6).of(0.8 * 2_800.0 + 0.2 * 2_600.0)
        assertThat(b.why).contains("2,800")
        assertThat(b.why).contains("2,300")
        assertThat(b.why).contains("80%")
        assertThat(b.why).contains("2,600")
    }

    @Test
    fun `an implausible measurement is ignored`() {
        val m = MeasuredTdee(6_500.0, 4_000.0, -0.3, 7, 5, 7)
        val b = MeasuredEnergy.blend(2_600.0, 1_700.0, m, null)
        assertThat(b.source).isEqualTo(TdeeSource.ESTIMATE)
        assertThat(b.tdee).isWithin(1e-9).of(2_600.0)
        assertThat(b.why).contains("ignored")
        val tooLow = m.copy(tdee = 1_200.0)
        assertThat(MeasuredEnergy.blend(2_600.0, 1_700.0, tooLow, null).source).isEqualTo(TdeeSource.ESTIMATE)
    }

    @Test
    fun `the nightly adaptive estimate outranks the baseline`() {
        val m = MeasuredTdee(2_800.0, 2_300.0, -0.065, 7, 5, 7)
        val high = MeasuredEnergy.blend(2_600.0, 1_700.0, m, MeasuredEnergy.Adaptive(3_000.0, TdeeConfidence.HIGH))
        assertThat(high.source).isEqualTo(TdeeSource.MEASURED)
        assertThat(high.tdee).isWithin(1e-9).of(3_000.0)
        val medium = MeasuredEnergy.blend(2_600.0, 1_700.0, m, MeasuredEnergy.Adaptive(3_000.0, TdeeConfidence.MEDIUM))
        assertThat(medium.source).isEqualTo(TdeeSource.BLENDED)
        assertThat(medium.tdee).isWithin(1e-6).of(0.85 * 3_000.0 + 0.15 * 2_600.0)
        // LOW adaptive is not trusted; the baseline is used instead.
        val low = MeasuredEnergy.blend(2_600.0, 1_700.0, m, MeasuredEnergy.Adaptive(3_000.0, TdeeConfidence.LOW))
        assertThat(low.tdee).isWithin(1e-6).of(0.8 * 2_800.0 + 0.2 * 2_600.0)
    }
}
