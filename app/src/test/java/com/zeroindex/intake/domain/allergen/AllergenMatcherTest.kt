package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

/**
 * SPEC §14 names the buttermilk-versus-butternut case specifically, because a
 * substring match here either cries wolf on squash or misses hidden dairy.
 */
class AllergenMatcherTest {

    private val dairyAliases = listOf(
        "milk", "butter", "cream", "cheese", "whey", "casein", "caseinate",
        "sodium caseinate", "lactose", "ghee", "curds", "milkfat", "buttermilk",
        "custard", "half and half", "condensed milk", "evaporated milk",
        "nonfat dry milk", "milk solids", "lactalbumin", "lactoglobulin",
        "rennet casein", "dairy",
    ).map { AllergenAlias("dairy", it) }

    private fun scan(
        name: String? = null,
        brand: String? = null,
        ingredients: String? = null,
        contains: List<String> = emptyList(),
        traces: List<String> = emptyList(),
    ) = AllergenMatcher.scan(
        aliases = dairyAliases,
        avoidedAllergens = setOf("dairy"),
        name = name,
        brand = brand,
        ingredients = ingredients,
        structuredContains = contains,
        structuredTraces = traces,
    )

    @Test
    fun `buttermilk matches`() {
        val result = scan(name = "Buttermilk pancakes")
        assertThat(result.blocks).isTrue()
        assertThat(result.contains.map { it.matchedTerm }).contains("buttermilk")
    }

    @Test
    fun `butternut does not match butter`() {
        assertThat(scan(name = "Butternut squash soup").hasAnything).isFalse()
    }

    @Test
    fun `butterfly and buttercup do not match either`() {
        assertThat(scan(name = "Butterfly pea tea").hasAnything).isFalse()
        assertThat(scan(name = "Buttercup gourd").hasAnything).isFalse()
    }

    @Test
    fun `matching is case insensitive`() {
        assertThat(scan(name = "WHEY protein isolate").blocks).isTrue()
        assertThat(scan(ingredients = "Sodium Caseinate").blocks).isTrue()
    }

    @Test
    fun `multi word aliases match as a phrase`() {
        assertThat(scan(ingredients = "water, sodium caseinate, salt").blocks).isTrue()
        assertThat(scan(ingredients = "half and half").blocks).isTrue()
        assertThat(scan(ingredients = "sodium chloride").hasAnything).isFalse()
    }

    @Test
    fun `hidden dairy in an ingredient panel is caught`() {
        val result = scan(
            name = "Protein bar",
            ingredients = "Soy protein isolate, glycerin, lactalbumin, natural flavour",
        )
        assertThat(result.blocks).isTrue()
        val match = result.contains.single()
        assertThat(match.matchedTerm).isEqualTo("lactalbumin")
        assertThat(match.field).isEqualTo(MatchField.INGREDIENTS)
        assertThat(match.note).contains("ingredients")
    }

    @Test
    fun `the match names the exact term and where it was found`() {
        // SPEC §7.2: the sheet must name the matched term and its location.
        val match = scan(brand = "Cream Co").matches.single()
        assertThat(match.matchedTerm).isEqualTo("cream")
        assertThat(match.field).isEqualTo(MatchField.BRAND)
        assertThat(match.note).contains("brand")
    }

    @Test
    fun `whole-word matching also means creamery is missed`() {
        // The cost of the butternut rule, written down rather than discovered
        // later: a derived word like "creamery" is not the alias "cream", so a
        // brand name alone will not flag it. Structured tags and the ingredient
        // panel are what catch these.
        assertThat(scan(brand = "Cabot Creamery").hasAnything).isFalse()
        assertThat(scan(name = "Cheesecake").hasAnything).isFalse()
    }

    @Test
    fun `structured contains tags are trusted as primary`() {
        // SPEC §7.3: Open Food Facts tags first, text scan second.
        val result = scan(name = "Mystery biscuit", contains = listOf("en:milk"))
        assertThat(result.blocks).isTrue()
        assertThat(result.contains.single().field).isEqualTo(MatchField.STRUCTURED_TAG)
    }

    @Test
    fun `traces are may-contain rather than contains`() {
        val result = scan(name = "Dark chocolate", traces = listOf("en:milk"))
        assertThat(result.blocks).isFalse()
        assertThat(result.mayContain).hasSize(1)
        assertThat(result.mayContain.single().kind).isEqualTo(AllergenMatchKind.MAY_CONTAIN)
    }

    @Test
    fun `plant milks are softened to may-contain not a red block`() {
        val oat = scan(name = "Oat milk latte")
        assertThat(oat.blocks).isFalse()
        assertThat(oat.mayContain.single().note).contains("plant milk")
        assertThat(scan(name = "Almond milk").blocks).isFalse()
        assertThat(scan(name = "Coconut cream").blocks).isFalse()
    }

    @Test
    fun `real dairy milk still blocks`() {
        assertThat(scan(name = "Whole milk").blocks).isTrue()
        assertThat(scan(name = "Milk chocolate").blocks).isTrue()
    }

    @Test
    fun `dairy free claims are softened but never silent`() {
        val result = scan(name = "Dairy free spread")
        assertThat(result.blocks).isFalse()
        assertThat(result.hasAnything).isTrue()
        assertThat(result.mayContain.single().note).contains("absent")
    }

    @Test
    fun `non dairy creamer is softened`() {
        val result = scan(name = "Non-dairy creamer")
        assertThat(result.blocks).isFalse()
        assertThat(result.hasAnything).isTrue()
    }

    @Test
    fun `a clean food produces nothing`() {
        assertThat(scan(name = "Chicken breast", ingredients = "chicken").hasAnything).isFalse()
    }

    @Test
    fun `an empty profile never blocks anything`() {
        val result = AllergenMatcher.scan(
            aliases = dairyAliases,
            avoidedAllergens = emptySet(),
            name = "Buttermilk pancakes with extra cheese",
        )
        assertThat(result.hasAnything).isFalse()
    }

    @Test
    fun `aliases for allergens the user does not avoid are ignored`() {
        val result = AllergenMatcher.scan(
            aliases = dairyAliases + AllergenAlias("gluten", "wheat"),
            avoidedAllergens = setOf("gluten"),
            name = "Cheese sandwich on wheat",
        )
        assertThat(result.contains.map { it.allergen }).containsExactly("gluten")
    }

    @Test
    fun `each alias is reported once even when it appears repeatedly`() {
        val result = scan(ingredients = "milk, milk solids, milk powder, milk")
        assertThat(result.matches.count { it.matchedTerm == "milk" }).isEqualTo(1)
    }

    @Test
    fun `a structured contains tag wins over a softened text hit`() {
        // Labelled "oat milk" but the panel declares milk: still a hard block.
        assertThat(scan(name = "Oat milk blend", contains = listOf("en:milk")).blocks).isTrue()
    }

    @Test
    fun `punctuation and separators do not hide a term`() {
        assertThat(scan(ingredients = "sugar,whey,salt").blocks).isTrue()
        assertThat(scan(ingredients = "cocoa butter (milk)").blocks).isTrue()
        assertThat(scan(ingredients = "flavour: ghee.").blocks).isTrue()
    }
}
