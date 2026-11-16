package com.zeroindex.intake.domain.allergen

/**
 * The words that follow "May contain" on a chip or banner. A "may contain" keyword is a food that is often made with
 * an allergen ("pizza", "creamy"), so the chip names the allergen ("May contain dairy"), never the keyword
 * ("May contain creamy"). Only real allergen names (the profile's own keys) can appear.
 */
object ChipWords {
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
