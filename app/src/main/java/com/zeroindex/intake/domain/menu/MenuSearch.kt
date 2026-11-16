package com.zeroindex.intake.domain.menu

import com.zeroindex.intake.domain.allergen.AllergenEvidence
import com.zeroindex.intake.domain.model.FoodSource

/**
 * Finds imported menu items for the words typed in Log food. Pure, so the matching is tested without a database.
 *
 * Every word typed must appear in the restaurant's name or the item's name (apostrophes and case ignored, so
 * "arbys beef" finds "Arby's" "Classic Roast Beef"). Items whose own name holds all the words come first, then the
 * ones that needed the restaurant name, and inside a group the name that starts with the first word comes first.
 */
object MenuSearch {

    fun <T> search(
        items: List<T>,
        query: String,
        name: (T) -> String,
        restaurant: (T) -> String,
        limit: Int = Int.MAX_VALUE,
    ): List<T> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return items.mapNotNull { item ->
            val n = normalize(name(item))
            val r = normalize(restaurant(item))
            val all = "$r $n"
            if (!words.all { it in all }) return@mapNotNull null
            val inName = words.all { it in n }
            val rank = (if (inName) 0 else 2) + (if (n.startsWith(words.first())) 0 else 1)
            rank to item
        }.sortedBy { it.first }.map { it.second }.take(limit)
    }

    fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[‘’'`]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /**
     * True when a clear scan of this menu item rules nothing out: no ingredient text and no stated allergens were kept
     * with it (docs/ALLERGENS.md, "Search rows"). It then says the ingredients are not listed, never "No allergens".
     * A description is the restaurant's marketing for the dish, not an ingredient list, so it does not count.
     */
    fun hasNoIngredientInfo(ingredientsText: String): Boolean =
        AllergenEvidence.isNameOnlyProduct(FoodSource.SEARCH, isVerified = false, storedAllergenText = ingredientsText.trim())
}
