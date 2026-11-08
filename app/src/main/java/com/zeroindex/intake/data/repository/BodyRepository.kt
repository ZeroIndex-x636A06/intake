package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.tdee.TrendPoint
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BodyRepository @Inject constructor(
    private val dao: BodyMetricDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<BodyMetricEntity>> = dao.observeAll()
    fun observeLatest(): Flow<BodyMetricEntity?> = dao.observeLatest()
    fun observeWithPhotos(): Flow<List<BodyMetricEntity>> = dao.observeWithPhotos()

    suspend fun latest(): BodyMetricEntity? = dao.latest()
    suspend fun all(): List<BodyMetricEntity> = dao.all()

    fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<BodyMetricEntity>> =
        dao.observeInRange(clock.rangeFor(from).startInclusive, clock.rangeFor(to).endExclusive)

    /** SPEC §8: the trend line, not the raw weigh-ins, is what gets displayed. */
    fun observeTrend(from: LocalDate, to: LocalDate): Flow<List<TrendPoint>> =
        observeInRange(from, to).map { metrics -> WeightTrend.smooth(metrics.toWeightPoints()) }

    suspend fun weightPointsSince(from: LocalDate): List<WeightPoint> =
        dao.inRange(clock.rangeFor(from).startInclusive, clock.nowMillis() + 1).toWeightPoints()

    suspend fun save(
        weightKg: Double,
        measuredAt: Long = clock.nowMillis(),
        bodyFatPercent: Double? = null,
        waistCm: Double? = null,
        chestCm: Double? = null,
        armCm: Double? = null,
        thighCm: Double? = null,
        hipCm: Double? = null,
        notes: String? = null,
        photoUri: String? = null,
    ): BodyMetricEntity {
        val now = clock.nowMillis()
        val metric = BodyMetricEntity(
            id = newId(),
            measuredAt = measuredAt,
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            waistCm = waistCm,
            chestCm = chestCm,
            armCm = armCm,
            thighCm = thighCm,
            hipCm = hipCm,
            notes = notes,
            photoUri = photoUri,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(metric)
        return metric
    }

    suspend fun saveAll(metrics: List<BodyMetricEntity>) = dao.upsertAll(metrics)

    suspend fun softDelete(id: String) = dao.softDelete(id, clock.nowMillis())

    private fun List<BodyMetricEntity>.toWeightPoints(): List<WeightPoint> = map { metric ->
        WeightPoint(
            dayIndex = localDateOf(metric.measuredAt, clock.zone()).toEpochDay(),
            weightKg = metric.weightKg,
        )
    }
}
