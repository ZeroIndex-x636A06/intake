package com.zeroindex.intake.data.health

import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.health.ActivityNetting
import com.zeroindex.intake.domain.health.BmrBasis
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class HealthAvailability { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED, UNSUPPORTED }

/** One Health Connect data type the app reads or writes, for the permissions screen. */
/** One Health Connect permission, with the plain reason Intake asks for it. */
data class HealthDataType(val label: String, val permission: String, val why: String, val write: Boolean = false)

data class HealthSyncState(
    val availability: HealthAvailability = HealthAvailability.UNSUPPORTED,
    val granted: Set<String> = emptySet(),
    val syncing: Boolean = false,
    val lastError: String? = null,
    val bmrBasis: BmrBasis? = null,
    val bmrKcalPerDay: Double? = null,
    /** What the last sync read, per type. Null until a sync has run in this process. */
    val report: SyncReport? = null,
)

/**
 * SPEC §12 Health Connect, refined by amendments §2 and §3.
 *
 * Every call is guarded: Health Connect may be absent, out of date, or have
 * any subset of permissions granted, and the app must work in every case.
 * Nothing here runs on a timer (SPEC §12) - [HealthSync] calls it on resume
 * and, debounced, after local writes.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    private val source: HealthDataSource,
    private val foodLogDao: FoodLogDao,
    private val bodyDao: BodyMetricDao,
    private val hydrationDao: HydrationDao,
    private val workoutDao: WorkoutDao,
    private val summaryDao: HealthSummaryDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    companion object {
        private const val TAG = "HealthConnect"
        private const val WEIGH_IN_MATCH_WINDOW_MS = 10 * 60 * 1000L

        val TYPES: List<HealthDataType> = listOf(
            read("Exercise sessions", ExerciseSessionRecord::class, "Read so only tracked workouts count"),
            read("Total calories burned", TotalCaloriesBurnedRecord::class, "Read for daily energy balance"),
            read("Active calories", ActiveCaloriesBurnedRecord::class, "Read for daily energy balance"),
            read("Basal metabolic rate", BasalMetabolicRateRecord::class, "Read so resting burn is not counted twice"),
            read("Steps", StepsRecord::class, "Read daily total"),
            read("Sleep", SleepSessionRecord::class, "Read duration and stages"),
            read("Heart rate", HeartRateRecord::class, "Read workout samples"),
            read("Resting heart rate", RestingHeartRateRecord::class, "Read daily value"),
            read("Weight", WeightRecord::class, "Read weigh-ins from your scale"),
            read("Body fat", BodyFatRecord::class, "Read measurements"),
            read("Lean body mass", LeanBodyMassRecord::class, "Read measurements · stands in for skeletal muscle"),
            write("Nutrition", NutritionRecord::class, "Write your food log"),
            write("Weight", WeightRecord::class, "Write weigh-ins you log here"),
            write("Hydration", HydrationRecord::class, "Write water you log"),
            write("Workouts", ExerciseSessionRecord::class, "Write finished training sessions"),
        )

        private fun read(label: String, type: KClass<out Record>, why: String) =
            HealthDataType(label, HealthPermission.getReadPermission(type), why)

        private fun write(label: String, type: KClass<out Record>, why: String) =
            HealthDataType(label, HealthPermission.getWritePermission(type), why, write = true)

        val ALL_PERMISSIONS: Set<String> = TYPES.map { it.permission }.toSet()
    }

    private val mutex = Mutex()
    private val reader = HealthReader(source, clock)
    private val _state = MutableStateFlow(HealthSyncState())
    val state: StateFlow<HealthSyncState> = _state.asStateFlow()

    fun availability(): HealthAvailability = source.availability()

    suspend fun refreshPermissions(): Set<String> {
        val available = availability()
        val granted = if (available == HealthAvailability.AVAILABLE) {
            try {
                source.grantedPermissions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read granted permissions", e)
                _state.update { it.copy(lastError = "Could not check which permissions you gave: ${HealthErrors.plain(e)}.") }
                emptySet()
            }
        } else {
            emptySet()
        }
        _state.update { it.copy(availability = available, granted = granted) }
        return granted
    }

    /**
     * Reads, then writes. Each type is skipped if its permission is missing, and the result says so
     * ([HealthSyncState.report]). The first sync reads 30 days; later ones re-read at least 14.
     */
    suspend fun sync() = mutex.withLock {
        if (availability() != HealthAvailability.AVAILABLE) {
            _state.update { it.copy(availability = availability()) }
            return@withLock
        }
        _state.update { it.copy(syncing = true, lastError = null) }
        try {
            val granted = refreshPermissions()
            val now = clock.nowMillis()
            val days = HealthReader.historyDays(settings.settings.first().healthLastSyncAt, now)
            val run = HealthReader.Run(granted)

            val weighIns = reader.readWeighIns(run, days) { at -> bodyDao.near(at, WEIGH_IN_MATCH_WINDOW_MS) != null }
            weighIns.forEach { bodyDao.upsert(it.toEntity(clock.nowMillis())) }

            val latest = bodyDao.latest()
            val bmr = ActivityNetting.bmrPerDay(reader.readMeasuredBmr(run), latest?.weightKg, latest?.bodyFatPercent)
            _state.update { it.copy(bmrBasis = bmr?.second, bmrKcalPerDay = bmr?.first) }
            val drafts = reader.readDays(run, days, bmr)
            drafts.forEach { d ->
                val existing = summaryDao.forDay(d.dayStartMillis)
                summaryDao.upsert(d.toEntity(existing, ::newId, clock.nowMillis()))
            }

            val report = run.report(now, days)
            val watermark = writeChanges(granted)
            settings.setHealthSynced(watermark, now, report.summary())
            _state.update { it.copy(report = report, lastError = failureText(report)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect sync failed", e)
            _state.update { it.copy(lastError = "Sync stopped: ${HealthErrors.plain(e)}.") }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    private fun failureText(report: SyncReport): String? =
        report.failures.takeIf { it.isNotEmpty() }?.joinToString(" ") { "${it.label}: ${it.error}." }

    // ----- Writes -----------------------------------------------------------

    private fun canWrite(granted: Set<String>, type: KClass<out Record>) = HealthPermission.getWritePermission(type) in granted

    /**
     * Writes everything changed since the last run. Each record's client id is
     * the row's own UUID and its version the row's updatedAt, so a re-write
     * replaces the old record instead of duplicating it, and a soft-deleted
     * row deletes its record. Returns the new watermark.
     */
    private suspend fun writeChanges(granted: Set<String>): Long {
        val since = settings.settings.first().healthLastWriteAt
        var watermark = since

        if (canWrite(granted, NutritionRecord::class)) {
            val changed = foodLogDao.changedSince(since)
            changed.filter { it.deletedAt != null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
                runCatching { source.delete(NutritionRecord::class, it) }
            }
            changed.filter { it.deletedAt == null }.chunked(100).forEach { batch ->
                source.insert(
                    batch.map { log ->
                        val start = Instant.ofEpochMilli(log.loggedAt)
                        NutritionRecord(
                            startTime = start,
                            startZoneOffset = offset(start),
                            endTime = start.plusSeconds(60),
                            endZoneOffset = offset(start),
                            metadata = Metadata.manualEntry(log.id, log.updatedAt),
                            energy = Energy.kilocalories(log.kcal),
                            protein = Mass.grams(log.protein),
                            totalCarbohydrate = Mass.grams(log.carbs),
                            totalFat = Mass.grams(log.fat),
                            dietaryFiber = Mass.grams(log.fiber),
                            sugar = Mass.grams(log.sugar),
                            sodium = Mass.grams(log.sodium / 1000.0),
                            saturatedFat = Mass.grams(log.saturatedFat),
                            name = log.foodNameAtLog,
                            mealType = log.mealSlot.toMealType(),
                        )
                    },
                )
            }
            watermark = maxOf(watermark, changed.maxOfOrNull { it.updatedAt } ?: watermark)
        }

        if (canWrite(granted, WeightRecord::class)) {
            // Only weigh-ins made here; ones imported from Health Connect are already there.
            val changed = bodyDao.changedSince(since).filter { it.source != "HEALTH_CONNECT" }
            changed.filter { it.deletedAt != null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
                runCatching { source.delete(WeightRecord::class, it) }
            }
            changed.filter { it.deletedAt == null }.takeIf { it.isNotEmpty() }?.let { rows ->
                source.insert(
                    rows.map { m ->
                        val at = Instant.ofEpochMilli(m.measuredAt)
                        WeightRecord(
                            time = at,
                            zoneOffset = offset(at),
                            weight = Mass.kilograms(m.weightKg),
                            metadata = Metadata.manualEntry(m.id, m.updatedAt),
                        )
                    },
                )
            }
            watermark = maxOf(watermark, changed.maxOfOrNull { it.updatedAt } ?: watermark)
        }

        if (canWrite(granted, HydrationRecord::class)) {
            val changed = hydrationDao.changedSince(since)
            changed.filter { it.deletedAt != null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
                runCatching { source.delete(HydrationRecord::class, it) }
            }
            // Alcohol is not written as hydration: Health Connect would count it as water.
            changed.filter { it.deletedAt == null && it.alcoholMl == 0.0 }.takeIf { it.isNotEmpty() }?.let { rows ->
                source.insert(
                    rows.map { h ->
                        val at = Instant.ofEpochMilli(h.loggedAt)
                        HydrationRecord(
                            startTime = at,
                            startZoneOffset = offset(at),
                            endTime = at.plusSeconds(60),
                            endZoneOffset = offset(at),
                            volume = Volume.milliliters(h.volumeMl),
                            metadata = Metadata.manualEntry(h.id, h.updatedAt),
                        )
                    },
                )
            }
            watermark = maxOf(watermark, changed.maxOfOrNull { it.updatedAt } ?: watermark)
        }

        if (canWrite(granted, ExerciseSessionRecord::class)) {
            val changed = workoutDao.changedSince(since)
            changed.filter { it.deletedAt != null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
                runCatching { source.delete(ExerciseSessionRecord::class, it) }
            }
            // Only finished sessions: an open one has no end time to write.
            changed.filter { it.deletedAt == null && it.finishedAt != null }.takeIf { it.isNotEmpty() }?.let { rows ->
                source.insert(
                    rows.map { w ->
                        val start = Instant.ofEpochMilli(w.startedAt)
                        val end = Instant.ofEpochMilli(w.finishedAt!!)
                        ExerciseSessionRecord(
                            startTime = start,
                            startZoneOffset = offset(start),
                            endTime = end,
                            endZoneOffset = offset(end),
                            metadata = Metadata.manualEntry(w.id, w.updatedAt),
                            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
                            title = "Strength training",
                            notes = w.notes,
                        )
                    },
                )
            }
            watermark = maxOf(watermark, changed.maxOfOrNull { it.updatedAt } ?: watermark)
        }
        return watermark
    }

    private fun offset(at: Instant): ZoneOffset = clock.zone().rules.getOffset(at)

    private fun MealSlot.toMealType(): Int = when (this) {
        MealSlot.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
        MealSlot.LUNCH -> MealType.MEAL_TYPE_LUNCH
        MealSlot.DINNER -> MealType.MEAL_TYPE_DINNER
        MealSlot.SNACK -> MealType.MEAL_TYPE_SNACK
    }
}
