package com.zeroindex.intake.domain.training

import kotlin.math.roundToInt

/**
 * SPEC §10: estimated 1RM over time, with the formula shown. Epley, stated
 * openly so the number is never a black box:
 *
 *     1RM = weight x (1 + reps / 30)
 */
object OneRepMax {

    const val FORMULA = "Epley: 1RM = weight x (1 + reps / 30)"

    fun epley(weightKg: Double, reps: Int): Double {
        require(reps >= 1) { "reps must be at least 1, was $reps" }
        // A true single needs no estimating.
        if (reps == 1) return weightKg
        return weightKg * (1.0 + reps / 30.0)
    }

    /** Best estimated 1RM across a set of logged sets, or null if there are none. */
    fun best(sets: List<SetResult>): Double? = sets
        .filter { it.reps >= 1 && it.weightKg > 0 }
        .maxOfOrNull { epley(it.weightKg, it.reps) }

    /**
     * Weight for a target percentage of 1RM, rounded to the nearest
     * [incrementKg] so the result is loadable.
     */
    fun weightForPercentage(
        oneRepMax: Double,
        percentage: Double,
        incrementKg: Double = 2.5,
    ): Double {
        require(percentage > 0) { "percentage must be positive, was $percentage" }
        require(incrementKg > 0) { "incrementKg must be positive, was $incrementKg" }
        val raw = oneRepMax * percentage / 100.0
        return (raw / incrementKg).roundToInt() * incrementKg
    }
}
