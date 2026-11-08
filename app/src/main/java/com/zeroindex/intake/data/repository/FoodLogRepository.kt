package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.db.projection.DailyIntake
import com.zeroindex.intake.data.db.projection.SlotTotals
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FoodLogRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val foodDao: FoodDao,
    private val clock: Clock,
) {
    fun observeDay(date: LocalDate): Flow<List<FoodLogEntity>> {
        val range = clock.rangeFor(date)
        return foodLogDao.observeInRange(range.startInclusive, range.endExclusive)
    }

    fun observeDayTotals(date: LocalDate): Flow<Macros> {
        val range = clock.rangeFor(date)
        return foodLogDao.observeTotalsInRange(range.startInclusive, range.endExclusive).map {
            Macros(
                kcal = it.kcalOrZero,
                protein = it.proteinOrZero,
                carbs = it.carbsOrZero,
                fat = it.fatOrZero,
            )
        }
    }

    fun observeSlotTotals(date: LocalDate): Flow<List<SlotTotals>> {
        val range = clock.rangeFor(date)
        return foodLogDao.observeSlotTotalsInRange(range.startInclusive, range.endExclusive)
    }

    fun observeDailyIntake(from: LocalDate, to: LocalDate): Flow<List<DailyIntake>> =
        foodLogDao.observeDailyIntake(
            clock.rangeFor(from).startInclusive,
            clock.rangeFor(to).endExclusive,
            zoneOffsetMillis(from),
        )

    suspend fun dailyIntake(from: LocalDate, to: LocalDate): List<DailyIntake> =
        foodLogDao.dailyIntake(
            clock.rangeFor(from).startInclusive,
            clock.rangeFor(to).endExclusive,
            zoneOffsetMillis(from),
        )

    suspend fun countDaysLogged(from: LocalDate, to: LocalDate): Int =
        foodLogDao.countDaysLogged(
            clock.rangeFor(from).startInclusive,
            clock.rangeFor(to).endExclusive,
            zoneOffsetMillis(from),
        )

    suspend fun findById(id: String): FoodLogEntity? = foodLogDao.findById(id)

    /** Logs [grams] of [food], copying its macros so later edits leave history alone. */
    suspend fun logFood(
        food: FoodEntity,
        grams: Double,
        slot: MealSlot,
        at: Long = clock.nowMillis(),
    ): FoodLogEntity {
        val macros = Macros.fromPer100g(
            grams = grams,
            kcal = food.kcalPer100g,
            protein = food.proteinPer100g,
            carbs = food.carbsPer100g,
            fat = food.fatPer100g,
            fiber = food.fiberPer100g,
            sugar = food.sugarPer100g,
            sodium = food.sodiumPer100g,
            saturatedFat = food.saturatedFatPer100g,
        )
        val log = newLog(
            foodId = food.id,
            name = food.name,
            servingLabel = food.servingLabel,
            grams = grams,
            slot = slot,
            macros = macros,
            at = at,
        )
        foodLogDao.upsert(log)
        foodDao.incrementTimesLogged(food.id, clock.nowMillis())
        return log
    }

    /** SPEC §5.4: quick add writes raw macros with no food record behind it. */
    suspend fun quickAdd(
        name: String,
        macros: Macros,
        slot: MealSlot,
        at: Long = clock.nowMillis(),
    ): FoodLogEntity {
        val log = newLog(
            foodId = null,
            name = name.ifBlank { "Quick add" },
            servingLabel = null,
            grams = null,
            slot = slot,
            macros = macros,
            at = at,
        )
        foodLogDao.upsert(log)
        return log
    }

    /** Used by recipes, meal templates and eat-out estimates, which log as one row. */
    suspend fun logPrepared(
        name: String,
        macros: Macros,
        slot: MealSlot,
        servingLabel: String? = null,
        photoUri: String? = null,
        aiConfidence: AiConfidence? = null,
        at: Long = clock.nowMillis(),
    ): FoodLogEntity {
        val log = newLog(
            foodId = null,
            name = name,
            servingLabel = servingLabel,
            grams = null,
            slot = slot,
            macros = macros,
            at = at,
            photoUri = photoUri,
            aiConfidence = aiConfidence,
        )
        foodLogDao.upsert(log)
        return log
    }

    /** Rescales an entry to a new gram amount, proportionally to what was logged. */
    suspend fun updateQuantity(log: FoodLogEntity, newGrams: Double) {
        val oldGrams = log.quantityGrams
        val factor = if (oldGrams != null && oldGrams > 0) newGrams / oldGrams else 1.0
        foodLogDao.upsert(
            log.copy(
                quantityGrams = newGrams,
                kcal = log.kcal * factor,
                protein = log.protein * factor,
                carbs = log.carbs * factor,
                fat = log.fat * factor,
                fiber = log.fiber * factor,
                sugar = log.sugar * factor,
                sodium = log.sodium * factor,
                saturatedFat = log.saturatedFat * factor,
                wasEdited = true,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun update(log: FoodLogEntity) {
        foodLogDao.upsert(log.copy(updatedAt = clock.nowMillis(), wasEdited = true))
    }

    /** SPEC §5: swipe to copy an entry into another slot. */
    suspend fun copyToSlot(log: FoodLogEntity, slot: MealSlot) {
        val now = clock.nowMillis()
        foodLogDao.upsert(log.copy(id = newId(), mealSlot = slot, createdAt = now, updatedAt = now))
    }

    suspend fun moveToSlot(log: FoodLogEntity, slot: MealSlot) {
        foodLogDao.upsert(log.copy(mealSlot = slot, updatedAt = clock.nowMillis()))
    }

    suspend fun softDelete(id: String) = foodLogDao.softDelete(id, clock.nowMillis())

    private fun newLog(
        foodId: String?,
        name: String,
        servingLabel: String?,
        grams: Double?,
        slot: MealSlot,
        macros: Macros,
        at: Long,
        photoUri: String? = null,
        aiConfidence: AiConfidence? = null,
    ) = FoodLogEntity(
        id = newId(),
        foodId = foodId,
        loggedAt = at,
        mealSlot = slot,
        quantityGrams = grams,
        foodNameAtLog = name,
        servingLabelAtLog = servingLabel,
        kcal = macros.kcal,
        protein = macros.protein,
        carbs = macros.carbs,
        fat = macros.fat,
        fiber = macros.fiber,
        sugar = macros.sugar,
        sodium = macros.sodium,
        saturatedFat = macros.saturatedFat,
        sourcePhotoUri = photoUri,
        aiConfidence = aiConfidence,
        createdAt = at,
        updatedAt = at,
    )

    /**
     * SQL buckets days by integer division, which needs the zone's offset. Taken
     * from the start of the window: a DST shift inside a long range moves one
     * boundary by an hour, which the charts tolerate and the per-day queries
     * (which use DayRange) do not rely on.
     */
    private fun zoneOffsetMillis(on: LocalDate): Long =
        clock.zone().rules.getOffset(on.atStartOfDay()).totalSeconds * 1000L
}
