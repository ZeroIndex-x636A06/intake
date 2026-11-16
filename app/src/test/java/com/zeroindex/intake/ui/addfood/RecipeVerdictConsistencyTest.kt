package com.zeroindex.intake.ui.addfood

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenEvidence
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchField
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.swap.SwapAllergen
import org.junit.Test

/**
 * Q5a-1 / Q5a-2: one recipe, one verdict. Every surface (Recipes list, Log food > Mine, recipe page, plan picker, templates)
 * gets its chip from `RecipeLogger.rowAllergen` or `MealTemplatesViewModel`, and both call `recipeRowAllergenOf`, so the same
 * scan and the same line counts must give the same chip everywhere, and an unchecked recipe is never green.
 */
class RecipeVerdictConsistencyTest {

    private val dairyHit = AllergenScanResult(
        listOf(AllergenMatch("dairy", "stock", MatchField.INGREDIENTS, AllergenMatchKind.CHECK_LABEL, "n", reason = "Stock can carry dairy")),
    )

    private val surfaces: Map<String, (AllergenScanResult, Int, Int) -> RowAllergen> = mapOf(
        "recipes list" to { s, n, r -> recipeRowAllergenOf(s, n, r) },
        "log food mine" to { s, n, r -> recipeRowAllergenOf(s, n, r) },
        "recipe page" to { s, n, r -> recipeRowAllergenOf(s, n, r) },
        "plan picker" to { s, n, r -> recipeRowAllergenOf(s, n, r) },
        "templates" to { s, n, r -> recipeRowAllergenOf(s, n, r) },
    )

    @Test fun `every surface gives the same verdict for the same recipe`() {
        for ((n, read) in listOf(0 to 0, 5 to 0, 5 to 3, 5 to 5)) {
            val verdicts = surfaces.mapValues { (_, f) -> f(AllergenScanResult.CLEAN, n, read) }
            assertThat(verdicts.values.toSet()).hasSize(1)
        }
    }

    @Test fun `a recipe whose ingredients were not all checked is never green`() {
        val grandma = recipeRowAllergenOf(AllergenScanResult.CLEAN, ingredientLineCount = 6, readLineCount = 0)
        assertThat(grandma.verdict).isEqualTo(SwapAllergen.CLEAR)
        assertThat(grandma.cautionClear).isTrue()
        assertThat(grandma.basis).isEqualTo(AllergenEvidence.ClearBasis.NAME_ONLY)
        val empty = recipeRowAllergenOf(AllergenScanResult.CLEAN, 0, 0)
        assertThat(empty.cautionClear).isTrue()
    }

    @Test fun `only a recipe with every line read is green`() {
        val ok = recipeRowAllergenOf(AllergenScanResult.CLEAN, 4, 4)
        assertThat(ok.cautionClear).isFalse()
        assertThat(ok.basis).isEqualTo(AllergenEvidence.ClearBasis.LABEL_READ)
    }

    @Test fun `a hit stays a hit whatever was read`() {
        val r = recipeRowAllergenOf(dairyHit, 4, 4)
        assertThat(r.verdict).isEqualTo(SwapAllergen.CHECK_LABEL)
        assertThat(r.cautionClear).isFalse()
    }

    // Q5a-24
    @Test fun `the safe tick never sits beside a name-only chip`() {
        val nameOnly = RowAllergen(SwapAllergen.CLEAR, null, basis = AllergenEvidence.ClearBasis.NAME_ONLY)
        val noList = RowAllergen(SwapAllergen.CLEAR, null, basis = AllergenEvidence.ClearBasis.NO_LIST)
        val read = RowAllergen(SwapAllergen.CLEAR, null, basis = AllergenEvidence.ClearBasis.LABEL_READ)
        assertThat(nameOnly.allowsSafeTick).isFalse()
        assertThat(noList.allowsSafeTick).isFalse()
        assertThat(RowAllergen.Checking.allowsSafeTick).isFalse()
        assertThat(RowAllergen(SwapAllergen.CHECK_LABEL, "x").allowsSafeTick).isFalse()
        assertThat(read.allowsSafeTick).isTrue()
    }
}
