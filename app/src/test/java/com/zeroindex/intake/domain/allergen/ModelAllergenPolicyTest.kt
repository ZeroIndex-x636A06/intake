package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
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
}
