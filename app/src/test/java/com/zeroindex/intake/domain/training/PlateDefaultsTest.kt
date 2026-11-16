package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class PlateDefaultsTest {

    @Test fun kilogramGymsGetTheMetricBarAndPlates() {
        assertThat(PlateDefaults.barKg(WeightUnit.KILOGRAMS)).isEqualTo(20.0)
        assertThat(PlateDefaults.platesCsvKg(WeightUnit.KILOGRAMS)).isEqualTo("25,20,15,10,5,2.5,1.25")
    }

    @Test fun poundGymsGetAFortyFivePoundBarAndPoundPlates() {
        val unit = WeightUnit.POUNDS
        assertThat(unit.fromKg(PlateDefaults.barKg(unit))).isWithin(1e-9).of(45.0)
        assertThat(PlateDefaults.toDisplayCsv(PlateDefaults.platesCsvKg(unit), unit)).isEqualTo("45,35,25,10,5,2.5")
    }

    @Test fun aPoundLoadComesOutInWholePlates() {
        val unit = WeightUnit.POUNDS
        val plates = PlateDefaults.platesCsvKg(unit).split(',').map { it.toDouble() }
        val load = PlateCalculator.calculate(unit.toKg(135.0), PlateDefaults.barKg(unit), plates)
        assertThat(load.perSide.map { Math.round(unit.fromKg(it)) }).containsExactly(45L)
        assertThat(unit.fromKg(load.totalKg)).isWithin(1e-6).of(135.0)
    }

    @Test fun typedPlatesRoundTripThroughKilograms() {
        val unit = WeightUnit.POUNDS
        val stored = PlateDefaults.fromDisplayCsv("45, 25,10 ,x,5", unit)
        assertThat(PlateDefaults.toDisplayCsv(stored, unit)).isEqualTo("45,25,10,5")
        assertThat(PlateDefaults.fromDisplayCsv("20,10,2.5", WeightUnit.KILOGRAMS)).isEqualTo("20,10,2.5")
    }
}
