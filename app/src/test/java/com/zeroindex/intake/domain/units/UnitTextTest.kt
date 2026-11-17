package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nearby.TravelBudget
import com.zeroindex.intake.domain.nearby.TravelMode
import com.zeroindex.intake.domain.nearby.TravelRadius
import org.junit.Test

class UnitTextTest {

    @Test
    fun `ml and kg become fl oz and lb for imperial`() {
        val text = "You averaged 2,254 ml a day against a target of 3,157 ml (71%). Max 87.1 kg."
        val out = UnitText.inUnits(text, WeightUnit.POUNDS, VolumeUnit.US_FLUID_OUNCES)
        assertThat(out).isEqualTo("You averaged 76.2 fl oz a day against a target of 106.8 fl oz (71%). Max 192 lb.")
    }

    @Test
    fun `metric units leave the text as written`() {
        val text = "3,050 ml and 87.1 kg"
        assertThat(UnitText.inUnits(text, WeightUnit.KILOGRAMS, VolumeUnit.MILLILITRES)).isEqualTo(text)
    }

    @Test
    fun `ratios such as g per kg are not rewritten`() {
        assertThat(UnitText.inUnits("2.4 g/kg of body weight", WeightUnit.POUNDS, VolumeUnit.US_FLUID_OUNCES))
            .isEqualTo("2.4 g/kg of body weight")
    }

    @Test
    fun `distances become miles for inches and stay km for centimetres`() {
        assertThat(DistanceText.inUnit("rounded to about 10 km", LengthUnit.INCHES)).isEqualTo("rounded to about 6.2 miles")
        assertThat(DistanceText.inUnit("rounded to about 10 km", LengthUnit.CENTIMETRES)).isEqualTo("rounded to about 10 km")
        assertThat(DistanceText.inUnit("about 5 km/h", LengthUnit.INCHES)).isEqualTo("about 3 mph")
    }

    // Q5b-14: the Swap sheet's location rounding is stated with DistanceText.format.
    @Test
    fun `the location rounding reads in miles for inches and km otherwise`() {
        assertThat(DistanceText.format(1.1, LengthUnit.INCHES, 1)).isEqualTo("0.7 miles")
        assertThat(DistanceText.format(1.1, LengthUnit.CENTIMETRES, 1)).isEqualTo("1.1 km")
    }

    @Test
    fun `eat out help text is in miles and mph for imperial`() {
        val travel = TravelBudget(10, TravelMode.DRIVE)
        val imperial = TravelRadius.helpText(travel, LengthUnit.INCHES)
        assertThat(imperial).contains("3 mph")
        assertThat(imperial).contains("25 mph")
        assertThat(imperial).contains("3.2 miles out for 10 min drive")
        assertThat(imperial).doesNotContain("km")
        assertThat(TravelRadius.helpText(travel, LengthUnit.CENTIMETRES)).contains("5.1 km out for 10 min drive")
    }

    @Test
    fun `weight field shows the stored value and follows a late profile`() {
        assertThat(WeightUnit.POUNDS.fieldText(36.0)).isEqualTo("79.4")
        assertThat(WeightUnit.KILOGRAMS.fieldText(36.0)).isEqualTo("36")
        // Profile arrives after the first composition: the empty box takes it.
        assertThat(WeightUnit.POUNDS.fieldSync(36.0, "")).isEqualTo("79.4")
        // The box already means the stored value (typed 79.4 lb saved as kg): left alone, so typing round-trips.
        assertThat(WeightUnit.POUNDS.fieldSync(WeightUnit.POUNDS.toKg(79.4), "79.4")).isNull()
        assertThat(WeightUnit.POUNDS.fieldSync(null, "12")).isNull()
    }
}
