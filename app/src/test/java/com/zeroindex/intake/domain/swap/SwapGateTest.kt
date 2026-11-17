package com.zeroindex.intake.domain.swap

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenMatch
import com.zeroindex.intake.domain.allergen.AllergenOverride
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.MatchField
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

class SwapGateTest {
    private fun match(kind: AllergenMatchKind, allergen: String = "Dairy") =
        AllergenMatch(allergen, "milk", MatchField.INGREDIENTS, kind, "note")

    @Test fun `a clean scan proceeds`() {
        assertThat(SwapGate.decide(AllergenScanResult.CLEAN)).isEqualTo(SwapGate.Decision.Proceed)
    }

    @Test fun `may contain and contains both warn`() {
        assertThat(SwapGate.decide(AllergenScanResult(listOf(match(AllergenMatchKind.MAY_CONTAIN))))).isInstanceOf(SwapGate.Decision.Warn::class.java)
        assertThat(SwapGate.decide(AllergenScanResult(listOf(match(AllergenMatchKind.CONTAINS))))).isInstanceOf(SwapGate.Decision.Warn::class.java)
    }

    @Test fun `check the label alone does not open the sheet`() {
        assertThat(SwapGate.decide(AllergenScanResult(listOf(match(AllergenMatchKind.CHECK_LABEL))))).isEqualTo(SwapGate.Decision.Proceed)
    }

    @Test fun `a may contain verdict raises a clean live scan`() {
        val scan = SwapGate.withVerdict(AllergenScanResult.CLEAN, SwapAllergen.MAY_CONTAIN)
        assertThat(scan.mayContain).hasSize(1)
        assertThat(scan.blocks).isFalse()
        assertThat(SwapGate.decide(scan)).isInstanceOf(SwapGate.Decision.Warn::class.java)
    }

    @Test fun `a contains verdict raises a may contain live scan to a block`() {
        val scan = SwapGate.withVerdict(AllergenScanResult(listOf(match(AllergenMatchKind.MAY_CONTAIN))), SwapAllergen.CONTAINS)
        assertThat(scan.blocks).isTrue()
        assertThat(scan.mayContain).hasSize(1)
    }

    @Test fun `a verdict never lowers a live scan`() {
        val live = AllergenScanResult(listOf(match(AllergenMatchKind.CONTAINS)))
        assertThat(SwapGate.withVerdict(live, SwapAllergen.CLEAR)).isSameInstanceAs(live)
        assertThat(SwapGate.withVerdict(live, SwapAllergen.MAY_CONTAIN)).isSameInstanceAs(live)
    }

    @Test fun `the override note names the allergens raised`() {
        val scan = SwapGate.withVerdict(AllergenScanResult(listOf(match(AllergenMatchKind.MAY_CONTAIN, "Dairy"))), SwapAllergen.MAY_CONTAIN)
        assertThat(AllergenOverride.noteFor(scan)).isEqualTo("Logged despite: dairy")
    }
}
