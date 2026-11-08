package com.zeroindex.intake.data.ai.vision

import com.zeroindex.intake.data.ai.S
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What the Vision Tracker returns (SPEC §6.4, extended by amendment §1 with a
 * conservative range and hidden calories). Field names match the schema the
 * model is held to.
 */
@Serializable
data class VisionEstimate(
    val items: List<VisionItem>,
    /** Oils, butter, sauces and dressings - the calories a photo hides. */
    val hiddenCalorieSources: List<HiddenCalories>,
    val range: MacroRange,
    val portionAssumption: String,
    val overallConfidence: String,
    /** True when the photo is not of food at all. */
    val notFood: Boolean,
)

@Serializable
data class VisionItem(
    val name: String,
    val estimatedGrams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val confidence: String,
    val containsAllergens: List<String>,
    val allergenReasoning: String,
)

@Serializable
data class HiddenCalories(val source: String, val kcal: Double)

/**
 * The plausible span for the whole plate. "Conservative" for someone eating to
 * a target means it errs toward more calories and less protein than the
 * point estimate, so the high end of kcal and the low end of protein are the
 * cautious numbers.
 */
@Serializable
data class MacroRange(
    val kcalLow: Double,
    val kcalHigh: Double,
    val proteinLow: Double,
    val proteinHigh: Double,
    val carbsLow: Double,
    val carbsHigh: Double,
    val fatLow: Double,
    val fatHigh: Double,
)

internal object VisionSchema {
    private val confidence = S.enumOf("high", "medium", "low")

    val schema: JsonObject = S.obj(
        "items" to S.array(
            S.obj(
                "name" to S.string("Plain food name, e.g. 'grilled chicken thigh'"),
                "estimatedGrams" to S.number("Edible weight in grams"),
                "kcal" to S.number(),
                "protein" to S.number("grams"),
                "carbs" to S.number("grams"),
                "fat" to S.number("grams, including any cooking fat on this item"),
                "confidence" to confidence,
                "containsAllergens" to S.array(S.string()),
                "allergenReasoning" to S.string("Why, or empty if none"),
            ),
        ),
        "hiddenCalorieSources" to S.array(
            S.obj("source" to S.string("e.g. '1 tbsp olive oil in the pan'"), "kcal" to S.number()),
        ),
        "range" to S.obj(
            "kcalLow" to S.number(), "kcalHigh" to S.number(),
            "proteinLow" to S.number(), "proteinHigh" to S.number(),
            "carbsLow" to S.number(), "carbsHigh" to S.number(),
            "fatLow" to S.number(), "fatHigh" to S.number(),
        ),
        "portionAssumption" to S.string("The one assumption that most moves the numbers, e.g. 'a 2-cup bowl'"),
        "overallConfidence" to confidence,
        "notFood" to S.boolean(),
    )

    fun systemPrompt(avoided: Collection<String>): String = buildString {
        appendLine("You estimate the nutrition of a meal from one photo for a calorie-tracking app.")
        appendLine("Identify every distinct food. Estimate edible grams from scale cues - plate, fork, hand.")
        appendLine(
            "Account for calories a photo hides: cooking oil, butter, sauces, dressings, sugar in drinks. " +
                "List each in hiddenCalorieSources and include it in the matching item's numbers.",
        )
        appendLine(
            "Give a range for the whole plate that covers the realistic uncertainty. Be conservative: when " +
                "unsure, lean the range toward more calories and less protein, never the reverse.",
        )
        appendLine("State the single portion assumption that most affects the result.")
        if (avoided.isNotEmpty()) {
            appendLine(
                "The user avoids: ${avoided.joinToString()}. For every item that is likely to contain one - " +
                    "including hidden forms such as butter, cream, whey, casein or ghee for dairy - list it in " +
                    "containsAllergens and say why. When a dish commonly contains it, flag it even if it is not visible.",
            )
        }
        appendLine("If the photo is not food, set notFood to true and return empty lists and zeros.")
        append("Reply with JSON only, matching the schema. No prose, no markdown.")
    }
}
