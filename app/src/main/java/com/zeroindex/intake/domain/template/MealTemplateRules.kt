package com.zeroindex.intake.domain.template

import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.NutrientKey
import com.zeroindex.intake.domain.nutrition.Unlisted
import kotlin.math.roundToInt

/** One line of a meal template as the editor and the list see it: a food or recipe at an amount. */
data class TemplateLine(
    val name: String,
    val grams: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    /** False when nobody gave a protein figure for this food: it counts as unknown, not as 0 g. */
    val proteinListed: Boolean = true,
)

data class TemplateTotals(val kcal: Double, val protein: Double, val proteinComplete: Boolean, val proteinKnownAny: Boolean) {
    /** "35 g protein", "35 g+ protein" when some line has none listed, "protein not listed" when none does. */
    val proteinText: String
        get() = when {
            !proteinKnownAny -> "protein not listed"
            proteinComplete -> "${protein.roundToInt()} g protein"
            else -> "${protein.roundToInt()} g+ protein"
        }
}

/**
 * Rules for creating and editing meal templates (feedback round 4, item 11): what a valid template is, and its totals.
 * Pure, so the editor and the repository share one definition. A template belongs to no meal plan.
 */
object MealTemplateRules {
    const val MAX_NAME = 60
    const val MAX_GRAMS = 5000.0
    const val MAX_LINES = 40

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    fun portionOk(grams: Double): Boolean = grams.isFinite() && grams > 0.0 && grams <= MAX_GRAMS

    /** Why the template cannot be saved yet, in words for the person; null when it can. */
    fun problem(name: String, lines: List<TemplateLine>): String? = when {
        cleanName(name).isEmpty() -> "Give the template a name."
        lines.isEmpty() -> "Add at least one food or recipe."
        lines.size > MAX_LINES -> "A template holds up to $MAX_LINES items."
        lines.any { !portionOk(it.grams) } ->
            "Check the amount for ${lines.first { !portionOk(it.grams) }.name}: it must be more than 0 and at most ${MAX_GRAMS.roundToInt()} g."
        else -> null
    }

    fun totals(lines: List<TemplateLine>): TemplateTotals {
        val kcal = lines.sumOf { it.kcalPer100g * it.grams / 100.0 }
        val listed = lines.filter { it.proteinListed }
        return TemplateTotals(
            kcal = kcal.takeIf { it.isFinite() } ?: 0.0,
            protein = listed.sumOf { it.proteinPer100g * it.grams / 100.0 }.takeIf { it.isFinite() } ?: 0.0,
            proteinComplete = lines.isNotEmpty() && listed.size == lines.size,
            proteinKnownAny = listed.isNotEmpty(),
        )
    }
}

/**
 * A saved recipe stored as a food, so a meal template (whose items are foods) can hold it. One serving is 100 g by
 * convention, so per-100 g figures are the per-serving figures and "2 servings" is 200 g. Figures the recipe never
 * gave stay unknown: they are stored as unlisted, not as 0 g, and a recipe with no calorie figure cannot be added.
 */
object RecipeFood {
    const val SERVING_GRAMS = 100.0
    const val SERVING_LABEL = "1 serving"

    data class Spec(
        val kcalPer100g: Double,
        val proteinPer100g: Double,
        val carbsPer100g: Double,
        val fatPer100g: Double,
        val fiberPer100g: Double,
        val sugarPer100g: Double,
        val sodiumMgPer100g: Double,
        val saturatedFatPer100g: Double,
        /** Stored in the food's unlisted list: optional nutrients not given, and protein, carbs or fat not given. */
        val unlisted: String,
        val proteinListed: Boolean,
    )

    fun spec(
        kcal: Double?, protein: Double?, carbs: Double?, fat: Double?,
        fiber: Double?, sugar: Double?, sodiumMg: Double?, saturatedFat: Double?,
    ): Spec? {
        if (kcal == null || !kcal.isFinite() || kcal < 0) return null
        val optional = buildSet {
            if (fiber == null) add(NutrientKey.FIBER)
            if (sugar == null) add(NutrientKey.SUGAR)
            if (sodiumMg == null) add(NutrientKey.SODIUM)
            if (saturatedFat == null) add(NutrientKey.SATURATED_FAT)
        }
        val core = buildSet {
            if (protein == null) add(CoreMacro.PROTEIN)
            if (carbs == null) add(CoreMacro.CARBS)
            if (fat == null) add(CoreMacro.FAT)
        }
        return Spec(
            kcalPer100g = kcal,
            proteinPer100g = protein ?: 0.0,
            carbsPer100g = carbs ?: 0.0,
            fatPer100g = fat ?: 0.0,
            fiberPer100g = fiber ?: 0.0,
            sugarPer100g = sugar ?: 0.0,
            sodiumMgPer100g = sodiumMg ?: 0.0,
            saturatedFatPer100g = saturatedFat ?: 0.0,
            unlisted = Unlisted.encode(optional, core),
            proteinListed = protein != null,
        )
    }

    fun gramsForServings(servings: Double): Double = servings * SERVING_GRAMS
    fun servingsForGrams(grams: Double): Double = grams / SERVING_GRAMS

    /** "1", "1.5", "2": servings as the editor shows them. */
    fun servingsText(grams: Double): String = servingsForGrams(grams).let {
        if (it == it.roundToInt().toDouble()) it.roundToInt().toString() else String.format(java.util.Locale.US, "%.1f", it)
    }
}
