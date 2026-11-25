package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MeasurementDeltaTest {
    @Test fun `a loss and a gain carry their sign and unit`() {
        assertThat(MeasurementDelta.text(-0.39, LengthUnit.INCHES)).isEqualTo("−0.4 in")
        assertThat(MeasurementDelta.text(1.24, LengthUnit.CENTIMETRES)).isEqualTo("+1.2 cm")
    }

    @Test fun `no change reads plus or minus zero with the unit, never plus minus`() {
        assertThat(MeasurementDelta.text(0.0, LengthUnit.INCHES)).isEqualTo("±0.0 in")
        assertThat(MeasurementDelta.text(-0.02, LengthUnit.INCHES)).isEqualTo("±0.0 in")
        assertThat(MeasurementDelta.text(0.04, LengthUnit.CENTIMETRES)).isEqualTo("±0.0 cm")
        assertThat(MeasurementDelta.text(-0.0, LengthUnit.INCHES)).doesNotContain("+-")
    }
}
