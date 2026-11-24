package com.zeroindex.intake.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MealSlotLabelTest {
    @Test
    fun `every slot reads with a capital, as the diary shows it`() {
        assertThat(MealSlot.entries.map { it.label })
            .containsExactly("Breakfast", "Lunch", "Dinner", "Snacks", "Drinks").inOrder()
    }
}
