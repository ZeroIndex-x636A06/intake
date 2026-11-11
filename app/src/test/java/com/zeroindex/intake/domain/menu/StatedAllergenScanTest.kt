package com.zeroindex.intake.domain.menu

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.model.KeywordLevel
import org.junit.Test

/** A restaurant's stated allergens reach the engine as structured inputs: they raise a verdict and never clear one. */
class StatedAllergenScanTest {

    private val aliases = listOf(
        AllergenAlias("sesame", "sesame", KeywordLevel.CONTAINS),
        AllergenAlias("dairy", "milk", KeywordLevel.CONTAINS),
        AllergenAlias("soy", "soy", KeywordLevel.CONTAINS),
    )

    private fun scan(name: String, ingredients: String) = StatedAllergens.parse(ingredients).let { stated ->
        AllergenMatcher.scan(
            aliases = aliases,
            avoidedAllergens = setOf("sesame", "dairy"),
            name = name,
            ingredients = StatedAllergens.textForScan(ingredients),
            structuredContains = stated.contains,
            structuredTraces = stated.mayContain,
        )
    }

    @Test
    fun `a stated contains blocks and names the allergen`() {
        val r = scan("Classic Roast Beef", "Contains: Soy, Wheat, Sesame")
        assertThat(r.blocks).isTrue()
        assertThat(r.contains.map { it.allergen.lowercase() }).contains("sesame")
    }

    @Test
    fun `a stated may contain is a may contain, not a block`() {
        val r = scan("Classic Roast Beef", "Contains: Soy, Wheat; May contain: Milk")
        assertThat(r.blocks).isFalse()
        assertThat(r.mayContain.map { it.allergen.lowercase() }).contains("dairy")
    }

    @Test
    fun `an allergen the person does not avoid raises nothing`() {
        val r = scan("Classic Roast Beef", "Contains: Soy, Wheat")
        assertThat(r.hasAnything).isFalse()
    }

    @Test
    fun `no stated text is just the name check, which is why it must not be shown as no allergens`() {
        val r = scan("Classic Roast Beef", "")
        assertThat(r.hasAnything).isFalse()
        assertThat(MenuSearch.hasNoIngredientInfo("")).isTrue()
    }
}
