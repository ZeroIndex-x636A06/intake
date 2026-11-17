package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightText
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class SetRowTextTest {

    @Test fun bodyweightWithNothingAddedReadsBw() {
        assertThat(SetRowText.load(0.0, bodyweight = true, unit = WeightUnit.POUNDS)).isEqualTo("BW")
        assertThat(SetRowText.previous(0.0, 10, bodyweight = true, unit = WeightUnit.POUNDS)).isEqualTo("BW × 10")
    }

    @Test fun bodyweightWithAddedLoadReadsThePlus() {
        val tenPounds = WeightUnit.POUNDS.toKg(10.0)
        assertThat(SetRowText.load(tenPounds, bodyweight = true, unit = WeightUnit.POUNDS)).isEqualTo("+10 lb")
        assertThat(SetRowText.previous(tenPounds, 8, bodyweight = true, unit = WeightUnit.POUNDS)).isEqualTo("+10 lb × 8")
    }

    @Test fun barbellWeightsAlwaysHaveOneDecimal() {
        assertThat(SetRowText.load(WeightUnit.POUNDS.toKg(50.0), bodyweight = false, unit = WeightUnit.POUNDS)).isEqualTo("50.0")
        // Converting 50 lb to kg and back is not exact; the row must still read "50.0".
        assertThat(SetRowText.load(22.679899, bodyweight = false, unit = WeightUnit.POUNDS)).isEqualTo("50.0")
        assertThat(SetRowText.previous(WeightUnit.POUNDS.toKg(57.5), 6, bodyweight = false, unit = WeightUnit.POUNDS))
            .isEqualTo("57.5 × 6")
    }

    @Test fun kilogramsKeepOneDecimalToo() {
        assertThat(WeightText.setRow(60.0, WeightUnit.KILOGRAMS)).isEqualTo("60.0")
        assertThat(WeightText.setRow(2.5, WeightUnit.KILOGRAMS)).isEqualTo("2.5")
    }

    @Test fun noRuleHintNeverSaysPickWhenThereIsHistory() {
        assertThat(SetRowText.noRuleReason(0.0, hasHistory = true, bodyweight = true)).doesNotContain("pick a working weight")
        assertThat(SetRowText.noRuleReason(0.0, hasHistory = true, bodyweight = false)).doesNotContain("pick a working weight")
        assertThat(SetRowText.noRuleReason(60.0, hasHistory = true, bodyweight = false)).doesNotContain("pick a working weight")
    }

    @Test fun noRuleHintAsksForAWeightOnlyForANewBarbellLift() {
        assertThat(SetRowText.noRuleReason(0.0, hasHistory = false, bodyweight = false))
            .isEqualTo("No rule and no history yet - pick a working weight.")
        assertThat(SetRowText.noRuleReason(0.0, hasHistory = false, bodyweight = true)).doesNotContain("pick a working weight")
    }
}
