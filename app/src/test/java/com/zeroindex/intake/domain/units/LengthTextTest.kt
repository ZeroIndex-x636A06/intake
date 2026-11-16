package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LengthTextTest {

    @Test fun heightShowsOneDecimalInInches() {
        assertThat(LengthText.entry(183.0, LengthUnit.INCHES)).isEqualTo("72.0")
        assertThat(LengthText.entry(183.0, LengthUnit.CENTIMETRES)).isEqualTo("183.0")
    }

    @Test fun retypingTheShownNumberNeverMovesTheStoredHeight() {
        // 183 cm is 72.05 in: the field shows 72.0, and typing 72 back must leave 183 cm alone, not 182.88.
        assertThat(LengthText.typed("72", 183.0, LengthUnit.INCHES)).isNull()
        assertThat(LengthText.typed("72.0", 183.0, LengthUnit.INCHES)).isNull()
        assertThat(LengthText.typed("183", 183.0, LengthUnit.CENTIMETRES)).isNull()
    }

    @Test fun aChangedNumberIsStored() {
        val cm = LengthText.typed("70", 183.0, LengthUnit.INCHES)
        assertThat(cm).isWithin(1e-9).of(LengthUnit.INCHES.toCm(70.0))
    }

    @Test fun noStoredHeightMeansAnyNumberIsStored() {
        assertThat(LengthText.typed("180", null, LengthUnit.CENTIMETRES)).isEqualTo(180.0)
        assertThat(LengthText.typed("", null, LengthUnit.CENTIMETRES)).isNull()
    }
}
