package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.pdf.PdfTextLayout
import com.zeroindex.intake.domain.pdf.TextFragment
import org.junit.Test

/**
 * The fixture is page 1 of the McDonald's USA nutrition PDF a person tried to import
 * (fcs.osu.edu ... mcdonalds-nutrition-facts.pdf): every word with its box, no PDF bytes. The asserted numbers were
 * read off the PDF by eye, not from the parser.
 */
class PositionedNutritionTableTest {

    private val fragments: List<TextFragment> by lazy {
        checkNotNull(javaClass.getResourceAsStream("/menu/mcdonalds-usa-nutrition-page1-fragments.tsv")).bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { l ->
                val c = l.split('\t')
                TextFragment(c[5], c[1].toFloat(), c[2].toFloat(), c[3].toFloat(), c[4].toFloat())
            }
    }
    private val parsed by lazy { PositionedNutritionTable.parse(listOf(fragments)) }
    private fun row(name: String) = parsed.rows.first { it.name == name }

    @Test
    fun `the old line reader finds nothing in this file, which is why it failed`() {
        // What the phone's PdfTextLayout makes of this page: names split around the numbers, no heading line.
        val lines = PdfTextLayout.lines(fragments)
        assertThat(NutritionTableParser.parseLines(lines).foundTable).isFalse()
    }

    @Test
    fun `columns are named from the rotated headings`() {
        assertThat(parsed.foundTable).isTrue()
        assertThat(parsed.columnsFromHeading).isTrue()
    }

    @Test
    fun `a two-line name with its serving size in the cell is read in full`() {
        val r = row("Bacon Clubhouse Burger")
        assertThat(r.servingWeightG).isEqualTo(274.0)
        assertThat(r.kcal).isEqualTo(740.0)
        assertThat(r.fatG).isEqualTo(41.0)
        assertThat(r.saturatedFatG).isEqualTo(16.0)
        assertThat(r.sodiumMg).isEqualTo(1480.0)
        assertThat(r.carbsG).isEqualTo(51.0)
        assertThat(r.fiberG).isEqualTo(4.0)
        assertThat(r.sugarsG).isEqualTo(14.0)
        assertThat(r.proteinG).isEqualTo(40.0)
        assertThat(r.category).isEqualTo("Burgers & Sandwiches")
    }

    @Test
    fun `a name wrapped around the numbers is put back together`() {
        val r = row("Bacon Clubhouse Grilled Chicken Sandwich")
        assertThat(r.servingWeightG).isEqualTo(285.0)
        assertThat(r.kcal).isEqualTo(610.0)
        assertThat(r.proteinG).isEqualTo(45.0)
    }

    @Test
    fun `percent daily value columns are not read as grams`() {
        val r = row("McChicken")
        assertThat(r.servingWeightG).isEqualTo(144.0)
        assertThat(r.kcal).isEqualTo(370.0)
        assertThat(r.fatG).isEqualTo(17.0)
        assertThat(r.saturatedFatG).isEqualTo(3.5)
        assertThat(r.sodiumMg).isEqualTo(650.0)
        assertThat(r.carbsG).isEqualTo(40.0)
        assertThat(r.fiberG).isEqualTo(2.0)
        assertThat(r.sugarsG).isEqualTo(5.0)
        assertThat(r.proteinG).isEqualTo(14.0)
    }

    @Test
    fun `the registered mark and dagger are dropped from a name`() {
        assertThat(parsed.rows.map { it.name }).containsNoneOf("McChicken ®", "McRib ® †")
        val r = row("McRib")
        assertThat(r.kcal).isEqualTo(500.0)
        assertThat(r.sharedFacility).isTrue()
    }

    @Test
    fun `Filet-O-Fish and a McChicken variant`() {
        val fish = row("Filet-O-Fish")
        assertThat(fish.servingWeightG).isEqualTo(142.0)
        assertThat(fish.kcal).isEqualTo(390.0)
        assertThat(fish.carbsG).isEqualTo(39.0)
        assertThat(fish.proteinG).isEqualTo(15.0)
        val cheddar = row("Bacon Cheddar McChicken")
        assertThat(cheddar.servingWeightG).isEqualTo(172.0)
        assertThat(cheddar.kcal).isEqualTo(490.0)
        assertThat(cheddar.fatG).isEqualTo(25.0)
        assertThat(cheddar.saturatedFatG).isEqualTo(7.0)
        assertThat(cheddar.sodiumMg).isEqualTo(1120.0)
        assertThat(cheddar.sugarsG).isEqualTo(6.0)
        assertThat(cheddar.proteinG).isEqualTo(22.0)
    }

    @Test
    fun `no allergen information is claimed`() {
        assertThat(parsed.rows.all { !it.allergensStated && it.stated.isEmpty }).isTrue()
    }

    @Test
    fun `the page is read almost whole and the intro text is not an item`() {
        assertThat(parsed.rows.size).isAtLeast(14)
        assertThat(parsed.skipped).isEqualTo(0)
        assertThat(parsed.rows.none { it.name.contains("provide", ignoreCase = true) }).isTrue()
    }
}
