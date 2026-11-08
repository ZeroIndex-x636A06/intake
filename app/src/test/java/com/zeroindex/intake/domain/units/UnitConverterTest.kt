package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** SPEC §14 requires unit conversion to be covered. */
class UnitConverterTest {

    @Test
    fun `pounds round trip through kilograms`() {
        val pounds = 183.4
        assertThat(UnitConverter.kgToLb(UnitConverter.lbToKg(pounds))).isWithin(1e-9).of(pounds)
    }

    @Test
    fun `known pound to kilogram conversions`() {
        assertThat(UnitConverter.lbToKg(1.0)).isWithin(1e-9).of(0.45359237)
        assertThat(UnitConverter.lbToKg(220.462262) / 100.0).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `inches round trip through centimetres`() {
        val inches = 33.5
        val cm = UnitConverter.inchesToCm(inches)
        assertThat(cm).isWithin(1e-9).of(85.09)
        assertThat(UnitConverter.cmToInches(cm)).isWithin(1e-9).of(inches)
    }

    @Test
    fun `rounding keeps the requested number of places`() {
        assertThat(UnitConverter.round(83.4567, 1)).isEqualTo(83.5)
        assertThat(UnitConverter.round(83.4467, 2)).isEqualTo(83.45)
        assertThat(UnitConverter.round(83.5, 0)).isEqualTo(84.0)
    }

    @Test
    fun `weight unit converts in both directions`() {
        // SPEC §3.5: storage is kilograms, display defaults to pounds.
        assertThat(WeightUnit.POUNDS.fromKg(100.0)).isWithin(1e-6).of(220.4622622)
        assertThat(WeightUnit.POUNDS.toKg(220.4622622)).isWithin(1e-6).of(100.0)
        assertThat(WeightUnit.KILOGRAMS.fromKg(72.5)).isEqualTo(72.5)
    }

    @Test
    fun `length unit converts in both directions`() {
        assertThat(LengthUnit.INCHES.fromCm(2.54)).isWithin(1e-9).of(1.0)
        assertThat(LengthUnit.INCHES.toCm(1.0)).isWithin(1e-9).of(2.54)
        assertThat(LengthUnit.CENTIMETRES.fromCm(91.4)).isEqualTo(91.4)
    }

    @Test
    fun `a display unit never changes what is stored`() {
        val typedInPounds = 185.0
        val stored = WeightUnit.POUNDS.toKg(typedInPounds)
        assertThat(WeightUnit.POUNDS.fromKg(stored)).isWithin(1e-9).of(typedInPounds)
        assertThat(stored).isWithin(1e-9).of(83.91458845)
    }
}
