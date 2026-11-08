package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.model.AllergenMatchKind

/** What a model said about one item it cannot see the ingredients of. */
data class ModelAllergenClaim(
    val itemName: String,
    val allergens: List<String>,
    val reasoning: String,
)

/**
 * How the local allergen engine and a model's guesses combine, wherever a
 * model is involved (photo estimates, Recipe Hunter, Plan B, menu import).
 *
 * One rule: **a model can raise a warning but never clear one.**
 * - A local "contains" match blocks, exactly as for any other food.
 * - A model claim the local engine did not find becomes an amber review:
 *   the model is guessing at ingredients, and a guess earns a check, not a
 *   block and not silence.
 * - Nothing a model says removes or downgrades a local match.
 */
object ModelAllergenPolicy {

    data class Verdict(
        val scan: AllergenScanResult,
        /** Worst kind per item index, for per-row markers. */
        val perItem: Map<Int, AllergenMatchKind>,
    )

    fun merge(
        local: List<AllergenScanResult>,
        claims: List<ModelAllergenClaim>,
        avoided: Set<String>,
        extraLocal: List<AllergenScanResult> = emptyList(),
        source: String = "The model",
    ): Verdict {
        val matches = mutableListOf<AllergenMatch>()
        val perItem = mutableMapOf<Int, AllergenMatchKind>()
        fun flag(index: Int, kind: AllergenMatchKind) {
            // Only ever raise: CONTAINS > MAY_CONTAIN > CHECK_LABEL.
            val current = perItem[index]
            if (current == null || kind.rank > current.rank) perItem[index] = kind
        }

        local.forEachIndexed { index, result ->
            if (result.matches.isNotEmpty()) {
                matches += result.matches
                flag(
                    index,
                    when {
                        result.blocks -> AllergenMatchKind.CONTAINS
                        result.mayContain.isNotEmpty() -> AllergenMatchKind.MAY_CONTAIN
                        else -> AllergenMatchKind.CHECK_LABEL
                    },
                )
            }
        }
        claims.forEachIndexed { index, claim ->
            val alreadyLocal = local.getOrNull(index)?.matches.orEmpty()
                // A "check the label" hint is not an answer: the model's claim still raises a review.
                .filter { it.kind != AllergenMatchKind.CHECK_LABEL }.map { it.allergen.lowercase() }.toSet()
            claim.allergens
                .map { it.trim().lowercase() }
                .mapNotNull { claimed -> avoided.firstOrNull { a -> claimed.contains(a) || a.contains(claimed) } }
                .distinct()
                .filter { it !in alreadyLocal }
                .forEach { allergen ->
                    flag(index, AllergenMatchKind.MAY_CONTAIN)
                    matches += AllergenMatch(
                        allergen = allergen,
                        matchedTerm = claim.itemName,
                        field = MatchField.NAME,
                        kind = AllergenMatchKind.MAY_CONTAIN,
                        note = "$source thinks \"${claim.itemName}\" may contain $allergen" +
                            claim.reasoning.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty() +
                            ". It cannot see the ingredients, so check before eating.",
                    )
                }
        }
        extraLocal.forEach { matches += it.matches }
        return Verdict(
            scan = AllergenScanResult(matches.distinctBy { Triple(it.allergen, it.matchedTerm, it.kind) }),
            perItem = perItem,
        )
    }
}
