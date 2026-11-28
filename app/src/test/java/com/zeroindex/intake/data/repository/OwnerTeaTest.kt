package com.zeroindex.intake.data.repository

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.domain.drinks.AddInCatalog
import com.zeroindex.intake.domain.drinks.AddInUnit
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.nutrition.Unlisted
import org.junit.Test

/** Round 5 item 13 end to end through the food row: the owner's tea made with Customise at 16 fl oz. */
class OwnerTeaTest {

    private val seededTea = DrinkSeed.foods().first { it.name == "Black tea" }

    /** A packaged "Hot Tea" whose numbers cannot be a brew's: 0.52 g protein and 3.9 kcal per 100 ml, sugars never listed. */
    private val packagedTea = seededTea.copy(
        id = "off-tea", name = "Hot Tea – English Breakfast", source = FoodSource.OPEN_FOOD_FACTS, isVerified = false,
        kcalPer100g = 3.9, proteinPer100g = 0.52, carbsPer100g = 0.0, sugarPer100g = 0.0, unlistedNutrients = "fiber,sugar,satfat", createdAt = 0, updatedAt = 0,
    )
    private val sugar = AddInCatalog.byKey("sugar")!!.toAddIn(3.0, AddInUnit.TSP)
    private val draft = CustomDrinkDraft(packagedTea, 473.0, listOf(sugar))

    @Test
    fun `the saved drink has no protein, its sugars listed, and the drink category kept`() {
        val saved = CustomDrinkFood.build(draft, "", id = "mine-1", now = 5L)
        assertThat(CustomDrinkFood.baseReplaced(packagedTea)).isTrue()
        assertThat(saved.proteinPer100g * 4.73).isLessThan(0.1)
        assertThat(saved.sugarPer100g * 4.73).isWithin(0.1).of(12.6)
        assertThat(saved.kcalPer100g * 4.73).isWithin(5.0).of(53.0)
        assertThat(Unlisted.decode(saved.unlistedNutrients)).doesNotContain(com.zeroindex.intake.domain.nutrition.NutrientKey.SUGAR)
        // The icon is chosen from this, so a saved tea shows a tea cup, never the cookie its "sugar" name would give.
        assertThat(saved.drinkCategory).isEqualTo(packagedTea.drinkCategory)
        assertThat(saved.drinkCategory).isEqualTo("TEA")
    }

    @Test
    fun `the seeded tea is not replaced and a drink saved before the fix would be rebuilt`() {
        assertThat(CustomDrinkFood.baseReplaced(seededTea)).isFalse()
        val before = CustomDrinkFood.build(CustomDrinkDraft(seededTea, 473.0, listOf(sugar)), "x", "mine-2", 5L)
        assertThat(before.proteinPer100g).isWithin(1e-9).of(0.0)
    }
}
