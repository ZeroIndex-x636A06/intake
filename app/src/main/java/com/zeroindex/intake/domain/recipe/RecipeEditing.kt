package com.zeroindex.intake.domain.recipe

import com.zeroindex.intake.domain.plan.IngredientNutrition
import com.zeroindex.intake.domain.plan.RecipeMacroResult
import com.zeroindex.intake.domain.plan.RecipeMacros
import com.zeroindex.intake.domain.plan.compute

/**
 * One ingredient in the recipe editor. It is either a food the user picked ([foodId] set, the
 * per-100g values copied from it) or free text. With [grams] and all four per-100g values it counts
 * toward the recipe's macros; otherwise it is kept as a text line and listed as incomplete.
 */
data class IngredientDraft(
    /** Stable key for the list in the UI. */
    val key: String,
    val name: String,
    val foodId: String? = null,
    /** The amount as written ("2 cups"); display only. */
    val quantityText: String = "",
    val grams: Double? = null,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    /** Allergen words of a picked food, scanned together with the name. */
    val allergenTags: String = "",
    /** The group header this ingredient sits under ("For the Sauce"), or null. */
    val group: String? = null,
) {
    val hasNutrition: Boolean
        get() = (grams ?: 0.0) > 0.0 && kcalPer100g != null && proteinPer100g != null &&
            carbsPer100g != null && fatPer100g != null

    /** The line shown in the recipe and used for allergen scanning: "2 cups milk" or "250 g chicken". */
    val line: String
        get() = listOf(
            quantityText.ifBlank { grams?.takeIf { it > 0 }?.let { "${trim(it)} g" }.orEmpty() },
            name.trim(),
        ).filter { it.isNotBlank() }.joinToString(" ")

    fun nutrition(): IngredientNutrition? =
        if (hasNutrition) IngredientNutrition(name.trim(), grams!!, kcalPer100g!!, proteinPer100g!!, carbsPer100g!!, fatPer100g!!) else null

    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}

/** Live numbers for the editor. */
object RecipeEditing {
    /**
     * Per-serving macros from the ingredients that have data, plus the lines that do not (so the
     * editor can say what is not counted instead of presenting a partial total as complete).
     */
    fun macros(ingredients: List<IngredientDraft>, servings: Int): RecipeMacroResult {
        val used = ingredients.filter { it.name.isNotBlank() }
        return RecipeMacros.compute(
            known = used.mapNotNull { it.nutrition() },
            missingNames = used.filter { !it.hasNutrition }.map { it.line },
            servings = servings,
        )
    }

    /** One step per non-empty line; "1." and "Step 2:" numbering is dropped (cook mode numbers them). */
    fun stepsFromText(text: String): List<String> = com.zeroindex.intake.domain.plan.CookSteps.split(text)

    /** Ingredient lines pasted or typed one per line. Blank lines are dropped. */
    fun ingredientLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** A draft from a line of text: "2 cups milk" keeps its words, and a weight (g, kg, oz, lb) gives grams. */
    fun draftFromLine(key: String, line: String, group: String? = null): IngredientDraft {
        val p = IngredientLineParser.parse(line)
        return IngredientDraft(key = key, name = p.name, quantityText = p.quantityText, grams = p.grams, group = group)
    }

    /** Drafts from pasted text where "## For the Sauce" lines start a group; [defaultGroup] covers lines before any header. */
    fun draftsFromText(text: String, newKey: () -> String, defaultGroup: String? = null): List<IngredientDraft> =
        IngredientSections.parseWithHeaders(text, defaultGroup).map { (group, line) -> draftFromLine(newKey(), line, group) }
}
