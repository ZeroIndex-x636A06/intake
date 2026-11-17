package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.units.WeightText
import com.zeroindex.intake.domain.units.WeightUnit

/** One line of the percentage table. */
data class PercentRow(val percent: Int, val weightKg: Double)

/**
 * The maths behind the calculator screen. Everything is in kilograms, like the rest of the app; the screen converts what
 * the person types from their unit and back. The 1RM follows the one formula setting ([OneRepMax.estimate]).
 */
object TrainingCalculators {
    /** Above this the formulas are guesses, so the calculator says so instead of refusing. */
    const val RELIABLE_REPS = 10
    const val MAX_REPS = 30

    /** The estimated max for a set, or null when the weight or the reps are not usable. */
    fun estimate(weightKg: Double?, reps: Int?): Double? {
        if (weightKg == null || reps == null || !weightKg.isFinite() || weightKg <= 0.0 || reps !in 1..MAX_REPS) return null
        return OneRepMax.estimate(weightKg, reps)
    }

    /** A note for sets whose estimate is shaky, or null. */
    fun caution(reps: Int?): String? = when {
        reps == null -> null
        reps > RELIABLE_REPS -> "Estimates are least reliable above $RELIABLE_REPS reps. A heavier, lower-rep set gives a better number."
        else -> null
    }

    /** 50% to 100% of [oneRepMaxKg] in steps of [step] percent, lightest first. Empty for a max that is not positive. */
    fun percentTable(oneRepMaxKg: Double?, from: Int = 50, to: Int = 100, step: Int = 5): List<PercentRow> {
        if (oneRepMaxKg == null || !oneRepMaxKg.isFinite() || oneRepMaxKg <= 0.0 || step <= 0) return emptyList()
        return (from..to step step).map { PercentRow(it, oneRepMaxKg * it / 100.0) }
    }

    /** The plates for a target total, with the bar and plates from the person's settings. Empty-plate lists load nothing. */
    fun plates(targetKg: Double, barKg: Double, platesKg: List<Double>): PlateLoad =
        PlateCalculator.calculate(targetKg, barKg, platesKg)

    /** "Per side: 2 x 20, 1 x 5" or the shortfall, in the person's unit. */
    fun platesText(load: PlateLoad, unit: WeightUnit): String = PlateCalculator.describe(load, unit)

    /** "Bar 45 lb" in the person's unit. */
    fun barText(barKg: Double, unit: WeightUnit): String = "Bar ${WeightText.format(barKg, unit)}"
}
