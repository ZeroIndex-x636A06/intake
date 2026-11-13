package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import org.junit.Test

/** The import preview and the saved recipe scan every ingredient line; pasta must reach the wheat rules. */
class RecipeLineAllergenTest {

    private val lines = listOf(
        "2 medium zucchinis", "2 cups cherry tomatoes", "8 oz (220g) pasta (we used fusilli)", "2 tablespoons olive oil",
        "1 teaspoon garlic powder", "1 teaspoon black pepper", "1 teaspoon fennel seeds", "1/4 cup vegetable stock",
        "1/2 teaspoon Cayenne pepper, optional", "1 cup grated parmesan", "Fresh basil or Parsley",
    )

    private fun scan(allergen: String, text: String) = AllergenMatcher.scan(
        aliases = AllergenCatalog.aliases().filter { it.allergen == allergen },
        avoidedAllergens = setOf(allergen),
        name = "Healthy Tomato Zucchini Pasta",
        ingredients = text,
        checkLabelInIngredients = true,
    )

    @Test fun `the editor keeps every non-blank line as an ingredient`() {
        var n = 0
        val drafts = RecipeEditing.draftsFromText(lines.joinToString("\n"), { "i${n++}" })
        assertThat(drafts).hasSize(lines.size)
        assertThat(drafts.all { it.name.isNotBlank() }).isTrue()
    }

    @Test fun `the pasta line is flagged for wheat and the parmesan line for dairy`() {
        var n = 0
        val drafts = RecipeEditing.draftsFromText(lines.joinToString("\n"), { "i${n++}" })
        val text = drafts.joinToString("\n") { it.line }
        val wheat = scan(AllergenCatalog.WHEAT, text)
        assertThat(wheat.hasAnything).isTrue()
        assertThat(wheat.contains.isNotEmpty() || wheat.mayContain.isNotEmpty() || wheat.checkLabel.isNotEmpty()).isTrue()
        // Each line is also tagged on its own, as the recipe screen does.
        val pastaLine = drafts.first { it.line.contains("pasta") }.line
        assertThat(scan(AllergenCatalog.WHEAT, pastaLine).hasAnything).isTrue()
        assertThat(scan("dairy", drafts.first { it.line.contains("parmesan") }.line).hasAnything).isTrue()
    }
}
