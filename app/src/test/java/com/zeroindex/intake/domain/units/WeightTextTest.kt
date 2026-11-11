package com.zeroindex.intake.domain.units

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeightTextTest {

    @Test fun kilogramsAreLeftAsWritten() {
        val text = "Start at about 59.0 kg. Work up the rep range, then add weight (+2.5 kg)"
        assertThat(WeightText.inUnit(text, WeightUnit.KILOGRAMS)).isEqualTo(text)
    }

    @Test fun everyWeightInASentenceIsRewrittenInPounds() {
        assertThat(WeightText.inUnit("Start at about 59.0 kg. Start light.", WeightUnit.POUNDS))
            .isEqualTo("Start at about 130 lb. Start light.")
        assertThat(WeightText.inUnit("Based on your last Bench press: 60 kg for 8.", WeightUnit.POUNDS))
            .isEqualTo("Based on your last Bench press: 132.5 lb for 8.")
        assertThat(WeightText.inUnit("80 kg, 80% of an estimated 100 kg max (Epley).", WeightUnit.POUNDS))
            .isEqualTo("176.5 lb, 80% of an estimated 220.5 lb max (Epley).")
    }

    @Test fun aFivePoundStepReadsAsFivePounds() {
        val step = WeightUnit.POUNDS.toKg(5.0)
        assertThat(WeightText.inUnit("Work up the rep range, then add weight (+$step kg)", WeightUnit.POUNDS))
            .isEqualTo("Work up the rep range, then add weight (+5 lb)")
        assertThat(WeightText.inUnit("then add weight (+2.5 kg)", WeightUnit.POUNDS)).isEqualTo("then add weight (+5.5 lb)")
    }

    @Test fun wordsThatOnlyContainKgAreNotTouched() {
        assertThat(WeightText.inUnit("2.0 g per kg of body weight", WeightUnit.POUNDS)).isEqualTo("2.0 g per kg of body weight")
        assertThat(WeightText.inUnit("backgammon 12 kgf", WeightUnit.POUNDS)).isEqualTo("backgammon 12 kgf")
    }

    @Test fun oneWeightFormatsWithItsUnit() {
        assertThat(WeightText.format(20.0, WeightUnit.KILOGRAMS)).isEqualTo("20 kg")
        assertThat(WeightText.format(36.3, WeightUnit.KILOGRAMS)).isEqualTo("36.3 kg")
        assertThat(WeightText.format(WeightUnit.POUNDS.toKg(45.0), WeightUnit.POUNDS)).isEqualTo("45 lb")
    }
}
