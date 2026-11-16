package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.MacroRingSplit.Macro
import org.junit.Test

class MacroRingSplitTest {

    @Test
    fun `kcal uses 4, 4 and 9 per gram`() {
        assertThat(MacroRingSplit.kcal(34.0, 84.0, 16.0)).isEqualTo(MacroRingSplit.Kcal(136, 336, 144))
    }

    @Test
    fun `kcal treats negative and non-finite grams as zero`() {
        assertThat(MacroRingSplit.kcal(-5.0, Double.NaN, Double.POSITIVE_INFINITY)).isEqualTo(MacroRingSplit.Kcal(0, 0, 0))
    }

    @Test
    fun `the mockup day splits 22, 55 and 23 and the percentages sum to 100`() {
        val s = MacroRingSplit.segments(34.0, 84.0, 16.0, 0.27f)
        assertThat(s.map { it.pct }).containsExactly(22, 55, 23).inOrder()
        assertThat(s.sumOf { it.pct }).isEqualTo(100)
    }

    @Test
    fun `rounding always sums to 100`() {
        for (p in listOf(1.0, 7.3, 33.3)) for (c in listOf(2.0, 9.9, 50.0)) for (f in listOf(0.5, 3.3, 12.0)) {
            assertThat(MacroRingSplit.segments(p, c, f, 0.5f).sumOf { it.pct }).isEqualTo(100)
        }
    }

    @Test
    fun `zero eaten gives no segments`() {
        assertThat(MacroRingSplit.segments(0.0, 0.0, 0.0, 0f)).isEmpty()
        assertThat(MacroRingSplit.segments(10.0, 10.0, 10.0, 0f)).isEmpty()
        assertThat(MacroRingSplit.segments(10.0, 10.0, 10.0, Float.NaN)).isEmpty()
    }

    @Test
    fun `segments are consecutive from the top and fill the eaten arc`() {
        val s = MacroRingSplit.segments(25.0, 25.0, 11.111, 0.5f) // 100, 100, 100 kcal: thirds
        assertThat(s.map { it.macro }).containsExactly(Macro.PROTEIN, Macro.CARBS, Macro.FAT).inOrder()
        assertThat(s[0].startDegrees).isEqualTo(0f)
        assertThat(s[1].startDegrees).isWithin(0.001f).of(s[0].startDegrees + s[0].sweepDegrees)
        assertThat(s[2].startDegrees).isWithin(0.001f).of(s[1].startDegrees + s[1].sweepDegrees)
        assertThat(s.sumOf { it.sweepDegrees.toDouble() }).isWithin(0.01).of(180.0)
    }

    @Test
    fun `midpoints sit in the middle of each segment`() {
        val s = MacroRingSplit.segments(50.0, 0.0, 0.0, 1f)
        assertThat(s).hasSize(1)
        assertThat(s[0].sweepDegrees).isWithin(0.001f).of(360f)
        assertThat(s[0].midDegrees).isWithin(0.001f).of(180f)
        val t = MacroRingSplit.segments(25.0, 25.0, 0.0, 0.5f) // half protein, half carbs, over 180 degrees
        assertThat(t[0].midDegrees).isWithin(0.001f).of(45f)
        assertThat(t[1].midDegrees).isWithin(0.001f).of(135f)
    }

    @Test
    fun `a macro with no calories is left out`() {
        val s = MacroRingSplit.segments(10.0, 0.0, 10.0, 1f)
        assertThat(s.map { it.macro }).containsExactly(Macro.PROTEIN, Macro.FAT).inOrder()
    }

    @Test
    fun `the arc fraction is clamped`() {
        assertThat(MacroRingSplit.segments(10.0, 10.0, 10.0, 3f).sumOf { it.sweepDegrees.toDouble() }).isWithin(0.01).of(360.0)
    }
}
