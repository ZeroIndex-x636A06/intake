package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.ProgressionType
import org.junit.Test

/** SPEC §14 requires every progression rule type to be covered. */
class ProgressionEvaluatorTest {

    private val target = ExerciseTarget(
        sets = 3,
        repsMin = 8,
        repsMax = 10,
        incrementKg = 2.5,
    )

    private fun evaluate(
        type: ProgressionType,
        sets: List<SetResult>,
        params: RuleParams = RuleParams(),
        target: ExerciseTarget = this.target,
        estimated1rm: Double? = null,
    ) = ProgressionEvaluator.evaluate(type, sets, target, params, estimated1rm)

    private fun next(outcome: ProgressionOutcome): Prescription {
        assertThat(outcome).isInstanceOf(ProgressionOutcome.Next::class.java)
        return (outcome as ProgressionOutcome.Next).prescription
    }

    private fun error(outcome: ProgressionOutcome): String {
        assertThat(outcome).isInstanceOf(ProgressionOutcome.Invalid::class.java)
        return (outcome as ProgressionOutcome.Invalid).error.message
    }

    // ------------------------------------------------------------------ LINEAR

    @Test
    fun `linear adds weight when every set hits the target reps`() {
        val sets = List(3) { SetResult(weightKg = 80.0, reps = 8) }
        val prescription = next(evaluate(ProgressionType.LINEAR, sets))
        assertThat(prescription.weightKg).isEqualTo(82.5)
        // SPEC §10: always show the reason next to the number.
        assertThat(prescription.reason).contains("up 2.5")
        assertThat(prescription.reason).contains("8 reps")
    }

    @Test
    fun `linear holds the weight when a set falls short`() {
        val sets = listOf(
            SetResult(80.0, 8),
            SetResult(80.0, 8),
            SetResult(80.0, 6),
        )
        val prescription = next(evaluate(ProgressionType.LINEAR, sets))
        assertThat(prescription.weightKg).isEqualTo(80.0)
        assertThat(prescription.isDeload).isFalse()
        assertThat(prescription.reason).contains("under 8 reps")
    }

    @Test
    fun `linear deloads after the configured number of failures`() {
        val sets = List(3) { SetResult(100.0, 5) }
        val prescription = next(
            evaluate(
                ProgressionType.LINEAR,
                sets,
                RuleParams(failureCount = 1, failuresBeforeDeload = 2, deloadPercent = 10.0),
            ),
        )
        assertThat(prescription.isDeload).isTrue()
        assertThat(prescription.weightKg).isEqualTo(90.0)
        assertThat(prescription.reason).contains("Deload")
    }

    @Test
    fun `linear with no history asks for a starting weight rather than guessing`() {
        val prescription = next(evaluate(ProgressionType.LINEAR, emptyList()))
        assertThat(prescription.weightKg).isEqualTo(0.0)
        assertThat(prescription.reason).contains("First time")
    }

    @Test
    fun `linear needs the full set count, not just good reps`() {
        // Two sets of 8 when three were prescribed is not a completed session.
        val sets = List(2) { SetResult(80.0, 10) }
        val prescription = next(evaluate(ProgressionType.LINEAR, sets))
        assertThat(prescription.weightKg).isEqualTo(80.0)
    }

    // ----------------------------------------------------- DOUBLE_PROGRESSION

    @Test
    fun `double progression adds a rep before it adds weight`() {
        val sets = List(3) { SetResult(60.0, 8) }
        val prescription = next(evaluate(ProgressionType.DOUBLE_PROGRESSION, sets))
        assertThat(prescription.weightKg).isEqualTo(60.0)
        assertThat(prescription.repsMin).isEqualTo(9)
        assertThat(prescription.reason).contains("one more rep")
    }

    @Test
    fun `double progression adds weight and drops to the bottom of the range at the top`() {
        val sets = List(3) { SetResult(60.0, 10) }
        val prescription = next(evaluate(ProgressionType.DOUBLE_PROGRESSION, sets))
        assertThat(prescription.weightKg).isEqualTo(62.5)
        assertThat(prescription.repsMin).isEqualTo(8)
        assertThat(prescription.reason).contains("back to 8 reps")
    }

    @Test
    fun `double progression aims one above the worst set`() {
        val sets = listOf(SetResult(60.0, 10), SetResult(60.0, 9), SetResult(60.0, 8))
        val prescription = next(evaluate(ProgressionType.DOUBLE_PROGRESSION, sets))
        assertThat(prescription.repsMin).isEqualTo(9)
    }

    // --------------------------------------------------------------- RPE_BASED

    @Test
    fun `rpe based reduces load when the session was harder than target`() {
        val rpeTarget = target.copy(targetRpe = 8.0)
        val sets = List(3) { SetResult(100.0, 5, rpe = 10.0) }
        val prescription = next(
            evaluate(ProgressionType.RPE_BASED, sets, target = rpeTarget),
        )
        assertThat(prescription.weightKg).isLessThan(100.0)
        assertThat(prescription.reason).contains("RPE 10")
    }

    @Test
    fun `rpe based raises load when the session was easier than target`() {
        val rpeTarget = target.copy(targetRpe = 9.0)
        val sets = List(3) { SetResult(100.0, 5, rpe = 7.0) }
        val prescription = next(evaluate(ProgressionType.RPE_BASED, sets, target = rpeTarget))
        assertThat(prescription.weightKg).isGreaterThan(100.0)
    }

    @Test
    fun `rpe based without a target rpe is a validation error not a guess`() {
        val sets = List(3) { SetResult(100.0, 5, rpe = 8.0) }
        assertThat(error(evaluate(ProgressionType.RPE_BASED, sets)))
            .contains("needs a target RPE")
    }

    @Test
    fun `rpe based holds the weight when no rpe was logged`() {
        val rpeTarget = target.copy(targetRpe = 8.0)
        val sets = List(3) { SetResult(90.0, 8) }
        val prescription = next(evaluate(ProgressionType.RPE_BASED, sets, target = rpeTarget))
        assertThat(prescription.weightKg).isEqualTo(90.0)
        assertThat(prescription.reason).contains("no RPE")
    }

    // ----------------------------------------------------------- PERCENTAGE_1RM

    @Test
    fun `percentage of 1rm prescribes from the estimated max`() {
        val prescription = next(
            evaluate(
                ProgressionType.PERCENTAGE_1RM,
                emptyList(),
                RuleParams(percentageOf1rm = 80.0),
                estimated1rm = 150.0,
            ),
        )
        assertThat(prescription.weightKg).isEqualTo(120.0)
        // SPEC §10: show the formula used.
        assertThat(prescription.reason).contains("Epley")
    }

    @Test
    fun `percentage of 1rm derives the max from logged sets when none is given`() {
        val sets = listOf(SetResult(100.0, 5))
        val prescription = next(
            evaluate(
                ProgressionType.PERCENTAGE_1RM,
                sets,
                RuleParams(percentageOf1rm = 50.0),
            ),
        )
        // Epley on 100x5 is about 116.7, so half rounds to 57.5.
        assertThat(prescription.weightKg).isEqualTo(57.5)
    }

    @Test
    fun `percentage of 1rm says what is missing when there is no max yet`() {
        val prescription = next(evaluate(ProgressionType.PERCENTAGE_1RM, emptyList()))
        assertThat(prescription.weightKg).isEqualTo(0.0)
        assertThat(prescription.reason).contains("No estimated 1RM")
    }

    // ------------------------------------------------------------------ CUSTOM

    @Test
    fun `custom rule evaluates arithmetic over the documented variables`() {
        val sets = List(3) { SetResult(100.0, 8) }
        val prescription = next(
            evaluate(
                ProgressionType.CUSTOM,
                sets,
                RuleParams(expression = "weight + 5"),
            ),
        )
        assertThat(prescription.weightKg).isEqualTo(105.0)
        assertThat(prescription.reason).contains("weight + 5")
    }

    @Test
    fun `custom rule supports if else`() {
        val sets = List(3) { SetResult(100.0, 10) }
        val prescription = next(
            evaluate(
                ProgressionType.CUSTOM,
                sets,
                RuleParams(expression = "if (reps >= 8) weight + 5 else weight - 5"),
            ),
        )
        assertThat(prescription.weightKg).isEqualTo(105.0)
    }

    @Test
    fun `a malformed custom rule is a validation error, never a crash`() {
        val sets = List(3) { SetResult(100.0, 8) }
        listOf(
            "weight +",
            "weight + + 5",
            "(weight + 5",
            "weight / 0",
            "bogusVariable * 2",
            "if (reps > 8) weight",
            "weight = 100",
            "weight $ 5",
            "",
        ).forEach { expression ->
            val outcome = evaluate(
                ProgressionType.CUSTOM,
                sets,
                RuleParams(expression = expression),
            )
            assertThat(outcome).isInstanceOf(ProgressionOutcome.Invalid::class.java)
        }
    }

    @Test
    fun `a custom rule that works out negative is rejected`() {
        val sets = List(3) { SetResult(10.0, 8) }
        assertThat(error(evaluate(ProgressionType.CUSTOM, sets, RuleParams(expression = "weight - 100"))))
            .contains("negative")
    }
}

class ExpressionEvaluatorTest {

    private val vars = mapOf(
        "weight" to 100.0,
        "reps" to 8.0,
        "sets" to 3.0,
        "completedReps" to 24.0,
        "rpe" to 8.5,
        "failureCount" to 1.0,
        "week" to 4.0,
    )

    private fun value(expression: String): Double {
        val result = ExpressionEvaluator.evaluate(expression, vars)
        assertThat(result).isInstanceOf(ExpressionResult.Value::class.java)
        return (result as ExpressionResult.Value).value
    }

    private fun message(expression: String): String {
        val result = ExpressionEvaluator.evaluate(expression, vars)
        assertThat(result).isInstanceOf(ExpressionResult.Error::class.java)
        return (result as ExpressionResult.Error).message
    }

    @Test
    fun `arithmetic and precedence`() {
        assertThat(value("2 + 3 * 4")).isEqualTo(14.0)
        assertThat(value("(2 + 3) * 4")).isEqualTo(20.0)
        assertThat(value("10 / 4")).isEqualTo(2.5)
        assertThat(value("-5 + 10")).isEqualTo(5.0)
        assertThat(value("2.5 * 2")).isEqualTo(5.0)
    }

    @Test
    fun `variables resolve`() {
        assertThat(value("weight")).isEqualTo(100.0)
        assertThat(value("weight + reps")).isEqualTo(108.0)
        assertThat(value("week * 2")).isEqualTo(8.0)
    }

    @Test
    fun `comparisons produce one or zero so they compose`() {
        assertThat(value("reps > 5")).isEqualTo(1.0)
        assertThat(value("reps < 5")).isEqualTo(0.0)
        assertThat(value("reps >= 8")).isEqualTo(1.0)
        assertThat(value("reps <= 7")).isEqualTo(0.0)
        assertThat(value("reps == 8")).isEqualTo(1.0)
        assertThat(value("reps != 8")).isEqualTo(0.0)
        assertThat(value("(reps > 5) * 10")).isEqualTo(10.0)
    }

    @Test
    fun `if else picks a branch`() {
        assertThat(value("if (reps >= 8) 1 else 2")).isEqualTo(1.0)
        assertThat(value("if (reps > 100) 1 else 2")).isEqualTo(2.0)
        assertThat(value("if (failureCount >= 2) weight * 0.9 else weight + 2.5"))
            .isEqualTo(102.5)
    }

    @Test
    fun `nested if else works`() {
        assertThat(value("if (week > 3) if (reps > 5) 10 else 20 else 30")).isEqualTo(10.0)
    }

    @Test
    fun `helper functions`() {
        assertThat(value("min(5, 10)")).isEqualTo(5.0)
        assertThat(value("max(5, 10)")).isEqualTo(10.0)
        assertThat(value("round(2.6)")).isEqualTo(3.0)
        assertThat(value("min(weight, 90)")).isEqualTo(90.0)
    }

    @Test
    fun `errors are specific enough to fix`() {
        assertThat(message("weight +")).contains("ends before it finishes")
        assertThat(message("(weight")).contains("bracket")
        assertThat(message("weight / 0")).contains("divides by zero")
        assertThat(message("nope + 1")).contains("not a variable")
        assertThat(message("nope + 1")).contains("weight")
        assertThat(message("weight = 5")).contains("==")
        assertThat(message("if (reps > 2) 5")).contains("else")
        assertThat(message("1 @ 2")).contains("@")
        assertThat(message("1.2.3")).contains("not a number")
        assertThat(message("")).contains("empty")
    }

    @Test
    fun `trailing junk is reported rather than ignored`() {
        assertThat(message("weight 5")).contains("Unexpected")
    }

    @Test
    fun `absurd input cannot blow the stack`() {
        val deep = "(".repeat(200) + "1" + ")".repeat(200)
        assertThat(ExpressionEvaluator.evaluate(deep, vars))
            .isInstanceOf(ExpressionResult.Error::class.java)
        val long = "1 + ".repeat(500) + "1"
        assertThat(ExpressionEvaluator.evaluate(long, vars))
            .isInstanceOf(ExpressionResult.Error::class.java)
    }

    @Test
    fun `validate works without real values for the builder`() {
        assertThat(ExpressionEvaluator.validate("weight + 2.5"))
            .isInstanceOf(ExpressionResult.Value::class.java)
        assertThat(ExpressionEvaluator.validate("weight +"))
            .isInstanceOf(ExpressionResult.Error::class.java)
    }
}

class OneRepMaxTest {

    @Test
    fun `epley matches the stated formula`() {
        // 100 kg x 5 reps: 100 * (1 + 5/30) = 116.67
        assertThat(OneRepMax.epley(100.0, 5)).isWithin(0.01).of(116.67)
        assertThat(OneRepMax.epley(140.0, 1)).isEqualTo(140.0)
    }

    @Test
    fun `best takes the highest estimate across sets`() {
        val sets = listOf(SetResult(100.0, 5), SetResult(110.0, 3), SetResult(90.0, 10))
        assertThat(OneRepMax.best(sets)!!).isWithin(0.01).of(121.0)
    }

    @Test
    fun `best is null with nothing usable`() {
        assertThat(OneRepMax.best(emptyList())).isNull()
        assertThat(OneRepMax.best(listOf(SetResult(0.0, 5)))).isNull()
    }

    @Test
    fun `percentage weights round to a loadable increment`() {
        assertThat(OneRepMax.weightForPercentage(150.0, 80.0, 2.5)).isEqualTo(120.0)
        assertThat(OneRepMax.weightForPercentage(153.0, 77.0, 2.5)).isEqualTo(117.5)
        assertThat(OneRepMax.weightForPercentage(100.0, 70.0, 5.0)).isEqualTo(70.0)
    }
}

class PlateCalculatorTest {

    private val plates = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    @Test
    fun `loads the bar greedily from the heaviest plate`() {
        val load = PlateCalculator.calculate(100.0, 20.0, plates)
        assertThat(load.perSide).containsExactly(25.0, 15.0).inOrder()
        assertThat(load.totalKg).isEqualTo(100.0)
        assertThat(load.isExact).isTrue()
    }

    @Test
    fun `bar only when the target is the bar`() {
        val load = PlateCalculator.calculate(20.0, 20.0, plates)
        assertThat(load.perSide).isEmpty()
        assertThat(PlateCalculator.describe(load)).isEqualTo("Bar only")
    }

    @Test
    fun `reports a shortfall when the target is not loadable`() {
        val load = PlateCalculator.calculate(101.0, 20.0, listOf(20.0))
        assertThat(load.isExact).isFalse()
        assertThat(PlateCalculator.describe(load)).contains("short")
    }

    @Test
    fun `describes the load the way you would say it`() {
        val load = PlateCalculator.calculate(140.0, 20.0, plates)
        assertThat(PlateCalculator.describe(load)).isEqualTo("2 x 25, 1 x 10")
    }

    @Test
    fun `no plates available is not a crash`() {
        val load = PlateCalculator.calculate(100.0, 20.0, emptyList())
        assertThat(load.perSide).isEmpty()
        assertThat(load.shortfallKg).isEqualTo(80.0)
    }
}

class WarmupGeneratorTest {

    @Test
    fun `ramps up to the working weight`() {
        val warmups = WarmupGenerator.generate(100.0, 20.0)
        assertThat(warmups).isNotEmpty()
        assertThat(warmups.map { it.weightKg }).isInOrder()
        assertThat(warmups.last().weightKg).isLessThan(100.0)
        assertThat(warmups.first().reps).isAtLeast(warmups.last().reps)
    }

    @Test
    fun `nothing to ramp from at or below the bar`() {
        assertThat(WarmupGenerator.generate(20.0, 20.0)).isEmpty()
        assertThat(WarmupGenerator.generate(0.0, 20.0)).isEmpty()
    }

    @Test
    fun `never prescribes less than the bar`() {
        val warmups = WarmupGenerator.generate(30.0, 20.0)
        warmups.forEach { assertThat(it.weightKg).isAtLeast(20.0) }
    }
}
