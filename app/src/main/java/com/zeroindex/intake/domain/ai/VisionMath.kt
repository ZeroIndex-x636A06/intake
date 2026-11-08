package com.zeroindex.intake.domain.ai

import kotlin.math.abs
import kotlin.math.roundToInt

/** One editable line on the review screen. */
data class EstimateLine(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
) {
    fun scaled(factor: Double) = copy(
        grams = grams * factor,
        kcal = kcal * factor,
        protein = protein * factor,
        carbs = carbs * factor,
        fat = fat * factor,
    )
}

/**
 * The arithmetic behind the photo review screen, kept pure so it is tested
 * rather than trusted.
 */
object VisionMath {

    /** SPEC §6.7: the slider runs 0.5x to 2x. */
    const val MIN_SCALE = 0.5
    const val MAX_SCALE = 2.0

    /** SPEC §6.9: the accuracy note appears after twenty corrections. */
    const val CORRECTIONS_FOR_NOTE = 20

    fun total(lines: List<EstimateLine>): EstimateLine = EstimateLine(
        name = "Total",
        grams = lines.sumOf { it.grams },
        kcal = lines.sumOf { it.kcal },
        protein = lines.sumOf { it.protein },
        carbs = lines.sumOf { it.carbs },
        fat = lines.sumOf { it.fat },
    )

    /**
     * The scale that brings the plate to the high end of the model's calorie
     * range - the one-tap "use the cautious estimate". Clamped to the
     * slider's range so the slider can always show where it landed.
     */
    fun cautiousScale(lines: List<EstimateLine>, kcalHigh: Double): Double {
        val kcal = total(lines).kcal
        if (kcal <= 0 || kcalHigh <= 0) return 1.0
        return (kcalHigh / kcal).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /**
     * "AI estimates have run about 15% low for you" (SPEC §6.9). Compares the
     * total the model estimated with the total the user settled on, across
     * every correction; one large meal therefore counts for more than a
     * snack, which matches how errors actually move a day's total.
     *
     * Null until there are enough corrections to say anything, or when the
     * bias is under 5% - "within 3%" is not worth a note.
     */
    fun biasNote(corrections: List<Pair<Double, Double>>): String? {
        if (corrections.size < CORRECTIONS_FOR_NOTE) return null
        val estimated = corrections.sumOf { it.first }
        val corrected = corrections.sumOf { it.second }
        if (estimated <= 0) return null
        val bias = corrected / estimated - 1.0
        val percent = (abs(bias) * 100).roundToInt()
        if (percent < 5) return null
        return if (bias > 0) {
            "AI estimates have run about $percent% low for you - you usually correct them upward."
        } else {
            "AI estimates have run about $percent% high for you - you usually correct them downward."
        }
    }

    /** Whether a line was changed enough to count as a correction. */
    fun isCorrection(estimatedKcal: Double, finalKcal: Double): Boolean =
        estimatedKcal > 0 && abs(finalKcal - estimatedKcal) / estimatedKcal > 0.01
}
