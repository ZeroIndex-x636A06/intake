package com.zeroindex.intake.ui.screens.nutrition

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.DayEntry
import com.zeroindex.intake.domain.nutrition.DayNutrition
import com.zeroindex.intake.domain.nutrition.Macros
import org.junit.Test

class NutritionSlotRowsTest {

    private fun row(slot: MealSlot, name: String, kcal: Double, sugar: Double = 0.0, at: Long = 1L) = FoodLogEntity(
        id = name,
        loggedAt = at,
        mealSlot = slot,
        foodNameAtLog = name,
        kcal = kcal,
        protein = 1.0,
        carbs = 2.0,
        fat = 3.0,
        sugar = sugar,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private val day = listOf(
        row(MealSlot.BREAKFAST, "Oats", 300.0),
        row(MealSlot.DINNER, "Rice", 400.0),
        row(MealSlot.DRINKS, "Cola", 150.0, sugar = 35.0),
        row(MealSlot.DRINKS, "Coffee", 5.0, at = 2L),
    )

    @Test
    fun `the Nutrition meals card lists only the four meals, never a Drinks row`() {
        val rows = nutritionSlotRows(day)
        assertThat(rows.map { it.slot }).containsExactly(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK).inOrder()
        assertThat(rows.map { it.slot }).doesNotContain(MealSlot.DRINKS)
    }

    @Test
    fun `an empty day still shows the four meal rows`() {
        assertThat(nutritionSlotRows(emptyList()).map { it.slot }).isEqualTo(MealSlot.MEALS)
    }

    @Test
    fun `meal rows carry their own foods and kcal`() {
        val rows = nutritionSlotRows(day).associateBy { it.slot }
        assertThat(rows.getValue(MealSlot.BREAKFAST).foods).containsExactly("Oats")
        assertThat(rows.getValue(MealSlot.BREAKFAST).kcal).isEqualTo(300)
        assertThat(rows.getValue(MealSlot.LUNCH).logged).isFalse()
    }

    @Test
    fun `drink kcal and sugar still count in the day totals`() {
        // The overview card's totals are built from every row, as NutritionHomeViewModel does.
        val totals = DayNutrition.aggregate(day.map { DayEntry(Macros(it.kcal, it.protein, it.carbs, it.fat, it.fiber, it.sugar, it.sodium, it.saturatedFat)) })
        assertThat(totals.kcal).isWithin(1e-9).of(300.0 + 400.0 + 150.0 + 5.0)
        assertThat(totals.sugar.total).isWithin(1e-9).of(35.0)
    }
}
