package com.zeroindex.intake.data.recipe

import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.foodref.RefFood
import com.zeroindex.intake.domain.recipe.IngredientEstimator
import com.zeroindex.intake.domain.recipe.RecipeEstimate
import com.zeroindex.intake.domain.recipe.ReferenceFood
import com.zeroindex.intake.domain.recipe.ReferencePortion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Works out a recipe's nutrition from its ingredient lines against the bundled USDA database. Offline,
 * deterministic, and never an AI call; see [IngredientEstimator].
 */
@Singleton
class RecipeNutritionEstimator @Inject constructor(private val refs: FoodRefRepository) {

    suspend fun estimate(
        lines: List<String>,
        servings: Int,
        overrides: Map<Int, ReferenceFood> = emptyMap(),
    ): RecipeEstimate =
        IngredientEstimator.estimate(lines, servings, { q -> refs.search(q, limit = 30).map(::toReference) }, overrides)

    private fun toReference(f: RefFood) = ReferenceFood(
        id = f.id, name = f.name, kcal = f.kcal, protein = f.protein, carbs = f.carbs, fat = f.fat,
        fiber = f.fiber, sugar = f.sugar, sodiumMg = f.sodium, saturatedFat = f.saturatedFat,
        portions = f.portions.map { ReferencePortion(it.label, it.grams) },
    )
}
