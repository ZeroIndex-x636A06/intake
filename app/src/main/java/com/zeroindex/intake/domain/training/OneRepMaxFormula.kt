package com.zeroindex.intake.domain.training

import kotlin.math.pow

/**
 * The published formulas that turn "weight x reps" into an estimated one-rep max. The choice is one setting
 * (Settings > Training) and every estimated max in the app follows it through [OneRepMax.estimate].
 *
 * All of them return the weight itself for a single, and they get less reliable as the reps go up (past about 10).
 */
enum class OneRepMaxFormula(val id: String, val label: String, val formulaText: String, val source: String) {
    /** Epley, 1985. */
    EPLEY("epley", "Epley", "weight x (1 + reps / 30)", "Epley, 1985") {
        override fun raw(weightKg: Double, reps: Int): Double = weightKg * (1.0 + reps / 30.0)
    },

    /** Brzycki, 1993. The reps are capped at 36 because the formula divides by (37 - reps). */
    BRZYCKI("brzycki", "Brzycki", "weight x 36 / (37 - reps)", "Brzycki, 1993") {
        override fun raw(weightKg: Double, reps: Int): Double = weightKg * 36.0 / (37 - reps.coerceAtMost(MAX_BRZYCKI_REPS))
    },

    /** Lombardi, 1989. */
    LOMBARDI("lombardi", "Lombardi", "weight x reps ^ 0.10", "Lombardi, 1989") {
        override fun raw(weightKg: Double, reps: Int): Double = weightKg * reps.toDouble().pow(0.10)
    },

    /** The mean of the three above. */
    AVERAGE("average", "Average", "the mean of Epley, Brzycki and Lombardi", "Mean of the three") {
        override fun raw(weightKg: Double, reps: Int): Double =
            (EPLEY.raw(weightKg, reps) + BRZYCKI.raw(weightKg, reps) + LOMBARDI.raw(weightKg, reps)) / 3.0
    },
    ;

    protected abstract fun raw(weightKg: Double, reps: Int): Double

    /** Estimated one-rep max in the same unit as [weightKg]. A single is the weight itself. */
    fun estimate(weightKg: Double, reps: Int): Double {
        require(reps >= 1) { "reps must be at least 1, was $reps" }
        if (reps == 1) return weightKg
        return raw(weightKg, reps)
    }

    /** "Epley: 1RM = weight x (1 + reps / 30)". */
    val description: String get() = "$label: 1RM = $formulaText"

    companion object {
        const val MAX_BRZYCKI_REPS = 36
        val DEFAULT = EPLEY
        fun byId(id: String?): OneRepMaxFormula = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
