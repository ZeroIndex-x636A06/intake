package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.domain.plan.ShoppingContribution
import com.zeroindex.intake.domain.plan.StandaloneShopping
import com.zeroindex.intake.domain.recipe.IngredientGroupsCodec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the ingredient lines of chosen recipes and meal templates for a shopping list that belongs to no plan
 * (feedback round 4, item 11). Read only: nothing is written, and the plan's own shopping list is untouched.
 */
@Singleton
class ShoppingBuilderRepository @Inject constructor(
    private val recipeDao: RecipeDao,
    private val foodDao: FoodDao,
    private val templates: MealTemplateRepository,
) {
    /** Each id with how many times it is cooked or prepared (1 to 9). Unknown or deleted ids are skipped. */
    suspend fun contributions(recipes: Map<String, Int>, templateBatches: Map<String, Int>): List<ShoppingContribution> {
        val out = ArrayList<ShoppingContribution>()
        recipes.forEach { (id, batches) ->
            val recipe = recipeDao.findById(id) ?: return@forEach
            // Picked foods first ("250.0 g Chicken thigh"), then the text lines, in the order the recipe shows them.
            val fromFoods = recipeDao.ingredientsOf(id).mapNotNull { row ->
                foodDao.findById(row.foodId)?.let { Pair("${row.quantityGrams} g ${it.name}", row.groupLabel) }
            }
            val rawLines = recipe.ingredientsText.lines()
            val rawGroups = IngredientGroupsCodec.decode(recipe.ingredientGroups, rawLines.size)
            val fromText = rawLines.indices.filter { rawLines[it].isNotBlank() }.map { Pair(rawLines[it].trim(), rawGroups[it]) }
            val all = fromFoods + fromText
            out += StandaloneShopping.fromRecipe(recipe.name, all.map { it.first }, all.map { it.second }, batches)
        }
        templateBatches.forEach { (id, batches) ->
            val summary = templates.find(id) ?: return@forEach
            out += StandaloneShopping.fromTemplate(summary.template.name, summary.items.map { (food, grams) -> food.name to grams }, batches)
        }
        return out
    }
}
