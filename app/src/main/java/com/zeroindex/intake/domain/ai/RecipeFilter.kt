package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.allergen.AllergenScanResult
import kotlin.math.roundToInt

/** What the user asked Recipe Hunter for. Macros are per serving. */
data class RecipeQuery(
    val minProteinPerServing: Int? = 40,
    val maxKcalPerServing: Int? = 650,
    val servings: Int = 4,
    val maxMinutes: Int? = 60,
    val cuisines: List<String> = emptyList(),
    val notes: String = "",
    /** Always the user's avoided allergens; the form shows them locked on. */
    val avoidedAllergens: Set<String> = emptySet(),
)

/** A recipe as extracted, reduced to what the filter checks. */
data class RecipeCandidate(
    val title: String,
    val sourceUrl: String,
    val ingredients: List<String>,
    val kcalPerServing: Double,
    val proteinPerServing: Double,
    val totalMinutes: Int?,
)

sealed interface RecipeVerdict {
    data object Passed : RecipeVerdict
    /** Passed, but an allergen review is needed before it can be saved or logged. */
    data class NeedsReview(val reason: String) : RecipeVerdict
    data class Rejected(val reason: String) : RecipeVerdict
}

/**
 * The local half of Recipe Hunter's loop: every recipe the model brings back
 * is judged here, in code, and never on the model's word.
 *
 * Order matters - allergens first, because a recipe that contains something
 * the user avoids is rejected whatever its macros. Macro goals get a 5%
 * tolerance: 39 g against a 40 g goal is a pass, since recipe macros are
 * estimates to begin with and rejecting on rounding helps no one.
 */
object RecipeFilter {

    const val TOLERANCE = 0.05

    fun evaluate(candidate: RecipeCandidate, query: RecipeQuery, scan: AllergenScanResult): RecipeVerdict {
        if (scan.blocks) {
            val terms = scan.contains.map { it.matchedTerm }.distinct().joinToString(", ")
            return RecipeVerdict.Rejected("Contains $terms")
        }
        if (!candidate.sourceUrl.startsWith("http")) return RecipeVerdict.Rejected("No source link to check it against")
        if (candidate.ingredients.isEmpty()) return RecipeVerdict.Rejected("No ingredient list")

        query.minProteinPerServing?.let { goal ->
            if (candidate.proteinPerServing < goal * (1 - TOLERANCE)) {
                return RecipeVerdict.Rejected(
                    "${candidate.proteinPerServing.roundToInt()} g protein per serving, goal ≥ $goal g",
                )
            }
        }
        query.maxKcalPerServing?.let { cap ->
            if (candidate.kcalPerServing > cap * (1 + TOLERANCE)) {
                return RecipeVerdict.Rejected("${candidate.kcalPerServing.roundToInt()} kcal per serving, goal ≤ $cap")
            }
        }
        query.maxMinutes?.let { cap ->
            val minutes = candidate.totalMinutes
            if (minutes != null && minutes > cap * (1 + TOLERANCE)) {
                return RecipeVerdict.Rejected("Takes $minutes min, goal ≤ $cap")
            }
        }
        if (scan.mayContain.isNotEmpty()) {
            return RecipeVerdict.NeedsReview(scan.mayContain.first().note)
        }
        return RecipeVerdict.Passed
    }
}
