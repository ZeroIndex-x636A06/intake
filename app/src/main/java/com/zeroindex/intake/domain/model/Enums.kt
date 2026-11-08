package com.zeroindex.intake.domain.model

/**
 * Domain vocabulary. Pure Kotlin on purpose: SPEC §2 forbids Android imports in
 * this package, and Room stores these by name in TEXT columns.
 */

enum class FoodSource { MANUAL, BARCODE, SEARCH, AI_PHOTO, RECIPE }

enum class MealSlot { BREAKFAST, LUNCH, DINNER, SNACK }

/** Self-reported confidence from the AI photo estimator (SPEC §6.4). */
enum class AiConfidence { HIGH, MEDIUM, LOW }

/** Data density behind a TDEE estimate (SPEC §8). */
enum class TdeeConfidence { LOW, MEDIUM, HIGH }

/** Progression rule flavours evaluated in the domain layer (SPEC §10). */
enum class ProgressionType { LINEAR, DOUBLE_PROGRESSION, RPE_BASED, PERCENTAGE_1RM, CUSTOM }

enum class PlanRecipeRole { MAIN, VARIATION }

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
