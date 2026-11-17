package com.zeroindex.intake.data.health

import android.os.RemoteException
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import com.zeroindex.intake.domain.health.ActivityNetting
import com.zeroindex.intake.domain.health.KnownWeighIn
import com.zeroindex.intake.domain.health.OfferedWeighIn
import com.zeroindex.intake.domain.health.SessionMath
import com.zeroindex.intake.domain.health.SleepMath
import com.zeroindex.intake.domain.health.SleepSpan
import com.zeroindex.intake.domain.health.WeighInDedupe
import com.zeroindex.intake.domain.health.BmrBasis
import com.zeroindex.intake.domain.health.EnergySample
import com.zeroindex.intake.domain.health.SessionWindow
import com.zeroindex.intake.domain.time.Clock
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.reflect.KClass

// ----- What a sync reports ---------------------------------------------------

enum class OutcomeKind { READ, NONE, NO_PERMISSION, FAILED }

/** What one Health Connect type did on the last sync, in words the user can read. */
data class TypeOutcome(
    val label: String,
    val kind: OutcomeKind,
    /** Days looked at, for types read day by day (steps, sleep, ...). 0 for types read in one go. */
    val daysRead: Int = 0,
    val daysWithData: Int = 0,
    val records: Int = 0,
    val error: String? = null,
) {
    fun describe(): String = when (kind) {
        OutcomeKind.NO_PERMISSION -> "$label: none (no permission)"
        OutcomeKind.NONE -> "$label: none found"
        OutcomeKind.FAILED -> "$label: could not read (${error ?: "unknown problem"})"
        OutcomeKind.READ -> {
            val parts = buildList {
                if (daysRead > 0) add(if (daysWithData == daysRead) "$daysRead days" else "$daysWithData of $daysRead days")
                if (records > 0) add(if (records == 1) "1 record" else "$records records")
            }
            "$label: ${parts.joinToString(", ")}"
        }
    }
}

data class SyncReport(val at: Long, val historyDays: Int, val outcomes: List<TypeOutcome>) {
    /** "Steps: 30 days, 41 records · Weight: 6 records · Sleep: none (no permission)" */
    fun summary(): String = outcomes.joinToString(" · ") { it.describe() }

    val anyData: Boolean get() = outcomes.any { it.kind == OutcomeKind.READ }
    val failures: List<TypeOutcome> get() = outcomes.filter { it.kind == OutcomeKind.FAILED }
}

/** Turns what Health Connect throws into one plain sentence. */
object HealthErrors {
    fun plain(e: Throwable): String = when (e) {
        is SecurityException -> "Health Connect refused it; check Intake's permissions in Health Connect"
        is RemoteException, is IOException -> "Health Connect did not answer; try again in a moment"
        is IllegalStateException -> "Health Connect is not ready; open it once, then try again"
        else -> e.message?.take(80)?.takeIf { it.isNotBlank() } ?: "an unexpected problem"
    }
}

// ----- What a sync reads -----------------------------------------------------

/** One weigh-in read from Health Connect, with body fat and lean mass matched by time. */
data class WeighInDraft(
    val healthConnectId: String,
    val measuredAt: Long,
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val leanBodyMassKg: Double?,
) {
    fun toEntity(now: Long) = BodyMetricEntity(
        id = "hc-weight-$healthConnectId",
        measuredAt = measuredAt,
        weightKg = weightKg,
        bodyFatPercent = bodyFatPercent,
        leanBodyMassKg = leanBodyMassKg,
        source = "HEALTH_CONNECT",
        createdAt = now,
        updatedAt = now,
    )
}

/** One local day of activity, steps, sleep and heart rate. */
data class DayDraft(
    val dayStartMillis: Long,
    val activeKcalRaw: Double?,
    val bmrKcalInWindows: Double?,
    val activeKcalNet: Double?,
    val totalKcalBurned: Double?,
    /** All-day active calories, the figure a watch shows. Null when there is no data. */
    val activeKcalDay: Double? = null,
    val steps: Long?,
    val sleepMinutes: Long?,
    val sleepStartMillis: Long?,
    val restingHeartRate: Double?,
    val avgHeartRate: Double?,
) {
    fun toEntity(existing: HealthDailySummaryEntity?, newId: () -> String, now: Long) = HealthDailySummaryEntity(
        id = existing?.id ?: newId(),
        dayStartMillis = dayStartMillis,
        activeKcalRaw = activeKcalRaw,
        bmrKcalInWindows = bmrKcalInWindows,
        activeKcalNet = activeKcalNet,
        totalKcalBurned = totalKcalBurned,
        activeKcalDay = activeKcalDay,
        steps = steps,
        sleepMinutes = sleepMinutes,
        sleepStartMillis = sleepStartMillis,
        restingHeartRate = restingHeartRate,
        avgHeartRate = avgHeartRate,
        syncedAt = now,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}

/** Counts for one type while a sync runs. */
class Tally(val label: String, private val permitted: Boolean) {
    var daysRead = 0
    var daysWithData = 0
    var records = 0
    var error: String? = null

    fun outcome(): TypeOutcome = when {
        !permitted -> TypeOutcome(label, OutcomeKind.NO_PERMISSION)
        daysWithData > 0 || records > 0 -> TypeOutcome(label, OutcomeKind.READ, daysRead, daysWithData, records, error)
        error != null -> TypeOutcome(label, OutcomeKind.FAILED, error = error)
        else -> TypeOutcome(label, OutcomeKind.NONE, daysRead)
    }
}

/**
 * Reads Health Connect through a [HealthDataSource] and produces drafts, with no
 * database or Android context, so it is unit-testable with a fake source.
 *
 * One type failing never stops the others: its message goes in the report.
 */
class HealthReader(private val source: HealthDataSource, private val clock: Clock) {
    companion object {
        /** The first sync reads this far back, so history is not lost to a changes-only read. */
        const val FIRST_SYNC_DAYS = 30
        /** Later syncs re-read at least this many days; summaries are rewritten, so overlap is safe. */
        const val MIN_SYNC_DAYS = 14
        /**
         * Weight, body fat and lean mass are sparse (a few readings a month), so every sync looks a year back. It is one
         * paged read, and readings already imported are recognised by id and skipped.
         */
        const val WEIGHT_HISTORY_DAYS = 365
        private const val DAY_MS = 86_400_000L
        private const val WEIGH_IN_MATCH_WINDOW_MS = WeighInDedupe.MATCH_WINDOW_MS

        /**
         * Never synced: a full 30 days. Otherwise cover the gap since the last sync, between 14 and 30 days.
         * [forceFull] is for a sync that follows one where some types had no permission yet: those types have no
         * history in the database, so the short window would leave them with 14 days.
         */
        fun historyDays(lastSyncAt: Long?, now: Long, forceFull: Boolean = false): Int {
            if (lastSyncAt == null || forceFull) return FIRST_SYNC_DAYS
            val gap = ((now - lastSyncAt) / DAY_MS).toInt() + 2
            return gap.coerceIn(MIN_SYNC_DAYS, FIRST_SYNC_DAYS)
        }

        /** Types that this app writes, so records from our own package are skipped on read. */
        private val OWN_WRITES: Set<KClass<out Record>> = setOf(WeightRecord::class, ExerciseSessionRecord::class)
    }

    class Run(val granted: Set<String>) {
        private val tallies = LinkedHashMap<String, Tally>()
        fun tally(label: String, type: KClass<out Record>): Tally =
            tallies.getOrPut(label) { Tally(label, HealthPermission.getReadPermission(type) in granted) }

        fun report(at: Long, days: Int) = SyncReport(at, days, tallies.values.map { it.outcome() })
    }

    private fun Run.can(type: KClass<out Record>) = HealthPermission.getReadPermission(type) in granted

    private suspend fun <T : Record> read(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val all = source.readAll(type, from, to)
        // Never read back what this app wrote: it is already in the database.
        return if (type in OWN_WRITES) all.filter { it.metadata.dataOrigin.packageName != source.ownPackage } else all
    }

    private inline fun <R> attempt(tally: Tally, block: () -> R): R? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (tally.error == null) tally.error = HealthErrors.plain(e)
        null
    }

    /**
     * Weight, body fat and lean mass from a smart scale become weigh-ins, matched by time. Reads
     * [WEIGHT_HISTORY_DAYS] back whatever the sync window is. [known] is every body row already in the database
     * (deleted ones too): see [WeighInDedupe] for what is left out. The id comes from Health Connect's own record id,
     * so two devices that read the same scale produce the same row.
     */
    suspend fun readWeighIns(run: Run, known: List<KnownWeighIn> = emptyList(), days: Int = WEIGHT_HISTORY_DAYS): List<WeighInDraft> {
        val weightTally = run.tally("Weight", WeightRecord::class)
        val fatTally = run.tally("Body fat", BodyFatRecord::class)
        val leanTally = run.tally("Lean body mass", LeanBodyMassRecord::class)
        val to = Instant.ofEpochMilli(clock.nowMillis())
        val from = to.minusSeconds(days * 86_400L)

        val fats = if (run.can(BodyFatRecord::class)) attempt(fatTally) { read(BodyFatRecord::class, from, to) }.orEmpty() else emptyList()
        val leans = if (run.can(LeanBodyMassRecord::class)) attempt(leanTally) { read(LeanBodyMassRecord::class, from, to) }.orEmpty() else emptyList()
        fatTally.records = fats.size
        leanTally.records = leans.size
        if (!run.can(WeightRecord::class)) return emptyList()

        val weights = attempt(weightTally) { read(WeightRecord::class, from, to) }.orEmpty()
        weightTally.records = weights.size
        val byId = weights.associateBy { it.metadata.id }
        val accepted = WeighInDedupe.toImport(
            offered = weights.map { OfferedWeighIn(it.metadata.id, it.time.toEpochMilli()) },
            known = known,
            rowId = { "hc-weight-$it" },
        )
        return accepted.mapNotNull { o ->
            val w = byId[o.id] ?: return@mapNotNull null
            val at = o.measuredAt
            WeighInDraft(
                healthConnectId = w.metadata.id,
                measuredAt = at,
                weightKg = w.weight.inKilograms,
                bodyFatPercent = fats.minByOrNull { abs(it.time.toEpochMilli() - at) }
                    ?.takeIf { abs(it.time.toEpochMilli() - at) < WEIGH_IN_MATCH_WINDOW_MS }?.percentage?.value,
                leanBodyMassKg = leans.minByOrNull { abs(it.time.toEpochMilli() - at) }
                    ?.takeIf { abs(it.time.toEpochMilli() - at) < WEIGH_IN_MATCH_WINDOW_MS }?.mass?.inKilograms,
            )
        }
    }

    /** The most recent measured basal metabolic rate in the last 90 days, or null. */
    suspend fun readMeasuredBmr(run: Run): Double? {
        val tally = run.tally("Basal metabolic rate", BasalMetabolicRateRecord::class)
        if (!run.can(BasalMetabolicRateRecord::class)) return null
        val to = Instant.ofEpochMilli(clock.nowMillis())
        val records = attempt(tally) { read(BasalMetabolicRateRecord::class, to.minusSeconds(90 * 86_400L), to) }.orEmpty()
        tally.records = records.size
        return records.maxByOrNull { it.time }?.basalMetabolicRate?.inKilocaloriesPerDay
    }

    /** One draft per local day, today and the [days] - 1 before it. */
    suspend fun readDays(run: Run, days: Int, bmr: Pair<Double, BmrBasis>?): List<DayDraft> {
        val steps = run.tally("Steps", StepsRecord::class)
        val sleepT = run.tally("Sleep", SleepSessionRecord::class)
        val exerciseT = run.tally("Exercise sessions", ExerciseSessionRecord::class)
        val totalT = run.tally("Total calories", TotalCaloriesBurnedRecord::class)
        val activeT = run.tally("Active calories", ActiveCaloriesBurnedRecord::class)
        val restingT = run.tally("Resting heart rate", RestingHeartRateRecord::class)
        val hrT = run.tally("Heart rate", HeartRateRecord::class)

        val today = clock.today()
        val out = mutableListOf<DayDraft>()
        for (offset in 0 until days) {
            val range = clock.rangeFor(today.minusDays(offset.toLong()))
            val from = Instant.ofEpochMilli(range.startInclusive)
            val to = Instant.ofEpochMilli(range.endExclusive)
            listOf(steps, sleepT, exerciseT, totalT, activeT, restingT, hrT).forEach { it.daysRead++ }

            // Every tracked workout counts as a window, ours included (the energy inside it came from the watch), and
            // windows two apps recorded for the same workout are merged so the energy is not counted twice.
            val sessions = if (run.can(ExerciseSessionRecord::class)) attempt(exerciseT) { source.readAll(ExerciseSessionRecord::class, from, to) }.orEmpty() else emptyList()
            val totals = if (run.can(TotalCaloriesBurnedRecord::class)) attempt(totalT) { read(TotalCaloriesBurnedRecord::class, from, to) }.orEmpty() else emptyList()
            val actives = if (run.can(ActiveCaloriesBurnedRecord::class)) attempt(activeT) { read(ActiveCaloriesBurnedRecord::class, from, to) }.orEmpty() else emptyList()
            if (sessions.isNotEmpty()) { exerciseT.daysWithData++; exerciseT.records += sessions.size }
            if (actives.isNotEmpty()) { activeT.daysWithData++; activeT.records += actives.size }
            if (totals.isNotEmpty()) totalT.records += totals.size

            val net = bmr?.let { (perDay, _) ->
                ActivityNetting.net(
                    sessions = SessionMath.merge(sessions.map { SessionWindow(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }),
                    totalSamples = totals.map { EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) },
                    activeSamples = actives.map { EnergySample(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.energy.inKilocalories) },
                    bmrKcalPerDay = perDay,
                )
            }

            val activeDay = if (run.can(ActiveCaloriesBurnedRecord::class)) attempt(activeT) { source.totalActiveCalories(from, to) } else null

            val totalBurn = if (run.can(TotalCaloriesBurnedRecord::class)) attempt(totalT) { source.totalCaloriesBurned(from, to) } else null
            if (totalBurn != null && totalBurn > 0) totalT.daysWithData++

            val stepCount = if (run.can(StepsRecord::class)) {
                // The daily total resolves overlapping phone and watch counts. If it fails, add up
                // the raw records rather than report a day with no steps.
                try {
                    source.totalSteps(from, to)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt(steps) {
                        val records = source.readAll(StepsRecord::class, from, to)
                        steps.records += records.size
                        records.sumOf { it.count }.takeIf { records.isNotEmpty() }
                    }
                }
            } else {
                null
            }
            if (stepCount != null && stepCount > 0) steps.daysWithData++

            // Sleep belongs to the day it ends on: last night's sleep is today's.
            val sleep = if (run.can(SleepSessionRecord::class)) {
                attempt(sleepT) { read(SleepSessionRecord::class, from.minusSeconds(14 * 3_600), to) }
                    .orEmpty().filter { it.endTime >= from && it.endTime < to }
            } else {
                emptyList()
            }
            if (sleep.isNotEmpty()) { sleepT.daysWithData++; sleepT.records += sleep.size }
            // Overlapping sessions from a phone and a watch count once; a daytime nap is not last night.
            val sleepSpans = sleep.map { SleepSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }

            val restingRecords = if (run.can(RestingHeartRateRecord::class)) attempt(restingT) { read(RestingHeartRateRecord::class, from, to) }.orEmpty() else emptyList()
            if (restingRecords.isNotEmpty()) { restingT.daysWithData++; restingT.records += restingRecords.size }
            val resting = restingRecords.map { it.beatsPerMinute.toDouble() }.takeIf { it.isNotEmpty() }?.average()

            val avgHr = if (run.can(HeartRateRecord::class)) attempt(hrT) { source.averageHeartRate(from, to) } else null
            if (avgHr != null) hrT.daysWithData++

            out += DayDraft(
                dayStartMillis = range.startInclusive,
                activeKcalRaw = net?.rawKcal,
                bmrKcalInWindows = net?.bmrKcal,
                activeKcalNet = net?.netKcal,
                totalKcalBurned = totalBurn,
                activeKcalDay = activeDay?.takeIf { it > 0 },
                steps = stepCount,
                sleepMinutes = SleepMath.lastNight(sleepSpans, range.startInclusive, range.endExclusive, clock.zone()),
                sleepStartMillis = sleep.minOfOrNull { it.startTime.toEpochMilli() },
                restingHeartRate = resting,
                avgHeartRate = avgHr,
            )
        }
        return out
    }
}
