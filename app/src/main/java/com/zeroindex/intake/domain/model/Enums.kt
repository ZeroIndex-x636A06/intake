package com.zeroindex.intake.domain.model

/**
 * Domain vocabulary. Pure Kotlin on purpose: SPEC §2 forbids Android imports in
 * this package, and Room stores these by name in TEXT columns.
 */

/**
 * [DATABASE] is a copy of a row from the bundled offline USDA database;
 * [OPEN_FOOD_FACTS] a product fetched from Open Food Facts and not yet edited by
 * the user. Editing one in the app turns it into [BARCODE], which is what makes a
 * food eligible to contribute back (docs/FOOD-DATA.md).
 */
enum class FoodSource { MANUAL, BARCODE, SEARCH, AI_PHOTO, RECIPE, DATABASE, OPEN_FOOD_FACTS }

/**
 * Where a food-log row sits on Today. [DRINKS] is "no meal": a drink logged from Drinks, the water card or the watch.
 * Stored by name in the same TEXT column, so it needs no schema change; rows written before it keep their meal slot.
 */
enum class MealSlot {
    BREAKFAST, LUNCH, DINNER, SNACK, DRINKS;

    companion object {
        /** The four real meals, in day order: what a meal picker or the meal cards list. */
        val MEALS: List<MealSlot> = listOf(BREAKFAST, LUNCH, DINNER, SNACK)
    }
}

/** Self-reported confidence from the AI photo estimator (SPEC §6.4). */
enum class AiConfidence { HIGH, MEDIUM, LOW }

/** Data density behind a TDEE estimate (SPEC §8). */
enum class TdeeConfidence { LOW, MEDIUM, HIGH }

/** Progression rule flavours evaluated in the domain layer (SPEC §10). */
enum class ProgressionType { LINEAR, DOUBLE_PROGRESSION, RPE_BASED, PERCENTAGE_1RM, CUSTOM }

enum class PlanRecipeRole { MAIN, VARIATION }

/** What happens when a meal plan reaches its last week. */
enum class PlanLoopMode { NONE, REPEAT, REPEAT_N }

/** Shopping list grouping, ordered the way a store is walked (SPEC §9). */
enum class StoreCategory { PRODUCE, MEAT, PANTRY, FROZEN, OTHER }

enum class AllergenSeverity { AVOID, STRICT }

/**
 * How firmly a scan hit flags a food (SPEC §7.3, docs/ALLERGENS.md): a hard
 * "contains", a softer "may contain", the softer still "check the label" for foods
 * that commonly hide the allergen, and the purely informational
 * "mentioned as avoided" for free text that names the allergen only to say it is
 * left out ("no cheese"). Never persisted, so adding a value is free.
 */
enum class AllergenMatchKind(val rank: Int) {
    CONTAINS(3),
    MAY_CONTAIN(2),
    CHECK_LABEL(1),
    MENTIONED_AVOIDED(0),
}

/**
 * How firmly one keyword speaks (docs/ALLERGENS.md). Stored by name on the keyword row.
 * A keyword's level is the strongest verdict a hit on it can give; negation and
 * "-free" wording can only soften a hit in free text, never raise it.
 */
enum class KeywordLevel(val kind: AllergenMatchKind) {
    CONTAINS(AllergenMatchKind.CONTAINS),
    MAY_CONTAIN(AllergenMatchKind.MAY_CONTAIN),
    CHECK_LABEL(AllergenMatchKind.CHECK_LABEL),
}
