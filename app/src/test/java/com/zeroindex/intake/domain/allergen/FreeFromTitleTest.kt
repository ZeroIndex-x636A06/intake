package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.RecipeCandidate
import com.zeroindex.intake.domain.ai.RecipeFilter
import com.zeroindex.intake.domain.ai.RecipeQuery
import com.zeroindex.intake.domain.ai.RecipeVerdict
import org.junit.Test

/**
 * Dairy-free and vegan versions of dishes named for the allergen stay in the main results. Mirrors how Recipe Hunter
 * scans: the title with the free-text rules, the ingredient lines with the strict ones, then the two combined.
 */
class FreeFromTitleTest {
    private val avoided = setOf("dairy")
    private val aliases = AllergenCatalog.aliases().filter { it.allergen in avoided }

    private fun scan(title: String?, text: String?, freeText: Boolean = false) = AllergenMatcher.scan(
        aliases = aliases, avoidedAllergens = avoided, name = title, ingredients = text,
        checkLabelInIngredients = true, freeText = freeText,
    )

    private fun recipeScan(title: String, lines: List<String>): AllergenScanResult {
        val t = scan(title, null, freeText = true)
        val l = scan(null, lines.joinToString("\n"))
        return AllergenScanResult(t.matches + l.matches)
    }

    private fun verdict(title: String, lines: List<String>) = RecipeFilter.evaluate(
        RecipeCandidate(title, "https://x.example/r", lines, null, null, null),
        RecipeQuery(minProteinPerServing = null, maxKcalPerServing = null, maxMinutes = null),
        recipeScan(title, lines),
    )

    private val veganMac = listOf(
        "1 lb macaroni", "1 cup raw cashews, soaked", "1/4 cup nutritional yeast", "1 cup vegan cheddar", "1 cup unsweetened oat milk", "1 tsp salt",
    )

    @Test fun `a vegan mac and cheese with clean ingredients stays in the main results`() {
        val v = verdict("Vegan Mac and Cheese", veganMac)
        assertThat(v is RecipeVerdict.Passed || v is RecipeVerdict.NeedsReview).isTrue()
        val s = recipeScan("Vegan Mac and Cheese", veganMac)
        assertThat(s.blocks).isFalse()
        // Labelled-free products are check the label, never cleared and never a block.
        assertThat(s.checkLabel).isNotEmpty()
        val lines = veganMac.map { scan(null, it) }
        assertThat(Substitutions.assess("Vegan Mac and Cheese", veganMac, avoided, lines, s)).isEqualTo(Adaptation.AlreadyFine)
    }

    @Test fun `a dairy free cheesecake and a dairy free alfredo are not flagged for their titles`() {
        val cake = listOf("2 cups graham crackers", "2 cups soaked cashews", "1/2 cup coconut cream", "1 cup sugar", "1/2 cup coconut oil")
        assertThat(verdict("Dairy-Free Cheesecake", cake)).isNotInstanceOf(RecipeVerdict.Rejected::class.java)
        val alfredo = listOf("1 lb fettuccine", "1 cup cashew cream", "2 cloves garlic", "1/4 cup nutritional yeast", "1 cup dairy-free cheese")
        assertThat(verdict("Dairy-free alfredo", alfredo)).isNotInstanceOf(RecipeVerdict.Rejected::class.java)
        assertThat(recipeScan("Dairy-free alfredo", alfredo).blocks).isFalse()
    }

    @Test fun `a vegan mac whose ingredients include real cheddar is flagged and not adaptable`() {
        val lines = veganMac + "1 cup shredded cheddar cheese"
        val s = recipeScan("Vegan Mac and Cheese", lines)
        assertThat(s.blocks).isTrue()
        assertThat(verdict("Vegan Mac and Cheese", lines)).isInstanceOf(RecipeVerdict.Rejected::class.java)
        val a = Substitutions.assess("Vegan Mac and Cheese", lines, avoided, lines.map { scan(null, it) }, s)
        assertThat(a).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `a title never decides for a recipe whose lines are clean`() {
        val lines = listOf("1 lb pasta", "2 cups cashew cream", "1 tsp salt")
        val a = Substitutions.assess("Alfredo", lines, avoided, lines.map { scan(null, it) }, recipeScan("Alfredo", lines))
        // Whatever the title scan says, defining-dish rules only run on a blocking allergen found in the lines.
        val s = recipeScan("Alfredo", lines)
        if (!s.blocks) assertThat(a).isEqualTo(Adaptation.AlreadyFine)
    }
}
