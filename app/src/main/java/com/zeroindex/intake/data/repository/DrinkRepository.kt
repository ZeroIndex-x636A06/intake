package com.zeroindex.intake.data.repository

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.seed.DrinkSeed
import com.zeroindex.intake.data.settings.HydrationPrefs
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Drinks in the food database, and the one flow that logs them. A drink is a [FoodEntity] with `drinkCategory` set;
 * logging one goes through [DrinkLogger] so a hydration row and a food-log row are written together and removed
 * together. [HydrationRepository] (Today's water card, quick sizes) calls the same code, so there is one flow.
 */
@Singleton
class DrinkRepository @Inject constructor(
    private val db: IntakeDatabase,
    private val foodDao: FoodDao,
    private val foodLogDao: FoodLogDao,
    private val hydrationDao: HydrationDao,
    private val hydrationPrefs: HydrationPrefs,
    private val clock: Clock,
) : DrinkLogStore {

    private val logger = DrinkLogger(this)

    /** Adds the generic drinks once. A seeded drink the person later removed is never brought back (deleted rows count). */
    suspend fun ensureSeeded() {
        val missing = DrinkSeed.foods().filter { foodDao.idCount(it.id) == 0 }
        if (missing.isNotEmpty()) foodDao.upsertAll(missing)
    }

    fun observeDrinks(category: DrinkCategory?): Flow<List<FoodEntity>> = foodDao.observeDrinks(category?.name)

    fun searchDrinks(query: String): Flow<List<FoodEntity>> = foodDao.searchDrinks(query.trim())

    /** One drink: the hydration row and, if it has nutrition, the food-log row. */
    suspend fun log(
        food: FoodEntity,
        volumeMl: Double,
        at: Long = clock.nowMillis(),
        slot: MealSlot = slotAt(at),
        presetId: String? = null,
    ): DrinkLogged {
        val spec = DrinkSpec.of(food)
        return logger.log(food, spec, volumeMl, at, slot, clock.nowMillis(), presetId, overrideFor(spec.type))
    }

    /**
     * Today's water card and the quick sizes know a drink type, not a food. They log the seeded drink of that type, so
     * "+ Tea" counts its calories too, with the type's own hydration numbers as before. A type with no seed (oral
     * rehydration, custom) is a hydration-only row.
     */
    suspend fun logOfType(type: DrinkType, volumeMl: Double, at: Long, presetId: String?): HydrationLogEntity {
        val food = DrinkSeed.nameForType(type)?.let { foodDao.findById(DrinkSeed.idFor(it)) }
        val spec = DrinkSpec(type)
        return logger.log(food, spec, volumeMl, at, slotAt(at), clock.nowMillis(), presetId, overrideFor(type)).hydration
    }

    suspend fun delete(hydrationId: String) = logger.delete(hydrationId, clock.nowMillis())

    suspend fun restore(hydrationId: String) = logger.restore(hydrationId, clock.nowMillis())

    private suspend fun overrideFor(type: DrinkType) = hydrationPrefs.current().drinkOverrides[type]

    private fun slotAt(at: Long): MealSlot =
        TemplateLink.slotFor(Instant.ofEpochMilli(at).atZone(clock.zone()).toLocalTime())

    // ---- DrinkLogStore ------------------------------------------------------------------------------------------

    override suspend fun saveHydration(log: HydrationLogEntity) = hydrationDao.upsertLog(log)
    override suspend fun saveFoodLog(log: FoodLogEntity) = foodLogDao.upsert(log)
    override suspend fun hydrationById(id: String): HydrationLogEntity? = hydrationDao.logById(id)
    override suspend fun foodLogById(id: String): FoodLogEntity? = foodLogDao.findAny(id)
    override suspend fun incrementTimesLogged(foodId: String, now: Long) = foodDao.incrementTimesLogged(foodId, now)
    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
}
