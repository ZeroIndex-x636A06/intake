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
        /**
         * The keyword table the claimed terms are resolved against: pass the user's full table (built-in
         * plus custom keywords) from the repository. Defaults to the built-in catalogue.
         */
        aliases: List<AllergenAlias> = AllergenCatalog.aliases(),
    ): Verdict {
        val avoidedKeys = avoided.map { it.trim().lowercase() }.toSet()
        val knownKeys = aliases.map { it.allergen.trim().lowercase() }.toSet() +
            AllergenCatalog.ALL.map { it.key } + avoidedKeys
        val matches = mutableListOf<AllergenMatch>()
        val perItem = mutableMapOf<Int, AllergenMatchKind>()
        fun flag(index: Int, kind: AllergenMatchKind) {
            // Only ever raise: CONTAINS > MAY_CONTAIN > CHECK_LABEL.
            val current = perItem[index]
            if (current == null || kind.rank > current.rank) perItem[index] = kind
        }

        local.forEach { if (it.matches.all { m -> m.kind == AllergenMatchKind.MENTIONED_AVOIDED }) matches += it.matches }
        local.forEachIndexed { index, result ->
            // "Mentioned as avoided" is informational: kept in the matches, never a per-item flag.
            if (result.matches.any { it.kind != AllergenMatchKind.MENTIONED_AVOIDED }) {
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
                .filter { it.kind != AllergenMatchKind.CHECK_LABEL && it.kind != AllergenMatchKind.MENTIONED_AVOIDED }.map { it.allergen.lowercase() }.toSet()
            // allergen label -> the model's raw term when it could not be placed in any category
            val raised = LinkedHashMap<String, String?>()
            if (avoidedKeys.isNotEmpty()) {
                claim.allergens.map { it.trim() }.filter { it.isNotEmpty() }.forEach { term ->
                    val categories = resolve(term, aliases, knownKeys)
                    if (categories.isEmpty()) {
                        // Never silently dropped: a term we cannot place is still a review.
                        raised["model flag: $term"] = term
                    } else {
                        // A known category the person does not avoid stays silent.
                        categories.filter { it in avoidedKeys }.forEach { raised[it] = null }
                    }
                }
            }
            raised.entries
                .filter { it.key !in alreadyLocal }
                .forEach { (allergen, rawTerm) ->
                    flag(index, AllergenMatchKind.MAY_CONTAIN)
                    val why = claim.reasoning.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
                    matches += AllergenMatch(
                        allergen = allergen,
                        matchedTerm = claim.itemName,
                        field = MatchField.NAME,
                        kind = AllergenMatchKind.MAY_CONTAIN,
                        note = if (rawTerm == null) {
                            "$source thinks \"${claim.itemName}\" may contain $allergen$why. " +
                                "It cannot see the ingredients, so check before eating."
                        } else {
                            "$source flagged \"$rawTerm\" for \"${claim.itemName}\"$why. " +
                                "It cannot see the ingredients, so check before eating."
                        },
                    )
                }
        }
        extraLocal.forEach { matches += it.matches }
        return Verdict(
            scan = AllergenScanResult(matches.distinctBy { Triple(it.allergen, it.matchedTerm, it.kind) }),
            perItem = perItem,
        )
    }

    /**
     * Maps one claimed term to allergen categories, deterministically and locally: an exact category key or
     * display name, else the keyword engine (whole words and phrases, plurals, US/UK spellings, the user's
     * custom keywords). Empty when the term is not recognised.
     */
    internal fun resolve(term: String, aliases: List<AllergenAlias>, knownKeys: Set<String>): Set<String> {
        val t = term.trim().lowercase()
        if (t in knownKeys) return setOf(t)
        AllergenCatalog.ALL.firstOrNull { it.displayName.lowercase() == t }?.let { return setOf(it.key) }
        return AllergenMatcher.scan(
            aliases = aliases,
            avoidedAllergens = knownKeys,
            name = t,
            checkLabelTerms = emptyList(),
        ).matches.map { it.allergen.trim().lowercase() }.toSet()
    }
}
