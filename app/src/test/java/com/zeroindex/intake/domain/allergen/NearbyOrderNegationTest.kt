package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * An order's title or modification note that names an allergen only to leave it out ("Whopper without cheese") is not
 * evidence of that ingredient. It clears nothing: everything else still counts, and panels and stated lists are never
 * negation-downgraded. These are the nearby finder's inputs (name and modification note as free text).
 */
class NearbyOrderNegationTest {

    private val aliases = AllergenCatalog.aliases()

    private fun scan(name: String, mods: String? = null, contains: List<String> = emptyList()) =
        AllergenMatcher.scan(
            aliases = aliases, avoidedAllergens = setOf("dairy"), name = name, ingredients = mods,
            structuredContains = contains,
            checkLabelTerms = HiddenCarriers.forAllergen("dairy"),
            checkLabelInIngredients = mods != null, freeText = true,
        )

    @Test fun `without cheese is not a cheese match`() {
        val r = scan("Whopper without cheese")
        assertThat(r.blocks).isFalse()
        assertThat(r.mentionedAvoided.map { it.matchedTerm }).contains("cheese")
    }

    @Test fun `no cheese, hold the cheese and cheese-free are not matches`() {
        listOf("Whopper, no cheese", "Whopper hold the cheese", "Cheese-free Whopper", "Whopper with no cheese").forEach {
            assertThat(scan(it).blocks).isFalse()
        }
    }

    @Test fun `a negated modification note is not a match`() {
        val r = scan("Grilled chicken sandwich", mods = "no cheese, no butter, ask for oil instead")
        assertThat(r.blocks).isFalse()
        assertThat(r.mentionedAvoided).isNotEmpty()
    }

    @Test fun `a plain mention still blocks`() {
        assertThat(scan("Whopper with cheese").blocks).isTrue()
        assertThat(scan("Cheeseburger").hasAnything).isTrue() // a may-contain keyword: not cleared, though it does not block
    }

    @Test fun `negating one item does not clear another`() {
        assertThat(scan("Burger, no cheese, with butter").blocks).isTrue()
        assertThat(scan("Cheeseburger, no pickles").hasAnything).isTrue()
        assertThat(scan("Whopper without cheese, with buttermilk ranch").blocks).isTrue()
    }

    @Test fun `known dairy names still match when the cheese is negated`() {
        assertThat(scan("Buttermilk crispy chicken, no cheese").blocks).isTrue()
    }

    @Test fun `a stated allergen list is never negated`() {
        assertThat(scan("Whopper without cheese", contains = listOf("milk")).blocks).isTrue()
    }

    @Test fun `an ingredient panel is never negated`() {
        val r = AllergenMatcher.scan(
            aliases = aliases, avoidedAllergens = setOf("dairy"), name = "Whopper without cheese",
            ingredients = "beef, bun, cheese sauce", checkLabelTerms = HiddenCarriers.forAllergen("dairy"),
            freeText = true,
        )
        assertThat(r.blocks).isTrue()
    }

    @Test fun `the negated word alone leaves nothing positive to rely on`() {
        val r = scan("Grilled chicken, no cheese")
        assertThat(r.hasAnything).isFalse()
        assertThat(r.hasMentionedAvoided).isTrue()
    }
}
