package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VisionMathTest {

    private val plate = listOf(
        EstimateLine("chicken", 150.0, 314.0, 34.0, 0.0, 19.5),
        EstimateLine("rice", 180.0, 234.0, 4.3, 50.0, 0.5),
    )

    @Test
    fun `totals add every macro`() {
        val t = VisionMath.total(plate)
        assertThat(t.kcal).isEqualTo(548.0)
        assertThat(t.protein).isWithin(1e-9).of(38.3)
    }

    @Test
    fun `cautious scale lands the plate on the high end of the range`() {
        val scale = VisionMath.cautiousScale(plate, kcalHigh = 685.0)
        assertThat(scale).isWithin(1e-9).of(1.25)
        val scaled = VisionMath.total(plate.map { it.scaled(scale) })
        assertThat(scaled.kcal).isWithin(1e-6).of(685.0)
    }

    @Test
    fun `cautious scale never leaves the slider range`() {
        assertThat(VisionMath.cautiousScale(plate, kcalHigh = 5_000.0)).isEqualTo(VisionMath.MAX_SCALE)
        assertThat(VisionMath.cautiousScale(plate, kcalHigh = 10.0)).isEqualTo(VisionMath.MIN_SCALE)
        assertThat(VisionMath.cautiousScale(emptyList(), kcalHigh = 600.0)).isEqualTo(1.0)
    }

    @Test
    fun `no bias note before twenty corrections`() {
        assertThat(VisionMath.biasNote(List(19) { 500.0 to 600.0 })).isNull()
    }

    @Test
    fun `bias note reads in the direction the user corrects`() {
        assertThat(VisionMath.biasNote(List(20) { 500.0 to 575.0 })).contains("15% low")
        assertThat(VisionMath.biasNote(List(20) { 500.0 to 400.0 })).contains("20% high")
    }

    @Test
    fun `a bias under five percent is not worth a note`() {
        assertThat(VisionMath.biasNote(List(25) { 500.0 to 515.0 })).isNull()
    }

    @Test
    fun `tiny edits are not corrections`() {
        assertThat(VisionMath.isCorrection(500.0, 503.0)).isFalse()
        assertThat(VisionMath.isCorrection(500.0, 560.0)).isTrue()
    }
}
