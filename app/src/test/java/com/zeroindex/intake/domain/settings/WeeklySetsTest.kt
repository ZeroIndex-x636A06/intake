package com.zeroindex.intake.domain.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeeklySetsTest {
    @Test fun `empty means the suggested numbers`() {
        val v = WeeklySets.parse("")
        MuscleRegion.entries.forEach { assertThat(v[it]).isEqualTo(it.suggestedSets) }
        assertThat(WeeklySets.isSuggested("")).isTrue()
    }

    @Test fun `setting one region keeps the others and round trips`() {
        val csv = WeeklySets.with("", MuscleRegion.BACK, 18)
        assertThat(WeeklySets.parse(csv)[MuscleRegion.BACK]).isEqualTo(18)
        assertThat(WeeklySets.parse(csv)[MuscleRegion.CHEST]).isEqualTo(MuscleRegion.CHEST.suggestedSets)
        assertThat(csv.split(',')).hasSize(6)
        assertThat(WeeklySets.isSuggested(csv)).isFalse()
    }

    @Test fun `out of range or garbage falls back`() {
        assertThat(WeeklySets.with("", MuscleRegion.ARMS, 0)).isEqualTo("")
        assertThat(WeeklySets.with("", MuscleRegion.ARMS, 99)).isEqualTo("")
        assertThat(WeeklySets.parse("x,,5")[MuscleRegion.CHEST]).isEqualTo(MuscleRegion.CHEST.suggestedSets)
        assertThat(WeeklySets.parse("x,,5")[MuscleRegion.SHOULDERS]).isEqualTo(5)
    }
}
