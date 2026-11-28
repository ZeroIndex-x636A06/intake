package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.model.KeywordLevel
import com.zeroindex.intake.domain.pdf.TextFragment
import org.junit.Test

/**
 * The fixture is positioned words from a slice of the McDonald's Ireland allergen chart (Drinks, Breakfast, the top of
 * the promotion table and Condiments & Sauces). Expected values below were read off the printed chart by eye.
 */
class AllergenChartParserTest {

    private fun fixture(): List<List<TextFragment>> {
        val lines = javaClass.classLoader!!.getResourceAsStream("menu/mcdonalds-ie-allergen-fragments.tsv")!!
            .bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val byPage = sortedMapOf<Int, MutableList<TextFragment>>()
        for (l in lines) {
            val c = l.split('\t')
            byPage.getOrPut(c[0].toInt()) { ArrayList() } += TextFragment(c[5], c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat())
        }
        return byPage.values.toList()
    }

    private val parse by lazy { AllergenChartParser.parse(fixture()) }

    private fun item(name: String, size: String? = null) =
        parse.items.single { it.name == name && it.size == size }

    @Test
    fun `the chart is recognised and every table's columns line up`() {
        assertThat(parse.foundChart).isTrue()
        assertThat(parse.skippedTables).isEqualTo(0)
        assertThat(parse.marks).isGreaterThan(50)
    }

    @Test
    fun `breakfast rows carry their ticks and asterisks`() {
        val wrap = item("Breakfast Wrap with Ketchup")
        assertThat(wrap.category).isEqualTo("Breakfast")
        assertThat(wrap.contains).containsAtLeast("Wheat", "Gluten", "Milk", "Egg")
        assertThat(wrap.contains).doesNotContain("Rye")
        assertThat(wrap.mayContain).containsAtLeast("Rye", "Barley", "Sesame")

        val brown = item("Breakfast Wrap with Brown Sauce")
        assertThat(brown.contains).containsAtLeast("Wheat", "Barley", "Milk", "Egg")
        assertThat(brown.mayContain).containsAtLeast("Rye", "Sesame")

        val mcmuffin = item("Sausage & Egg McMuffin®")
        assertThat(mcmuffin.contains).containsAtLeast("Wheat", "Rye", "Milk", "Egg")
        assertThat(mcmuffin.mayContain).containsExactly("Sesame")

        val jam = item("Muffin with Jam")
        assertThat(jam.vegetarian).isTrue()
        assertThat(jam.contains).containsAtLeast("Wheat", "Rye", "Milk")
        assertThat(jam.contains).doesNotContain("Egg")
        assertThat(jam.mayContain).containsExactly("Sesame")

        val toastie = item("Cheese Toastie")
        assertThat(toastie.contains).containsAtLeast("Wheat", "Milk")
        assertThat(toastie.mayContain).containsAtLeast("Sesame", "Egg")
    }

    @Test
    fun `an item named with a footnote sign has the sign dropped and a tick under Oats means gluten`() {
        val porridge = item("Flahavan’s® Porridge Oats (Plain) with Milk")
        assertThat(porridge.vegetarian).isTrue()
        assertThat(porridge.contains).containsAtLeast("Oats", "Milk", "Gluten")
        assertThat(porridge.mayContain).containsAtLeast("Wheat", "Rye", "Barley")
    }

    @Test
    fun `a vegan item with no marks is kept, and a blank cell never becomes a claim`() {
        val hash = item("Hash Brown")
        assertThat(hash.vegan).isTrue()
        assertThat(hash.contains).isEmpty()
        assertThat(hash.mayContain).isEmpty()
        assertThat(hash.stated.isEmpty).isTrue()
    }

    @Test
    fun `sizes become their own rows and carry the name of the row above`() {
        val small = item("Chocolate Milkshake", "Small")
        val large = item("Chocolate Milkshake", "Large")
        assertThat(small.category).isEqualTo("Drinks")
        assertThat(small.vegetarian).isTrue()
        assertThat(small.contains).containsExactly("Milk")
        assertThat(large.contains).containsExactly("Milk")
        val cola = item("Coca-Cola® Zero Sugar®", "Medium")
        assertThat(cola.vegan).isTrue()
        assertThat(cola.contains).isEmpty()
    }

    @Test
    fun `quantities in the size slot and a two-line title are read`() {
        val mayo = item("Mayo Dip", "19g")
        assertThat(mayo.category).isEqualTo("Condiments & Sauces")
        assertThat(mayo.vegetarian).isTrue()
        assertThat(mayo.contains).containsExactly("Egg", "Mustard")

        val sweetSour = item("Sweet & Sour Dip", "25ml")
        assertThat(sweetSour.vegan).isTrue()
        assertThat(sweetSour.contains).containsExactly("Wheat", "Gluten", "Barley", "Soy", "Celery")

        assertThat(item("Butter", "7g").contains).containsExactly("Milk")
        val spicy = item("Spicy Mayo Dip", "40g")
        assertThat(spicy.vegetarian).isFalse()
        assertThat(spicy.contains).containsExactly("Egg")
        assertThat(item("Sweet Curry Dip", "25ml").contains).containsExactly("Celery", "Mustard")
    }

    @Test
    fun `no mark means no chart, so a reader that cannot see the tick glyphs is not mistaken for an all-clear`() {
        val stripped = fixture().map { page -> page.filter { f -> f.text.none { it in ''..'' || it == '*' } } }
        val r = AllergenChartParser.parse(stripped)
        assertThat(r.items).isNotEmpty()
        assertThat(r.foundChart).isFalse()
    }

    @Test
    fun `a page with no chart finds nothing`() {
        val r = AllergenChartParser.parse(listOf(listOf(TextFragment("Some text", 0f, 0f, 40f, 10f))))
        assertThat(r.foundChart).isFalse()
        assertThat(r.items).isEmpty()
    }

    @Test
    fun `a grid whose headings do not sit on a regular grid is skipped, not guessed`() {
        val shifted = fixture().map { page ->
            page.map { f -> if (f.text == "Milk" && f.bottom - f.top > f.right - f.left) f.copy(left = f.left + 9f, right = f.right + 9f) else f }
        }
        val r = AllergenChartParser.parse(shifted)
        assertThat(r.skippedTables).isGreaterThan(0)
    }

    /** Run against every word of the real chart when INTAKE_CHART_TSV names a file in the fixture's format. */
    @Test
    fun `a whole chart is read table by table`() {
        val path = System.getenv("INTAKE_CHART_TSV")
        org.junit.Assume.assumeTrue("INTAKE_CHART_TSV not set", path != null && java.io.File(path).isFile)
        val lines = java.io.File(path!!).readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        val byPage = sortedMapOf<Int, MutableList<TextFragment>>()
        for (l in lines) {
            val c = l.split('\t')
            byPage.getOrPut(c[0].toInt()) { ArrayList() } += TextFragment(c[5], c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat())
        }
        val r = AllergenChartParser.parse(byPage.values.toList())
        java.io.File(path + ".out").writeText(r.items.joinToString("\n") { "${it.category}|${it.name}|${it.size}|V=${it.vegetarian}|VE=${it.vegan}|C=${it.contains}|M=${it.mayContain}|placed=${it.marksPlaced}" } + "\nmarks=${r.marks} skipped=${r.skippedTables}")
        assertThat(r.foundChart).isTrue()
    }

    // --- the column headings reach the allergen engine under names it understands ---------------------------

    private val aliases = AllergenCatalog.ALL.flatMap { a -> a.keywords.map { AllergenAlias(a.key, it.term, it.level) } }
    private val every = AllergenCatalog.ALL.map { it.key }.toSet()

    private fun blocked(tag: String): Set<String> =
        AllergenMatcher.scan(aliases = aliases, avoidedAllergens = every, name = "Item", structuredContains = listOf(tag))
            .contains.map { it.allergen.lowercase() }.toSet()

    @Test
    fun `every column heading maps to the allergen the person would have set`() {
        val expected = mapOf(
            "wheat" to "wheat", "rye" to "gluten", "barley" to "gluten", "oats" to "gluten", "spelt" to "wheat", "kamut" to "wheat",
            "soya" to "soy", "nuts" to "tree nuts", "peanuts" to "peanuts", "sesame" to "sesame", "milk" to "dairy", "eggs" to "egg",
            "fish" to "fish", "crustaceans" to "shellfish", "celery" to "celery", "sulphur" to "sulphites", "mustard" to "mustard",
            "molluscs" to "molluscs", "lupin" to "lupin",
        )
        for ((heading, allergen) in expected) {
            val i = AllergenChartColumns.indexOfHeading(heading)!!
            val hits = AllergenChartColumns.tags(i).flatMap(::blocked).toSet()
            assertThat(hits).contains(allergen)
        }
        assertThat(AllergenChartColumns.indexOfHeading("Vegetarian")).isNull()
        assertThat(AllergenChartColumns.indexOfHeading("Sulphur Dioxide & Sulphites")).isEqualTo(AllergenChartColumns.indexOfHeading("sulphur"))
    }
}
