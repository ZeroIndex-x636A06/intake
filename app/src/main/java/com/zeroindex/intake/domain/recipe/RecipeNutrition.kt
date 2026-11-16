package com.zeroindex.intake.domain.recipe

import com.zeroindex.intake.domain.nutrition.CoreMacro
import kotlin.math.roundToInt

enum class FigureSource { PAGE, ESTIMATE }

/** One per-serving number and where it came from. A number nobody has is not a Figure: it is null, and shows as "not listed". */
data class Figure(val value: Double, val source: FigureSource) {
    val estimated: Boolean get() = source == FigureSource.ESTIMATE

    /** "444 kcal", "18 g", or "~18 g" when it is an estimate. */
    fun text(unit: String): String = (if (estimated) "~" else "") + "${value.roundToInt()} $unit"
}

/** What the recipe screen and "Log a serving" work from. A missing figure is null, never zero. */
data class ResolvedRecipeNutrition(
    val kcal: Figure?,
    val protein: Figure?,
    val carbs: Figure?,
    val fat: Figure?,
    val fiber: Figure? = null,
    val sugar: Figure? = null,
    val sodiumMg: Figure? = null,
    val saturatedFat: Figure? = null,
) {
    val anyEstimated: Boolean
        get() = listOfNotNull(kcal, protein, carbs, fat, fiber, sugar, sodiumMg, saturatedFat).any { it.estimated }

    /** The core macros nobody has a number for. */
    val unknownMacros: Set<CoreMacro>
        get() = buildSet {
            if (protein == null) add(CoreMacro.PROTEIN)
            if (carbs == null) add(CoreMacro.CARBS)
            if (fat == null) add(CoreMacro.FAT)
        }

    val hasAnything: Boolean get() = kcal != null || protein != null || carbs != null || fat != null

    companion object {
        const val NOT_LISTED = "not listed"

        fun text(f: Figure?, unit: String): String = f?.text(unit) ?: NOT_LISTED

        /**
         * The page's (or the saved recipe's) own numbers win. Where it has none, the estimate from the ingredient
         * lines fills in, marked as an estimate. With neither, the figure stays null.
         */
        fun resolve(
            kcal: Double?, protein: Double?, carbs: Double?, fat: Double?,
            fiber: Double?, sugar: Double?, sodiumMg: Double?, saturatedFat: Double?,
            estimate: EstimateTotals?,
        ): ResolvedRecipeNutrition {
            fun pick(page: Double?, est: Double?): Figure? = when {
                page != null -> Figure(page, FigureSource.PAGE)
                est != null -> Figure(est, FigureSource.ESTIMATE)
                else -> null
            }
            return ResolvedRecipeNutrition(
                kcal = pick(kcal, estimate?.kcal),
                protein = pick(protein, estimate?.protein),
                carbs = pick(carbs, estimate?.carbs),
                fat = pick(fat, estimate?.fat),
                // Fibre, sugar, sodium and saturated fat are shown only as the page published them.
                fiber = pick(fiber, null),
                sugar = pick(sugar, null),
                sodiumMg = pick(sodiumMg, null),
                saturatedFat = pick(saturatedFat, null),
            )
        }
    }
}
