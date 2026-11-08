package com.zeroindex.intake.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
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
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.health.ActivityNetting
import com.zeroindex.intake.domain.health.BmrBasis
import com.zeroindex.intake.domain.health.EnergySample
import com.zeroindex.intake.domain.health.SessionWindow
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
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
    @ApplicationContext private val context: Context,
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
        const val SYNC_DAYS = 14L
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
    private val _state = MutableStateFlow(HealthSyncState())
    val state: StateFlow<HealthSyncState> = _state.asStateFlow()

    fun availability(): HealthAvailability = when (runCatching { HealthConnectClient.getSdkStatus(context) }.getOrNull()) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        HealthConnectClient.SDK_UNAVAILABLE -> HealthAvailability.NOT_INSTALLED
        else -> HealthAvailability.UNSUPPORTED
    }

    private fun client(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun refreshPermissions(): Set<String> {
        val granted = client()?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrNull() }.orEmpty()
        _state.update { it.copy(availability = availability(), granted = granted) }
        return granted
    }

    /** Reads, then writes. Each type is skipped quietly if its permission is missing. */
    suspend fun sync() = mutex.withLock {
        val hc = client() ?: run {
            _state.update { it.copy(availability = availability()) }
            return@withLock
        }
        _state.update { it.copy(syncing = true, lastError = null) }
        try {
            val granted = refreshPermissions()
            importBody(hc, granted)
            summarise(hc, granted)
            val watermark = writeChanges(hc, granted)
            settings.setHealthSynced(watermark, clock.nowMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect sync failed", e)
            _state.update { it.copy(lastError = "Health Connect sync failed: ${e.message ?: "unknown error"}") }
        } finally {
            _state.update { it.copy(syncing = false) }
        }
    }

    // ----- Reads ------------------------------------------------------------

    private fun has(granted: Set<String>, type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

    private suspend fun <T : Record> readAll(hc: HealthConnectClient, type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = hc.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        // Never read back what this app wrote: it is already in the database.
        return out.filter { it.metadata.dataOrigin.packageName != context.packageName }
    }

    /**
     * Amendment §3: weight, body fat and lean mass from a smart scale become
     * weigh-ins, tagged with their source and skipped if a weigh-in already
     * exists within ten minutes (the user may have typed the same reading).
     */
    private suspend fun importBody(hc: HealthConnectClient, granted: Set<String>) {
        if (!has(granted, WeightRecord::class)) return
        val to = Instant.ofEpochMilli(clock.nowMillis())
        val from = to.minusSeconds(SYNC_DAYS * 86_400)
        val fats = if (has(granted, BodyFatRecord::class)) readAll(hc, BodyFatRecord::class, from, to) else emptyList()
        val leans = if (has(granted, LeanBodyMassRecord::class)) readAll(hc, LeanBodyMassRecord::class, from, to) else emptyList()

        readAll(hc, WeightRecord::class, from, to).forEach { w ->
            val at = w.time.toEpochMilli()
            if (bodyDao.near(at, WEIGH_IN_MATCH_WINDOW_MS) != null) return@forEach
            val now = clock.nowMillis()
            bodyDao.upsert(
                BodyMetricEntity(
                    measuredAt = at,
                    weightKg = w.weight.inKilograms,
                    bodyFatPercent = fats.minByOrNull { kotlin.math.abs(it.time.toEpochMilli() - at) }
                        ?.takeIf { kotlin.math.abs(it.time.toEpochMilli() - at) < WEIGH_IN_MATCH_WINDOW_MS }?.percentage?.value,
                    leanBodyMassKg = leans.minByOrNull { kotlin.math.abs(it.time.toEpochMilli() - at) }
                        ?.takeIf { kotlin.math.abs(it.time.toEpochMilli() - at) < WEIGH_IN_MATCH_WINDOW_MS }?.mass?.inKilograms,
                    source = "HEALTH_CONNECT",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** One cached summary per local day: activity (netted), steps, sleep, heart rate. */
    private suspend fun summarise(hc: HealthConnectClient, granted: Set<String>) {
        val latest = bodyDao.latest()
        val measuredBmr = if (has(granted, BasalMetabolicRateRecord::class)) {
            val to = Instant.ofEpochMilli(clock.nowMillis())
            readAll(hc, BasalMetabolicRateRecord::class, to.minusSeconds(90 * 86_400), to)
                .maxByOrNull { it.time }?.basalMetabolicRate?.inKilocaloriesPerDay
        } else {
            null
        }
        val bmr = ActivityNetting.bmrPerDay(measuredBmr, latest?.weightKg, latest?.bodyFatPercent)
        _state.update { it.copy(bmrBasis = bmr?.second, bmrKcalPerDay = bmr?.first) }

        val today = clock.today()
        for (offset in 0 until SYNC_DAYS) {
            val day = today.minusDays(offset)
            val range = clock.rangeFor(day)
            val from = Instant.ofEpochMilli(range.startInclusive)
            val to = Instant.ofEpochMilli(range.endExclusive)

            val sessions = if (has(granted, ExerciseSessionRecord::class)) readAll(hc, ExerciseSessionRecord::class, from, to) else emptyList()
            val totals = if (has(granted, TotalCaloriesBurnedRecord::class)) readAll(hc, TotalCaloriesBurnedRecord::class, from, to) else emptyList()
            val actives = if (has(granted, ActiveCaloriesBurnedRecord::class)) readAll(hc, ActiveCaloriesBurnedRecord::class, from, to) else emptyList()
            val net = bmr?.let { (perDay, _) ->
                ActivityNetting.net(
                    sessions = sessions.map { SessionWindow(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
                    totalSamples = totals.map { EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) },
                    activeSamples = actives.map { EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) },
                    bmrKcalPerDay = perDay,
                )
            }

            val totalBurn = if (has(granted, TotalCaloriesBurnedRecord::class)) {
                runCatching {
                    hc.aggregate(
                        AggregateRequest(setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL), TimeRangeFilter.between(from, to)),
                    )[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                }.getOrNull()
            } else {
                null
            }
            val steps = if (has(granted, StepsRecord::class)) {
                runCatching {
                    hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(from, to)))[StepsRecord.COUNT_TOTAL]
                }.getOrNull()
            } else {
                null
            }
            // Sleep belongs to the day it ends on: last night's sleep is today's.
            val sleep = if (has(granted, SleepSessionRecord::class)) {
                readAll(hc, SleepSessionRecord::class, from.minusSeconds(14 * 3_600), to).filter { it.endTime >= from && it.endTime < to }
            } else {
                emptyList()
            }
            val resting = if (has(granted, RestingHeartRateRecord::class)) {
                readAll(hc, RestingHeartRateRecord::class, from, to).map { it.beatsPerMinute.toDouble() }.takeIf { it.isNotEmpty() }?.average()
            } else {
                null
            }
            val avgHr = if (has(granted, HeartRateRecord::class)) {
                runCatching {
                    hc.aggregate(AggregateRequest(setOf(HeartRateRecord.BPM_AVG), TimeRangeFilter.between(from, to)))[HeartRateRecord.BPM_AVG]
                }.getOrNull()?.toDouble()
            } else {
                null
            }

            val now = clock.nowMillis()
            val existing = summaryDao.forDay(range.startInclusive)
            summaryDao.upsert(
                HealthDailySummaryEntity(
                    id = existing?.id ?: com.zeroindex.intake.data.db.entity.newId(),
                    dayStartMillis = range.startInclusive,
                    activeKcalRaw = net?.rawKcal,
                    bmrKcalInWindows = net?.bmrKcal,
                    activeKcalNet = net?.netKcal,
                    totalKcalBurned = totalBurn,
                    steps = steps,
                    sleepMinutes = sleep.sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60_000 }.takeIf { sleep.isNotEmpty() },
                    sleepStartMillis = sleep.minOfOrNull { it.startTime.toEpochMilli() },
                    restingHeartRate = resting,
                    avgHeartRate = avgHr,
                    syncedAt = now,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
        }
    }

    // ----- Writes -----------------------------------------------------------

    private fun canWrite(granted: Set<String>, type: KClass<out Record>) = HealthPermission.getWritePermission(type) in granted

    /**
     * Writes everything changed since the last run. Each record's client id is
     * the row's own UUID and its version the row's updatedAt, so a re-write
     * replaces the old record instead of duplicating it, and a soft-deleted
     * row deletes its record. Returns the new watermark.
     */
    private suspend fun writeChanges(hc: HealthConnectClient, granted: Set<String>): Long {
        val since = settings.settings.first().healthLastWriteAt
        var watermark = since

        if (canWrite(granted, NutritionRecord::class)) {
            val changed = foodLogDao.changedSince(since)
            changed.filter { it.deletedAt != null }.map { it.id }.takeIf { it.isNotEmpty() }?.let {
                runCatching { hc.deleteRecords(NutritionRecord::class, emptyList(), it) }
            }
            changed.filter { it.deletedAt == null }.chunked(100).forEach { batch ->
                hc.insertRecords(
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
                runCatching { hc.deleteRecords(WeightRecord::class, emptyList(), it) }
            }
            changed.filter { it.deletedAt == null }.takeIf { it.isNotEmpty() }?.let { rows ->
                hc.insertRecords(
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
                runCatching { hc.deleteRecords(HydrationRecord::class, emptyList(), it) }
            }
            changed.filter { it.deletedAt == null }.takeIf { it.isNotEmpty() }?.let { rows ->
                hc.insertRecords(
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
                runCatching { hc.deleteRecords(ExerciseSessionRecord::class, emptyList(), it) }
            }
            // Only finished sessions: an open one has no end time to write.
            changed.filter { it.deletedAt == null && it.finishedAt != null }.takeIf { it.isNotEmpty() }?.let { rows ->
                hc.insertRecords(
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
