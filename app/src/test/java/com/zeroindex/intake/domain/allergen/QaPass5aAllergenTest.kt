package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.RecipeCandidate
import org.junit.Test

/** QA pass 5a: override notes, "May contain" words, one recipe verdict, recipe line status and Log food recipe rows. */
class QaPass5aAllergenTest {

    private fun dairyScan(name: String?, ingredients: String? = null, checkLabelInIngredients: Boolean = false) = AllergenMatcher.scan(
        aliases = AllergenCatalog.aliases(),
        avoidedAllergens = setOf("dairy"),
        name = name,
        ingredients = ingredients,
        checkLabelInIngredients = checkLabelInIngredients,
    )

    // Q5a-10
    @Test fun `the override note is stripped from names and labels`() {
        assertThat(AllergenOverride.stripNote("Baked mac and cheese (Logged despite: dairy)")).isEqualTo("Baked mac and cheese")
        assertThat(AllergenOverride.stripNote("Toast (Logged despite: dairy, egg)")).isEqualTo("Toast")
        assertThat(AllergenOverride.stripNote("1 serving · Logged despite: dairy")).isEqualTo("1 serving")
        assertThat(AllergenOverride.stripNote("Plain apple")).isEqualTo("Plain apple")
        assertThat(AllergenOverride.stripNoteOrNull("Logged despite: dairy")).isNull()
    }

    @Test fun `annotate then strip gives back the original name`() {
        val (name, label) = AllergenOverride.annotate("Greek yogurt bowl", "1 bowl", "Logged despite: dairy")
        assertThat(AllergenOverride.stripNote(name)).isEqualTo("Greek yogurt bowl")
        assertThat(AllergenOverride.stripNote(label)).isEqualTo("1 bowl")
    }

    @Test fun `the matcher never reads the override note as an ingredient`() {
        val clean = dairyScan("Plain rice (Logged despite: dairy)")
        assertThat(clean.hasAnything).isFalse()
        assertThat(clean.matches.map { it.matchedTerm }).doesNotContain("dairy")
        // A real match in the same name is still found, and still only for its own words.
        val real = dairyScan("Baked mac and cheese (Logged despite: dairy)")
        assertThat(real.contains.map { it.matchedTerm }).contains("mac and cheese")
        assertThat(real.matches.map { it.matchedTerm }).doesNotContain("dairy")
        // Note in an ingredient line is not read either.
        assertThat(dairyScan("Rice", ingredients = "Apple, Rice (Logged despite: dairy)").hasAnything).isFalse()
    }

    // Q5a-16
    @Test fun `may contain chip words are allergen names, never keywords`() {
        val scan = dairyScan("Almond butter, creamy")
        assertThat(scan.mayContain.map { it.matchedTerm }).contains("creamy")
        assertThat(scan.mayContain.all { it.kind == AllergenMatchKind.MAY_CONTAIN }).isTrue()
        assertThat(ChipWords.allergenNames(scan.mayContain)).isEqualTo("dairy")
        val two = listOf(
            AllergenMatch("dairy", "creamy", MatchField.NAME, AllergenMatchKind.MAY_CONTAIN, "n"),
            AllergenMatch("egg", "pizza", MatchField.NAME, AllergenMatchKind.MAY_CONTAIN, "n"),
        )
        assertThat(ChipWords.allergenNames(two)).isEqualTo("dairy and egg")
        assertThat(ChipWords.allergenNames(emptyList())).isEmpty()
    }

    // Q5a-17
    @Test fun `a line with grams but no macros says it needs macros, not grams`() {
        val pasta = IngredientDraft(key = "a", name = "pasta", quantityText = "200 g", grams = 200.0)
        assertThat(pasta.notCountedReason).isEqualTo("Not counted: needs macros per 100 g")
        val milk = IngredientDraft(key = "b", name = "milk", quantityText = "1 cup")
        assertThat(milk.notCountedReason).isEqualTo("Not counted: needs grams and macros per 100 g")
        val noGrams = IngredientDraft(key = "c", name = "rice", kcalPer100g = 130.0, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3)
        assertThat(noGrams.notCountedReason).isEqualTo("Not counted: needs grams")
        val partial = IngredientDraft(key = "d", name = "oil", grams = 10.0, kcalPer100g = 884.0)
        assertThat(partial.notCountedReason).isEqualTo("Not counted: needs protein, carbs and fat per 100 g")
        val counted = IngredientDraft(key = "e", name = "oats", grams = 40.0, kcalPer100g = 380.0, proteinPer100g = 13.0, carbsPer100g = 66.0, fatPer100g = 7.0)
        assertThat(counted.notCountedReason).isNull()
    }

    // Q5a-1, Q5a-2
    @Test fun `a recipe is green only when every line was read`() {
        val b = AllergenEvidence.ClearBasis.LABEL_READ
        assertThat(AllergenEvidence.recipeClearBasis(5, 5)).isEqualTo(b)
        assertThat(AllergenEvidence.recipeClearBasis(5, 4)).isEqualTo(AllergenEvidence.ClearBasis.LINES_BY_NAME)
        assertThat(AllergenEvidence.recipeClearBasis(5, 0).isCaution).isTrue()
        assertThat(AllergenEvidence.recipeClearBasis(0, 0).isCaution).isTrue()
    }

    // Q5a-11
    @Test fun `a recipe row with no calories says why and an estimate shows with a tilde`() {
        val estimated = RecipeCandidate("1", "Grandma's soup", 62.0, 2.0, estimated = true)
        assertThat(estimated.summary).isEqualTo("~62 kcal · ~2 g protein")
        assertThat(estimated.loggable).isTrue()
        val none = RecipeCandidate("2", "Mystery", null, null, whyNoKcal = RecipeCandidate.reasonNoKcal(3, listOf("carrots", "stock", "salt")))
        assertThat(none.loggable).isFalse()
        assertThat(none.whyNoKcal).contains("carrots, stock and 1 more")
        assertThat(RecipeCandidate.reasonNoKcal(0, emptyList())).contains("No ingredients")
        assertThat(RecipeCandidate("3", "Plain", 400.0, 30.0).summary).isEqualTo("400 kcal · 30 g protein")
    }
}
