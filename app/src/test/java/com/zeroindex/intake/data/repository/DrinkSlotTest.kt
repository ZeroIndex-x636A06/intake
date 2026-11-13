package com.zeroindex.intake.data.repository

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.domain.model.MealSlot
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Round 4, item 7: a drink logged from Drinks, Today's water card or the watch belongs to no meal. It used to take the meal
 * of the time of day (`TemplateLink.slotFor(clock)`), so a 12:30 coffee from the Drinks page landed in Lunch.
 */
class DrinkSlotTest {

    private class FakeStore : DrinkLogStore {
        val foodLogs = mutableMapOf<String, FoodLogEntity>()
        override suspend fun saveHydration(log: HydrationLogEntity) = Unit
        override suspend fun saveFoodLog(log: FoodLogEntity) { foodLogs[log.id] = log }
        override suspend fun hydrationById(id: String): HydrationLogEntity? = null
        override suspend fun foodLogById(id: String) = foodLogs[id]
        override suspend fun incrementTimesLogged(foodId: String, now: Long) = Unit
        override suspend fun <T> transaction(block: suspend () -> T): T = block()
    }

    private val cola = DrinkSeed.foods().first { it.name == "Cola" }

    private fun log(slot: MealSlot): FoodLogEntity {
        val store = FakeStore()
        var n = 0
        val logged = runBlocking { DrinkLogger(store) { "id-${n++}" }.log(cola, DrinkSpec.of(cola), 355.0, 1_000L, slot, 2_000L) }
        return logged.foodLog!!
    }

    @Test
    fun `a drink with no meal keeps its calories and sugar and is stored as Drinks`() {
        val row = log(MealSlot.DRINKS)
        assertThat(row.mealSlot).isEqualTo(MealSlot.DRINKS)
        assertThat(row.kcal).isWithin(1e-6).of(42.0 * 3.55)
        assertThat(row.sugar).isWithin(1e-6).of(10.6 * 3.55)
    }

    @Test
    fun `a drink logged from a meal stays in that meal`() {
        assertThat(log(MealSlot.DINNER).mealSlot).isEqualTo(MealSlot.DINNER)
        assertThat(log(MealSlot.BREAKFAST).mealSlot).isEqualTo(MealSlot.BREAKFAST)
    }

    @Test
    fun `Drinks is a slot of its own, stored by name, and not one of the meals`() {
        assertThat(MealSlot.MEALS).containsExactly(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK).inOrder()
        assertThat(MealSlot.MEALS).doesNotContain(MealSlot.DRINKS)
        // Rows already stored keep their meal name; the new value is only ever added.
        assertThat(MealSlot.valueOf("LUNCH")).isEqualTo(MealSlot.LUNCH)
        assertThat(MealSlot.valueOf("DRINKS")).isEqualTo(MealSlot.DRINKS)
    }

    @Test
    fun `the drink repository no longer derives a meal from the clock`() {
        val source = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
            .resolve("com/zeroindex/intake/data/repository/DrinkRepository.kt").readText()
        assertThat(source).doesNotContain("TemplateLink")
        assertThat(source).doesNotContain("slotAt")
        assertThat(source).contains("MealSlot.DRINKS")
    }
}
