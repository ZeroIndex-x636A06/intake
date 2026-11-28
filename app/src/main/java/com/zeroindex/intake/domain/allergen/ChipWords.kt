package com.zeroindex.intake.domain.allergen

/**
 * The words that follow "May contain" on a chip or banner. A "may contain" keyword is a food that is often made with
 * an allergen ("pizza", "creamy"), so the chip names the allergen ("May contain dairy"), never the keyword
 * ("May contain creamy"). Only real allergen names (the profile's own keys) can appear.
 */
object ChipWords {
    /**
     * "butter, cheese and milk": the matched terms of a recipe's hits, as ingredient words (Q6-8). A hit in the recipe's
     * own name (the dish keyword "mac and cheese") is a reason to warn but not an ingredient, so it is left out of the
     * list whenever an ingredient hit exists. A recipe flagged by its name alone names the allergen instead ("dairy"),
     * never its title. The warning itself is never dropped: [matches] decides the tier, this only words the chip.
     */
    fun ingredientTerms(matches: List<AllergenMatch>): String {
        val terms = ingredientTermList(matches)
        return when (terms.size) {
            0 -> ""
            1 -> terms[0]
            else -> terms.dropLast(1).joinToString(", ") + " and " + terms.last()
        }
    }

    /**
     * The same terms as a list, for the Plan and Swap rows (Q7-14): the matched terms of the hits that are not in the item's own
     * name (the title's "mac and cheese" is never listed as a matched term while an ingredient hit exists). With only name hits
     * the list holds the allergen names. Empty only when there are no hits. This words the list; it never changes a verdict.
     */
    fun ingredientTermList(matches: List<AllergenMatch>): List<String> {
        val fromIngredients = matches.filter { it.field != MatchField.NAME }
        if (fromIngredients.isEmpty()) {
            return matches.map { it.allergen.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        }
        return fromIngredients.map { it.matchedTerm }.distinct()
    }

    /** "dairy", "dairy and egg", "dairy, egg and soy": the distinct allergens of [matches], in the order given. */
    fun allergenNames(matches: List<AllergenMatch>): String {
        val names = matches.map { it.allergen.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }
}
