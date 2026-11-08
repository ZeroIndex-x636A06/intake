package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartingWeightTest {
    private val target = ExerciseTarget(sets = 3, repsMin = 5, repsMax = 5, targetRpe = 8.0)

    @Test fun `a starting weight becomes the first prescription with its reason`() {
        val p = StartingWeight.prescription(83.9, target)!!
        assertThat(p.weightKg).isEqualTo(83.9)
        assertThat(p.sets).isEqualTo(3)
        assertThat(p.targetRpe).isEqualTo(8.0)
        assertThat(p.reason).contains("Starting weight from your program")
    }

    @Test fun `no starting weight means no prescription from here`() {
        assertThat(StartingWeight.prescription(null, target)).isNull()
        assertThat(StartingWeight.prescription(0.0, target)).isNull()
    }
}
