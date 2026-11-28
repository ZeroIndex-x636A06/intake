package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.FoodSource
import org.junit.Test

/** A clear result is only as good as what the row carries (docs/ALLERGENS.md, "Search rows"). */
class AllergenEvidenceTest {

    @Test
    fun `a packaged product with no ingredient list and no tags is a name check only`() {
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.OPEN_FOOD_FACTS, isVerified = false, storedAllergenText = "")).isTrue()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.BARCODE, isVerified = false, storedAllergenText = "  ")).isTrue()
        // A branded food from the USDA API with no ingredients.
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.SEARCH, isVerified = false, storedAllergenText = "")).isTrue()
    }

    @Test
    fun `tags or an ingredient list are something to go on`() {
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.OPEN_FOOD_FACTS, false, "en:gluten,en:eggs")).isFalse()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.BARCODE, false, "en:milk | sugar, cocoa butter, milk")).isFalse()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.SEARCH, false, "WATER, SUGAR")).isFalse()
    }

    @Test
    fun `a generic food or one the person entered is its name`() {
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.DATABASE, isVerified = true, storedAllergenText = "")).isFalse()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.SEARCH, isVerified = true, storedAllergenText = "")).isFalse()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.MANUAL, isVerified = false, storedAllergenText = "")).isFalse()
        assertThat(AllergenEvidence.isNameOnlyProduct(FoodSource.RECIPE, isVerified = false, storedAllergenText = "")).isFalse()
    }
}
