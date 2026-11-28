package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.MealSlot
import java.time.LocalTime
import org.junit.Test

class PlannedMealSlotTest {

    @Test
    fun `a planned dish follows the meal of the day`() {
        assertThat(PlannedMealSlot.forTime(LocalTime.of(7, 30))).isEqualTo(MealSlot.BREAKFAST)
        assertThat(PlannedMealSlot.forTime(LocalTime.of(12, 5))).isEqualTo(MealSlot.LUNCH)
        assertThat(PlannedMealSlot.forTime(LocalTime.of(18, 45))).isEqualTo(MealSlot.DINNER)
    }

    @Test
    fun `a planned dish opened late in the evening is dinner, never a snack`() {
        assertThat(PlannedMealSlot.forTime(LocalTime.of(22, 34))).isEqualTo(MealSlot.DINNER)
        assertThat(PlannedMealSlot.forTime(LocalTime.of(21, 0))).isEqualTo(MealSlot.DINNER)
    }
}
