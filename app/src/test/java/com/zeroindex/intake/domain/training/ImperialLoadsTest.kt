package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.Sex
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class ImperialLoadsTest {
    private val lb = WeightUnit.POUNDS
    private val kg = WeightUnit.KILOGRAMS

    private fun lbContext(dumbbellMaxKg: Double? = null) = StartingWeightContext(
        experience = Experience.SOME, bodyweightKg = lb.toKg(180.0), sex = Sex.MALE,
        barWeightKg = PlateDefaults.barKg(lb),
        platesKg = PlateDefaults.platesCsvKg(lb).split(',').map { it.toDouble() },
        smallStepKg = lb.toKg(5.0),
        dumbbellMaxKg = dumbbellMaxKg,
    )

    @Test fun `a stored metric default in a pound gym becomes the 45 lb bar and pound plates`() {
        assertThat(lb.fromKg(PlateDefaults.effectiveBarKg(20.0, lb))).isWithin(1e-9).of(45.0)
        val csv = PlateDefaults.effectivePlatesCsvKg("25,20,15,10,5,2.5,1.25", lb)
        assertThat(PlateDefaults.toDisplayCsv(csv, lb)).isEqualTo("45,35,25,10,5,2.5")
    }

    @Test fun `a kilogram gym and a bar the person typed are left alone`() {
        assertThat(PlateDefaults.effectiveBarKg(20.0, kg)).isEqualTo(20.0)
        assertThat(PlateDefaults.effectivePlatesCsvKg("25,20,15,10,5,2.5,1.25", kg)).isEqualTo("25,20,15,10,5,2.5,1.25")
        assertThat(PlateDefaults.effectiveBarKg(15.0, lb)).isEqualTo(15.0)
        assertThat(PlateDefaults.effectivePlatesCsvKg("20,10", lb)).isEqualTo("20,10")
        assertThat(PlateDefaults.effectiveBarKg(null, kg)).isEqualTo(20.0)
    }

    @Test fun `the Olympic bar hint is in the person's unit`() {
        assertThat(PlateDefaults.olympicBarHint(lb)).isEqualTo("2 in sleeves, 45 lb bar")
        assertThat(PlateDefaults.olympicBarHint(kg)).isEqualTo("2 in sleeves, 20 kg bar")
    }

    @Test fun `pound plate maths loads whole pound plates`() {
        val plates = PlateDefaults.platesCsvKg(lb).split(',').map { it.toDouble() }
        val load = PlateCalculator.calculate(lb.toKg(225.0), PlateDefaults.barKg(lb), plates)
        assertThat(load.perSide.map { Math.round(lb.fromKg(it)) }).containsExactly(45L, 45L)
        assertThat(load.isExact).isTrue()
        val odd = PlateCalculator.calculate(lb.toKg(185.0), PlateDefaults.barKg(lb), plates)
        assertThat(odd.perSide.map { Math.round(lb.fromKg(it)) }).containsExactly(45L, 25L).inOrder()
        assertThat(PlateCalculator.describe(odd, lb)).isEqualTo("1 x 45, 1 x 25")
    }

    @Test fun `kilogram plate maths loads metric plates`() {
        val plates = PlateDefaults.platesCsvKg(kg).split(',').map { it.toDouble() }
        val load = PlateCalculator.calculate(100.0, PlateDefaults.barKg(kg), plates)
        assertThat(load.perSide).containsExactly(25.0, 15.0).inOrder()
        assertThat(load.totalKg).isEqualTo(100.0)
    }

    @Test fun `barbell starts in a pound gym are multiples of 5 lb from the bar`() {
        val bench = ExerciseCatalog.requireByName("Bench press")
        for (target in listOf(60.0, 67.5, 70.0, 109.0, 149.0)) {
            val w = StartingWeightEstimator.roundDown(lb.toKg(target), bench, lbContext())!!
            val inLb = lb.fromKg(w)
            assertThat(inLb % 5.0).isWithin(1e-6).of(0.0)
            assertThat(inLb).isAtMost(maxOf(target, 45.0) + 1e-6)
            assertThat(inLb).isAtLeast(45.0)
        }
        // 150 lb is loadable, so it stays 150 and does not fall to 145 through rounding error.
        assertThat(lb.fromKg(StartingWeightEstimator.roundDown(lb.toKg(150.0), bench, lbContext())!!)).isWithin(1e-6).of(150.0)
        assertThat(lb.fromKg(StartingWeightEstimator.roundDown(lb.toKg(149.0), bench, lbContext())!!)).isWithin(1e-6).of(145.0)
    }

    @Test fun `machine and cable starts move in 5 lb steps and metric ones in 2_5 kg`() {
        val press = ExerciseCatalog.requireByName("Leg press")
        assertThat(lb.fromKg(StartingWeightEstimator.roundDown(lb.toKg(112.0), press, lbContext())!!)).isWithin(1e-6).of(110.0)
        val metric = lbContext().copy(smallStepKg = 2.5)
        assertThat(StartingWeightEstimator.roundDown(51.0, press, metric)).isEqualTo(50.0)
    }

    @Test fun `dumbbell starts never pass the heaviest dumbbell the person owns`() {
        val db = ExerciseCatalog.requireByName("Dumbbell bench press")
        assertThat(db.equipment).isEqualTo(Equipment.DUMBBELL)
        val capped = StartingWeightEstimator.roundDown(lb.toKg(60.0), db, lbContext(dumbbellMaxKg = lb.toKg(40.0)))!!
        assertThat(lb.fromKg(capped)).isWithin(1e-6).of(40.0)
        val free = StartingWeightEstimator.roundDown(lb.toKg(60.0), db, lbContext())!!
        assertThat(lb.fromKg(free)).isWithin(1e-6).of(60.0)
        // The cap only limits dumbbells, not barbells.
        val bench = ExerciseCatalog.requireByName("Bench press")
        val barbell = StartingWeightEstimator.roundDown(lb.toKg(135.0), bench, lbContext(dumbbellMaxKg = lb.toKg(40.0)))!!
        assertThat(lb.fromKg(barbell)).isWithin(1e-6).of(135.0)
    }

    @Test fun `equipment names for bodyweight exercises say bodyweight once`() {
        assertThat(Equipment.describe("bodyweight", true)).isEqualTo("Bodyweight")
        assertThat(Equipment.describe("dumbbell", true)).isEqualTo("Dumbbell · bodyweight")
        assertThat(Equipment.describe("barbell", false)).isEqualTo("Barbell")
        assertThat(Equipment.describe("", false)).isEqualTo("")
    }
}
