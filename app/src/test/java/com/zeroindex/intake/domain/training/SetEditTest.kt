package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class SetEditTest {
    @Test fun `typed pounds become kilograms`() {
        val v = SetEdit.parse("225", "5", WeightUnit.POUNDS)!!
        assertThat(WeightUnit.POUNDS.fromKg(v.weightKg)).isWithin(1e-6).of(225.0)
        assertThat(v.reps).isEqualTo(5)
        assertThat(SetEdit.parse("62.5", "8", WeightUnit.KILOGRAMS)!!.weightKg).isEqualTo(62.5)
    }

    @Test fun `a blank weight is a bodyweight set`() {
        assertThat(SetEdit.parse("", "10", WeightUnit.POUNDS)!!.weightKg).isEqualTo(0.0)
    }

    @Test fun `nonsense is not a set`() {
        assertThat(SetEdit.parse("abc", "5", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("100", "", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("100", "0", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("100", "1000", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("-5", "5", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("5000", "5", WeightUnit.POUNDS)).isNull()
        assertThat(SetEdit.parse("100", "2.5", WeightUnit.POUNDS)).isNull()
    }

    @Test fun `the weight field shows the stored weight in the person's unit`() {
        assertThat(SetEdit.weightText(WeightUnit.POUNDS.toKg(160.0), WeightUnit.POUNDS)).isEqualTo("160")
        assertThat(SetEdit.weightText(62.5, WeightUnit.KILOGRAMS)).isEqualTo("62.5")
    }
}
