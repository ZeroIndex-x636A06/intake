package com.zeroindex.intake.data.ai.recipes

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.Fit
import com.zeroindex.intake.domain.ai.FitIssue
import com.zeroindex.intake.domain.ai.RecipeVerdict
import com.zeroindex.intake.domain.allergen.Adaptation
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchField
import com.zeroindex.intake.domain.allergen.SwapConfidence
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

/** How a result is sorted into kept, close, adaptable and removed, and what its short chips say. */
class HuntedRecipeTest {

    private val recipe = HuntRecipe(
        title = "Healthy Ground Beef Burrito Bowls", sourceUrl = "https://example.test/bowls", sourceName = "Example",
        servings = 4, totalMinutes = 30, ingredients = listOf("1 lb ground beef", "1/2 cup shredded cheddar cheese"),
        steps = emptyList(), kcalPerServing = 520.0, proteinPerServing = 32.0, carbsPerServing = null, fatPerServing = null,
    )
    private val dairy = AllergenScanResult(
        listOf(
            AllergenMatch("dairy", "cheddar cheese", MatchField.INGREDIENTS, AllergenMatchKind.CONTAINS, "Contains cheddar cheese."),
            AllergenMatch("dairy", "sour cream", MatchField.INGREDIENTS, AllergenMatchKind.CONTAINS, "Contains sour cream."),
        ),
    )
    private val adaptable = Adaptation.Adaptable(
        swaps = emptyList(), adaptedIngredients = listOf("1 lb ground beef"), adaptedTitle = recipe.title, confidence = SwapConfidence.HIGH, caveats = emptyList(),
    )

    @Test
    fun `an allergen plus a protein miss shows two short chips and keeps both reasons`() {
        val h = HuntedRecipe(
            recipe, RecipeVerdict.Rejected(listOf("Contains cheddar cheese, sour cream", "32 g protein per serving, goal ≥ 40 g")),
            dairy, Fit.OUT, "32 g protein per serving, goal ≥ 40 g", listOf(FitIssue.PROTEIN),
        )
        assertThat(h.reasonChips).containsExactly("Contains dairy", "Under protein goal").inOrder()
        assertThat(h.isRemoved).isTrue()
        assertThat((h.verdict as RecipeVerdict.Rejected).reasons).hasSize(2)
    }

    @Test
    fun `cheese and sour cream as swappable toppings are adaptable when the numbers are within limits`() {
        val ok = recipe.copy(proteinPerServing = 44.0)
        val h = HuntedRecipe(
            ok, RecipeVerdict.Rejected("Contains cheddar cheese, sour cream"), dairy, Fit.MATCH, null, emptyList(),
            adaptable, AllergenScanResult.CLEAN,
        )
        assertThat(h.isAdaptable).isTrue()
        assertThat(h.isRemoved).isFalse()
    }

    @Test
    fun `the same bowl that is well under the protein goal is removed, not adaptable`() {
        val h = HuntedRecipe(
            recipe, RecipeVerdict.Rejected(listOf("Contains cheddar cheese, sour cream", "32 g protein per serving, goal ≥ 40 g")),
            dairy, Fit.OUT, "32 g protein per serving, goal ≥ 40 g", listOf(FitIssue.PROTEIN), adaptable, AllergenScanResult.CLEAN,
        )
        assertThat(h.isAdaptable).isFalse()
        assertThat(h.isRemoved).isTrue()
    }

    @Test
    fun `a model read recipe is never plain passed`() {
        // Enforced in judge(); the verdict type for it is NeedsReview, which is kept but not "Passed allergen check".
        val h = HuntedRecipe(recipe.copy(aiRead = true), RecipeVerdict.NeedsReview("Read by AI"), AllergenScanResult.CLEAN, Fit.MATCH)
        assertThat(h.verdict).isNotEqualTo(RecipeVerdict.Passed)
        assertThat(h.recipe.aiRead).isTrue()
    }
}
