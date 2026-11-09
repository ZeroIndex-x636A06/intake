package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class PhasePlannerTest {

    private fun calories(
        phase: Phase,
        pace: Pace = Pace.STEADY,
        tdee: Double = 3_000.0,
        bmr: Double = 1_800.0,
        weightKg: Double = 80.0,
        age: Int = 30,
        contestPct: Double = 0.75,
        step: Int = 75,
        intake: Double? = null,
        allowBelowFloor: Boolean = false,
        manual: Int? = null,
        label: String = "burn",
    ) = PhasePlanner.calories(
        phase, pace, tdee, bmr, weightKg, age, contestPct, step, intake, allowBelowFloor, manual, label,
    )

    @Test
    fun `cut offsets are 15 20 and 25 percent by pace`() {
        assertThat(calories(Phase.CUT, Pace.GENTLE).kcal).isEqualTo(2_550)
        assertThat(calories(Phase.CUT, Pace.STEADY).kcal).isEqualTo(2_400)
        assertThat(calories(Phase.CUT, Pace.FAST).kcal).isEqualTo(2_250)
    }

    @Test
    fun `lean bulk offsets are 5 to 10 percent`() {
        assertThat(calories(Phase.LEAN_BULK, Pace.GENTLE).kcal).isEqualTo(3_150)
        assertThat(calories(Phase.LEAN_BULK, Pace.STEADY).kcal).isEqualTo(3_225)
        assertThat(calories(Phase.LEAN_BULK, Pace.FAST).kcal).isEqualTo(3_300)
    }

    @Test
    fun `bulk offsets are 10 to 20 percent`() {
        assertThat(calories(Phase.BULK, Pace.GENTLE).kcal).isEqualTo(3_300)
        assertThat(calories(Phase.BULK, Pace.STEADY).kcal).isEqualTo(3_450)
        assertThat(calories(Phase.BULK, Pace.FAST).kcal).isEqualTo(3_600)
    }

    @Test
    fun `maintain is the burn and recomp is a small deficit`() {
        assertThat(calories(Phase.MAINTAIN).kcal).isEqualTo(3_000)
        assertThat(calories(Phase.RECOMP, Pace.GENTLE).kcal).isEqualTo(2_850)
        assertThat(calories(Phase.RECOMP, Pace.STEADY).kcal).isEqualTo(2_775)
        assertThat(calories(Phase.RECOMP, Pace.FAST).kcal).isEqualTo(2_700)
    }

    @Test
    fun `every offset stays inside its documented band`() {
        Pace.entries.forEach { pace ->
            assertThat(PhasePlanner.offsetFraction(Phase.CUT, pace)).isIn(com.google.common.collect.Range.closed(-0.25, -0.15))
            assertThat(PhasePlanner.offsetFraction(Phase.LEAN_BULK, pace)).isIn(com.google.common.collect.Range.closed(0.05, 0.10))
            assertThat(PhasePlanner.offsetFraction(Phase.BULK, pace)).isIn(com.google.common.collect.Range.closed(0.10, 0.20))
            assertThat(PhasePlanner.offsetFraction(Phase.RECOMP, pace)).isIn(com.google.common.collect.Range.closed(-0.10, -0.05))
        }
    }

    @Test
    fun `contest prep converts a weekly loss percent to kcal`() {
        // 80 kg x 0.75% = 0.6 kg a week = 660 kcal a day.
        val c = calories(Phase.CONTEST_PREP, contestPct = 0.75)
        assertThat(c.kcal).isEqualTo(2_340)
        assertThat(c.percentBodyWeightPerWeek).isWithin(0.01).of(-0.75)
        assertThat(c.why).contains("0.8%")
    }

    @Test
    fun `contest prep rate is clamped to half to one percent`() {
        // 2.0% asks for too much: clamped to 1.0% = 0.8 kg = 880 kcal.
        assertThat(calories(Phase.CONTEST_PREP, contestPct = 2.0).kcal).isEqualTo(2_120)
        // 0.1% is clamped up to 0.5% = 0.4 kg = 440 kcal.
        assertThat(calories(Phase.CONTEST_PREP, contestPct = 0.1).kcal).isEqualTo(2_560)
    }

    @Test
    fun `reverse diet starts from current intake and climbs to the burn`() {
        val c = calories(Phase.REVERSE_DIET, tdee = 2_600.0, intake = 2_000.0, step = 75)
        assertThat(c.kcal).isEqualTo(2_000)
        assertThat(c.reverseSchedule.first()).isEqualTo(2_000)
        assertThat(c.reverseSchedule.last()).isEqualTo(2_600)
        assertThat(c.reverseSchedule.zipWithNext { a, b -> b - a }.dropLast(1).toSet()).containsExactly(75)
        assertThat(c.why).contains("2,000")
        assertThat(c.why).contains("75")
        assertThat(c.why).contains("2,600")
    }

    @Test
    fun `reverse diet step is clamped to 50 to 100`() {
        assertThat(calories(Phase.REVERSE_DIET, tdee = 2_600.0, intake = 2_000.0, step = 10).reverseSchedule[1]).isEqualTo(2_050)
        assertThat(calories(Phase.REVERSE_DIET, tdee = 2_600.0, intake = 2_000.0, step = 500).reverseSchedule[1]).isEqualTo(2_100)
    }

    @Test
    fun `reverse diet without intake data starts a little under the burn`() {
        val c = calories(Phase.REVERSE_DIET, tdee = 2_600.0, intake = null)
        assertThat(c.kcal).isEqualTo(2_210)
        // Already at the burn: one step, no climb.
        assertThat(PhasePlanner.reverseSchedule(2_700, 2_600, 75)).containsExactly(2_600)
    }

    @Test
    fun `calories are raised to 1_1 x resting burn and the person is told`() {
        // Floor = 1,800 x 1.1 = 1,980. A fast cut from 2,400 would land on 1,800.
        val c = calories(Phase.CUT, Pace.FAST, tdee = 2_400.0)
        assertThat(c.kcal).isEqualTo(1_980)
        assertThat(c.warnings.map { it.text }.single { "Raised" in it }).contains("1,980")
    }

    @Test
    fun `the advanced override keeps the low number but still warns`() {
        val c = calories(Phase.CUT, Pace.FAST, tdee = 2_400.0, allowBelowFloor = true)
        assertThat(c.kcal).isEqualTo(1_800)
        assertThat(c.warnings.any { it.level == WarningLevel.WARN && "below" in it.text }).isTrue()
    }

    @Test
    fun `a loss above one percent a week warns`() {
        // 2,000 vs a burn of 3,000: 1,000 x 7 / 7,700 = 0.91 kg a week, 1.14% of 80 kg.
        val c = calories(Phase.CUT, manual = 2_000, bmr = 1_500.0)
        assertThat(c.percentBodyWeightPerWeek).isLessThan(-1.0)
        assertThat(c.warnings.any { "1% limit" in it.text }).isTrue()
    }

    @Test
    fun `a loss at or under one percent has no pace warning`() {
        val c = calories(Phase.CUT, Pace.STEADY)
        assertThat(c.warnings).isEmpty()
        val atLimit = calories(Phase.CONTEST_PREP, contestPct = 1.0)
        assertThat(atLimit.warnings.none { "1% limit" in it.text }).isTrue()
    }

    @Test
    fun `under 18 gets no deficit`() {
        listOf(Phase.CUT, Phase.RECOMP, Phase.CONTEST_PREP).forEach { phase ->
            val c = calories(phase, age = 16)
            assertThat(c.kcal).isEqualTo(3_000)
            assertThat(c.offsetKcal).isEqualTo(0)
            assertThat(c.warnings.any { "Under 18" in it.text }).isTrue()
            assertThat(c.why).contains("under 18")
        }
        // A surplus is left alone, and so is exactly 18.
        assertThat(calories(Phase.LEAN_BULK, age = 16).kcal).isEqualTo(3_225)
        assertThat(calories(Phase.CUT, age = 18).kcal).isEqualTo(2_400)
        assertThat(calories(Phase.CUT, age = 17).kcal).isEqualTo(3_000)
    }

    @Test
    fun `a typed number is used and the rails still apply`() {
        val c = calories(Phase.CUT, manual = 2_500)
        assertThat(c.kcal).isEqualTo(2_500)
        assertThat(c.why).contains("number you chose")
        assertThat(c.why).contains("2,500")
        assertThat(calories(Phase.CUT, manual = 1_000).kcal).isEqualTo(1_980)
    }

    @Test
    fun `the why line carries the key numbers and the burn label`() {
        val c = calories(Phase.CUT, tdee = 2_900.0, label = "measured burn")
        // 2,900 x 0.80 = 2,320.
        assertThat(c.why).isEqualTo("2,320 kcal = your measured burn 2,900 − 20% for a steady cut.")
    }

    @Test
    fun `pace in words gives percent and weight`() {
        assertThat(PhasePlanner.paceInWords(-0.4, 80.0)).isEqualTo("About 0.5% of body weight per week (≈0.4 kg)")
        assertThat(PhasePlanner.paceInWords(-0.4, 80.0, WeightUnit.POUNDS)).isEqualTo("About 0.5% of body weight per week (≈0.9 lb)")
        assertThat(PhasePlanner.paceInWords(0.0, 80.0)).isEqualTo("Weight stays about the same")
    }

    @Test
    fun `phase calories are deterministic`() {
        val a = calories(Phase.CUT, Pace.FAST, tdee = 2_731.4)
        val b = calories(Phase.CUT, Pace.FAST, tdee = 2_731.4)
        assertThat(a).isEqualTo(b)
    }
}
