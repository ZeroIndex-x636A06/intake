package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** QA Q7-14: the Swap and Plan matched-term list excludes the item's own title and never clears a warning. */
class QaPass7SwapTermsTest {

    private fun dairyScan(name: String?, ingredients: String?) = AllergenMatcher.scan(
        aliases = AllergenCatalog.aliases(),
        avoidedAllergens = setOf("dairy"),
        name = name,
        ingredients = ingredients,
        checkLabelInIngredients = false,
    )

    @Test fun `the recipe title is not a matched term`() {
        val scan = dairyScan("Baked mac and cheese", "macaroni, butter, cheddar cheese, whole milk, cream")
        val terms = ChipWords.ingredientTermList(scan.matches)
        assertThat(terms).isNotEmpty()
        assertThat(terms).doesNotContain("mac and cheese")
        assertThat(terms).contains("butter")
        // The warning itself is untouched.
        assertThat(scan.blocks).isTrue()
    }

    @Test fun `a title-only hit names the allergen and still warns`() {
        val scan = dairyScan("Baked mac and cheese", "pasta, salt")
        assertThat(scan.blocks).isTrue()
        assertThat(ChipWords.ingredientTermList(scan.matches)).containsExactly("dairy")
    }

    @Test fun `no hits means no terms`() {
        assertThat(ChipWords.ingredientTermList(dairyScan("Rice bowl", "rice, salt").matches)).isEmpty()
    }
}
