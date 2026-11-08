package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.model.AllergenMatchKind

/** One alias term the user avoids, e.g. dairy -> "sodium caseinate". */
data class AllergenAlias(
    val allergen: String,
    val aliasTerm: String,
)

/** Where a match was found, so the warning can say exactly that (SPEC §7.2). */
enum class MatchField { NAME, BRAND, INGREDIENTS, STRUCTURED_TAG }

data class AllergenMatch(
    val allergen: String,
    val matchedTerm: String,
    val field: MatchField,
    val kind: AllergenMatchKind,
    /** Short human explanation, shown verbatim in the warning sheet. */
    val note: String,
)

data class AllergenScanResult(
    val matches: List<AllergenMatch>,
) {
    val contains: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.CONTAINS }
    val mayContain: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.MAY_CONTAIN }

    /** The softest tier: "Check the label". Never blocks, never opens a sheet. */
    val checkLabel: List<AllergenMatch> = matches.filter { it.kind == AllergenMatchKind.CHECK_LABEL }

    /** True when the save must be blocked behind the red sheet (SPEC §7.2). */
    val blocks: Boolean = contains.isNotEmpty()

    /** True when something stronger than "check the label" was found (the sheets key off this). */
    val hasAnything: Boolean = contains.isNotEmpty() || mayContain.isNotEmpty()
    val hasCheckLabel: Boolean = checkLabel.isNotEmpty()

    companion object {
        val CLEAN = AllergenScanResult(emptyList())
    }
}

/**
 * SPEC §7.1: runs the name, brand and ingredient text against the alias table,
 * case-insensitively, with whole-word matching so "buttermilk" matches and
 * "butternut" does not.
 *
 * Pure Kotlin so the matching rules are unit-testable on their own - the
 * buttermilk-versus-butternut case is the one SPEC §14 calls out by name.
 */
object AllergenMatcher {

    /**
     * Qualifiers that turn "milk" into something that is not dairy. A hit here
     * is softened to "may contain" rather than cleared: oat milk is not dairy,
     * but a shared production line is a real thing, and silently passing a
     * match is exactly what this engine exists to avoid.
     */
    private val PLANT_QUALIFIERS = setOf(
        "oat", "almond", "soy", "soya", "coconut", "rice", "cashew", "hemp",
        "pea", "macadamia", "walnut", "flax", "hazelnut", "pistachio", "banana",
    )

    /** Label phrasings that assert absence. Also softened rather than cleared. */
    private val NEGATION_PREFIXES = setOf("non", "no", "without", "free", "zero", "dairyfree")

    fun scan(
        aliases: List<AllergenAlias>,
        avoidedAllergens: Set<String>,
        name: String?,
        brand: String? = null,
        ingredients: String? = null,
        /** Structured "contains" tags, e.g. from Open Food Facts (SPEC §7.3). */
        structuredContains: List<String> = emptyList(),
        structuredTraces: List<String> = emptyList(),
        /** Foods that commonly hide an allergen; the softest tier (docs/ALLERGENS.md). */
        checkLabelTerms: List<CheckLabelTerm> = HiddenCarriers.DAIRY_TERMS,
        /**
         * Whether the ingredients text is scanned for [checkLabelTerms] too. Off by
         * default: a real ingredient panel already says what is in the food, and
         * "natural flavor" would nag on nearly every label. On for free-text
         * ingredient lines (plan recipes, Recipe Hunter), which have no label.
         */
        checkLabelInIngredients: Boolean = false,
    ): AllergenScanResult {
        if (avoidedAllergens.isEmpty()) return AllergenScanResult.CLEAN

        val avoided = avoidedAllergens.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val relevant = aliases.filter { it.allergen.trim().lowercase() in avoided }
        val found = LinkedHashMap<Pair<String, String>, AllergenMatch>()

        // SPEC §7.3: structured tags are the primary signal where they exist.
        structuredContains.forEach { tag ->
            matchAllergenForTag(tag, avoided, relevant)?.let { allergen ->
                found[allergen to "tag:$tag"] = AllergenMatch(
                    allergen = allergen,
                    matchedTerm = tag.substringAfterLast(':'),
                    field = MatchField.STRUCTURED_TAG,
                    kind = AllergenMatchKind.CONTAINS,
                    note = "The product's own allergen list declares " +
                        "${tag.substringAfterLast(':')}.",
                )
            }
        }
        structuredTraces.forEach { tag ->
            matchAllergenForTag(tag, avoided, relevant)?.let { allergen ->
                found.putIfAbsent(
                    allergen to "trace:$tag",
                    AllergenMatch(
                        allergen = allergen,
                        matchedTerm = tag.substringAfterLast(':'),
                        field = MatchField.STRUCTURED_TAG,
                        kind = AllergenMatchKind.MAY_CONTAIN,
                        note = "The product lists ${tag.substringAfterLast(':')} under traces " +
                            "or \"may contain\".",
                    ),
                )
            }
        }

        // Second pass over free text.
        listOf(
            MatchField.NAME to name,
            MatchField.BRAND to brand,
            MatchField.INGREDIENTS to ingredients,
        ).forEach { (field, text) ->
            if (text.isNullOrBlank()) return@forEach
            relevant.forEach inner@{ alias ->
                val occurrence = findWholeWord(text, alias.aliasTerm) ?: return@inner
                val softened = occurrence.softenReason
                val kind = if (softened == null) {
                    AllergenMatchKind.CONTAINS
                } else {
                    AllergenMatchKind.MAY_CONTAIN
                }
                val alreadyHardBlocked = found.values.any {
                    it.allergen.equals(alias.allergen, ignoreCase = true) &&
                        it.kind == AllergenMatchKind.CONTAINS
                }
                if (alreadyHardBlocked && kind == AllergenMatchKind.MAY_CONTAIN) return@inner

                val key = alias.allergen.lowercase() to alias.aliasTerm.lowercase()
                val note = softened ?: "\"${alias.aliasTerm}\" found in the ${field.describe()}."
                val existing = found[key]
                if (existing == null || (existing.kind == AllergenMatchKind.MAY_CONTAIN &&
                        kind == AllergenMatchKind.CONTAINS)
                ) {
                    found[key] = AllergenMatch(
                        allergen = alias.allergen,
                        matchedTerm = alias.aliasTerm,
                        field = field,
                        kind = kind,
                        note = note,
                    )
                }
            }
        }

        // Softest tier, last, and only where nothing stronger exists for that
        // allergen: this can add a nudge but can never replace or hide a stronger hit.
        val strongerFor = found.values.map { it.allergen.lowercase() }.toSet()
        val softFields = buildList {
            add(MatchField.NAME to name)
            if (checkLabelInIngredients) add(MatchField.INGREDIENTS to ingredients)
        }
        val seenPhrases = HashSet<String>()
        checkLabelTerms.forEach { term ->
            val allergen = term.allergen.trim().lowercase()
            if (allergen !in avoided || allergen in strongerFor) return@forEach
            var field: MatchField? = null
            var hit: String? = null
            for ((f, text) in softFields) {
                if (text.isNullOrBlank()) continue
                val m = term.findIn(text) ?: continue
                field = f
                hit = m.value.lowercase().replace(Regex("\\s+"), " ")
                break
            }
            if (field == null || hit == null) return@forEach
            if (!seenPhrases.add(allergen + "|" + term.phrase)) return@forEach
            found[allergen to "check:${term.phrase}"] = AllergenMatch(
                allergen = allergen,
                matchedTerm = hit,
                field = field,
                kind = AllergenMatchKind.CHECK_LABEL,
                note = term.reason,
            )
        }

        return AllergenScanResult(found.values.toList())
    }

    private fun MatchField.describe(): String = when (this) {
        MatchField.NAME -> "name"
        MatchField.BRAND -> "brand"
        MatchField.INGREDIENTS -> "ingredients"
        MatchField.STRUCTURED_TAG -> "product allergen tags"
    }

    /**
     * Open Food Facts tags look like "en:milk", so the useful part is the last
     * segment. A tag names an ingredient, not an allergen category, so it is
     * resolved through the alias table: "milk" is how the panel says "dairy".
     */
    private fun matchAllergenForTag(
        tag: String,
        avoided: Set<String>,
        relevant: List<AllergenAlias>,
    ): String? {
        val bare = tag.substringAfterLast(':').trim().lowercase().replace('-', ' ')
        if (bare.isEmpty()) return null
        avoided.firstOrNull { it == bare }?.let { return it }
        relevant.firstOrNull { it.aliasTerm.trim().lowercase() == bare }?.let { return it.allergen }
        return relevant.firstOrNull { findWholeWord(bare, it.aliasTerm) != null }?.allergen
    }

    private data class Occurrence(val start: Int, val end: Int, val softenReason: String?)

    /**
     * Finds [term] in [text] as a whole word or whole phrase. Letters and digits
     * on either side disqualify a match, which is what keeps "butter" out of
     * "butternut" while leaving "buttermilk" - itself an alias - matching.
     */
    private fun findWholeWord(text: String, term: String): Occurrence? {
        val haystack = text.lowercase()
        val needle = term.trim().lowercase()
        if (needle.isEmpty()) return null

        var from = 0
        while (true) {
            val index = haystack.indexOf(needle, from)
            if (index < 0) return null
            val before = haystack.getOrNull(index - 1)
            val after = haystack.getOrNull(index + needle.length)
            if ((before == null || !before.isLetterOrDigit()) &&
                (after == null || !after.isLetterOrDigit())
            ) {
                return Occurrence(index, index + needle.length, softenReason(haystack, index, needle))
            }
            from = index + 1
        }
    }

    /** A reason to downgrade a hit to "may contain", or null to keep it hard. */
    private fun softenReason(haystack: String, index: Int, needle: String): String? {
        val preceding = haystack.take(index)
            .trimEnd { !it.isLetterOrDigit() }
            .takeLastWhile { it.isLetterOrDigit() }

        if (preceding in PLANT_QUALIFIERS) {
            return "\"$preceding $needle\" is a plant milk, not dairy - flagged only in case " +
                "of shared equipment."
        }
        if (preceding in NEGATION_PREFIXES) {
            return "The text says \"$preceding $needle\", which claims the allergen is absent. " +
                "Worth a second look at the panel."
        }
        val following = haystack.drop(index + needle.length)
            .trimStart { !it.isLetterOrDigit() }
            .takeWhile { it.isLetterOrDigit() }
        if (following == "free") {
            return "The text says \"$needle free\", which claims the allergen is absent. " +
                "Worth a second look at the panel."
        }
        return null
    }
}
