package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.Sex
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.generateProgram
import org.junit.Test

class StartingWeightEstimatorTest {
    private fun ctx(exp: Experience = Experience.SOME, bw: Double? = 80.0, sex: Sex? = Sex.MALE) =
        StartingWeightContext(experience = exp, bodyweightKg = bw, sex = sex)

    private fun est(name: String, min: Int, max: Int, c: StartingWeightContext, history: Map<String, List<PastSet>> = emptyMap()) =
        StartingWeightEstimator.estimate(ExerciseCatalog.requireByName(name), min, max, c) { history[it].orEmpty() }

    @Test
    fun `the same lift starts at about 87 percent of the recent weight at the new rep range`() {
        val bench = ExerciseCatalog.requireByName("Bench press")
        // 80 kg x 8 -> e1RM 101.3; at 8 reps that is 80 kg; x 0.875 = 70 kg, loadable with the default plates.
        val e = est("Bench press", 6, 10, ctx(), mapOf(bench.id to listOf(PastSet(80.0, 8), PastSet(70.0, 10))))!!
        assertThat(e.source).isEqualTo(StartSource.SAME_LIFT)
        assertThat(e.weightKg).isIn(com.google.common.collect.Range.closed(66.0, 72.5))
        assertThat(e.note).contains(StartingWeightEstimate.LABEL)
        // Always lighter than what was lifted.
        assertThat(e.weightKg).isLessThan(80.0)
    }

    @Test
    fun `a lower rep range at the same strength starts heavier`() {
        val bench = ExerciseCatalog.requireByName("Bench press")
        val h = mapOf(bench.id to listOf(PastSet(80.0, 8)))
        val heavy = est("Bench press", 3, 5, ctx(), h)!!.weightKg
        val light = est("Bench press", 10, 15, ctx(), h)!!.weightKg
        assertThat(heavy).isGreaterThan(light)
    }

    @Test
    fun `a related lift is used when the exact one has no history`() {
        val bench = ExerciseCatalog.requireByName("Bench press")
        val e = est("Dumbbell bench press", 8, 12, ctx(), mapOf(bench.id to listOf(PastSet(100.0, 5))))!!
        assertThat(e.source).isEqualTo(StartSource.RELATED_LIFT)
        // A dumbbell is per hand: well under half the barbell weight.
        assertThat(e.weightKg).isLessThan(40.0)
        assertThat(e.weightKg).isGreaterThan(15.0)
        assertThat(e.basis).contains("Bench press")
    }

    @Test
    fun `with no history a key lift gets a careful guess from body weight and experience`() {
        val new = est("Back squat", 6, 10, ctx(Experience.NEW))!!
        val exp = est("Back squat", 6, 10, ctx(Experience.EXPERIENCED))!!
        assertThat(new.source).isEqualTo(StartSource.BODYWEIGHT_GUESS)
        assertThat(exp.weightKg).isGreaterThan(new.weightKg)
        assertThat(new.weightKg).isLessThan(80.0) // under body weight for a new lifter
        val female = est("Bench press", 6, 10, ctx(sex = Sex.FEMALE))!!.weightKg
        assertThat(female).isLessThan(est("Bench press", 6, 10, ctx())!!.weightKg)
    }

    @Test
    fun `nothing is guessed without a body weight, for isolation, bodyweight or power work`() {
        assertThat(est("Back squat", 6, 10, ctx(bw = null))).isNull()
        assertThat(est("Lateral raise", 10, 15, ctx())).isNull()
        assertThat(est("Push-up", 8, 12, ctx())).isNull()
        assertThat(est("Jump squat", 3, 5, ctx())).isNull()
        assertThat(est("Plank", 30, 60, ctx())).isNull()
    }

    @Test
    fun `barbell weights are loadable with the plates and never below the bar`() {
        val c = ctx(plates = listOf(20.0, 10.0, 5.0)) // 2.5 kg plates are missing
        val e = est("Bench press", 6, 10, c, mapOf(ExerciseCatalog.requireByName("Bench press").id to listOf(PastSet(67.5, 8))))!!
        val load = PlateCalculator.calculate(e.weightKg, c.barWeightKg, c.platesKg)
        assertThat(load.isExact).isTrue()
        assertThat(e.weightKg).isAtLeast(c.barWeightKg)
        // A very light estimate becomes the empty bar.
        val light = est("Bench press", 6, 10, c.copy(bodyweightKg = 30.0, experience = Experience.NEW))!!
        assertThat(light.weightKg).isEqualTo(c.barWeightKg)
    }

    @Test
    fun `dumbbell and machine weights move in small steps and round down`() {
        val e = est("Dumbbell shoulder press", 8, 12, ctx())!!
        assertThat(e.weightKg % 2.5).isEqualTo(0.0)
    }

    @Test
    fun `a generated program gets weights, kept when already set`() {
        val program = generateProgram(GeneratorInputs(experience = Experience.SOME))
        val withWeights = StartingWeightEstimator.apply(program, ctx()) { emptyList() }
        val weighted = withWeights.days.flatMap { it.exercises }.filter { it.startWeightKg != null }
        assertThat(weighted).isNotEmpty()
        assertThat(weighted.all { it.startNote!!.contains(StartingWeightEstimate.LABEL) }).isTrue()
        val again = StartingWeightEstimator.apply(withWeights, ctx(bw = 200.0)) { emptyList() }
        assertThat(again).isEqualTo(withWeights)
    }

    @Test
    fun `relevant ids include the lift and its family`() {
        val bench = ExerciseCatalog.requireByName("Bench press").id
        val db = ExerciseCatalog.requireByName("Dumbbell bench press").id
        assertThat(StartingWeightEstimator.relevantIds(db)).containsAtLeast(bench, db)
        assertThat(StartingWeightEstimator.relevantIds("custom-id")).containsExactly("custom-id")
    }

    private fun ctx(plates: List<Double>) = ctx().copy(platesKg = plates)
}
