package com.zeroindex.intake.domain.plan

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The plan-file format (docs/MEAL-PLAN.md), version 1. Everything beyond the
 * week number and dish name is optional, because a real plan is usually prose:
 * no grams, no nutrition, variations that are one-line ideas.
 */
@Serializable
data class PlanFile(
    val format: String = "",
    val version: Int = 0,
    /** Plan-wide: "How this works" guidance. */
    val intro: String = "",
    val standingBreakfasts: List<String> = emptyList(),
    val standingSnacks: List<String> = emptyList(),
    /** Plan-wide pantry list; becomes pantry-staple shopping items in every imported week. */
    val pantry: List<String> = emptyList(),
    val hiddenDairyChecklist: List<String> = emptyList(),
    /** Plan-wide guidance about eating out (why salads are risky, the verification habit). */
    val eatOutNotes: String = "",
    val weeks: List<PlanFileWeek> = emptyList(),
    val shoppingItems: List<PlanFileShoppingItem> = emptyList(),
    val eatOutOptions: List<PlanFileEatOut> = emptyList(),
)

@Serializable
data class PlanFileWeek(
    val weekNumber: Int = 0,
    val mainDishName: String = "",
    val proteinType: String = "",
    /** Free text such as "two approaches, the first is better". */
    val notes: String = "",
    /** How to serve it: "The bowls" / "The plates" text. */
    val serving: String = "",
    val servingTitle: String = "",
    /** One-line weeknight ideas. Not recipes; see [recipes] with role VARIATION for those. */
    val variations: List<String> = emptyList(),
    val recipes: List<PlanFileRecipe> = emptyList(),
)

@Serializable
data class PlanFileRecipe(
    val name: String = "",
    /** MAIN or VARIATION. */
    val role: String = "MAIN",
    val servings: Int = 1,
    val totalTimeMinutes: Int = 0,
    /** For a MAIN recipe these are the Sunday-batch steps. */
    val steps: List<String> = emptyList(),
    val ingredients: List<PlanFileIngredient> = emptyList(),
)

/**
 * An ingredient. With [grams] and all four per-100g values it counts toward the
 * recipe's macros; without them it is kept as a text line and the recipe's
 * macros are reported as incomplete.
 */
@Serializable
data class PlanFileIngredient(
    val name: String = "",
    /**
     * The amount as written, e.g. "3 lb", "1/2 cup", "5-6 lb" or "2". Optional display text
     * (the script format fills it); [grams] is the number the macro math uses.
     */
    val quantityText: String = "",
    val grams: Double? = null,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double? = null,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    /** Free text such as "milk, soy". Can only add warnings, never clear one. */
    val allergenTags: String = "",
) {
    val hasNutrition: Boolean
        get() = (grams ?: 0.0) > 0.0 && kcalPer100g != null && proteinPer100g != null &&
            carbsPer100g != null && fatPer100g != null
}

@Serializable
data class PlanFileShoppingItem(
    val name: String = "",
    /** Free text, may be empty ("2 lbs", "a bunch", ""). */
    val quantity: String = "",
    val category: String = "OTHER",
    val isPantryStaple: Boolean = false,
    /** Null: the item goes on every week in the file. */
    val weekNumber: Int? = null,
)

@Serializable
data class PlanFileEatOut(
    val venueName: String = "",
    val whatToOrder: String = "",
    val cautionNote: String = "",
    /** Null when the source gives no estimate; stored as 0 and shown as "no estimate". */
    val estimatedKcal: Int? = null,
    val estimatedProtein: Int? = null,
    val estimatedCarbs: Int? = null,
    val estimatedFat: Int? = null,
    /** Null: applies to every week. */
    val weekNumber: Int? = null,
)

sealed interface PlanFileParse {
    data class Ok(val file: PlanFile) : PlanFileParse
    data class Invalid(val errors: List<String>) : PlanFileParse
}

object PlanFileParser {
    const val FORMAT = "intake-meal-plan"
    const val SUPPORTED_VERSION = 1
    const val MAX_BYTES = 2_000_000
    private val CATEGORIES = setOf("PRODUCE", "MEAT", "PANTRY", "FROZEN", "OTHER")
    private val ROLES = setOf("MAIN", "VARIATION")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = true
    }

    fun parse(text: String): PlanFileParse {
        if (text.length > MAX_BYTES) return PlanFileParse.Invalid(listOf("The file is too large for a plan file."))
        val file = try {
            json.decodeFromString(PlanFile.serializer(), text)
        } catch (e: Exception) {
            return PlanFileParse.Invalid(listOf("This is not a readable plan file: ${e.message?.lineSequence()?.firstOrNull()}"))
        }
        val errors = validate(file)
        return if (errors.isEmpty()) PlanFileParse.Ok(file) else PlanFileParse.Invalid(errors)
    }

    fun validate(file: PlanFile): List<String> {
        val e = ArrayList<String>()
        if (file.format != FORMAT) e += "Not a meal plan file: format should be \"$FORMAT\"."
        if (file.version != SUPPORTED_VERSION) {
            e += if (file.version > SUPPORTED_VERSION) {
                "This file is version ${file.version}; this app reads version $SUPPORTED_VERSION. Update the app."
            } else {
                "Unsupported file version ${file.version}; expected $SUPPORTED_VERSION."
            }
        }
        if (file.weeks.isEmpty()) e += "The file has no weeks."
        val seen = HashSet<Int>()
        file.weeks.forEach { w ->
            val at = "Week ${w.weekNumber}"
            if (w.weekNumber !in 1..12) e += "Week number ${w.weekNumber} is outside 1-12."
            else if (!seen.add(w.weekNumber)) e += "$at appears more than once."
            if (w.mainDishName.isBlank()) e += "$at has no mainDishName."
            if (w.recipes.count { it.role.uppercase() == "MAIN" } > 1) e += "$at has more than one MAIN recipe."
            val names = HashSet<String>()
            w.recipes.forEach { r ->
                if (r.name.isBlank()) e += "$at has a recipe with no name."
                else if (!names.add(r.name.trim().lowercase())) e += "$at has two recipes named \"${r.name}\"."
                if (r.role.uppercase() !in ROLES) e += "$at / ${r.name}: role \"${r.role}\" must be MAIN or VARIATION."
                if (r.servings < 1) e += "$at / ${r.name}: servings must be at least 1."
                if (r.totalTimeMinutes < 0) e += "$at / ${r.name}: totalTimeMinutes cannot be negative."
                r.ingredients.forEach { i ->
                    val where = "$at / ${r.name} / ${i.name.ifBlank { "(unnamed)" }}"
                    if (i.name.isBlank()) e += "$at / ${r.name} has an ingredient with no name."
                    i.grams?.let { if (it < 0 || it > 20_000) e += "$where: grams $it is not plausible." }
                    val per100 = listOf(i.kcalPer100g, i.proteinPer100g, i.carbsPer100g, i.fatPer100g)
                    if (per100.any { it != null && it < 0 }) e += "$where: nutrition cannot be negative."
                    if ((i.kcalPer100g ?: 0.0) > 900.0) e += "$where: more than 900 kcal per 100 g is not possible."
                    val macroG = (i.proteinPer100g ?: 0.0) + (i.carbsPer100g ?: 0.0) + (i.fatPer100g ?: 0.0)
                    if (macroG > 100.5) e += "$where: protein + carbs + fat exceeds 100 g per 100 g."
                }
            }
        }
        file.shoppingItems.forEach { s ->
            if (s.name.isBlank()) e += "A shopping item has no name."
            if (s.category.uppercase() !in CATEGORIES) e += "Shopping item \"${s.name}\": category \"${s.category}\" is not one of $CATEGORIES."
            if (s.weekNumber != null && s.weekNumber !in 1..12) e += "Shopping item \"${s.name}\": weekNumber is outside 1-12."
        }
        file.eatOutOptions.forEach { o ->
            if (o.venueName.isBlank()) e += "An eat-out option has no venueName."
            if (o.whatToOrder.isBlank()) e += "Eat-out option at \"${o.venueName}\" has no whatToOrder."
            if (o.weekNumber != null && o.weekNumber !in 1..12) e += "Eat-out option at \"${o.venueName}\": weekNumber is outside 1-12."
            listOf(o.estimatedKcal, o.estimatedProtein, o.estimatedCarbs, o.estimatedFat).forEach {
                if (it != null && it < 0) e += "Eat-out option at \"${o.venueName}\": estimates cannot be negative."
            }
        }
        return e
    }
}

/**
 * Week-level extras that have no column of their own, kept as JSON inside
 * PlanWeek.notes so no schema change is needed (and so they sync and back up
 * with the week). Older or hand-entered plain-text notes are read as [note].
 */
@Serializable
data class PlanWeekExtras(
    val v: Int = 1,
    val note: String = "",
    val serving: String = "",
    val servingTitle: String = "",
    val variations: List<String> = emptyList(),
) {
    fun encode(): String =
        if (serving.isBlank() && servingTitle.isBlank() && variations.isEmpty()) note
        else extrasJson.encodeToString(serializer(), this)

    companion object {
        private val extrasJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(notes: String): PlanWeekExtras =
            if (notes.startsWith("{\"v\"")) {
                runCatching { extrasJson.decodeFromString(serializer(), notes) }.getOrElse { PlanWeekExtras(note = notes) }
            } else {
                PlanWeekExtras(note = notes)
            }
    }
}

/**
 * Plan-wide content ("Every week"): stored as JSON in the notes of a PlanWeek
 * row with weekNumber 0, which the week strip never shows. That row is covered
 * by sync, export and backup like any other.
 */
@Serializable
data class PlanMeta(
    val intro: String = "",
    val standingBreakfasts: List<String> = emptyList(),
    val standingSnacks: List<String> = emptyList(),
    val pantry: List<String> = emptyList(),
    val hiddenDairyChecklist: List<String> = emptyList(),
    val eatOutNotes: String = "",
) {
    val isEmpty: Boolean
        get() = intro.isBlank() && standingBreakfasts.isEmpty() && standingSnacks.isEmpty() &&
            pantry.isEmpty() && hiddenDairyChecklist.isEmpty() && eatOutNotes.isBlank()

    fun encode(): String = metaJson.encodeToString(serializer(), this)

    companion object {
        const val WEEK_NUMBER = 0
        private val metaJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun decode(notes: String?): PlanMeta =
            if (notes.isNullOrBlank()) PlanMeta()
            else runCatching { metaJson.decodeFromString(serializer(), notes) }.getOrDefault(PlanMeta())

        fun of(file: PlanFile) = PlanMeta(
            intro = file.intro.trim(),
            standingBreakfasts = file.standingBreakfasts.map { it.trim() }.filter { it.isNotEmpty() },
            standingSnacks = file.standingSnacks.map { it.trim() }.filter { it.isNotEmpty() },
            pantry = file.pantry.map { it.trim() }.filter { it.isNotEmpty() },
            hiddenDairyChecklist = file.hiddenDairyChecklist.map { it.trim() }.filter { it.isNotEmpty() },
            eatOutNotes = file.eatOutNotes.trim(),
        )
    }
}
