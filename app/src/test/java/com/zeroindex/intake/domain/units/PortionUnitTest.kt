package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PortionUnitTest {

    @Test
    fun `grams are whole numbers and ounces are a tenth`() {
        assertThat(PortionUnit.GRAMS.format(170.0)).isEqualTo("170 g")
        assertThat(PortionUnit.OUNCES.format(170.0)).isEqualTo("6.0 oz")
        assertThat(PortionUnit.OUNCES.number(28.349523125)).isEqualTo("1.0")
    }

    @Test
    fun `typed ounces convert back to stored grams`() {
        assertThat(PortionUnit.OUNCES.toGrams(6.0)).isWithin(1e-6).of(170.097)
        assertThat(PortionUnit.OUNCES.parseToGrams("6")).isWithin(1e-3).of(170.097)
        assertThat(PortionUnit.OUNCES.parseToGrams("1,5")).isWithin(1e-3).of(42.524)
        assertThat(PortionUnit.GRAMS.parseToGrams(" 85 ")).isEqualTo(85.0)
        assertThat(PortionUnit.GRAMS.parseToGrams("abc")).isNull()
        assertThat(PortionUnit.GRAMS.parseToGrams("-4")).isNull()
    }

    @Test
    fun `ranges collapse when both ends round alike`() {
        assertThat(PortionUnit.GRAMS.formatRange(150.0, 190.0)).isEqualTo("150–190 g")
        assertThat(PortionUnit.GRAMS.formatRange(150.2, 150.4)).isEqualTo("150 g")
    }

    @Test
    fun `the weight unit picks the portion unit`() {
        assertThat(PortionUnit.forWeightUnit(WeightUnit.POUNDS)).isEqualTo(PortionUnit.OUNCES)
        assertThat(PortionUnit.forWeightUnit(WeightUnit.KILOGRAMS)).isEqualTo(PortionUnit.GRAMS)
    }

    @Test
    fun `a round trip loses nothing visible`() {
        val grams = 123.0
        val oz = PortionUnit.OUNCES.fromGrams(grams)
        assertThat(PortionUnit.OUNCES.toGrams(oz)).isWithin(1e-9).of(grams)
    }
}
