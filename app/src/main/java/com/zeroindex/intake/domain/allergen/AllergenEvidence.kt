package com.zeroindex.intake.domain.allergen

import com.zeroindex.intake.domain.model.FoodSource

/**
 * How much a "nothing found" result is worth for one row (docs/ALLERGENS.md, "Search rows").
 *
 * The engine can only read what a row carries. A generic food ("Apples, raw") or one the person typed in is its
 * name, and a clear name is the honest answer. A packaged product is different: what is in it is on its label, so
 * when the row carries no ingredient list and no allergen tags, a clear name rules nothing out. Such a row must
 * not say "No allergens"; it says the ingredients are not listed.
 *
 * This only ever makes a clear result more cautious. A hit of any tier is shown as the hit it is.
 */
object AllergenEvidence {

    /**
     * True when a clear scan of this row is a name check of a packaged product with nothing else to go on.
     *
     * @param storedAllergenText `FoodEntity.allergenTags`: Open Food Facts tags and any ingredient text.
     * @param isVerified a USDA Foundation or SR Legacy record, which is a generic food, not a product.
     */
    fun isNameOnlyProduct(source: FoodSource, isVerified: Boolean, storedAllergenText: String): Boolean {
        if (storedAllergenText.isNotBlank()) return false
        return when (source) {
            FoodSource.OPEN_FOOD_FACTS, FoodSource.BARCODE -> true
            FoodSource.SEARCH -> !isVerified
            FoodSource.MANUAL, FoodSource.AI_PHOTO, FoodSource.RECIPE, FoodSource.DATABASE -> false
        }
    }
}
