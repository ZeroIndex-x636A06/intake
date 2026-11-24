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

    @Test fun theStockStepIsFivePoundsInAPoundGym() {
        val labels = com.zeroindex.intake.domain.training.ProgressionLabels
        val type = com.zeroindex.intake.domain.model.ProgressionType.DOUBLE_PROGRESSION
        assertThat(labels.summary(type, """{"incrementKg":2.5}""", WeightUnit.POUNDS)).endsWith("(+5 lb)")
        assertThat(labels.summary(type, """{"incrementKg":2.5}""", WeightUnit.KILOGRAMS)).endsWith("(+2.5 kg)")
        assertThat(labels.summary(type, """{"incrementKg":5.0}""", WeightUnit.POUNDS)).endsWith("(+11 lb)")
    }

    @Test fun anEstimateReadsOneWayEverywhere() {
        // Q7-10: 140 lb x 10 by Brzycki is 186.67 lb. It must not be 186.7 in one place and 186.5 (snapped) in another.
        val kg = 140.0 / 2.2046226218 * 36.0 / 27.0
        assertThat(WeightText.estimate(kg, WeightUnit.POUNDS)).isEqualTo("186.7")
        assertThat(WeightText.formatEstimate(kg, WeightUnit.POUNDS)).isEqualTo("186.7 lb")
        val records = com.zeroindex.intake.domain.training.ExerciseRecords(
            null, emptyList(), com.zeroindex.intake.domain.training.BestEstimate(kg, 140.0 / 2.2046226218, 10), null,
        )
        val line = com.zeroindex.intake.domain.training.RecordText.lines(records, WeightUnit.POUNDS).first { it.title == "Best estimated 1RM" }
        assertThat(line.value).isEqualTo("186.7 lb")
    }

    @Test fun anEstimateKeepsItsDecimalAndDropsAWholeZero() {
        assertThat(WeightText.estimate(100.0, WeightUnit.KILOGRAMS)).isEqualTo("100")
        assertThat(WeightText.estimate(233.33 / 2.2046226218, WeightUnit.POUNDS)).isEqualTo("233.3")
    }
}
