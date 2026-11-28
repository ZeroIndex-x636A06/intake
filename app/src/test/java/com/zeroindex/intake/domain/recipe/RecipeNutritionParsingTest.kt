package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.Unlisted
import org.junit.Test

/** What a page's schema.org nutrition becomes, and that a number the page did not give is null, never 0. */
class RecipeNutritionParsingTest {

    private fun nutrition(fixture: String): PageNutrition? {
        val html = checkNotNull(javaClass.getResource("/recipes/$fixture")) { "missing fixture $fixture" }.readText()
        return (RecipePageParser.parse(html, "https://example.test/r") as PageParse.Found).recipe.nutrition
    }

    @Test fun `full nutrition is read with every key`() {
        val n = nutrition("nutrition-full.html")!!
        assertThat(n.kcal).isEqualTo(444.0)
        assertThat(n.proteinG).isEqualTo(18.0)
        assertThat(n.carbsG).isEqualTo(52.5)
        assertThat(n.fatG).isEqualTo(16.0)
        assertThat(n.saturatedFatG).isEqualTo(5.0)
        assertThat(n.fiberG).isEqualTo(4.0)
        assertThat(n.sugarG).isEqualTo(6.0)
        assertThat(n.sodiumMg).isEqualTo(480.0)
    }

    @Test fun `calories only leaves the macros null, not zero (the eatwell101 page)`() {
        for (fixture in listOf("nutrition-calories-only.html", "eatwell101-cdata.html")) {
            val n = nutrition(fixture)!!
            assertThat(n.kcal).isEqualTo(444.0)
            assertThat(n.proteinG).isNull()
            assertThat(n.carbsG).isNull()
            assertThat(n.fatG).isNull()
        }
    }

    @Test fun `value strings with units, decimals, thousands and a decimal comma`() {
        val n = nutrition("nutrition-units.html")!!
        assertThat(n.kcal).isEqualTo(1200.0)
        assertThat(n.proteinG).isEqualTo(12.0)
        assertThat(n.carbsG).isEqualTo(12.0)
        assertThat(n.fatG).isEqualTo(12.5)
        assertThat(n.saturatedFatG).isEqualTo(3.5)
        assertThat(n.sodiumMg).isEqualTo(1200.0)
        assertThat(n.fiberG).isEqualTo(2.0)
        // A published zero is a real zero.
        assertThat(n.sugarG).isEqualTo(0.0)
    }

    @Test fun `nutrition referenced by id inside a graph`() {
        val n = nutrition("nutrition-graph.html")!!
        assertThat(n.kcal).isEqualTo(300.0)
        assertThat(n.proteinG).isEqualTo(20.0)
        assertThat(n.carbsG).isEqualTo(30.0)
        assertThat(n.fatG).isEqualTo(10.0)
    }

    @Test fun `nutrition given as an array, with the short key names`() {
        val n = nutrition("nutrition-array.html")!!
        assertThat(n.kcal).isEqualTo(250.0)
        assertThat(n.proteinG).isEqualTo(9.0)
        assertThat(n.carbsG).isEqualTo(31.0)
        assertThat(n.fatG).isEqualTo(8.0)
    }

    @Test fun `kilocalories win over kilojoules in the same text`() {
        assertThat(nutrition("nutrition-kj.html")!!.kcal).isEqualTo(440.0)
        assertThat(RecipePageParser.nutritionOf("1,840 kJ", null, null, null)!!.kcal!!).isWithin(0.5).of(439.8)
    }

    @Test fun `unit spellings`() {
        fun p(s: String) = RecipePageParser.nutritionOf(null, s, null, null)?.proteinG
        assertThat(p("12 g")).isEqualTo(12.0)
        assertThat(p("12g")).isEqualTo(12.0)
        assertThat(p("12 grams")).isEqualTo(12.0)
        assertThat(p("12grams")).isEqualTo(12.0)
        assertThat(p("12.5 g")).isEqualTo(12.5)
        assertThat(p("500 mg")).isEqualTo(0.5)
        assertThat(p("trace")).isNull()
        assertThat(p("")).isNull()
    }

    @Test fun `no nutrition at all is null`() {
        assertThat(RecipePageParser.nutritionOf(null, null, null, null)).isNull()
    }

    // ---- what is shown and logged ------------------------------------------------------------

    @Test fun `a missing figure shows not listed and the page's own numbers win over an estimate`() {
        val est = EstimateTotals(kcal = 436.0, protein = 18.5, carbs = 60.0, fat = 20.0, fiber = 3.0, sugar = 5.0, sodiumMg = 300.0, saturatedFat = 8.0)
        val r = ResolvedRecipeNutrition.resolve(444.0, null, null, null, null, null, null, null, est)
        assertThat(r.kcal!!.estimated).isFalse()
        assertThat(r.kcal!!.value).isEqualTo(444.0)
        assertThat(r.protein!!.estimated).isTrue()
        assertThat(r.protein!!.text("g")).isEqualTo("~19 g")
        assertThat(r.fiber).isNull()

        val none = ResolvedRecipeNutrition.resolve(444.0, null, null, null, null, null, null, null, estimate = null)
        assertThat(none.protein).isNull()
        assertThat(ResolvedRecipeNutrition.text(none.protein, "g")).isEqualTo("not listed")
        assertThat(none.unknownMacros).containsExactly(CoreMacro.PROTEIN, CoreMacro.CARBS, CoreMacro.FAT)
    }

    @Test fun `unknown macros are stored in the unlisted list and read back`() {
        val stored = Unlisted.encode(emptySet(), setOf(CoreMacro.PROTEIN, CoreMacro.FAT))
        assertThat(stored).isEqualTo("protein,fat")
        assertThat(Unlisted.decodeMacros(stored)).containsExactly(CoreMacro.PROTEIN, CoreMacro.FAT)
        // Optional nutrients are unaffected and "fat" is not mistaken for "satfat".
        assertThat(Unlisted.decode(Unlisted.encode(setOf(com.zeroindex.intake.domain.nutrition.NutrientKey.SATURATED_FAT), setOf(CoreMacro.PROTEIN))))
            .containsExactly(com.zeroindex.intake.domain.nutrition.NutrientKey.SATURATED_FAT)
        assertThat(Unlisted.decodeMacros("satfat,fiber")).isEmpty()
    }
}
