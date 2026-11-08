package com.zeroindex.intake.domain.ai

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Ranks an eat-out option against what is left of today's targets
 * (amendment §1 Plan B). Protein is weighted above calories, because that is
 * the framing the whole app uses: hitting protein is the goal, calories are
 * the budget it is spent from.
 *
 * - Protein: share of the remaining protein the meal covers, capped at 1.
 * - Calories: 1 while inside the remaining budget, falling linearly to 0 at
 *   double the budget. A meal that blows the day by 50% scores 0.5 here.
 *
 * With no target set, every option scores by protein density alone, so the
 * list is still in a useful order.
 */
object PlanBFit {

    const val PROTEIN_WEIGHT = 0.6
    const val KCAL_WEIGHT = 0.4

    data class Fit(val score: Double, val summary: String)

    fun score(kcal: Double, protein: Double, kcalLeft: Int?, proteinLeft: Int?): Fit {
        if (kcalLeft == null || proteinLeft == null) {
            val density = if (kcal > 0) protein * 4 / kcal else 0.0
            return Fit(density.coerceIn(0.0, 1.0), "${protein.roundToInt()} g protein · ${kcal.roundToInt()} kcal")
        }
        val proteinPart = if (proteinLeft <= 0) 1.0 else min(protein / proteinLeft, 1.0)
        val budget = max(kcalLeft, 1)
        val kcalPart = when {
            kcal <= budget -> 1.0
            else -> max(0.0, 1.0 - (kcal - budget) / budget)
        }
        val summary = buildString {
            append(
                if (proteinLeft <= 0) "Protein already met" else "Fits ${protein.roundToInt()} g of your $proteinLeft g protein left",
            )
            append(" · ")
            append(
                if (kcal <= kcalLeft) "${kcal.roundToInt()} of $kcalLeft kcal left"
                else "${(kcal - kcalLeft).roundToInt()} kcal over",
            )
        }
        return Fit(PROTEIN_WEIGHT * proteinPart + KCAL_WEIGHT * kcalPart, summary)
    }

    /**
     * Coordinates rounded to two decimal places, about a kilometre, before
     * they go anywhere: enough to find restaurants nearby, not enough to
     * place someone at an address.
     */
    fun coarsen(value: Double): Double = (value * 100).roundToInt() / 100.0
}
