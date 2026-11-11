package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.pdf.PdfTextLayout
import com.zeroindex.intake.domain.pdf.TextFragment
import org.junit.Test

/** The fixture is text extracted from a real restaurant nutrition-and-allergen PDF (pages 1 to 6, 8 and 9 of 10). */
class NutritionTableParserTest {

    private val pages: List<String> by lazy {
        checkNotNull(javaClass.getResourceAsStream("/menu/arbys-nutrition-pages.txt")).bufferedReader().readText().split('\u000C')
    }
    private val parsed by lazy { NutritionTableParser.parse(pages) }
    private fun row(name: String) = parsed.rows.first { it.name == name }

    @Test
    fun `a known row is read in full`() {
        val r = row("Classic Roast Beef")
        assertThat(r.servingWeightG).isEqualTo(154.0)
        assertThat(r.kcal).isEqualTo(360.0)
        assertThat(r.fatG).isEqualTo(14.0)
        assertThat(r.saturatedFatG).isEqualTo(5.0)
        assertThat(r.sodiumMg).isEqualTo(970.0)
        assertThat(r.carbsG).isEqualTo(37.0)
        assertThat(r.fiberG).isEqualTo(2.0)
        assertThat(r.sugarsG).isEqualTo(5.0)
        assertThat(r.proteinG).isEqualTo(23.0)
        assertThat(r.stated.contains).containsExactly("Soy", "Wheat", "Sesame").inOrder()
        // "**Milk" is a footnote (buttered buns at some locations): a may-contain, not a contains.
        assertThat(r.stated.mayContain).containsExactly("Milk")
        assertThat(r.category).isEqualTo("Slow Roasted Beef")
    }

    @Test
    fun `contains and may contain are separate lists`() {
        val r = row("Deep Fried Turkey Gobbler")
        assertThat(r.stated.contains).containsExactly("Milk", "Egg", "Wheat").inOrder()
        assertThat(r.stated.mayContain).containsExactly("Sesame")
        assertThat(r.category).isEqualTo("Limited Time Offers")
    }

    @Test
    fun `a May Contain without a colon and a dagger list are both may contain`() {
        val r = row("Crispy Chicken")
        assertThat(r.stated.contains).containsExactly("Egg", "Wheat").inOrder()
        assertThat(r.stated.mayContain).containsExactly("Sesame", "Milk", "Soy", "Fish").inOrder()
        assertThat(r.sharedFacility).isTrue()
    }

    @Test
    fun `an item with no stated allergens has an empty list`() {
        val r = row("Steak Nuggets (5)")
        assertThat(r.stated.isEmpty).isTrue()
        assertThat(r.servingWeightG).isEqualTo(79.0)
        assertThat(r.proteinG).isEqualTo(20.0)
    }

    @Test
    fun `a drink row without a serving weight keeps its size word and no weight`() {
        val r = row("Coca-Cola (Small)")
        assertThat(r.servingWeightG).isNull()
        assertThat(r.servingLabel).isEqualTo("Small")
        assertThat(r.kcal).isEqualTo(120.0)
        assertThat(r.carbsG).isEqualTo(34.0)
    }

    @Test
    fun `a size after the allergen text is the serving label and not an allergen`() {
        val r = row("1% Lowfat Milk (8oz)")
        assertThat(r.stated.contains).containsExactly("Milk")
        assertThat(r.kcal).isEqualTo(110.0)
        assertThat(r.proteinG).isEqualTo(8.0)
    }

    @Test
    fun `the footnote star adds the facility warning and leading symbols are dropped from the name`() {
        val r = row("Jamocha Shake – Small")
        assertThat(r.stated.contains).containsExactly("Milk")
        assertThat(r.stated.mayContain).containsExactly("Peanuts", "Tree nuts")
    }

    @Test
    fun `a row whose numbers do not fit is skipped and counted, never guessed`() {
        // Sugars 54 g in 0 g of carbohydrate cannot be a real serving.
        assertThat(parsed.rows.none { it.name.startsWith("Mello Yello") && it.servingLabel == "Medium" }).isTrue()
        assertThat(parsed.skipped).isAtLeast(1)
        assertThat(parsed.skippedLines.any { it.contains("Mello Yello") && it.contains(" 200 ") }).isTrue()
    }

    @Test
    fun `the common order is used when the headings cannot be read in order`() {
        // The headings in this file come out as one word per line, in a shuffled order.
        assertThat(parsed.columnsFromHeading).isFalse()
        assertThat(parsed.foundTable).isTrue()
    }

    @Test
    fun `the whole fixture reads nearly every row`() {
        val candidates = pages.sumOf { p -> p.lines().count { l -> Regex("( [0-9.+]+){11,12}\\s*$").containsMatchIn(l) } }
        println("rows=${parsed.rows.size} skipped=${parsed.skipped} candidates=$candidates")
        parsed.skippedLines.forEach { println("SKIPPED: $it") }
        assertThat(parsed.rows.size + parsed.skipped).isAtLeast(candidates)
        assertThat(parsed.rows.size).isAtLeast(candidates - 3)
    }

    @Test
    fun `a name line followed by a numbers line is one row`() {
        val text = """
            Classic Roast Beef
            Contains: Soy, Wheat, Sesame
            154 360 120 14 5 0.5 50 970 37 2 5 23
        """.trimIndent()
        val r = NutritionTableParser.parse(text).rows.single()
        assertThat(r.name).isEqualTo("Classic Roast Beef")
        assertThat(r.stated.contains).containsExactly("Soy", "Wheat", "Sesame")
        assertThat(r.kcal).isEqualTo(360.0)
    }

    @Test
    fun `a heading line in order maps the columns`() {
        val text = """
            Item Serving Weight (g) Calories Calories from Fat Total Fat (g) Saturated Fat (g) Trans Fat (g) Cholesterol (mg) Sodium (mg) Total Carbohydrate (g) Dietary Fiber (g) Sugars (g) Protein (g)
            Plain Burger 120 300 100 11 4 0 40 500 30 2 5 17
        """.trimIndent()
        val p = NutritionTableParser.parse(text)
        assertThat(p.columnsFromHeading).isTrue()
        assertThat(p.rows.single().proteinG).isEqualTo(17.0)
    }

    @Test
    fun `a heading with fewer columns is read from its words`() {
        val text = """
            Menu item Calories Total Fat (g) Saturated Fat (g) Sodium (mg) Total Carbohydrate (g) Dietary Fiber (g) Sugars (g) Protein (g)
            Garden Salad 150 7 1 300 15 4 6 8
        """.trimIndent()
        val p = NutritionTableParser.parse(text)
        assertThat(p.columnsFromHeading).isTrue()
        val r = p.rows.single()
        assertThat(r.kcal).isEqualTo(150.0)
        assertThat(r.fatG).isEqualTo(7.0)
        assertThat(r.servingWeightG).isNull()
        assertThat(r.proteinG).isEqualTo(8.0)
    }

    @Test
    fun `text with no table finds nothing`() {
        val p = NutritionTableParser.parse("Our story\nFresh food since 1964.\nCall 555 0100 for catering.")
        assertThat(p.foundTable).isFalse()
        assertThat(p.skipped).isEqualTo(0)
    }

    @Test
    fun `stated allergens round trip through the stored text`() {
        val s = StatedAllergens(listOf("Milk", "Egg"), listOf("Sesame"))
        assertThat(StatedAllergens.parse(s.toText())).isEqualTo(s)
        assertThat(StatedAllergens.parse("")).isEqualTo(StatedAllergens.NONE)
    }

    @Test
    fun `fragments are put back into lines by position`() {
        val fragments = listOf(
            TextFragment("360", 300f, 100f, 320f, 112f),
            TextFragment("Classic Roast Beef", 10f, 101f, 120f, 113f),
            TextFragment("Contains: Soy", 130f, 100f, 250f, 112f),
            TextFragment("Reuben", 10f, 130f, 60f, 142f),
            TextFragment("680", 300f, 131f, 320f, 143f),
        )
        assertThat(PdfTextLayout.lines(fragments)).containsExactly("Classic Roast Beef Contains: Soy 360", "Reuben 680").inOrder()
    }

    @Test
    fun `fragments with no positions keep the readers own lines`() {
        val fragments = listOf(TextFragment("Reuben\n680", 0f, 0f, 0f, 0f))
        assertThat(PdfTextLayout.lines(fragments)).containsExactly("Reuben", "680").inOrder()
    }
}
