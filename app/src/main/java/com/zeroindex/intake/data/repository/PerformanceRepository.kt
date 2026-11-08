package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.PerformanceDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.PerformanceMetricEntity
import com.zeroindex.intake.domain.performance.JumpMetrics
import com.zeroindex.intake.domain.performance.JumpPoint
import com.zeroindex.intake.domain.performance.OverlayWeek
import com.zeroindex.intake.domain.performance.PerformanceOverlay
import com.zeroindex.intake.domain.performance.VolumeSample
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class PerformanceRepository @Inject constructor(
    private val dao: PerformanceDao,
    private val workoutDao: WorkoutDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<PerformanceMetricEntity>> = dao.observeAll()

    /**
     * Saves what was measured. The derived third value is stored too, so
     * charts and exports do not have to re-derive it, but the entry keeps which
     * fields were measured implicitly: re-saving with a correction re-derives.
     */
    suspend fun save(
        verticalJumpCm: Double?,
        standingReachCm: Double?,
        totalReachCm: Double?,
        measuredAt: Long = clock.nowMillis(),
        notes: String? = null,
    ): PerformanceMetricEntity {
        val complete = JumpMetrics.complete(verticalJumpCm, standingReachCm, totalReachCm)
        require(complete.verticalJumpCm != null || complete.totalReachCm != null) {
            "Enter a vertical jump, or a standing and total reach"
        }
        val now = clock.nowMillis()
        val metric = PerformanceMetricEntity(
            measuredAt = measuredAt,
            verticalJumpCm = complete.verticalJumpCm,
            standingReachCm = complete.standingReachCm,
            totalReachCm = complete.totalReachCm,
            notes = notes,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(metric)
        return metric
    }

    suspend fun delete(id: String) = dao.softDelete(id, clock.nowMillis())

    fun observeOverlay(from: LocalDate, to: LocalDate): Flow<List<OverlayWeek>> {
        val start = clock.rangeFor(from).startInclusive
        val end = clock.rangeFor(to).endExclusive
        return combine(dao.observeAll(), workoutDao.observeWorkingSetsInRange(start, end)) { metrics, sets ->
            PerformanceOverlay.build(
                jumps = metrics
                    .filter { it.measuredAt in start until end }
                    .mapNotNull { m -> m.verticalJumpCm?.let { JumpPoint(m.measuredAt, it) } },
                sets = sets.map { VolumeSample(it.completedAt, it.weightKg, it.reps) },
                zone = clock.zone(),
            )
        }
    }
}
