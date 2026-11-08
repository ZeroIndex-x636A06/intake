package com.zeroindex.intake.domain.allergen

/**
 * A food that commonly hides an allergen without naming it. A hit is the softest
 * tier, "Check the label" (docs/ALLERGENS.md): it never blocks and never asks for
 * a tap. [phrase] is matched as whole words, case-insensitively; the token
 * "..." allows up to two other words in that spot ("taco ... seasoning" matches
 * "Taco Bell mild seasoning").
 */
data class CheckLabelTerm(
    val allergen: String,
    val phrase: String,
    /** Short human reason, shown verbatim next to the chip. */
    val reason: String,
) {
    /** Whole-word regex: letters and digits on either side disqualify a match. */
    val regex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val gap = """(?:[\p{L}\p{N}'&-]+\s+){0,2}"""
        val body = phrase.trim().lowercase().split(Regex("\\s+\\.\\.\\.\\s+")).joinToString("""\s+""" + gap) { segment ->
            segment.split(Regex("\\s+")).joinToString("""\s+""") { Regex.escape(it) }
        }
        Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    /** First whole-word hit in [text], or null. */
    fun findIn(text: String): MatchResult? = regex.find(text)
}

/**
 * Seed list of foods that commonly hide dairy, from the user's own hidden-dairy
 * checklist. In code rather than in the alias table: the alias table has no
 * severity column, a column would need a schema change, and a list in code reaches
 * existing installs on the next update with no re-seed. It is separate from the
 * alias table, so it can never weaken an alias match.
 *
 * Decisions (also in docs/ALLERGENS.md):
 * - bare "roll(s)" is NOT a term, so egg rolls, spring rolls and "rolled oats"
 *   stay quiet; rolls count only when qualified as bread (hoagie, dinner, ...).
 * - bare "seasoning" is NOT a term ("Italian seasoning" is a dried herb blend);
 *   only packets, mixes, and taco, ranch, fajita and chili seasoning.
 * - "chocolate" is a term on its own (milk chocolate is already a hard match
 *   through "milk"); "caramelized" does not match "caramel" (whole words).
 */
object HiddenCarriers {
    const val DAIRY = "dairy"

    private fun dairy(reason: String, vararg phrases: String) =
        phrases.map { CheckLabelTerm(DAIRY, it, reason) }

    val DAIRY_TERMS: List<CheckLabelTerm> =
        dairy(
            "Flour tortillas often contain milk, whey or butter",
            "tortilla", "tortillas",
        ) + dairy(
            "Bread rolls and buns often contain milk or butter",
            "bun", "buns",
            "hoagie roll", "hoagie rolls", "hamburger roll", "hamburger rolls",
            "hot dog roll", "hot dog rolls", "burger roll", "burger rolls",
            "dinner roll", "dinner rolls", "sandwich roll", "sandwich rolls",
            "sub roll", "sub rolls", "bread roll", "bread rolls", "kaiser roll",
            "kaiser rolls", "hawaiian roll", "hawaiian rolls", "crescent roll",
            "crescent rolls", "cinnamon roll", "cinnamon rolls",
        ) + dairy(
            "Brioche and croissants are made with butter and milk",
            "brioche", "croissant", "croissants",
        ) + dairy(
            "Breadcrumbs often contain milk or whey",
            "breadcrumb", "breadcrumbs", "bread crumbs", "panko",
        ) + dairy(
            "Crackers often contain milk, butter or whey",
            "cracker", "crackers", "saltine", "saltines",
        ) + dairy(
            "Corn chips and seasoned chips can contain cheese, whey or milk powder",
            "fritos", "frito", "corn chips",
        ) + dairy(
            "Seasoning packets often contain milk powder, whey or cheese",
            "taco ... seasoning", "ranch ... seasoning", "fajita seasoning", "chili seasoning",
            "seasoning packet", "seasoning packets", "seasoning mix", "seasoning mixes",
            "seasoning envelope", "taco mix", "ranch mix", "ranch packet",
        ) + dairy(
            "\"Natural flavoring\" on seasoned meat can include dairy",
            "natural flavoring", "natural flavorings", "natural flavor", "natural flavors",
            "natural flavour", "natural flavours",
        ) + dairy(
            "Margarine usually contains whey or milk solids",
            "margarine",
        ) + dairy(
            "Creamers, even \"non-dairy\" ones, can contain sodium caseinate",
            "creamer", "creamers",
        ) + dairy(
            "Instant mashed potatoes often contain milk or butter",
            "instant mashed potatoes", "instant mashed potato", "instant potatoes",
        ) + dairy(
            "Boxed rice sides often contain milk, butter or cheese",
            "boxed rice", "rice side", "rice sides", "rice pilaf mix",
        ) + dairy(
            "Caramel and nougat are made with milk or cream",
            "caramel", "nougat",
        ) + dairy(
            "Chocolate often contains milk or milk fat",
            "chocolate",
        ) + dairy(
            "Protein bars often have milk chocolate, caramel or whey coatings",
            "protein bar", "protein bars",
        )

    /** First term in [terms] found as whole words in [text]; pure, for list rows. */
    fun firstHit(terms: List<CheckLabelTerm>, text: String?): CheckLabelTerm? =
        if (text.isNullOrBlank()) null else terms.firstOrNull { it.findIn(text) != null }

    /** The reference screen's list, grouped by the reason shown with each. */
    fun termsByReason(): Map<String, List<String>> =
        DAIRY_TERMS.groupBy({ it.reason }, { it.phrase })
}
