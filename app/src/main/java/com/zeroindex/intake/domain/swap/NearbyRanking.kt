package com.zeroindex.intake.domain.swap

import com.zeroindex.intake.domain.ai.PlanBFit
import com.zeroindex.intake.domain.nutrition.Macros
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What the nearby finder is for. The same finder serves both; only the ranking and the question to the model differ.
 * - [SWAP_VS_PLANNED]: options that stand in for a planned meal, ranked by closeness to its calories and protein.
 * - [HEALTHIER]: no planned meal to compare with, so options are ranked by how healthy they are for what is left today.
 */
enum class NearbyMode { SWAP_VS_PLANNED, HEALTHIER }

/**
 * An option's nutrition where some values may be unknown. Calories and protein are always present; the rest are null
 * when the menu or the source did not give them, and a null is never read as zero.
 */
data class MealNutrition(
    val kcal: Double,
    val protein: Double,
    val fiberG: Double? = null,
    val satFatG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
)

/** The score (0..1, higher is healthier) and a short plain reason, e.g. "High protein for the calories". */
data class HealthierScore(val score: Double, val reason: String)

object NearbyRanking {

    /**
     * Closeness of an option to a planned meal, 0..1 (1 is identical). Calories and protein count equally: each part is
     * 1 minus the relative gap, floored at 0. A planned meal with almost no protein is judged on a 10 g floor so a few
     * grams do not read as a huge relative gap.
     */
    fun closeness(option: Macros, planned: Macros): Double {
        val kcalPart = 1.0 - abs(option.kcal - planned.kcal) / max(planned.kcal, 100.0)
        val proteinPart = 1.0 - abs(option.protein - planned.protein) / max(planned.protein, 10.0)
        return 0.5 * kcalPart.coerceIn(0.0, 1.0) + 0.5 * proteinPart.coerceIn(0.0, 1.0)
    }

    // Weights of the healthier score. Unknown parts are left out and the rest are renormalised.
    const val W_PROTEIN_DENSITY = 0.30
    const val W_FIBER = 0.15
    const val W_SAT_FAT = 0.15
    const val W_SUGAR = 0.10
    const val W_SODIUM = 0.10
    const val W_FIT_TODAY = 0.20

    /** Protein calories as a share of the meal's calories at which the protein part is full: 40%. */
    const val PROTEIN_SHARE_FULL = 0.40

    /** Grams of fibre per 100 kcal at which the fibre part is full. */
    const val FIBER_PER_100_KCAL_FULL = 2.0

    /** Saturated fat's share of calories at which the part reaches zero. */
    const val SAT_FAT_SHARE_ZERO = 0.15

    /** Sugar's share of calories at which the part reaches zero. */
    const val SUGAR_SHARE_ZERO = 0.25

    /** Sodium in a meal at which the part reaches zero. */
    const val SODIUM_ZERO_MG = 1500.0

    /**
     * How healthy [n] is, for what is left today ([kcalLeft], [proteinLeft], each null when no target is set).
     * Rewards protein per calorie, fibre, and fit with what is left; penalises saturated fat, sugar and sodium. A
     * value that is unknown is skipped, never counted as zero, so a menu with only calories and protein is judged on
     * those and the score is not flattered by missing numbers.
     */
    fun healthier(n: MealNutrition, kcalLeft: Int?, proteinLeft: Int?): HealthierScore {
        val parts = mutableListOf<Pair<Double, Double>>() // weight to value
        val strengths = mutableListOf<Pair<Double, String>>() // priority to phrase
        val kcal = n.kcal
        if (kcal > 0) {
            val share = n.protein * 4 / kcal
            parts += W_PROTEIN_DENSITY to min(1.0, share / PROTEIN_SHARE_FULL)
            if (share >= 0.25) strengths += share to "High protein for the calories"
        }
        val fiber = n.fiberG
        if (fiber != null && kcal > 0) {
            val per100 = fiber / (kcal / 100)
            parts += W_FIBER to min(1.0, per100 / FIBER_PER_100_KCAL_FULL)
            if (per100 >= 1.5) strengths += per100 / 2 to "High in fibre"
        }
        val satFat = n.satFatG
        if (satFat != null && kcal > 0) {
            val share = satFat * 9 / kcal
            parts += W_SAT_FAT to (1.0 - min(1.0, share / SAT_FAT_SHARE_ZERO))
            if (share <= 0.07) strengths += 0.5 to "Low in saturated fat"
        }
        val sugar = n.sugarG
        if (sugar != null && kcal > 0) {
            val share = sugar * 4 / kcal
            parts += W_SUGAR to (1.0 - min(1.0, share / SUGAR_SHARE_ZERO))
            if (share <= 0.10) strengths += 0.4 to "Low in sugar"
        }
        val sodium = n.sodiumMg
        if (sodium != null) {
            parts += W_SODIUM to (1.0 - min(1.0, sodium / SODIUM_ZERO_MG))
            if (sodium <= 600) strengths += 0.4 to "Lower in salt"
        }
        if (kcalLeft != null && proteinLeft != null) {
            val fit = PlanBFit.score(n.kcal, n.protein, kcalLeft, proteinLeft).score
            parts += W_FIT_TODAY to fit
            if (kcalLeft > 0 && n.kcal <= kcalLeft && fit >= 0.7) strengths += 0.6 to "Fits what is left today"
        } else if (kcalLeft != null) {
            val budget = max(kcalLeft, 1)
            val fit = if (n.kcal <= budget) 1.0 else max(0.0, 1.0 - (n.kcal - budget) / budget)
            parts += W_FIT_TODAY to fit
            if (kcalLeft > 0 && n.kcal <= kcalLeft) strengths += 0.6 to "Fits the calories left today"
        }
        val total = parts.sumOf { it.first }
        val score = if (total <= 0) 0.0 else parts.sumOf { it.first * it.second } / total
        val reason = strengths.sortedByDescending { it.first }.take(2).joinToString(" · ") { it.second }
            .ifEmpty { "Not a standout choice" }
        return HealthierScore(score.coerceIn(0.0, 1.0), reason)
    }

    /** Options that can be chosen first, healthiest first; blocked ("contains") ones last, in the same order. */
    fun orderHealthier(options: List<SwapOption>): List<SwapOption> =
        options.sortedWith(
            compareBy<SwapOption> { it.blocked }
                .thenByDescending { it.score ?: 0.0 }
                .thenBy { it.title.lowercase() },
        )
}
