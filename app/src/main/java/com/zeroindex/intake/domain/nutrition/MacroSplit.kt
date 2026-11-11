package com.zeroindex.intake.domain.nutrition

import kotlin.math.floor

/**
 * The share of calories from protein, carbs and fat, as whole percentages that add up to exactly 100.
 * Protein and carbs are 4 kcal per gram, fat 9. Pure.
 */
object MacroShares {

    data class Shares(val proteinPct: Int, val carbPct: Int, val fatPct: Int) {
        val total: Int get() = proteinPct + carbPct + fatPct
    }

    /**
     * The split for the given grams, or null when there are no calories to split (all zero, or nothing usable).
     * Negative and non-finite amounts count as zero. Whole percentages use largest remainders, so they sum to 100
     * and a macro with no calories stays at 0.
     */
    fun of(proteinGrams: Double, carbGrams: Double, fatGrams: Double): Shares? {
        val kcal = doubleArrayOf(
            proteinGrams.usable() * 4.0,
            carbGrams.usable() * 4.0,
            fatGrams.usable() * 9.0,
        )
        val total = kcal.sum()
        if (total <= 0.0) return null

        val exact = kcal.map { it / total * 100.0 }
        val pct = IntArray(3) { floor(exact[it]).toInt() }
        var missing = 100 - pct.sum()
        // Hand the missing points to the largest fractions first; ties go to the earlier macro.
        val order = (0..2).filter { exact[it] - floor(exact[it]) > 0.0 }
            .sortedWith(compareByDescending<Int> { exact[it] - floor(exact[it]) }.thenBy { it })
        for (i in order) {
            if (missing <= 0) break
            pct[i] += 1
            missing--
        }
        return Shares(pct[0], pct[1], pct[2])
    }

    private fun Double.usable(): Double = if (isFinite() && this > 0.0) this else 0.0
}
