package com.zeroindex.intake.domain.allergen

/** Plain words about where the engine found a match, so a warning says what actually happened. */
object MatchWording {

    /** "the name", "the ingredient list", "the name and the ingredient list". */
    fun where(matches: List<AllergenMatch>): String {
        val parts = matches.map { it.field }.distinct().sortedBy { it.ordinal }.map {
            when (it) {
                MatchField.NAME -> "the name"
                MatchField.BRAND -> "the brand"
                MatchField.INGREDIENTS -> "the ingredient list"
                MatchField.STRUCTURED_TAG -> "the product's allergen tags"
            }
        }
        return when (parts.size) {
            0 -> "this food"
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }

    /** The body of the red sheet: names where the match was, not always "an ingredient". */
    fun blockedBody(contains: List<AllergenMatch>): String =
        if (contains.isNotEmpty() && contains.all { it.trace }) {
            "Your allergen profile matched ${where(contains)}, listed under \"may contain\" (a possible trace, not an ingredient). " +
                "Nothing is logged until you choose what to do."
        } else {
            "Your allergen profile matched ${where(contains)}. Nothing is logged until you choose what to do."
        }

    /** The eyebrow of one match card: "found in the name". */
    fun foundIn(match: AllergenMatch): String = "found in ${where(listOf(match))}"
}
