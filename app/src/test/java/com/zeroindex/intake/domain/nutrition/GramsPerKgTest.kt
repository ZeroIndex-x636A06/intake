package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GramsPerKgTest {
    @Test fun `170 g at 87 point 1 kg reads 2 point 0, not the setting's 1 point 9`() {
        assertThat(GramsPerKg.text(170.0, 87.1, fallbackPerKg = 1.9)).isEqualTo("2.0")
    }

    @Test fun `a tie rounds up and below a tie rounds down`() {
        assertThat(GramsPerKg.round1(1.95)).isEqualTo(2.0)
        assertThat(GramsPerKg.round1(1.94)).isEqualTo(1.9)
        assertThat(GramsPerKg.round1(0.65)).isEqualTo(0.7)
    }

    @Test fun `without a weight the setting is used`() {
        assertThat(GramsPerKg.text(170.0, null, fallbackPerKg = 1.9)).isEqualTo("1.9")
        assertThat(GramsPerKg.text(170.0, 0.0, fallbackPerKg = 1.9)).isEqualTo("1.9")
    }

    @Test fun `fat per kg`() {
        assertThat(GramsPerKg.text(70.0, 87.1, fallbackPerKg = 0.8)).isEqualTo("0.8")
    }
}
