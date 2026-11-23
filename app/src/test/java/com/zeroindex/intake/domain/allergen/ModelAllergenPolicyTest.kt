package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

class ModelAllergenPolicyTest {

    private val avoided = setOf("dairy")
    private fun contains(term: String) = AllergenScanResult(
        listOf(AllergenMatch("dairy", term, MatchField.NAME, AllergenMatchKind.CONTAINS, "\"$term\" found.")),
    )

    @Test
    fun `a local match blocks and the model cannot clear it`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(contains("butter")),
            claims = listOf(ModelAllergenClaim("butter chicken", emptyList(), "looks dairy-free to me")),
            avoided = avoided,
        )
        assertThat(v.scan.blocks).isTrue()
        assertThat(v.perItem[0]).isEqualTo(AllergenMatchKind.CONTAINS)
    }

    @Test
    fun `a model-only claim raises an amber review, not a block`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(ModelAllergenClaim("chicken curry", listOf("Dairy"), "often finished with ghee")),
            avoided = avoided,
        )
        assertThat(v.scan.blocks).isFalse()
        assertThat(v.scan.mayContain).hasSize(1)
        assertThat(v.scan.mayContain.single().note).contains("ghee")
        assertThat(v.perItem[0]).isEqualTo(AllergenMatchKind.MAY_CONTAIN)
    }

    @Test
    fun `claims about allergens the user does not avoid are ignored`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(ModelAllergenClaim("satay", listOf("peanuts"), "")),
            avoided = avoided,
        )
        assertThat(v.scan.hasAnything).isFalse()
        assertThat(v.perItem).isEmpty()
    }

    @Test
    fun `a model claim never downgrades a local block on the same item`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(contains("cheese")),
            claims = listOf(ModelAllergenClaim("pizza", listOf("dairy"), "")),
            avoided = avoided,
        )
        assertThat(v.perItem[0]).isEqualTo(AllergenMatchKind.CONTAINS)
        assertThat(v.scan.mayContain).isEmpty()
    }

    @Test
    fun `hidden sources found locally count toward the plate`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(ModelAllergenClaim("steak", emptyList(), "")),
            avoided = avoided,
            extraLocal = listOf(contains("butter")),
        )
        assertThat(v.scan.blocks).isTrue()
    }

    // ---- Issue #9: model terms resolve to categories through the local keyword catalogue ----

    private fun claim(vararg terms: String, item: String = "dish") =
        ModelAllergenClaim(item, terms.toList(), "")

    private fun raised(avoid: Set<String>, vararg terms: String): AllergenScanResult =
        ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(claim(*terms)),
            avoided = avoid,
        ).scan

    private fun assertRaises(avoid: Set<String>, allergen: String, term: String) {
        val scan = raised(avoid, term)
        assertWithMessage("term \"$term\"").that(scan.mayContain.map { it.allergen }).containsExactly(allergen)
        assertThat(scan.blocks).isFalse()
    }

    @Test
    fun `hidden dairy forms raise dairy`() {
        listOf("milk", "Milk", "MILK", "cheese", "butter", "cream", "whey", "casein", "ghee", "yoghurt", "yogurt", "lactose", "Dairy", "dairy", "cheeses", "Butter ")
            .forEach { assertRaises(setOf("dairy"), "dairy", it) }
    }

    @Test
    fun `shellfish molluscs almonds and sulphites resolve in both spellings`() {
        assertRaises(setOf("shellfish"), "shellfish", "shrimp")
        assertRaises(setOf("shellfish"), "shellfish", "Shrimps")
        assertRaises(setOf("shellfish"), "shellfish", "prawns")
        assertRaises(setOf("molluscs"), "molluscs", "mollusks")
        assertRaises(setOf("molluscs"), "molluscs", "molluscs")
        assertRaises(setOf("tree nuts"), "tree nuts", "almonds")
        assertRaises(setOf("tree nuts"), "tree nuts", "Almond")
        assertRaises(setOf("sulphites"), "sulphites", "sulfites")
        assertRaises(setOf("sulphites"), "sulphites", "sulphites")
    }

    @Test
    fun `terms that already worked still work`() {
        assertRaises(setOf("dairy"), "dairy", "dairy")
        assertRaises(setOf("peanuts"), "peanuts", "peanuts")
        assertRaises(setOf("peanuts"), "peanuts", "peanut")
        assertRaises(setOf("egg"), "egg", "eggs")
    }

    @Test
    fun `look-alike words do not raise the wrong allergen`() {
        // Unrecognised terms still raise a review, but never under the look-alike category.
        assertThat(raised(setOf("egg"), "eggplant").matches.map { it.allergen }).doesNotContain("egg")
        assertThat(raised(setOf("tree nuts"), "nutmeg").matches.map { it.allergen }).doesNotContain("tree nuts")
        assertThat(raised(setOf("dairy"), "butternut squash").matches.map { it.allergen }).doesNotContain("dairy")
    }

    @Test
    fun `coconut follows the catalogue rule`() {
        val inCatalogue = AllergenCatalog.ALL.any { a -> a.key == "tree nuts" && a.keywords.any { it.term == "coconut" } }
        val scan = raised(setOf("tree nuts"), "coconut")
        if (inCatalogue) {
            assertThat(scan.mayContain.map { it.allergen }).containsExactly("tree nuts")
        } else {
            assertThat(scan.matches.map { it.allergen }).doesNotContain("tree nuts")
        }
    }

    @Test
    fun `a claim for a known category the person does not avoid raises nothing`() {
        listOf("milk", "butter", "shrimp", "sulfites", "mollusks", "almonds", "peanuts").forEach {
            val v = ModelAllergenPolicy.merge(
                local = listOf(AllergenScanResult.CLEAN),
                claims = listOf(claim(it)),
                avoided = setOf("egg"),
            )
            assertWithMessage("term \"$it\"").that(v.scan.hasAnything).isFalse()
            assertThat(v.perItem).isEmpty()
        }
    }

    @Test
    fun `an unknown term is kept as an amber review with the raw term`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(ModelAllergenClaim("mystery stew", listOf("Zorblax"), "")),
            avoided = setOf("dairy"),
        )
        val m = v.scan.mayContain.single()
        assertThat(m.allergen).contains("Zorblax")
        assertThat(m.note).contains("The model flagged \"Zorblax\"")
        assertThat(m.note).contains("check before eating")
        assertThat(v.scan.blocks).isFalse()
        assertThat(v.perItem[0]).isEqualTo(AllergenMatchKind.MAY_CONTAIN)
    }

    @Test
    fun `a custom keyword resolves a model term`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(AllergenScanResult.CLEAN),
            claims = listOf(claim("skyr")),
            avoided = setOf("dairy"),
            aliases = AllergenCatalog.aliases() + AllergenAlias("dairy", "skyr"),
        )
        assertThat(v.scan.mayContain.map { it.allergen }).containsExactly("dairy")
    }

    @Test
    fun `a local match for the same category is not duplicated by a model term`() {
        val v = ModelAllergenPolicy.merge(
            local = listOf(contains("cheese")),
            claims = listOf(claim("milk")),
            avoided = avoided,
        )
        assertThat(v.scan.mayContain).isEmpty()
        assertThat(v.perItem[0]).isEqualTo(AllergenMatchKind.CONTAINS)
    }
}
