package com.zeroindex.intake.domain.template

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.MealSlot
import org.junit.Test

class TemplateLogMessageTest {
    @Test fun `a logged template names the slot as the screen does, never in lower case`() {
        assertThat(TemplateLogMessage.logged("Post-workout shake", MealSlot.SNACK, null))
            .isEqualTo("Logged Post-workout shake to Snacks")
        assertThat(TemplateLogMessage.slotLabel(MealSlot.BREAKFAST)).isEqualTo("Breakfast")
    }

    @Test fun `the allergen note follows the message after a full stop`() {
        assertThat(TemplateLogMessage.logged("Shake", MealSlot.SNACK, "Almond milk, unsweetened: check the label for dairy"))
            .isEqualTo("Logged Shake to Snacks. Almond milk, unsweetened: check the label for dairy")
    }
}
