package com.zeroindex.intake.data.repository

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.domain.hydration.DrinkOverride
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.model.MealSlot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DrinkLoggerTest {

    private class FakeStore : DrinkLogStore {
        val hydration = mutableMapOf<String, HydrationLogEntity>()
        val foodLogs = mutableMapOf<String, FoodLogEntity>()
        val timesLogged = mutableListOf<String>()
        var transactions = 0
        override suspend fun saveHydration(log: HydrationLogEntity) { hydration[log.id] = log }
        override suspend fun saveFoodLog(log: FoodLogEntity) { foodLogs[log.id] = log }
        override suspend fun hydrationById(id: String) = hydration[id]
        override suspend fun foodLogById(id: String) = foodLogs[id]
        override suspend fun incrementTimesLogged(foodId: String, now: Long) { timesLogged += foodId }
        override suspend fun <T> transaction(block: suspend () -> T): T { transactions++; return block() }
    }

    private var next = 0
    private val store = FakeStore()
    private val logger = DrinkLogger(store, idFactory = { "id-${next++}" })

    private fun drink(name: String): FoodEntity = DrinkSeed.foods().first { it.name == name }

    private fun log(food: FoodEntity?, ml: Double, override: DrinkOverride? = null) = runBlocking {
        val spec = food?.let { DrinkSpec.of(it) } ?: DrinkSpec(DrinkType.CUSTOM)
        logger.log(food, spec, ml, at = 1_000L, slot = MealSlot.SNACK, now = 2_000L, override = override)
    }

    @Test
    fun `a cola writes a hydration row and a food-log row so sugar and calories count`() {
        val cola = drink("Cola")
        val result = log(cola, 355.0)
        assertThat(store.hydration).hasSize(1)
        assertThat(store.foodLogs).hasSize(1)
        val food = result.foodLog!!
        assertThat(result.hydration.foodLogId).isEqualTo(food.id)
        assertThat(food.kcal).isWithin(1e-6).of(42.0 * 3.55)
        assertThat(food.sugar).isWithin(1e-6).of(10.6 * 3.55)
        assertThat(food.quantityGrams).isEqualTo(355.0)
        assertThat(result.hydration.volumeMl).isEqualTo(355.0)
        assertThat(result.hydration.drinkType).isEqualTo(DrinkType.SODA)
        assertThat(result.hydration.caffeineMg).isWithin(1e-6).of(9.6 * 3.55)
        assertThat(store.timesLogged).containsExactly(cola.id)
        assertThat(store.transactions).isEqualTo(1)
    }

    @Test
    fun `water writes the hydration row only`() {
        val result = log(drink("Water"), 500.0)
        assertThat(store.hydration).hasSize(1)
        assertThat(store.foodLogs).isEmpty()
        assertThat(result.foodLog).isNull()
        assertThat(result.hydration.foodLogId).isNull()
        assertThat(result.hydration.effectiveMl).isEqualTo(500.0)
    }

    @Test
    fun `a drink with no food is a hydration-only entry`() {
        val result = log(null, 250.0)
        assertThat(result.foodLog).isNull()
        assertThat(store.foodLogs).isEmpty()
        assertThat(store.hydration).hasSize(1)
    }

    @Test
    fun `deleting a drink soft-deletes both rows and nothing is removed`() {
        val result = log(drink("Cola"), 330.0)
        runBlocking { logger.delete(result.hydration.id, now = 9_000L) }
        assertThat(store.hydration).hasSize(1)
        assertThat(store.foodLogs).hasSize(1)
        assertThat(store.hydration.getValue(result.hydration.id).deletedAt).isEqualTo(9_000L)
        assertThat(store.foodLogs.getValue(result.foodLog!!.id).deletedAt).isEqualTo(9_000L)
    }

    @Test
    fun `deleting twice keeps the first deletion time and undo brings both back`() {
        val result = log(drink("Cola"), 330.0)
        runBlocking {
            logger.delete(result.hydration.id, now = 9_000L)
            logger.delete(result.hydration.id, now = 9_500L)
        }
        assertThat(store.hydration.getValue(result.hydration.id).deletedAt).isEqualTo(9_000L)
        runBlocking { logger.restore(result.hydration.id, now = 10_000L) }
        assertThat(store.hydration.getValue(result.hydration.id).deletedAt).isNull()
        assertThat(store.foodLogs.getValue(result.foodLog!!.id).deletedAt).isNull()
    }

    @Test
    fun `deleting an unknown id does nothing`() {
        runBlocking { logger.delete("missing", now = 1L) }
        assertThat(store.hydration).isEmpty()
    }

    @Test
    fun `beer carries its alcohol and a negative hydration is not invented for it`() {
        val result = log(drink("Beer"), 355.0)
        assertThat(result.hydration.drinkType).isEqualTo(DrinkType.BEER)
        assertThat(result.hydration.alcoholMl).isWithin(1e-6).of(355.0 * 0.05)
        assertThat(result.hydration.hydrationFactor).isEqualTo(0.9)
        assertThat(result.foodLog).isNotNull()
    }

    @Test
    fun `the person's own override wins over the drink and the type default`() {
        val result = log(drink("Oat milk, unsweetened"), 250.0, override = DrinkOverride(factor = 1.2))
        assertThat(result.hydration.hydrationFactor).isEqualTo(1.2)
        // Without an override the seeded oat milk counts 1.0, not milk's 1.5.
        val plain = log(drink("Oat milk, unsweetened"), 250.0)
        assertThat(plain.hydration.hydrationFactor).isEqualTo(1.0)
    }

    @Test
    fun `a volume of zero is refused`() {
        var failed = false
        try { log(drink("Cola"), 0.0) } catch (e: IllegalArgumentException) { failed = true }
        assertThat(failed).isTrue()
        assertThat(store.hydration).isEmpty()
    }

    @Test
    fun `the seeded drinks cover the brief and keep stable ids`() {
        val names = DrinkSeed.SPECS.map { it.name }
        assertThat(names).containsAtLeast(
            "Water", "Sparkling water", "Coffee, black", "Latte (made with milk)", "Black tea", "Green tea", "Cola", "Diet cola",
            "Lemon-lime soda", "Orange juice", "Apple juice", "Sports drink", "Energy drink", "Milk, whole", "Oat milk, unsweetened",
            "Almond milk, unsweetened", "Beer", "Wine", "Spirits (40%)",
        )
        assertThat(DrinkSeed.foods().map { it.id }.toSet()).hasSize(DrinkSeed.SPECS.size)
        assertThat(DrinkSeed.foods().map { it.id }).isEqualTo(DrinkSeed.foods().map { it.id })
        assertThat(DrinkSeed.foods().all { it.drinkCategory != null }).isTrue()
        // The latte and the milks carry milk, so the allergen engine sees it.
        assertThat(drink("Latte (made with milk)").allergenTags).contains("en:milk")
    }
}
