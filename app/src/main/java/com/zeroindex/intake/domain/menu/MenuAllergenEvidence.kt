package com.zeroindex.intake.domain.menu

/**
 * When a menu item's "nothing found" scan may be shown as clear (docs/ALLERGENS.md, "Imported restaurant menu items").
 *
 * The engine can only read what an item carries. A clear scan means something only when the item's source STATED
 * allergen information: a chart row, a "Contains: ..." line, an ingredient list. These do not:
 *
 *  - an AI estimate (a model's guess from the menu's wording, marked with [ESTIMATE_MARK]), even when the model also
 *    wrote an ingredient list, because that list is a guess too;
 *  - an item from a nutrition-only table or page, which has no allergen column and so keeps no ingredient text;
 *  - an item saved from a restaurant's description with no ingredient text.
 *
 * Such an item is never "No allergens found": the best it can be is "check with the restaurant". A hit of any tier still
 * shows as the hit it is, and what a model claims can only raise a warning.
 */
object MenuAllergenEvidence {
    /** The description of every estimated item starts with this, so an estimate can always be told from published data. */
    const val ESTIMATE_MARK = "AI estimate"

    /** The full note put on an estimated item. */
    const val ESTIMATE_NOTE = "$ESTIMATE_MARK from the menu's wording, not published nutrition"

    fun isEstimate(description: String): Boolean = description.trimStart().startsWith(ESTIMATE_MARK, ignoreCase = true)

    /**
     * True when the item's source says nothing about its allergens, so a clear scan rules nothing out. [description] is
     * the item's description and [ingredientsText] the ingredient or stated-allergen text kept with it.
     */
    fun statesNothing(description: String, ingredientsText: String): Boolean =
        isEstimate(description) || MenuSearch.hasNoIngredientInfo(ingredientsText)

    /**
     * The text kept as an estimate's ingredient line: the allergens the model says the item contains, as a stated
     * "May contain" list. A model's claim is a guess, so it reads as "may contain" (an amber review), never as a
     * published "Contains" and never as clear. Empty when the model named none.
     */
    fun claimsAsText(claims: List<String>): String {
        val names = claims.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        return StatedAllergens(contains = emptyList(), mayContain = names).toText()
    }
}
