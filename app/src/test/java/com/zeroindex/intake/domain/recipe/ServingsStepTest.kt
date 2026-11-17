package com.zeroindex.intake.domain.recipe

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServingsStepTest {

    @Test
    fun `steps by half a serving in both directions`() {
        assertThat(ServingsStep.next(1.0, up = true)).isEqualTo(1.5)
        assertThat(ServingsStep.next(1.5, up = true)).isEqualTo(2.0)
        assertThat(ServingsStep.next(2.0, up = false)).isEqualTo(1.5)
    }

    @Test
    fun `never goes below half a serving`() {
        assertThat(ServingsStep.next(0.5, up = false)).isEqualTo(0.5)
        assertThat(ServingsStep.next(0.0, up = false)).isEqualTo(0.5)
    }

    @Test
    fun `labels read naturally`() {
        assertThat(ServingsStep.label(1.0)).isEqualTo("1 serving")
        assertThat(ServingsStep.label(0.5)).isEqualTo("0.5 servings")
        assertThat(ServingsStep.label(1.5)).isEqualTo("1.5 servings")
        assertThat(ServingsStep.label(2.0)).isEqualTo("2 servings")
    }
}
