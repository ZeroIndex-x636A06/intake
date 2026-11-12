package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchField
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

class RecipeFilterTest {

    private val query = RecipeQuery(minProteinPerServing = 40, maxKcalPerServing = 600, maxMinutes = 45)
    private val good = RecipeCandidate(
        title = "Lemon chicken rice bowls",
        sourceUrl = "https://example.com/r",
        ingredients = listOf("600 g chicken thigh", "300 g rice"),
        kcalPerServing = 560.0,
        proteinPerServing = 44.0,
        totalMinutes = 35,
    )

    private fun match(kind: AllergenMatchKind, term: String) =
        AllergenScanResult(listOf(AllergenMatch("dairy", term, MatchField.INGREDIENTS, kind, "\"$term\" found.")))

    @Test
    fun `a recipe meeting every goal passes`() {
        assertThat(RecipeFilter.evaluate(good, query, AllergenScanResult.CLEAN)).isEqualTo(RecipeVerdict.Passed)
    }

    @Test
    fun `an allergen match rejects regardless of macros`() {
        val v = RecipeFilter.evaluate(good, query, match(AllergenMatchKind.CONTAINS, "butter"))
        assertThat(v).isEqualTo(RecipeVerdict.Rejected("Contains butter"))
    }

    @Test
    fun `a possible allergen passes but needs review`() {
        val v = RecipeFilter.evaluate(good, query, match(AllergenMatchKind.MAY_CONTAIN, "milk"))
        assertThat(v).isInstanceOf(RecipeVerdict.NeedsReview::class.java)
    }

    @Test
    fun `a near miss is not rejected and is graded close`() {
        val near = good.copy(proteinPerServing = 38.5)
        assertThat(RecipeFilter.evaluate(near, query, AllergenScanResult.CLEAN)).isEqualTo(RecipeVerdict.Passed)
        assertThat(RecipeFilter.fitOf(near, query).fit).isEqualTo(Fit.CLOSE)
        assertThat(RecipeFilter.fitOf(good, query).fit).isEqualTo(Fit.MATCH)
    }

    @Test
    fun `well outside a limit is rejected`() {
        assertThat(RecipeFilter.evaluate(good.copy(proteinPerServing = 35.0), query, AllergenScanResult.CLEAN))
            .isEqualTo(RecipeVerdict.Rejected("35 g protein per serving, goal ≥ 40 g"))
        assertThat(RecipeFilter.evaluate(good.copy(kcalPerServing = 700.0), query, AllergenScanResult.CLEAN))
            .isInstanceOf(RecipeVerdict.Rejected::class.java)
    }

    @Test
    fun `a page with no nutrition is kept unchecked, not rejected`() {
        val none = good.copy(kcalPerServing = null, proteinPerServing = null)
        assertThat(RecipeFilter.evaluate(none, query, AllergenScanResult.CLEAN)).isEqualTo(RecipeVerdict.Passed)
        assertThat(RecipeFilter.fitOf(none, query).fit).isEqualTo(Fit.UNCHECKED)
    }

    @Test
    fun `a recipe without a checkable source or ingredients is rejected`() {
        assertThat(RecipeFilter.evaluate(good.copy(sourceUrl = ""), query, AllergenScanResult.CLEAN))
            .isInstanceOf(RecipeVerdict.Rejected::class.java)
        assertThat(RecipeFilter.evaluate(good.copy(ingredients = emptyList()), query, AllergenScanResult.CLEAN))
            .isInstanceOf(RecipeVerdict.Rejected::class.java)
    }

    @Test
    fun `unknown prep time is not held against a recipe`() {
        assertThat(RecipeFilter.evaluate(good.copy(totalMinutes = null), query, AllergenScanResult.CLEAN))
            .isEqualTo(RecipeVerdict.Passed)
    }
}
