package com.zeroindex.intake.data.recipe

import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.recipes.FoundRecipes
import com.zeroindex.intake.data.ai.recipes.RecipePrompts
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.recipe.PageNutrition
import com.zeroindex.intake.domain.recipe.RecipeDraft
import com.zeroindex.intake.domain.recipe.RecipeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only AI step of a link import: when a page has no recipe data, and only when the user taps
 * "Try with AI", the page's visible text goes through the existing gateway (its privacy setting,
 * model choice, budget and usage meter apply). The result is a draft the user reviews; the
 * allergen verdict is still computed locally from the ingredient lines.
 */
@Singleton
class RecipeAiExtractor @Inject constructor(private val gateway: AiGateway) {

    fun isAvailable(): Boolean = gateway.isConfigured()

    suspend fun extract(pageText: String, pageUrl: String): RecipeDraft? = extractCounted(pageText, pageUrl).draft

    /** A draft (or null when the text had no recipe) and what the call cost, for callers that count it against a cap. */
    data class Extracted(val draft: RecipeDraft?, val costUsd: Double?)

    suspend fun extractCounted(pageText: String, pageUrl: String): Extracted {
        val outcome = gateway.structured(
            feature = AiFeature.RECIPE_TRY_AI,
            trigger = AiTrigger.USER_ACTION,
            serializer = FoundRecipes.serializer(),
            schema = RecipePrompts.schema,
            system = SYSTEM,
            parts = listOf(AiPart.Text("Page address: $pageUrl\n\nPage text:\n${pageText.take(20_000)}")),
        )
        val cost = outcome.response.costUsd
        val r = outcome.value.recipes.firstOrNull() ?: return Extracted(null, cost)
        if (r.ingredients.isEmpty() && r.steps.isEmpty()) return Extracted(null, cost)
        val draft = RecipeDraft(
            name = r.title,
            servings = r.servings.takeIf { it > 0 },
            totalMinutes = r.totalMinutes?.toInt()?.takeIf { it > 0 },
            ingredients = r.ingredients,
            steps = r.steps,
            // Only numbers the page itself published; a model's own arithmetic is not shown as the page's.
            nutrition = if (r.macrosPublished) {
                PageNutrition(r.kcalPerServing, r.proteinPerServing, r.carbsPerServing, r.fatPerServing)
            } else {
                null
            },
            imageUrl = null,
            url = pageUrl,
            source = RecipeSource.JSON_LD,
        )
        return Extracted(draft, cost)
    }

    private companion object {
        const val SYSTEM = "You read the text of one recipe web page and copy its recipe into the schema. " +
            "Return exactly one recipe in the recipes array, or an empty array if the text has no recipe. " +
            "Copy ingredient lines with their quantities as written, and the method as separate steps. " +
            "Never invent ingredients or steps. Set macrosPublished true only if the page states nutrition per serving, " +
            "and then copy those numbers; otherwise set it false and use 0 for the numbers. " +
            "Use the page address for sourceUrl and the site name for sourceName. " +
            "Leave containsAllergens as an empty array and allergenReasoning empty: allergens are checked elsewhere. " +
            "Reply with JSON only."
    }
}
