package com.zeroindex.intake.data.repository

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.domain.drinks.AddInCatalog
import com.zeroindex.intake.domain.drinks.AddInUnit
import com.zeroindex.intake.domain.drinks.CustomDrinkRecipe
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.model.MealSlot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CustomDrinkFoodTest {

    private val coffee = DrinkSeed.foods().first { it.name == "Coffee, black" }
    private val oatMilk = DrinkSeed.foods().first { it.name == "Oat milk, unsweetened" }
    private fun sugar(tsp: Double) = AddInCatalog.byKey("sugar")!!.toAddIn(tsp, AddInUnit.TSP)

    private val threeSugars = CustomDrinkDraft(coffee, 240.0, listOf(sugar(3.0)))

    @Test
    fun `the saved drink is stored per 100 ml from the base at its volume plus the add-ins`() {
        val saved = CustomDrinkFood.build(threeSugars, "", id = "mine-1", now = 5L)
        assertThat(saved.name).isEqualTo("Coffee, black + 3 tsp sugar")
        assertThat(saved.servingSizeGrams).isEqualTo(240.0)
        // 240 ml holds about 48.8 kcal and 12 g sugar: per 100 ml that is a 100/240 share.
        assertThat(saved.kcalPer100g * 2.4).isWithin(1.5).of(48.0)
        assertThat(saved.sugarPer100g).isWithin(0.01).of(5.0)
        assertThat(saved.drinkCategory).isEqualTo(coffee.drinkCategory)
        assertThat(saved.customDrink).isNotNull()
        assertThat(saved.deletedAt).isNull()
    }

    @Test
    fun `the hydration factor and caffeine come from the base drink`() {
        val saved = CustomDrinkFood.build(threeSugars, "My coffee", "mine-1", 5L)
        assertThat(saved.hydrationFactor).isEqualTo(CustomDrinkFood.baseFactor(coffee))
        // 40 mg per 100 ml, no milk to dilute it.
        assertThat(saved.caffeineMgPer100ml).isWithin(1e-9).of(40.0)
        // A base whose own factor is set (oat milk counts 1.0) keeps it.
        val oat = CustomDrinkFood.build(CustomDrinkDraft(oatMilk, 240.0, listOf(sugar(1.0))), "", "mine-2", 5L)
        assertThat(oat.hydrationFactor).isEqualTo(1.0)
    }

    @Test
    fun `the recipe is stored so the drink can be edited`() {
        val saved = CustomDrinkFood.build(threeSugars, "x", "mine-1", 5L)
        val recipe = CustomDrinkRecipe.decode(saved.customDrink)!!
        assertThat(recipe.baseFoodId).isEqualTo(coffee.id)
        assertThat(recipe.baseVolumeMl).isEqualTo(240.0)
        assertThat(recipe.addIns).hasSize(1)
    }

    @Test
    fun `editing keeps the drink's identity, creation time and use count`() {
        val first = CustomDrinkFood.build(threeSugars, "My coffee", "mine-1", 5L).copy(timesLogged = 7, isFavorite = true)
        val edited = CustomDrinkFood.build(threeSugars.copy(addIns = listOf(sugar(2.0))), "My coffee", "mine-1", 99L, existing = first)
        assertThat(edited.id).isEqualTo("mine-1")
        assertThat(edited.createdAt).isEqualTo(5L)
        assertThat(edited.updatedAt).isEqualTo(99L)
        assertThat(edited.timesLogged).isEqualTo(7)
        assertThat(edited.isFavorite).isTrue()
        assertThat(edited.sugarPer100g).isLessThan(first.sugarPer100g)
    }

    @Test
    fun `a dairy add-in puts the dairy tag on the saved drink and the base's tags stay`() {
        val milk = AddInCatalog.byKey("milk-whole")!!.toAddIn(30.0, AddInUnit.ML)
        val saved = CustomDrinkFood.build(CustomDrinkDraft(coffee, 240.0, listOf(milk)), "Cup", "mine-1", 5L)
        assertThat(saved.allergenTags).contains("en:milk")
        val latte = DrinkSeed.foods().first { it.name.startsWith("Latte") }
        val again = CustomDrinkFood.build(CustomDrinkDraft(latte, 355.0, listOf(sugar(1.0))), "Cup", "mine-2", 5L)
        assertThat(again.allergenTags).contains("en:milk")
    }

    // ---- logging once: the same logger, no meal ----

    private class FakeStore : DrinkLogStore {
        val hydration = mutableMapOf<String, HydrationLogEntity>()
        val foodLogs = mutableMapOf<String, FoodLogEntity>()
        override suspend fun saveHydration(log: HydrationLogEntity) { hydration[log.id] = log }
        override suspend fun saveFoodLog(log: FoodLogEntity) { foodLogs[log.id] = log }
        override suspend fun hydrationById(id: String) = hydration[id]
        override suspend fun foodLogById(id: String) = foodLogs[id]
        override suspend fun incrementTimesLogged(foodId: String, now: Long) = Unit
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    @Test
    fun `a one-off custom log counts the glass for water and its calories and sugar for the day, in no meal`() {
        val store = FakeStore()
        var n = 0
        val logger = DrinkLogger(store) { "id-${n++}" }
        val food = CustomDrinkFood.oneOff(threeSugars, now = 1L)
        val spec = DrinkSpec(CustomDrinkFood.typeOf(coffee), food.hydrationFactor, food.caffeineMgPer100ml, food.abvPercent ?: 0.0)
        val volume = CustomDrinkFood.result(threeSugars).totalVolumeMl
        val logged = runBlocking { logger.log(food, spec, volume, at = 10L, slot = MealSlot.DRINKS, now = 10L) }
        val row = logged.foodLog!!
        assertThat(row.mealSlot).isEqualTo(MealSlot.DRINKS)
        assertThat(row.foodId).isEqualTo(coffee.id) // rides on the base drink's row; no My drink is created
        assertThat(row.foodNameAtLog).isEqualTo("Coffee, black + 3 tsp sugar")
        assertThat(row.kcal).isWithin(1.5).of(48.0)
        assertThat(row.sugar).isWithin(0.1).of(12.0)
        assertThat(logged.hydration.volumeMl).isEqualTo(240.0)
        assertThat(logged.hydration.hydrationFactor).isEqualTo(1.0)
        assertThat(logged.hydration.drinkType).isEqualTo(DrinkType.COFFEE)
        assertThat(logged.hydration.caffeineMg).isWithin(1e-6).of(96.0)
    }
}
