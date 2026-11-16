package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.TdeeEstimateDao
import com.zeroindex.intake.data.db.entity.TdeeEstimateEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.tdee.IntakeDay
import com.zeroindex.intake.domain.tdee.TdeeCalculator
import com.zeroindex.intake.domain.tdee.TdeeResult
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class TdeeRepository @Inject constructor(
    private val dao: TdeeEstimateDao,
    private val bodyRepository: BodyRepository,
    private val foodLogRepository: FoodLogRepository,
    private val clock: Clock,
) {
    fun observeLatest(): Flow<TdeeEstimateEntity?> = dao.observeLatest()

    suspend fun latest(): TdeeEstimateEntity? = dao.latest()

    /**
     * SPEC §8: recalculate over a 28-day rolling window. Returns null when the
     * data is too sparse to mean anything, and nothing is written in that case -
     * the UI says so plainly rather than showing a confident number.
     */
    suspend fun recalculate(): TdeeResult? {
        val today = clock.today()
        val from = today.minusDays((TdeeCalculator.WINDOW_DAYS - 1).toLong())

        val weights = bodyRepository.weightPointsSince(from)
        val intake = foodLogRepository.dailyIntake(from, today).mapNotNull { day ->
            val kcal = day.kcal ?: return@mapNotNull null
            IntakeDay(
                dayIndex = localDateOf(day.dayStartMillis, clock.zone()).toEpochDay(),
                kcal = kcal,
            )
        }

        val result = TdeeCalculator.estimate(weights, intake) ?: return null

        val now = clock.nowMillis()
        val dayStart = clock.rangeFor(today).startInclusive
        val existing = dao.forDay(dayStart)
        dao.upsert(
            TdeeEstimateEntity(
                id = existing?.id ?: newId(),
                calculatedFor = dayStart,
                estimatedTdee = result.estimatedTdee,
                confidence = result.confidence,
                rollingIntakeAvg = result.rollingIntakeAvg,
                weightTrendSlope = result.weightTrendSlopeKgPerDay,
                daysOfData = result.daysOfData,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return result
    }
}

data class TargetShift(
    val previousKcal: Int?,
    val newKcal: Int,
    val notify: Boolean,
    val confidence: com.zeroindex.intake.domain.model.TdeeConfidence,
)
