package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.model.ProgressionType
import kotlin.math.max
import kotlin.math.roundToInt

/** One logged working set, as the rules see it. */
data class SetResult(
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isFailure: Boolean = false,
)

/** What to put on the bar next time, and why (SPEC §10). */
data class Prescription(
    val weightKg: Double,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val targetRpe: Double? = null,
    /**
     * Human-readable explanation, shown next to the number. SPEC §10: "a number
     * with no explanation is why people abandon these apps".
     */
    val reason: String,
    val isDeload: Boolean = false,
)

/** The target the rule is working against, from the program. */
data class ExerciseTarget(
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val targetRpe: Double? = null,
    /** Smallest loadable jump, from the user's plate inventory. */
    val incrementKg: Double = 2.5,
)

/** Parsed rule parameters. Defaults are what a sane linear progression needs. */
data class RuleParams(
    val incrementKg: Double = 2.5,
    val deloadPercent: Double = 10.0,
    val failuresBeforeDeload: Int = 2,
    val percentageOf1rm: Double = 75.0,
    val expression: String = "",
    /** Consecutive failed attempts recorded by the caller. */
    val failureCount: Int = 0,
    val week: Int = 1,
)

/** A rule that could not be applied, with a message fit for the builder UI. */
data class RuleError(val message: String)

sealed interface ProgressionOutcome {
    data class Next(val prescription: Prescription) : ProgressionOutcome
    data class Invalid(val error: RuleError) : ProgressionOutcome
}

/**
 * SPEC §10: pure functions taking the previous session's sets plus rule params
 * and returning the next prescription with a reason string. No Android, no
 * database, no clock - which is what makes every branch here testable.
 */
object ProgressionEvaluator {

    fun evaluate(
        type: ProgressionType,
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
        estimated1rm: Double? = null,
    ): ProgressionOutcome = when (type) {
        ProgressionType.LINEAR -> linear(previousSets, target, params)
        ProgressionType.DOUBLE_PROGRESSION -> doubleProgression(previousSets, target, params)
        ProgressionType.RPE_BASED -> rpeBased(previousSets, target, params)
        ProgressionType.PERCENTAGE_1RM -> percentage1rm(previousSets, target, params, estimated1rm)
        ProgressionType.CUSTOM -> custom(previousSets, target, params)
    }

    /**
     * LINEAR: add X kg if all target sets hit the target reps. After N
     * consecutive failures, deload by Y percent.
     */
    private fun linear(
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
    ): ProgressionOutcome {
        if (previousSets.isEmpty()) {
            return ProgressionOutcome.Next(
                Prescription(
                    weightKg = 0.0,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "First time on this lift. Pick a weight you can hold for " +
                        "${target.sets} sets of ${target.repsMin}, and it will go up from there.",
                ),
            )
        }

        val topWeight = previousSets.maxOf { it.weightKg }
        val hitAll = previousSets.size >= target.sets &&
            previousSets.count { it.reps >= target.repsMin } >= target.sets

        if (params.failureCount + (if (hitAll) 0 else 1) >= params.failuresBeforeDeload && !hitAll) {
            val deloaded = roundTo(topWeight * (1 - params.deloadPercent / 100.0), target.incrementKg)
            return ProgressionOutcome.Next(
                Prescription(
                    weightKg = deloaded,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    isDeload = true,
                    reason = "Deload to ${fmt(deloaded)} kg, down " +
                        "${fmt(params.deloadPercent)}% after " +
                        "${params.failureCount + 1} sessions short of " +
                        "${target.repsMin} reps.",
                ),
            )
        }

        return if (hitAll) {
            val next = topWeight + params.incrementKg
            ProgressionOutcome.Next(
                Prescription(
                    weightKg = next,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "${fmt(next)} kg, up ${fmt(params.incrementKg)} from last time " +
                        "(all ${target.sets} sets hit ${target.repsMin} reps).",
                ),
            )
        } else {
            val short = previousSets.filter { it.reps < target.repsMin }
            ProgressionOutcome.Next(
                Prescription(
                    weightKg = topWeight,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "Hold ${fmt(topWeight)} kg: ${short.size} set(s) came in under " +
                        "${target.repsMin} reps last time.",
                ),
            )
        }
    }

    /**
     * DOUBLE_PROGRESSION: work up the rep range at a fixed weight; once the top
     * of the range is hit on every set, add weight and drop back to the bottom.
     */
    private fun doubleProgression(
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
    ): ProgressionOutcome {
        if (previousSets.isEmpty()) {
            return ProgressionOutcome.Next(
                Prescription(
                    weightKg = 0.0,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "First time on this lift. Start at a weight you can get " +
                        "${target.repsMin} reps with on every set.",
                ),
            )
        }

        val topWeight = previousSets.maxOf { it.weightKg }
        val allAtTop = previousSets.size >= target.sets &&
            previousSets.count { it.reps >= target.repsMax } >= target.sets

        return if (allAtTop) {
            val next = topWeight + params.incrementKg
            ProgressionOutcome.Next(
                Prescription(
                    weightKg = next,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "${fmt(next)} kg, up ${fmt(params.incrementKg)}, back to " +
                        "${target.repsMin} reps (every set reached ${target.repsMax} " +
                        "at ${fmt(topWeight)}).",
                ),
            )
        } else {
            val lowest = previousSets.minOf { it.reps }
            ProgressionOutcome.Next(
                Prescription(
                    weightKg = topWeight,
                    sets = target.sets,
                    repsMin = (lowest + 1).coerceIn(target.repsMin, target.repsMax),
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "Same ${fmt(topWeight)} kg, aim for one more rep. Lowest set was " +
                        "$lowest; weight goes up once all ${target.sets} sets hit " +
                        "${target.repsMax}.",
                ),
            )
        }
    }

    /**
     * RPE_BASED: adjust load so logged RPE converges on the target, using the
     * RPE-to-percentage table.
     */
    private fun rpeBased(
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
    ): ProgressionOutcome {
        val targetRpe = target.targetRpe
            ?: return ProgressionOutcome.Invalid(
                RuleError("This rule needs a target RPE on the exercise."),
            )

        val rated = previousSets.filter { it.rpe != null }
        if (rated.isEmpty()) {
            val weight = previousSets.maxOfOrNull { it.weightKg } ?: 0.0
            return ProgressionOutcome.Next(
                Prescription(
                    weightKg = weight,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = targetRpe,
                    reason = if (weight > 0) {
                        "Same ${fmt(weight)} kg: no RPE was logged last time, so there is " +
                            "nothing to adjust against."
                    } else {
                        "First time on this lift. Work up to something that feels like " +
                            "RPE ${fmt(targetRpe)}."
                    },
                ),
            )
        }

        val averageRpe = rated.mapNotNull { it.rpe }.average()
        val topWeight = rated.maxOf { it.weightKg }
        val reps = rated.map { it.reps }.maxOrNull() ?: target.repsMin

        // Convert both the logged and the target effort to a percentage of 1RM
        // and scale the load by the ratio.
        val loggedPct = RpeTable.percentageOf1rm(averageRpe, reps)
        val targetPct = RpeTable.percentageOf1rm(targetRpe, reps)
        if (loggedPct <= 0.0) {
            return ProgressionOutcome.Invalid(
                RuleError("Logged RPE $averageRpe at $reps reps is outside the table."),
            )
        }

        val scaled = roundTo(topWeight * (targetPct / loggedPct), target.incrementKg)
        val delta = scaled - topWeight
        val direction = when {
            delta > 0 -> "up ${fmt(delta)}"
            delta < 0 -> "down ${fmt(-delta)}"
            else -> "unchanged"
        }
        return ProgressionOutcome.Next(
            Prescription(
                weightKg = scaled,
                sets = target.sets,
                repsMin = target.repsMin,
                repsMax = target.repsMax,
                targetRpe = targetRpe,
                reason = "${fmt(scaled)} kg, $direction: last session averaged RPE " +
                    "${fmt(averageRpe)} against a target of ${fmt(targetRpe)}.",
            ),
        )
    }

    /** PERCENTAGE_1RM: a percentage of an estimated 1RM that updates with new maxes. */
    private fun percentage1rm(
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
        estimated1rm: Double?,
    ): ProgressionOutcome {
        val oneRm = estimated1rm ?: OneRepMax.best(previousSets)
            ?: return ProgressionOutcome.Next(
                Prescription(
                    weightKg = 0.0,
                    sets = target.sets,
                    repsMin = target.repsMin,
                    repsMax = target.repsMax,
                    targetRpe = target.targetRpe,
                    reason = "No estimated 1RM yet. Log a hard set and this will start " +
                        "prescribing ${fmt(params.percentageOf1rm)}% of it.",
                ),
            )

        val weight = OneRepMax.weightForPercentage(oneRm, params.percentageOf1rm, target.incrementKg)
        return ProgressionOutcome.Next(
            Prescription(
                weightKg = weight,
                sets = target.sets,
                repsMin = target.repsMin,
                repsMax = target.repsMax,
                targetRpe = target.targetRpe,
                reason = "${fmt(weight)} kg, ${fmt(params.percentageOf1rm)}% of an estimated " +
                    "${fmt(oneRm)} kg max (${OneRepMax.FORMULA}).",
            ),
        )
    }

    /**
     * CUSTOM: a small expression over the documented variables. A malformed rule
     * surfaces as a validation error rather than taking the app down, which is
     * exactly what SPEC §10 asks for.
     */
    private fun custom(
        previousSets: List<SetResult>,
        target: ExerciseTarget,
        params: RuleParams,
    ): ProgressionOutcome {
        if (params.expression.isBlank()) {
            return ProgressionOutcome.Invalid(RuleError("This custom rule has no expression."))
        }

        val topWeight = previousSets.maxOfOrNull { it.weightKg } ?: 0.0
        val variables = mapOf(
            "weight" to topWeight,
            "reps" to target.repsMin.toDouble(),
            "sets" to target.sets.toDouble(),
            "completedReps" to (previousSets.sumOf { it.reps }).toDouble(),
            "rpe" to (previousSets.mapNotNull { it.rpe }.maxOrNull() ?: 0.0),
            "failureCount" to params.failureCount.toDouble(),
            "week" to params.week.toDouble(),
        )

        return when (val result = ExpressionEvaluator.evaluate(params.expression, variables)) {
            is ExpressionResult.Value -> {
                // Checked before rounding: roundTo clamps at zero, so testing
                // the rounded value would never catch a negative rule.
                if (result.value < 0) {
                    ProgressionOutcome.Invalid(
                        RuleError("That rule worked out to a negative weight (${fmt(result.value)})."),
                    )
                } else {
                    val weight = roundTo(result.value, target.incrementKg)
                    ProgressionOutcome.Next(
                        Prescription(
                            weightKg = weight,
                            sets = target.sets,
                            repsMin = target.repsMin,
                            repsMax = target.repsMax,
                            targetRpe = target.targetRpe,
                            reason = "${fmt(weight)} kg, from your own rule: " +
                                params.expression.trim(),
                        ),
                    )
                }
            }
            is ExpressionResult.Error -> ProgressionOutcome.Invalid(RuleError(result.message))
        }
    }

    private fun roundTo(value: Double, increment: Double): Double {
        if (increment <= 0) return value
        return max(0.0, (value / increment).roundToInt() * increment)
    }

    internal fun fmt(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else ((value * 10).roundToInt() / 10.0).toString()
}

/**
 * RPE to percentage of 1RM, the usual reps-in-reserve table. Values outside it
 * return 0.0 so callers can report an error instead of inventing a number.
 */
object RpeTable {

    // Rows are RPE 10 down to 6; columns are 1..12 reps.
    private val table: Map<Int, DoubleArray> = mapOf(
        10 to doubleArrayOf(100.0, 95.5, 92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 74.0, 71.8, 69.4),
        9 to doubleArrayOf(95.5, 92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 74.0, 71.8, 69.4, 67.0),
        8 to doubleArrayOf(92.2, 89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 74.0, 71.8, 69.4, 67.0, 64.9),
        7 to doubleArrayOf(89.2, 86.3, 83.7, 81.1, 78.6, 76.2, 74.0, 71.8, 69.4, 67.0, 64.9, 62.8),
        6 to doubleArrayOf(86.3, 83.7, 81.1, 78.6, 76.2, 74.0, 71.8, 69.4, 67.0, 64.9, 62.8, 60.8),
    )

    /** Linearly interpolates between whole RPE rows; 0.0 when out of range. */
    fun percentageOf1rm(rpe: Double, reps: Int): Double {
        if (reps < 1 || reps > 12) return 0.0
        val clamped = rpe.coerceIn(6.0, 10.0)
        val lower = clamped.toInt().coerceIn(6, 10)
        val upper = (lower + 1).coerceAtMost(10)
        val lowerPct = table[lower]?.get(reps - 1) ?: return 0.0
        val upperPct = table[upper]?.get(reps - 1) ?: lowerPct
        val fraction = clamped - lower
        return lowerPct + (upperPct - lowerPct) * fraction
    }
}
