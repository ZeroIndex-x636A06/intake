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
     * What a "nothing found" result actually rests on. A clear verdict is only as strong as the data read, so the
     * chip says which of these it was and never a bare "No allergens" (docs/ALLERGENS.md, "Search rows").
     */
    enum class ClearBasis(val chipText: String) {
        /** An ingredient list or allergen tags were read and none of the person's allergens is in them. */
        LABEL_READ("Fits your allergens"),

        /** Only the name was read (a generic food, a drink, a recipe with no lines): not a statement about the ingredients. */
        NAME_ONLY("Only the name was checked"),

        /** A packaged product with no ingredient list and no tags: nothing at all to read. */
        NO_LIST("No ingredient list: check the label"),
        ;

        /** True when the chip must be the calm outlined "check the label" style, never green. */
        val isCaution: Boolean get() = this != LABEL_READ
    }

    /**
     * The ONE rule for what a clear scan of a recipe (or a template, which is a set of foods) rests on. Every surface
     * that shows a recipe verdict goes through here (Recipes list, Log food > Mine, recipe page, plan picker,
     * templates), so one recipe never reads differently in two places.
     *
     * Green ([ClearBasis.LABEL_READ]) needs at least one ingredient line and every line read from a label or allergen
     * tags ([readLineCount] == [ingredientLineCount]). A recipe with no lines, or with any line that carries nothing
     * but its own words, only had its name and line text checked: "Only the name was checked", never a pass.
     */
    fun recipeClearBasis(ingredientLineCount: Int, readLineCount: Int): ClearBasis =
        if (ingredientLineCount > 0 && readLineCount >= ingredientLineCount) ClearBasis.LABEL_READ else ClearBasis.NAME_ONLY

    /** The basis of a clear scan of a food row, from what the row carries. */
    fun clearBasis(source: FoodSource, isVerified: Boolean, storedAllergenText: String): ClearBasis = when {
        storedAllergenText.isNotBlank() -> ClearBasis.LABEL_READ
        isNameOnlyProduct(source, isVerified, storedAllergenText) -> ClearBasis.NO_LIST
        else -> ClearBasis.NAME_ONLY
    }

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
