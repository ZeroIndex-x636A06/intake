package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PortionTextTest {
    @Test fun `a label that is the amount is not repeated (Q10-7)`() {
        assertThat(PortionText.withAmount("100 g", "100 g")).isEqualTo("100 g")
        assertThat(PortionText.withAmount("100g", "100 g")).isEqualTo("100g")
    }

    @Test fun `a named portion gets its weight`() {
        assertThat(PortionText.withAmount("1 cup", "245 g")).isEqualTo("1 cup (245 g)")
        assertThat(PortionText.withAmount("1 cup (245 g)", "245 g")).isEqualTo("1 cup (245 g)")
        assertThat(PortionText.withAmount("100 g", "3.5 oz")).isEqualTo("100 g (3.5 oz)")
    }
}
