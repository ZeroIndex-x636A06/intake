package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

class MacroPlannerTest {

    @Test
    fun `protein fat and carbs sum to calories within one percent`() {
        Phase.entries.forEach { phase ->
            listOf(1_300, 1_600, 1_980, 2_400, 2_900, 3_500, 4_200).forEach { kcal ->
                listOf(50.0, 65.0, 80.0, 100.0, 130.0).forEach { kg ->
                    val m = MacroPlanner.plan(kcal, kg, null, phase)
                    val diff = abs(m.gramsKcal - kcal)
                    assertThat(diff.toDouble() / kcal).isAtMost(0.01)
                }
            }
        }
    }

    @Test
    fun `default protein sits inside the range for each phase`() {
        Phase.entries.forEach { phase ->
            val range = MacroPlanner.proteinRange(phase)
            assertThat(MacroPlanner.defaultProteinPerKg(phase)).isIn(com.google.common.collect.Range.closed(range.start, range.endInclusive))
        }
        assertThat(MacroPlanner.proteinRange(Phase.CUT)).isEqualTo(2.2..2.6)
        assertThat(MacroPlanner.proteinRange(Phase.CONTEST_PREP)).isEqualTo(2.2..2.6)
        assertThat(MacroPlanner.proteinRange(Phase.RECOMP)).isEqualTo(2.0..2.4)
        assertThat(MacroPlanner.proteinRange(Phase.BULK)).isEqualTo(1.6..2.2)
        assertThat(MacroPlanner.proteinRange(Phase.MAINTAIN)).isEqualTo(1.6..2.2)
    }

    @Test
    fun `a cut at 80 kg gets 2_4 g per kg`() {
        val m = MacroPlanner.plan(2_400, 80.0, null, Phase.CUT)
        assertThat(m.proteinG).isEqualTo(192)
        assertThat(m.proteinBasis).isEqualTo(ProteinBasis.BODY_WEIGHT)
        assertThat(m.proteinWhy).contains("192 g")
        assertThat(m.proteinWhy).contains("2.4 g per kg")
    }

    @Test
    fun `protein uses lean mass when it is known`() {
        // 80 kg at 20% fat = 64 kg lean; 2.4 x 64 = 154.
        val m = MacroPlanner.plan(2_400, 80.0, 64.0, Phase.CUT)
        assertThat(m.proteinG).isEqualTo(154)
        assertThat(m.proteinBasis).isEqualTo(ProteinBasis.LEAN_MASS)
        assertThat(m.proteinWhy).contains("lean mass")
        assertThat(m.proteinWhy).contains("64.0 kg")
    }

    @Test
    fun `fat never falls below 0_6 g per kg or 20 percent of calories`() {
        // An override below the floor is lifted to 0.6 g/kg: 0.6 x 80 = 48 g.
        val low = MacroPlanner.plan(2_400, 80.0, null, Phase.MAINTAIN, fatPerKg = 0.2)
        assertThat(low.fatG).isAtLeast(48)
        // Light person on big calories: the 20% rule wins. 3,500 x 0.2 / 9 = 77.8 -> 78 g.
        val share = MacroPlanner.plan(3_500, 50.0, null, Phase.MAINTAIN)
        assertThat(share.fatG).isEqualTo(78)
        assertThat(share.fatWhy).contains("20%")
        // Heavy person: the per-kg rule wins. 0.8 x 100 = 80 g, above 20% of 2,000 / 9 = 44 g.
        val heavy = MacroPlanner.plan(2_000, 100.0, null, Phase.MAINTAIN)
        assertThat(heavy.fatG).isEqualTo(80)
        assertThat(heavy.fatWhy).contains("0.8 g per kg")
    }

    @Test
    fun `fat is always at least 20 percent of calories`() {
        listOf(1_400, 2_000, 3_000, 4_000).forEach { kcal ->
            val m = MacroPlanner.plan(kcal, 70.0, null, Phase.CUT)
            assertThat(m.fatG * 9.0 / kcal).isAtLeast(0.195)
        }
    }

    @Test
    fun `advanced overrides are honoured and clamped`() {
        assertThat(MacroPlanner.plan(3_000, 80.0, null, Phase.MAINTAIN, proteinPerKg = 3.0).proteinG).isEqualTo(240)
        assertThat(MacroPlanner.plan(3_000, 80.0, null, Phase.MAINTAIN, proteinPerKg = 9.0).proteinG).isEqualTo(280)
        assertThat(MacroPlanner.plan(3_000, 80.0, null, Phase.MAINTAIN, proteinPerKg = 0.1).proteinG).isEqualTo(64)
        // Outside the usual range is allowed, with a note.
        val m = MacroPlanner.plan(3_000, 80.0, null, Phase.MAINTAIN, proteinPerKg = 3.0)
        assertThat(m.notes.single()).contains("outside the usual")
    }

    @Test
    fun `protein is trimmed when calories cannot hold it and the fat floor`() {
        val m = MacroPlanner.plan(900, 100.0, null, Phase.CUT)
        assertThat(m.carbsG).isAtMost(1)
        assertThat(m.proteinG * 4 + m.fatG * 9).isAtMost(900 + 4)
        assertThat(m.notes.any { "trimmed" in it }).isTrue()
    }

    @Test
    fun `carbs take the remainder and are never negative`() {
        val m = MacroPlanner.plan(2_400, 80.0, null, Phase.CUT)
        assertThat(m.carbsG).isEqualTo(((2_400 - m.proteinG * 4 - m.fatG * 9) / 4.0).let { Math.round(it).toInt() })
        assertThat(m.carbsWhy).contains("2,400")
        Phase.entries.forEach { assertThat(MacroPlanner.plan(1_200, 120.0, null, it).carbsG).isAtLeast(0) }
    }

    @Test
    fun `macro plan is deterministic`() {
        assertThat(MacroPlanner.plan(2_345, 77.7, 61.0, Phase.RECOMP)).isEqualTo(MacroPlanner.plan(2_345, 77.7, 61.0, Phase.RECOMP))
    }
}
