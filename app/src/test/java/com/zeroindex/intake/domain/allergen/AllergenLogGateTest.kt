package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.AllergenMatchKind
import org.junit.Test

class AllergenLogGateTest {

    private fun scan(vararg kinds: AllergenMatchKind) = AllergenScanResult(
        kinds.mapIndexed { i, k ->
            AllergenMatch("dairy", "cheese$i", MatchField.NAME, k, "note")
        },
    )

    @Test fun `contains and may contain both need a review`() {
        assertThat(AllergenLogGate.decide(scan(AllergenMatchKind.CONTAINS))).isInstanceOf(AllergenLogGate.Decision.Review::class.java)
        assertThat(AllergenLogGate.decide(scan(AllergenMatchKind.MAY_CONTAIN))).isInstanceOf(AllergenLogGate.Decision.Review::class.java)
    }

    @Test fun `check the label, mentioned as avoided and clear proceed`() {
        assertThat(AllergenLogGate.decide(scan(AllergenMatchKind.CHECK_LABEL))).isEqualTo(AllergenLogGate.Decision.Proceed)
        assertThat(AllergenLogGate.decide(scan(AllergenMatchKind.MENTIONED_AVOIDED))).isEqualTo(AllergenLogGate.Decision.Proceed)
        assertThat(AllergenLogGate.decide(AllergenScanResult.CLEAN)).isEqualTo(AllergenLogGate.Decision.Proceed)
    }

    @Test fun `the override note names what was logged despite`() {
        assertThat(AllergenLogGate.overrideNote(scan(AllergenMatchKind.CONTAINS))).isEqualTo("Logged despite: dairy")
    }

    // The watch has no warning sheet, so it refuses both tiers. A planned meal or a template that only MAY contain an
    // allergen must not be logged from the watch with no human confirmation.
    @Test fun `the watch refuses contains with the terms`() {
        val d = AllergenLogGate.forWatch(scan(AllergenMatchKind.CONTAINS))
        assertThat(d).isEqualTo(AllergenLogGate.WatchDecision.Blocked(listOf("cheese0")))
    }

    @Test fun `the watch sends may contain to the phone and never logs it`() {
        val d = AllergenLogGate.forWatch(scan(AllergenMatchKind.MAY_CONTAIN))
        assertThat(d).isEqualTo(AllergenLogGate.WatchDecision.CheckOnPhone(listOf("cheese0")))
    }

    @Test fun `the watch allows only a scan with nothing to review`() {
        assertThat(AllergenLogGate.forWatch(AllergenScanResult.CLEAN)).isEqualTo(AllergenLogGate.WatchDecision.Allow)
        assertThat(AllergenLogGate.forWatch(scan(AllergenMatchKind.CHECK_LABEL))).isEqualTo(AllergenLogGate.WatchDecision.Allow)
    }

    @Test fun `contains wins over may contain for the watch`() {
        val d = AllergenLogGate.forWatch(scan(AllergenMatchKind.MAY_CONTAIN, AllergenMatchKind.CONTAINS))
        assertThat(d).isInstanceOf(AllergenLogGate.WatchDecision.Blocked::class.java)
    }

    @Test fun `an entry that already says logged despite is recognised`() {
        assertThat(AllergenOverride.alreadyNoted("Mac and cheese (Logged despite: dairy)")).isTrue()
        assertThat(AllergenOverride.alreadyNoted("Mac and cheese")).isFalse()
    }

    @Test fun `a recipe with no ingredient lines is never a pass`() {
        assertThat(AllergenEvidence.recipeClearBasis(0)).isEqualTo(AllergenEvidence.ClearBasis.NAME_ONLY)
        assertThat(AllergenEvidence.recipeClearBasis(0).chipText).isEqualTo("Only the name was checked")
        assertThat(AllergenEvidence.recipeClearBasis(0).isCaution).isTrue()
        assertThat(AllergenEvidence.recipeClearBasis(3)).isEqualTo(AllergenEvidence.ClearBasis.LABEL_READ)
    }
}
