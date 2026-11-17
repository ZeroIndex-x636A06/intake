package com.zeroindex.intake.domain.training

import kotlin.math.roundToInt

/**
 * SPEC §10: estimated 1RM over time, with the formula shown, so the number is never a black box. The formula is the
 * person's choice (Settings > Training, [OneRepMaxFormula]); every figure in the app goes through [estimate], so one
 * setting changes them all. Epley is the default:
 *
 *     1RM = weight x (1 + reps / 30)
 */
object OneRepMax {

    /** The formula in use. Set from the settings at start-up and whenever the person changes it. */
    @Volatile
    var formula: OneRepMaxFormula = OneRepMaxFormula.DEFAULT

    /** "Epley: 1RM = weight x (1 + reps / 30)", for the formula in use. */
    val formulaText: String get() = formula.description

    /** Estimated one-rep max with the formula in use. A single is the weight itself. */
    fun estimate(weightKg: Double, reps: Int): Double = formula.estimate(weightKg, reps)

    /** Epley whatever the setting is: for stating the default formula in tests. */
    fun epley(weightKg: Double, reps: Int): Double = OneRepMaxFormula.EPLEY.estimate(weightKg, reps)

    /** Best estimated 1RM across a set of logged sets, or null if there are none. */
    fun best(sets: List<SetResult>): Double? = sets
        .filter { it.reps >= 1 && it.weightKg > 0 }
        .maxOfOrNull { estimate(it.weightKg, it.reps) }

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
