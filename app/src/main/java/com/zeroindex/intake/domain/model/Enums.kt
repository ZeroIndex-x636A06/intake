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

/** Whether a scan hit is a hard "contains" or a softer "may contain" (SPEC §7.3). */
enum class AllergenMatchKind { CONTAINS, MAY_CONTAIN }
