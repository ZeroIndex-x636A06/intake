package com.zeroindex.intake.domain.nutrition

import kotlin.math.roundToInt

/** What the per-meal screen needs from one logged entry. Kept free of the database type so this stays pure. */
data class MealItemFacts(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    /** Protein, carbs or fat the entry never had a number for (its row stores 0, which is not a measurement). */
    val unlisted: Set<CoreMacro> = emptySet(),
)

/** One macro's total over a meal: [grams] null means every entry left it out ("not listed", never 0). */
data class MacroTotal(val grams: Double?, val partial: Boolean) {
    /** "29 g", "at least 29 g" (some entries left it out) or "not listed". */
    val text: String
        get() = when {
            grams == null -> "not listed"
            partial -> "at least ${grams.roundToInt()} g"
            else -> "${grams.roundToInt()} g"
        }
}

/** The totals card of the per-meal screen. */
data class MealTotals(
    val items: Int,
    val kcal: Int,
    val protein: MacroTotal,
    val carbs: MacroTotal,
    val fat: MacroTotal,
) {
    /** The split bar's grams. A macro that is not listed counts as nothing in the split, which is shown beside the words. */
    val splitProtein: Double get() = protein.grams ?: 0.0
    val splitCarbs: Double get() = carbs.grams ?: 0.0
    val splitFat: Double get() = fat.grams ?: 0.0
}

object MealSummary {

    fun totals(items: List<MealItemFacts>): MealTotals {
        fun macro(macro: CoreMacro, pick: (MealItemFacts) -> Double): MacroTotal {
            if (items.isEmpty()) return MacroTotal(0.0, partial = false)
            val listed = items.filter { macro !in it.unlisted }
            return if (listed.isEmpty()) MacroTotal(null, partial = false) else MacroTotal(listed.sumOf(pick), partial = listed.size < items.size)
        }
        return MealTotals(
            items = items.size,
            kcal = items.sumOf { it.kcal }.roundToInt(),
            protein = macro(CoreMacro.PROTEIN) { it.protein },
            carbs = macro(CoreMacro.CARBS) { it.carbs },
            fat = macro(CoreMacro.FAT) { it.fat },
        )
    }

    /** "Carb 29 g · Fat 10 g · Protein 3 g" in the order of the macro cards on the tab: protein, carbs, fat. */
    fun macroLine(t: MealTotals): String = "Protein ${t.protein.text} · Carbs ${t.carbs.text} · Fat ${t.fat.text}"
}

/**
 * The − and + on a logged item. A portion moves by a step that suits its size (5 g for small amounts, then 10 g, then 25 g),
 * always landing on a multiple of the step. Stepping below one step is refused (null): remove the item instead.
 */
object PortionStep {

    fun stepFor(grams: Double): Double = when {
        grams < 50.0 -> 5.0
        grams < 200.0 -> 10.0
        else -> 25.0
    }

    /** [grams] one step up. */
    fun up(grams: Double): Double {
        val step = stepFor(grams)
        val snapped = (grams / step).let { if (it % 1.0 == 0.0) it else kotlin.math.floor(it) } * step
        return snapped + step
    }

    /** [grams] one step down, or null when that would reach zero. */
    fun down(grams: Double): Double? {
        val step = stepFor(grams - 1e-9)
        val snapped = kotlin.math.ceil(grams / step - 1e-9) * step
        val next = snapped - step
        return next.takeIf { it >= 1.0 }
    }
}
