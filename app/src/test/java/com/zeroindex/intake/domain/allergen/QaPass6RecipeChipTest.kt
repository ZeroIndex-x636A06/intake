package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.ui.allergen.recipeChipDetail
import org.junit.Test

/** QA Q6-8: a recipe chip lists matched ingredient words, never the recipe's own title as if it were one. */
class QaPass6RecipeChipTest {

    private fun dairyScan(name: String?, ingredients: String?) = AllergenMatcher.scan(
        aliases = AllergenCatalog.aliases(),
        avoidedAllergens = setOf("dairy"),
        name = name,
        ingredients = ingredients,
        checkLabelInIngredients = false,
    )

    @Test fun `the title is not listed as a matched ingredient term`() {
        val scan = dairyScan("Baked mac and cheese", "macaroni, butter, cheddar cheese, whole milk, cream")
        // The name still raises the warning (the scan keeps the title hit) ...
        assertThat(scan.contains.map { it.field }).contains(MatchField.NAME)
        assertThat(scan.blocks).isTrue()
        // ... but the chip words are ingredient terms only.
        val detail = scan.recipeChipDetail()!!
        assertThat(detail).doesNotContain("mac and cheese")
        assertThat(detail).contains("butter")
        assertThat(detail).contains("milk")
    }

    @Test fun `a recipe flagged by its title alone names the allergen and still warns`() {
        val scan = dairyScan("Baked mac and cheese", "pasta, salt")
        assertThat(scan.blocks).isTrue()
        assertThat(scan.recipeChipDetail()).isEqualTo("dairy")
    }

    @Test fun `a clean recipe has no chip words`() {
        assertThat(dairyScan("Rice bowl", "rice, salt").recipeChipDetail()).isNull()
    }

    @Test fun `ingredient terms join with commas and and`() {
        fun m(term: String, field: MatchField) = AllergenMatch(
            "dairy", term, field, com.zeroindex.intake.domain.model.AllergenMatchKind.CONTAINS, "n",
        )
        assertThat(ChipWords.ingredientTerms(listOf(m("pie", MatchField.NAME), m("butter", MatchField.INGREDIENTS), m("milk", MatchField.INGREDIENTS))))
            .isEqualTo("butter and milk")
    }
}
