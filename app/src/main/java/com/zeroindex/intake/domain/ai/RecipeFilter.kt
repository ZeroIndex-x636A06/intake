package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.allergen.AllergenScanResult

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
    /**
     * "Use AI to read pages the phone can't": off by default. On, a page none of the local readers could parse has its
     * text sent to the model to copy out the recipe, counted in the search's cost and cap.
     */
    val aiReadPages: Boolean = false,
)

/** A recipe as read from its page, reduced to what the filter checks. Null numbers are "not stated". */
data class RecipeCandidate(
    val title: String,
    val sourceUrl: String,
    val ingredients: List<String>,
    val kcalPerServing: Double?,
    val proteinPerServing: Double?,
    val totalMinutes: Int?,
)

sealed interface RecipeVerdict {
    data object Passed : RecipeVerdict

    /** Passed, but an allergen review is needed before it can be saved or logged. */
    data class NeedsReview(val reason: String) : RecipeVerdict

    /** [reasons] is every count against the recipe (allergen, protein, calories, time); [reason] reads them as one line. */
    data class Rejected(val reasons: List<String>) : RecipeVerdict {
        constructor(reason: String) : this(listOf(reason))

        val reason: String get() = reasons.joinToString("; ")
    }

    /** The page could not be read, so nothing (allergens included) was checked. Never saved, never "kept". */
    data class Unverified(val reason: String) : RecipeVerdict
}

/**
 * The local half of Recipe Hunter's loop: every recipe is judged here, in code, and never on the model's word.
 *
 * Allergens first, because a recipe that contains something the user avoids is rejected whatever its macros.
 * Then the numbers the page itself published ([RecipeFit]): well outside a limit is rejected; a near miss
 * (within 10%) is not rejected, the caller shows it as a close match; a page with no nutrition is not rejected.
 */
object RecipeFilter {

    fun evaluate(candidate: RecipeCandidate, query: RecipeQuery, scan: AllergenScanResult): RecipeVerdict {
        val fit = fitOf(candidate, query)
        if (scan.blocks) {
            // Every count against the recipe, not only the first: the allergen and any missed limit.
            val terms = scan.contains.map { it.matchedTerm }.distinct().joinToString(", ")
            return RecipeVerdict.Rejected(
                listOf("Contains $terms") + fit.reasons,
            )
        }
        if (!candidate.sourceUrl.startsWith("http")) return RecipeVerdict.Rejected("No source link to check it against")
        if (candidate.ingredients.isEmpty()) return RecipeVerdict.Rejected("No ingredient list")

        if (fit.fit == Fit.OUT) return RecipeVerdict.Rejected(fit.reasons)
        if (scan.mayContain.isNotEmpty()) {
            return RecipeVerdict.NeedsReview(scan.mayContain.first().note)
        }
        return RecipeVerdict.Passed
    }

    fun fitOf(candidate: RecipeCandidate, query: RecipeQuery): FitResult =
        RecipeFit.assess(candidate.proteinPerServing, candidate.kcalPerServing, candidate.totalMinutes, query)
}
