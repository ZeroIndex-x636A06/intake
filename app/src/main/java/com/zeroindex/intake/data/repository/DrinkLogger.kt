package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkKinds
import com.zeroindex.intake.domain.hydration.DrinkEntry
import com.zeroindex.intake.domain.hydration.DrinkOverride
import com.zeroindex.intake.domain.hydration.DrinkProfile
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import kotlin.math.roundToInt

/** What a drink log needs to know about the drink. A null number means "use the drink type's default". */
data class DrinkSpec(
    val type: DrinkType,
    val hydrationFactor: Double? = null,
    val caffeineMgPer100ml: Double? = null,
    val abvPercent: Double? = null,
) {
    companion object {
        /** A drink food's own numbers, with its hydration type read from its category and name. */
        fun of(food: FoodEntity): DrinkSpec {
            val category = DrinkCategory.fromStored(food.drinkCategory) ?: DrinkCategory.OTHER
            return DrinkSpec(DrinkKinds.typeOf(category, food.name, food.abvPercent), food.hydrationFactor, food.caffeineMgPer100ml, food.abvPercent)
        }
    }
}

/** The result of one drink log: the hydration row, and the food-log row when the drink has any nutrition. */
data class DrinkLogged(val hydration: HydrationLogEntity, val foodLog: FoodLogEntity?)

/**
 * Where the logger keeps its rows. The repository backs it with the DAOs inside one Room transaction; the unit test backs
 * it with maps, so the "one drink is two rows, and deleting removes both" rule is checked without a database.
 */
interface DrinkLogStore {
    suspend fun saveHydration(log: HydrationLogEntity)
    suspend fun saveFoodLog(log: FoodLogEntity)
    suspend fun hydrationById(id: String): HydrationLogEntity?
    suspend fun foodLogById(id: String): FoodLogEntity?
    suspend fun incrementTimesLogged(foodId: String, now: Long)
    suspend fun <T> transaction(block: suspend () -> T): T
}

/**
 * The ONE drink flow. Logging a drink writes a hydration row (volume times factor, caffeine, alcohol: what counts toward
 * the day's water) and, when the drink has any calories or macros, a food-log row (so sugar and calories count too). The
 * hydration row keeps the food row's id in `foodLogId`. Removing either soft-deletes both, and Undo brings both back.
 * Plain water has no nutrition, so it writes the hydration row only.
 */
class DrinkLogger(
    private val store: DrinkLogStore,
    private val idFactory: () -> String = ::newId,
) {
    /**
     * @param food the drink's food row, or null for a hydration-only entry (a custom drink with no food).
     * @param spec the numbers to use; defaults to the food's own.
     */
    suspend fun log(
        food: FoodEntity?,
        spec: DrinkSpec,
        volumeMl: Double,
        at: Long,
        slot: MealSlot,
        now: Long,
        presetId: String? = null,
        override: DrinkOverride? = null,
    ): DrinkLogged {
        require(volumeMl > 0) { "volume must be positive" }
        val entry = entryFor(spec, override, volumeMl)
        return store.transaction {
            val foodLog = food?.takeIf { hasNutrition(it) }?.let { f ->
                val macros = Macros.fromPer100g(
                    grams = volumeMl, // a millilitre of drink is counted as a gram
                    kcal = f.kcalPer100g, protein = f.proteinPer100g, carbs = f.carbsPer100g, fat = f.fatPer100g,
                    fiber = f.fiberPer100g, sugar = f.sugarPer100g, sodium = f.sodiumPer100g, saturatedFat = f.saturatedFatPer100g,
                )
                FoodLogEntity(
                    id = idFactory(),
                    foodId = f.id,
                    loggedAt = at,
                    mealSlot = slot,
                    quantityGrams = volumeMl,
                    foodNameAtLog = f.name,
                    servingLabelAtLog = "${volumeMl.roundToInt()} ml",
                    kcal = macros.kcal, protein = macros.protein, carbs = macros.carbs, fat = macros.fat,
                    fiber = macros.fiber, sugar = macros.sugar, sodium = macros.sodium, saturatedFat = macros.saturatedFat,
                    unlistedNutrients = f.unlistedNutrients,
                    createdAt = now,
                    updatedAt = now,
                ).also { store.saveFoodLog(it) }
            }
            val hydration = HydrationLogEntity(
                id = idFactory(),
                loggedAt = at,
                volumeMl = volumeMl,
                presetId = presetId,
                drinkType = spec.type,
                hydrationFactor = entry.factor,
                caffeineMg = entry.caffeineMg,
                alcoholMl = entry.alcoholMl,
                foodLogId = foodLog?.id,
                createdAt = now,
                updatedAt = now,
            ).also { store.saveHydration(it) }
            if (food != null && foodLog != null) store.incrementTimesLogged(food.id, now)
            DrinkLogged(hydration, foodLog)
        }
    }

    /** Soft-deletes the drink: its hydration row and, if it has one, its food-log row. Safe to call twice. */
    suspend fun delete(hydrationId: String, now: Long) {
        store.transaction {
            val h = store.hydrationById(hydrationId) ?: return@transaction
            store.saveHydration(h.copy(deletedAt = h.deletedAt ?: now, updatedAt = now))
            h.foodLogId?.let { id ->
                store.foodLogById(id)?.let { f -> store.saveFoodLog(f.copy(deletedAt = f.deletedAt ?: now, updatedAt = now)) }
            }
        }
    }

    /** Undo: brings the drink and its food row back. */
    suspend fun restore(hydrationId: String, now: Long) {
        store.transaction {
            val h = store.hydrationById(hydrationId) ?: return@transaction
            store.saveHydration(h.copy(deletedAt = null, updatedAt = now))
            h.foodLogId?.let { id ->
                store.foodLogById(id)?.let { f -> store.saveFoodLog(f.copy(deletedAt = null, updatedAt = now)) }
            }
        }
    }

    companion object {
        /**
         * The user's own edit (Settings > Water and drinks > Advanced) wins, then the drink's number, then the type's
         * default. So a seeded oat milk counts 1.0 per ml where milk counts 1.5, unless the person says otherwise.
         */
        fun entryFor(spec: DrinkSpec, override: DrinkOverride?, volumeMl: Double): DrinkEntry {
            val abv = (override?.abvPercent ?: spec.abvPercent ?: spec.type.abvPercent).coerceIn(0.0, 100.0)
            val factor = (override?.factor ?: spec.hydrationFactor ?: Drinks.defaultFactor(spec.type, abv)).coerceIn(-1.0, 3.0)
            val caffeinePer250 = (override?.caffeineMgPer250 ?: spec.caffeineMgPer100ml?.times(2.5) ?: spec.type.caffeineMgPer250).coerceIn(0.0, 1_000.0)
            return Drinks.entry(volumeMl, DrinkProfile(spec.type, factor, caffeinePer250, abv))
        }

        /** A drink with no calories, macros or sodium (water) writes no food row. */
        fun hasNutrition(f: FoodEntity): Boolean =
            f.kcalPer100g > 0.0 || f.proteinPer100g > 0.0 || f.carbsPer100g > 0.0 || f.fatPer100g > 0.0 ||
                f.sugarPer100g > 0.0 || f.sodiumPer100g > 0.0
    }
}
