package com.zeroindex.intake.domain.template

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.NutrientKey
import com.zeroindex.intake.domain.nutrition.Unlisted
import org.junit.Test

class MealTemplateRulesTest {
    private fun line(name: String = "Rice", grams: Double = 150.0, kcal: Double = 130.0, protein: Double = 2.7, listed: Boolean = true) =
        TemplateLine(name, grams, kcal, protein, listed)

    @Test
    fun `a template needs a name and at least one item`() {
        assertThat(MealTemplateRules.problem("", listOf(line()))).contains("name")
        assertThat(MealTemplateRules.problem("   ", listOf(line()))).contains("name")
        assertThat(MealTemplateRules.problem("Lunch", emptyList())).contains("at least one")
        assertThat(MealTemplateRules.problem("Lunch", listOf(line()))).isNull()
    }

    @Test
    fun `an amount must be a sensible positive number and the message names the item`() {
        listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY, 5001.0).forEach { g ->
            assertThat(MealTemplateRules.problem("Lunch", listOf(line("Oats", grams = g)))).contains("Oats")
        }
        assertThat(MealTemplateRules.portionOk(5000.0)).isTrue()
        assertThat(MealTemplateRules.portionOk(0.5)).isTrue()
    }

    @Test
    fun `a template holds a limited number of items`() {
        assertThat(MealTemplateRules.problem("Big", List(41) { line("Item $it") })).contains("40")
        assertThat(MealTemplateRules.problem("Big", List(40) { line("Item $it") })).isNull()
    }

    @Test
    fun `names are tidied and capped`() {
        assertThat(MealTemplateRules.cleanName("  Post   workout\nshake ")).isEqualTo("Post workout shake")
        assertThat(MealTemplateRules.cleanName("x".repeat(100))).hasLength(MealTemplateRules.MAX_NAME)
    }

    @Test
    fun `totals add up per amount`() {
        val t = MealTemplateRules.totals(listOf(line(grams = 200.0, kcal = 130.0, protein = 2.5), line("Chicken", 100.0, 165.0, 31.0)))
        assertThat(t.kcal).isWithin(0.001).of(425.0)
        assertThat(t.protein).isWithin(0.001).of(36.0)
        assertThat(t.proteinComplete).isTrue()
        assertThat(t.proteinText).isEqualTo("36 g protein")
    }

    @Test
    fun `protein that nobody listed is never counted as zero`() {
        val partial = MealTemplateRules.totals(listOf(line("Chicken", 100.0, 165.0, 31.0), line("Sauce", 50.0, 80.0, 0.0, listed = false)))
        assertThat(partial.proteinComplete).isFalse()
        assertThat(partial.proteinText).isEqualTo("31 g+ protein")
        val none = MealTemplateRules.totals(listOf(line("Sauce", 50.0, 80.0, 0.0, listed = false)))
        assertThat(none.proteinText).isEqualTo("protein not listed")
        assertThat(none.kcal).isWithin(0.001).of(40.0)
    }

    @Test
    fun `an empty template has zero energy and says protein is not listed`() {
        val t = MealTemplateRules.totals(emptyList())
        assertThat(t.kcal).isEqualTo(0.0)
        assertThat(t.proteinComplete).isFalse()
        assertThat(t.proteinText).isEqualTo("protein not listed")
    }

    @Test
    fun `a recipe becomes a food only with calories, and unknown figures stay unlisted`() {
        assertThat(RecipeFood.spec(null, 30.0, 40.0, 10.0, null, null, null, null)).isNull()
        val spec = RecipeFood.spec(520.0, null, 40.0, 18.0, 4.0, null, 600.0, null)!!
        assertThat(spec.kcalPer100g).isEqualTo(520.0)
        assertThat(spec.proteinListed).isFalse()
        assertThat(spec.proteinPer100g).isEqualTo(0.0)
        assertThat(Unlisted.decodeMacros(spec.unlisted)).containsExactly(CoreMacro.PROTEIN)
        assertThat(Unlisted.decode(spec.unlisted)).containsExactly(NutrientKey.SUGAR, NutrientKey.SATURATED_FAT)
    }

    @Test
    fun `a fully listed recipe has nothing unlisted`() {
        val spec = RecipeFood.spec(400.0, 30.0, 40.0, 10.0, 5.0, 6.0, 500.0, 3.0)!!
        assertThat(spec.unlisted).isEmpty()
        assertThat(spec.proteinListed).isTrue()
    }

    @Test
    fun `servings and grams convert both ways`() {
        assertThat(RecipeFood.gramsForServings(1.5)).isEqualTo(150.0)
        assertThat(RecipeFood.servingsForGrams(250.0)).isEqualTo(2.5)
        assertThat(RecipeFood.servingsText(200.0)).isEqualTo("2")
        assertThat(RecipeFood.servingsText(150.0)).isEqualTo("1.5")
    }
}
