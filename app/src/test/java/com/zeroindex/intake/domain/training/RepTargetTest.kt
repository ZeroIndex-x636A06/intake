package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepTargetTest {

    @Test fun amrapIsOneToNinetyNine() {
        assertThat(RepTarget.isAmrap(1, 99)).isTrue()
        assertThat(RepTarget.isAmrap(0, 99)).isTrue()
        assertThat(RepTarget.isAmrap(1, 12)).isFalse()
        assertThat(RepTarget.isAmrap(8, 99)).isFalse()
    }

    @Test fun labelsReadInPlainWords() {
        assertThat(RepTarget.label(1, 99)).isEqualTo("As many as you can")
        assertThat(RepTarget.label(8, 12)).isEqualTo("8–12")
        assertThat(RepTarget.label(5, 5)).isEqualTo("5")
        assertThat(RepTarget.short(1, 99)).isEqualTo("max")
        assertThat(RepTarget.short(6, 8)).isEqualTo("6–8")
    }

    @Test fun newBestNeedsHistoryAndAHigherEstimatedMax() {
        val prior = listOf(SetResult(100.0, 5), SetResult(90.0, 8))
        // 100 x 5 -> 116.7; 90 x 8 -> 114.0
        assertThat(NewBest.isNewBest(105.0, 5, prior)).isTrue()
        assertThat(NewBest.isNewBest(100.0, 5, prior)).isFalse()
        assertThat(NewBest.isNewBest(80.0, 5, prior)).isFalse()
        // No history: nothing to beat, so no claim.
        assertThat(NewBest.isNewBest(100.0, 5, emptyList())).isFalse()
        assertThat(NewBest.isNewBest(0.0, 5, prior)).isFalse()
        assertThat(NewBest.isNewBest(100.0, 0, prior)).isFalse()
    }
}
