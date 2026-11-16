package com.zeroindex.intake.data.ai.vision

import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.domain.ai.HiddenCalorieRules
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
    /** Up to three short questions, only when the model is not confident. Empty otherwise. */
    val questions: List<VisionQuestion> = emptyList(),
    /** Brand and product name when the food looks like a packaged product (a label beats a guess); else null. */
    val packagedProduct: String? = null,
)

/** One question with the answers offered, as the model returns it. */
@Serializable
data class VisionQuestion(val question: String, val choices: List<String>)

@Serializable
data class VisionItem(
    val name: String,
    val estimatedGrams: Double,
    /** The plausible portion span in grams, as a range around [estimatedGrams]; null when the model gave none. */
    val gramsLow: Double? = null,
    val gramsHigh: Double? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val confidence: String,
    val containsAllergens: List<String>,
    val allergenReasoning: String,
)

@Serializable
data class HiddenCalories(
    val source: String,
    val kcal: Double,
    /** What shows it is there: see `HiddenCalorieRules.BASES`. Defaults to seen so an older reply still reads. */
    val basis: String = "seen_in_photo",
)

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
                "estimatedGrams" to S.number("Edible weight in grams, your best single estimate"),
                "gramsLow" to S.nullableNumber("Lowest plausible edible weight in grams"),
                "gramsHigh" to S.nullableNumber("Highest plausible edible weight in grams"),
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
            S.obj(
                "source" to S.string("e.g. '1 tbsp olive oil in the pan'"),
                "kcal" to S.number(),
                "basis" to S.enumOf(*HiddenCalorieRules.BASES.toTypedArray()),
            ),
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
        "questions" to S.array(
            S.obj(
                "question" to S.string("A short question the person can answer by tapping, e.g. 'Cooked with water or milk?'"),
                "choices" to S.array(S.string("A short answer, 1 to 4 words")),
            ),
        ),
        "packagedProduct" to S.nullableString(
            "Brand and product name when this looks like a specific packaged or branded product, else null",
        ),
    )

    fun systemPrompt(avoided: Collection<String>): String = buildString {
        appendLine("You estimate the nutrition of a meal from one photo for a calorie-tracking app.")
        appendLine(
            "Return one entry in items for every distinct food on the plate, drink or side, never one entry for the " +
                "whole meal. Estimate each item's edible grams from scale cues - plate, fork, hand - with a low and " +
                "high bound around it.",
        )
        appendLine(
            "Account for calories a photo hides (cooking oil, butter, milk, sauces, dressings, sugar in drinks) only " +
                "when something shows them: a visible sheen or sauce, the person's note, or their answers. List each " +
                "one you counted in hiddenCalorieSources with its basis and include it in the matching item's numbers. " +
                "Never add butter, milk or oil by default when nothing in the photo or the note shows it. If it " +
                "matters and you cannot tell, ask a question instead of guessing.",
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
        appendLine(
            "The user may add a note about what the photo cannot show (for example cooked in butter, a large portion, " +
                "sauce on the side). Treat it as true, fold it into the numbers, and never let it lower the allergen warnings.",
        )
        appendLine(
            "Set overallConfidence to high only when you are sure what each food is and roughly how much. When you " +
                "are not sure (what a food is, whether it is a packaged product, how it was cooked, a portion that moves " +
                "the calories a lot), put up to 3 short questions in questions, each with 2 to 4 short choices, most " +
                "likely first. When you are confident, return questions as an empty list. Never ask what the photo or " +
                "the note already answers.",
        )
        appendLine(
            "If the food looks like a specific packaged or branded product, put its brand and product name in " +
                "packagedProduct, otherwise null.",
        )
        appendLine("If the photo is not food, set notFood to true and return empty lists and zeros.")
        append("Reply with JSON only, matching the schema. No prose, no markdown.")
    }
}
