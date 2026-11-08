package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.hydration.HydrationTarget
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.VolumeUnit
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** One day of water: what was drunk, against what target, and why. */
data class HydrationDay(
    val logs: List<HydrationLogEntity>,
    val target: HydrationTarget.Target,
) {
    val consumedMl: Double get() = logs.sumOf { it.volumeMl }
    val progress: Float get() = if (target.totalMl <= 0) 0f else (consumedMl / target.totalMl).toFloat()
}

@Singleton
class HydrationRepository @Inject constructor(
    private val dao: HydrationDao,
    private val healthDao: HealthSummaryDao,
    private val bodyRepository: BodyRepository,
    private val trainingRepository: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    fun observePresets(): Flow<List<HydrationPresetEntity>> = dao.observePresets()

    fun observeDay(date: LocalDate): Flow<HydrationDay> {
        val range = clock.rangeFor(date)
        return combine(
            dao.observeLogsInRange(range.startInclusive, range.endExclusive),
            settingsRepository.settings,
            bodyRepository.observeLatest(),
            trainingRepository.observeSessions().map { sessions ->
                sessions.any { it.startedAt >= range.startInclusive && it.startedAt < range.endExclusive }
            },
            healthDao.observeForDay(range.startInclusive),
        ) { logs, settings, latest, trained, health ->
            HydrationDay(
                logs = logs,
                target = HydrationTarget.compute(
                    manualBaseMl = settings.hydrationBaseMl,
                    bodyweightKg = latest?.weightKg,
                    dynamic = settings.hydrationDynamic,
                    trainedToday = trained,
                    activeKcalNet = health?.activeKcalNet,
                    trainingDayBonusMl = settings.hydrationTrainingBonusMl,
                ),
            )
        }
    }

    suspend fun log(volumeMl: Double, loggedAt: Long, presetId: String? = null): HydrationLogEntity {
        require(volumeMl > 0) { "volume must be positive" }
        val now = clock.nowMillis()
        val log = HydrationLogEntity(
            loggedAt = loggedAt,
            volumeMl = volumeMl,
            presetId = presetId,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertLog(log)
        return log
    }

    suspend fun delete(id: String) = dao.softDeleteLog(id, clock.nowMillis())

    /** Brings a soft-deleted entry back, for the snackbar's Undo. */
    suspend fun restore(log: HydrationLogEntity) =
        dao.upsertLog(log.copy(deletedAt = null, updatedAt = clock.nowMillis()))

    suspend fun savePreset(name: String, volumeMl: Double, existing: HydrationPresetEntity? = null) {
        val now = clock.nowMillis()
        val order = existing?.displayOrder ?: (dao.presets().maxOfOrNull { it.displayOrder }?.plus(1) ?: 0)
        dao.upsertPreset(
            existing?.copy(name = name, volumeMl = volumeMl, updatedAt = now)
                ?: HydrationPresetEntity(
                    name = name,
                    volumeMl = volumeMl,
                    displayOrder = order,
                    createdAt = now,
                    updatedAt = now,
                ),
        )
    }

    suspend fun deletePreset(id: String) = dao.softDeletePreset(id, clock.nowMillis())

    /**
     * Seeds three presets the first time only. Counts deleted rows too, so a
     * user who removes every preset is not given them back on the next launch.
     */
    suspend fun ensureDefaultPresets() {
        if (dao.presetRowCount() > 0) return
        val now = SEED_TIMESTAMP
        val oz = VolumeUnit.US_FLUID_OUNCES
        dao.upsertPresets(
            listOf(
                Triple("Glass", oz.toMl(8.0), 0),
                Triple("Bottle", 500.0, 1),
                Triple("Shaker", oz.toMl(24.0), 2),
            ).map { (name, ml, order) ->
                HydrationPresetEntity(
                    id = seedId("hydration-preset", name),
                    name = name,
                    volumeMl = ml,
                    displayOrder = order,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
    }
}
