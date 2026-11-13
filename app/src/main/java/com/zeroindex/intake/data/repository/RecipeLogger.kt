package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.recipe.EstimateTotals
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.ResolvedRecipeNutrition
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one way a recipe serving is logged: from the recipe screen, from cook mode's "Log a serving" and from Log food's
 * search. Numbers the recipe has are logged as they are; protein, carbs and fat it has none of come from the estimate
 * when there is one (and the row says so), and otherwise are stored as unlisted, never as 0 g. A recipe with no
 * calorie figure at all is not logged (the caller disables its button).
 */
@Singleton
class RecipeLogger @Inject constructor(
    private val foodLog: FoodLogRepository,
    private val recipes: RecipeRepository,
    private val clock: Clock,
) {
    /**
     * The local allergen engine over the recipe's name and every ingredient line (picked foods and text lines, with
     * their own tags). A model never decides this and nothing here can clear a warning.
     */
    suspend fun scan(recipe: RecipeEntity): AllergenScanResult {
        val drafts = runCatching { recipes.load(recipe.id)?.ingredients }.getOrNull()
        return if (drafts != null) {
            recipes.scan(recipe.name, drafts)
        } else {
            // The ingredient rows could not be read: the stored text still gets scanned, never skipped.
            recipes.scan(recipe.name, recipe.ingredientsText.lines().filter(String::isNotBlank).mapIndexed { i, l -> IngredientDraft(key = "t$i", name = l) })
        }
    }

    suspend fun logServings(
        recipe: RecipeEntity,
        slot: MealSlot,
        estimate: EstimateTotals? = null,
        servings: Double = 1.0,
        at: Long = clock.nowMillis(),
    ): FoodLogEntity? {
        val n = ResolvedRecipeNutrition.resolve(
            kcal = recipe.kcalPerServing, protein = recipe.proteinPerServing, carbs = recipe.carbsPerServing, fat = recipe.fatPerServing,
            fiber = recipe.fiberPerServing, sugar = recipe.sugarPerServing, sodiumMg = recipe.sodiumMgPerServing,
            saturatedFat = recipe.saturatedFatPerServing,
            estimate = estimate,
        )
        val kcal = n.kcal?.value ?: return null
        if (!servings.isFinite() || servings <= 0.0) return null
        val perServing = Macros(
            kcal = kcal,
            protein = n.protein?.value ?: 0.0,
            carbs = n.carbs?.value ?: 0.0,
            fat = n.fat?.value ?: 0.0,
            fiber = n.fiber?.value ?: 0.0,
            sugar = n.sugar?.value ?: 0.0,
            sodium = n.sodiumMg?.value ?: 0.0,
            saturatedFat = n.saturatedFat?.value ?: 0.0,
        )
        val count = if (servings == 1.0) "1 serving" else "${servings.toString().removeSuffix(".0")} servings"
        return foodLog.logPrepared(
            name = recipe.name,
            macros = perServing * servings,
            slot = slot,
            servingLabel = if (n.anyEstimated) "$count · macros estimated" else count,
            at = at,
            unknownMacros = n.unknownMacros,
        )
    }
}
