package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.SwapAllergen
import com.zeroindex.intake.domain.swap.SwapKind
import com.zeroindex.intake.domain.swap.SwapOption
import org.junit.Test

class AllergenOverrideTest {

    private fun option(allergen: SwapAllergen, allergens: List<String> = listOf("dairy"), evidence: List<String> = listOf("Says cheese")) =
        SwapOption(
            "k", "Whopper", null, SwapKind.NEARBY, Macros(kcal = 650.0), estimate = true, allergen = allergen,
            allergenReason = "Contains cheese", matchedAllergens = allergens, evidence = evidence,
        )

    @Test fun `only a blocked option has an override prompt`() {
        assertThat(AllergenOverride.prompt(option(SwapAllergen.CONTAINS))).isNotNull()
        listOf(SwapAllergen.CLEAR, SwapAllergen.CHECK_LABEL, SwapAllergen.MAY_CONTAIN).forEach {
            assertThat(AllergenOverride.prompt(option(it))).isNull()
        }
    }

    @Test fun `the prompt names the allergens, the evidence and the consequence`() {
        val p = AllergenOverride.prompt(option(SwapAllergen.CONTAINS, listOf("dairy", "egg", "soy")))!!
        assertThat(p.headline).isEqualTo("Contains dairy, egg and soy")
        assertThat(p.evidence).containsExactly("Says cheese")
        assertThat(p.consequence).isEqualTo("Intake will log this, but the allergen warning stays on the entry.")
    }

    @Test fun `with no allergen named the prompt falls back to the reason`() {
        val p = AllergenOverride.prompt(option(SwapAllergen.CONTAINS, allergens = emptyList(), evidence = emptyList()))!!
        assertThat(p.headline).isEqualTo("Contains an allergen you avoid")
        assertThat(p.evidence).containsExactly("Contains cheese")
    }

    @Test fun `the note on the log entry says logged despite`() {
        assertThat(AllergenOverride.loggedDespite(listOf("dairy"))).isEqualTo("Logged despite: dairy")
        assertThat(AllergenOverride.loggedDespite(listOf("dairy", "egg"))).isEqualTo("Logged despite: dairy, egg")
    }

    @Test fun `annotating keeps the name and adds the note once`() {
        val (name, label) = AllergenOverride.annotate("Whopper · Burger King", "eat-out estimate", "Logged despite: dairy")
        assertThat(name).isEqualTo("Whopper · Burger King (Logged despite: dairy)")
        assertThat(label).isEqualTo("eat-out estimate · Logged despite: dairy")
        assertThat(AllergenOverride.annotate(name, label, "Logged despite: dairy")).isEqualTo(name to label)
        assertThat(AllergenOverride.annotate("X", null, "Logged despite: dairy").second).isEqualTo("Logged despite: dairy")
    }

    @Test fun `an override never changes the option's allergen status`() {
        val o = option(SwapAllergen.CONTAINS)
        AllergenOverride.prompt(o)
        AllergenOverride.annotate(o.title, null, AllergenOverride.loggedDespite(o.matchedAllergens))
        assertThat(o.allergen).isEqualTo(SwapAllergen.CONTAINS)
        assertThat(o.blocked).isTrue()
    }
}
