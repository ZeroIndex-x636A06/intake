package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.nutrition.Macros

/** One ingredient line of a recipe: how much, and the food's per-100g nutrition (SPEC §4). */
data class IngredientNutrition(
    val name: String,
    val grams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val sodiumPer100g: Double = 0.0,
    val saturatedFatPer100g: Double = 0.0,
)

/**
 * SPEC §9: "Log this meal" writes the recipe's macros as one FoodLog, computed
 * from its ingredients divided by servings. Pure, so the arithmetic is tested
 * without a database.
 */
object RecipeMacros {

    /** Whole-recipe totals: the sum of per-100g values scaled by grams / 100. */
    fun total(ingredients: List<IngredientNutrition>): Macros =
        ingredients.fold(Macros.ZERO) { acc, i ->
            acc + Macros.fromPer100g(
                grams = i.grams.coerceAtLeast(0.0),
                kcal = i.kcalPer100g,
                protein = i.proteinPer100g,
                carbs = i.carbsPer100g,
                fat = i.fatPer100g,
                fiber = i.fiberPer100g,
                sugar = i.sugarPer100g,
                sodium = i.sodiumPer100g,
                saturatedFat = i.saturatedFatPer100g,
            )
        }

    /** One serving. A recipe saved with zero or negative servings counts as one. */
    fun perServing(ingredients: List<IngredientNutrition>, servings: Int): Macros =
        total(ingredients) * (1.0 / servings.coerceAtLeast(1))

    /** Recipe weight in grams for one serving. */
    fun gramsPerServing(ingredients: List<IngredientNutrition>, servings: Int): Double =
        ingredients.sumOf { it.grams.coerceAtLeast(0.0) } / servings.coerceAtLeast(1)
}

/**
 * Macros for a recipe where some ingredients may have no nutrition data. A plan
 * imported from prose has ingredient names but no grams or per-100g values;
 * summing only the known ones and presenting it as the total would silently
 * undercount, so [missing] names what was left out and [isComplete] says so.
 */
data class RecipeMacroResult(
    val total: com.zeroindex.intake.domain.nutrition.Macros,
    val perServing: com.zeroindex.intake.domain.nutrition.Macros,
    /** Ingredient lines that contributed nothing because they lack grams or nutrition. */
    val missing: List<String>,
    val knownCount: Int,
) {
    val isComplete: Boolean get() = missing.isEmpty() && knownCount > 0
    /** Nothing at all could be computed. */
    val isEmpty: Boolean get() = knownCount == 0
}

fun RecipeMacros.compute(
    known: List<IngredientNutrition>,
    missingNames: List<String>,
    servings: Int,
): RecipeMacroResult = RecipeMacroResult(
    total = total(known),
    perServing = perServing(known, servings),
    missing = missingNames.map { it.trim() }.filter { it.isNotEmpty() },
    knownCount = known.size,
)
