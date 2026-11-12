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

/**
 * Geometry for the hold-to-split calorie ring: the eaten arc cut into protein, carbs and fat by their share of calories.
 * Angles are in degrees, clockwise from 12 o'clock (0 = top). Pure.
 */
object MacroRingSplit {

    /** One coloured piece of the eaten arc. [midDegrees] is where its percentage label sits. */
    data class Segment(val macro: Macro, val pct: Int, val startDegrees: Float, val sweepDegrees: Float) {
        val midDegrees: Float get() = startDegrees + sweepDegrees / 2f
    }

    enum class Macro { PROTEIN, CARBS, FAT }

    /** Calories from each macro, in whole kcal (4, 4 and 9 kcal per gram). Negative and non-finite amounts count as zero. */
    data class Kcal(val protein: Int, val carbs: Int, val fat: Int)

    fun kcal(proteinGrams: Double, carbGrams: Double, fatGrams: Double): Kcal {
        fun Double.k(per: Double) = if (isFinite() && this > 0.0) Math.round(this * per).toInt() else 0
        return Kcal(proteinGrams.k(4.0), carbGrams.k(4.0), fatGrams.k(9.0))
    }

    /**
     * The segments of an eaten arc that covers [arcFraction] (0..1) of the ring, in order protein, carbs, fat, with
     * zero-share macros left out. Empty when there are no calories to split or the arc is empty.
     */
    fun segments(proteinGrams: Double, carbGrams: Double, fatGrams: Double, arcFraction: Float): List<Segment> {
        val shares = MacroShares.of(proteinGrams, carbGrams, fatGrams) ?: return emptyList()
        val arc = (if (arcFraction.isNaN()) 0f else arcFraction.coerceIn(0f, 1f)) * 360f
        if (arc <= 0f) return emptyList()
        var start = 0f
        val out = ArrayList<Segment>(3)
        listOf(Macro.PROTEIN to shares.proteinPct, Macro.CARBS to shares.carbPct, Macro.FAT to shares.fatPct).forEach { (m, pct) ->
            if (pct > 0) {
                val sweep = arc * pct / 100f
                out += Segment(m, pct, start, sweep)
                start += sweep
            }
        }
        return out
    }
}
