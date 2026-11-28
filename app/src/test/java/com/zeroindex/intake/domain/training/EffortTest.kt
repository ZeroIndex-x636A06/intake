package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EffortTest {

    @Test
    fun `rpe is ten minus reps in reserve and stays on the 6 to 10 scale`() {
        assertThat(Effort.rpeFromRir(0)).isEqualTo(10.0)
        assertThat(Effort.rpeFromRir(2)).isEqualTo(8.0)
        assertThat(Effort.rpeFromRir(4)).isEqualTo(6.0)
        assertThat(Effort.rpeFromRir(7)).isEqualTo(6.0)
        assertThat(Effort.rpeFromRir(-1)).isEqualTo(10.0)
    }

    @Test
    fun `reps in reserve come back from an rpe, half steps towards the harder side`() {
        assertThat(Effort.rirFromRpe(10.0)).isEqualTo(0)
        assertThat(Effort.rirFromRpe(8.0)).isEqualTo(2)
        assertThat(Effort.rirFromRpe(8.5)).isEqualTo(1)
        assertThat(Effort.rirFromRpe(6.0)).isEqualTo(4)
    }

    @Test
    fun `a scale that is off offers nothing`() {
        assertThat(Effort.levels(EffortScale.OFF)).isEmpty()
        assertThat(EffortScale.fromName("nonsense")).isEqualTo(EffortScale.OFF)
        assertThat(EffortScale.fromName(null)).isEqualTo(EffortScale.OFF)
        assertThat(EffortScale.fromName("RIR")).isEqualTo(EffortScale.RIR)
    }

    @Test
    fun `both scales offer the same five stored values, hardest first`() {
        assertThat(Effort.levels(EffortScale.RPE).map { it.rpe }).containsExactly(10.0, 9.0, 8.0, 7.0, 6.0).inOrder()
        assertThat(Effort.levels(EffortScale.RIR).map { it.rpe }).containsExactly(10.0, 9.0, 8.0, 7.0, 6.0).inOrder()
        assertThat(Effort.levels(EffortScale.RIR).map { it.short }).containsExactly("RIR 0", "RIR 1", "RIR 2", "RIR 3", "RIR 4+").inOrder()
        assertThat(Effort.levels(EffortScale.RPE).map { it.short }).containsExactly("RPE 10", "RPE 9", "RPE 8", "RPE 7", "RPE 6").inOrder()
    }

    @Test
    fun `each level has a plain line`() {
        assertThat(Effort.line(8.0)).isEqualTo("2 left in the tank")
        assertThat(Effort.line(10.0)).isEqualTo("Nothing left in the tank")
        assertThat(Effort.line(6.0)).contains("easy")
        assertThat(Effort.levels(EffortScale.RIR).first { it.rpe == 9.0 }.line).isEqualTo("1 left in the tank")
    }

    @Test
    fun `a half step from the watch reads as a range`() {
        assertThat(Effort.line(8.5)).isEqualTo("about 1 or 2 left in the tank")
        assertThat(Effort.line(7.5)).isEqualTo("about 2 or 3 left in the tank")
        assertThat(Effort.shortFor(8.5, EffortScale.RPE)).isEqualTo("RPE 8.5")
        assertThat(Effort.cellText(8.5, EffortScale.RPE)).isEqualTo("8.5")
    }

    @Test
    fun `an rpe logged on the watch shows the same in either scale`() {
        assertThat(Effort.cellText(8.0, EffortScale.RPE)).isEqualTo("8")
        assertThat(Effort.cellText(8.0, EffortScale.RIR)).isEqualTo("2")
        assertThat(Effort.cellText(6.0, EffortScale.RIR)).isEqualTo("4+")
        assertThat(Effort.cellText(8.0, EffortScale.OFF)).isEmpty()
    }

    @Test
    fun `bands go from easy to max`() {
        assertThat(Effort.band(6.0)).isEqualTo(EffortBand.EASY)
        assertThat(Effort.band(7.0)).isEqualTo(EffortBand.MODERATE)
        assertThat(Effort.band(8.5)).isEqualTo(EffortBand.HARD)
        assertThat(Effort.band(9.0)).isEqualTo(EffortBand.VERY_HARD)
        assertThat(Effort.band(10.0)).isEqualTo(EffortBand.MAX)
    }
}
