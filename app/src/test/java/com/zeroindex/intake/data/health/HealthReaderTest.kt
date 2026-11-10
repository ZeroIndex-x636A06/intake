package com.zeroindex.intake.data.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** A fake Health Connect: records in memory, paged, with optional failures. */
private class FakeSource : HealthDataSource {
    override val ownPackage = "com.zeroindex.intake"
    var granted: Set<String> = emptySet()
    var pageSize = 1000
    val records = mutableListOf<Record>()
    val failing = mutableSetOf<KClass<out Record>>()
    var aggregateFails = false

    class Call(val type: KClass<*>, val from: Instant, val to: Instant, val token: String?)

    val calls = mutableListOf<Call>()

    override fun availability() = HealthAvailability.AVAILABLE
    override suspend fun grantedPermissions() = granted

    private fun inRange(r: Record, from: Instant, to: Instant): Boolean = when (r) {
        is StepsRecord -> r.startTime < to && r.endTime > from
        is SleepSessionRecord -> r.startTime < to && r.endTime > from
        is WeightRecord -> r.time >= from && r.time < to
        is BodyFatRecord -> r.time >= from && r.time < to
        is RestingHeartRateRecord -> r.time >= from && r.time < to
        else -> false
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> readPage(type: KClass<T>, from: Instant, to: Instant, pageToken: String?): RecordPage<T> {
        calls += Call(type, from, to, pageToken)
        if (type in failing) throw SecurityException("no")
        val all = records.filter { type.isInstance(it) && inRange(it, from, to) }.map { it as T }
        val start = pageToken?.toInt() ?: 0
        val end = minOf(start + pageSize, all.size)
        return RecordPage(all.subList(start, end), if (end < all.size) end.toString() else null)
    }

    override suspend fun totalSteps(from: Instant, to: Instant): Long? {
        if (aggregateFails) throw IllegalStateException("aggregate broke")
        val s = records.filterIsInstance<StepsRecord>().filter { inRange(it, from, to) }
        return s.sumOf { it.count }.takeIf { s.isNotEmpty() }
    }

    override suspend fun totalCaloriesBurned(from: Instant, to: Instant): Double? = null
    override suspend fun averageHeartRate(from: Instant, to: Instant): Double? = null
    override suspend fun insert(records: List<Record>) = Unit
    override suspend fun delete(type: KClass<out Record>, clientRecordIds: List<String>) = Unit

    fun readsOf(type: KClass<*>) = calls.filter { it.type == type }
}

class HealthReaderTest {
    private val now = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = object : Clock {
        override fun nowMillis() = now.toEpochMilli()
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private fun read(vararg types: KClass<out Record>) = types.map { HealthPermission.getReadPermission(it) }.toSet()

    /** The public factories cannot set the origin package, which Health Connect fills in; reflect it in. */
    private fun meta(id: String = "r", pkg: String = "com.watch"): Metadata {
        val m = Metadata.manualEntryWithId(id)
        Metadata::class.java.getDeclaredField("dataOrigin").apply { isAccessible = true }.set(m, DataOrigin(pkg))
        return m
    }

    private fun steps(daysAgo: Long, count: Long, id: String = "s$daysAgo"): StepsRecord {
        val start = now.minusSeconds(daysAgo * 86_400 + 3_600)
        return StepsRecord(start, ZoneOffset.UTC, start.plusSeconds(600), ZoneOffset.UTC, count, meta(id))
    }

    private fun weight(minutesAgo: Long, kg: Double, id: String, pkg: String = "com.scale") =
        WeightRecord(now.minusSeconds(minutesAgo * 60), ZoneOffset.UTC, Mass.kilograms(kg), meta(id, pkg))

    @Test fun `first sync reads thirty days, later syncs at least fourteen`() {
        assertThat(HealthReader.historyDays(null, now.toEpochMilli())).isEqualTo(30)
        assertThat(HealthReader.historyDays(now.minusSeconds(3_600).toEpochMilli(), now.toEpochMilli())).isEqualTo(14)
        assertThat(HealthReader.historyDays(now.minusSeconds(20 * 86_400L).toEpochMilli(), now.toEpochMilli())).isEqualTo(22)
        assertThat(HealthReader.historyDays(now.minusSeconds(90 * 86_400L).toEpochMilli(), now.toEpochMilli())).isEqualTo(30)
    }

    @Test fun `a first sync reaches back to the oldest of the thirty days`() = runTest {
        val src = FakeSource().apply {
            granted = read(StepsRecord::class)
            records += steps(daysAgo = 29, count = 4_000) // the oldest day in range
            records += steps(daysAgo = 0, count = 1_000)
        }
        val reader = HealthReader(src, clock)
        val run = HealthReader.Run(src.granted)
        val days = reader.readDays(run, HealthReader.historyDays(null, clock.nowMillis()), bmr = null)

        assertThat(days).hasSize(30)
        assertThat(days.first { it.steps == 4_000L }.dayStartMillis)
            .isEqualTo(clock.rangeFor(clock.today().minusDays(29)).startInclusive)
        assertThat(run.report(clock.nowMillis(), 30).outcomes.first { it.label == "Steps" }.daysWithData).isEqualTo(2)
    }

    @Test fun `paging is followed to the last page`() = runTest {
        val src = FakeSource().apply {
            granted = read(WeightRecord::class)
            pageSize = 2
            (1..5L).forEach { records += weight(minutesAgo = it * 60, kg = 80.0 + it, id = "w$it") }
        }
        val run = HealthReader.Run(src.granted)
        val drafts = HealthReader(src, clock).readWeighIns(run, 30) { false }

        assertThat(drafts).hasSize(5)
        assertThat(src.readsOf(WeightRecord::class)).hasSize(3) // 2 + 2 + 1
        assertThat(src.readsOf(WeightRecord::class).map { it.token }).containsExactly(null, "2", "4").inOrder()
        assertThat(run.report(0, 30).outcomes.first { it.label == "Weight" }.records).isEqualTo(5)
    }

    @Test fun `with only some permissions only those types are read`() = runTest {
        val src = FakeSource().apply {
            granted = read(StepsRecord::class) // no weight, no sleep
            records += steps(0, 800)
            records += weight(30, 80.0, "w")
            records += SleepSessionRecord(now.minusSeconds(8 * 3_600), ZoneOffset.UTC, now.minusSeconds(3_600), ZoneOffset.UTC, metadata = meta("sl"))
        }
        val reader = HealthReader(src, clock)
        val run = HealthReader.Run(src.granted)
        val weighIns = reader.readWeighIns(run, 30) { false }
        val days = reader.readDays(run, 3, null)
        val report = run.report(0, 3)

        assertThat(weighIns).isEmpty()
        assertThat(days.first().steps).isEqualTo(800)
        assertThat(days.first().sleepMinutes).isNull()
        assertThat(src.readsOf(WeightRecord::class)).isEmpty()
        assertThat(src.readsOf(SleepSessionRecord::class)).isEmpty()
        assertThat(report.outcomes.first { it.label == "Weight" }.kind).isEqualTo(OutcomeKind.NO_PERMISSION)
        assertThat(report.outcomes.first { it.label == "Sleep" }.describe()).isEqualTo("Sleep: none (no permission)")
        assertThat(report.outcomes.first { it.label == "Steps" }.kind).isEqualTo(OutcomeKind.READ)
    }

    @Test fun `a granted type with no data says none found, not no permission`() = runTest {
        val src = FakeSource().apply { granted = read(WeightRecord::class) }
        val run = HealthReader.Run(src.granted)
        HealthReader(src, clock).readWeighIns(run, 30) { false }
        assertThat(run.report(0, 30).outcomes.first { it.label == "Weight" }.describe()).isEqualTo("Weight: none found")
    }

    @Test fun `records map to rows`() = runTest {
        val src = FakeSource().apply {
            granted = read(WeightRecord::class, BodyFatRecord::class, StepsRecord::class, SleepSessionRecord::class, RestingHeartRateRecord::class)
            records += weight(minutesAgo = 60, kg = 82.3, id = "abc")
            records += BodyFatRecord(now.minusSeconds(61 * 60), ZoneOffset.UTC, Percentage(21.5), meta("bf"))
            records += steps(0, 1_500, "s-a")
            records += steps(0, 2_500, "s-b")
            val wake = clock.rangeFor(clock.today()).startInclusive + 7 * 3_600_000L
            records += SleepSessionRecord(
                Instant.ofEpochMilli(wake - 8 * 3_600_000L), ZoneOffset.UTC, Instant.ofEpochMilli(wake), ZoneOffset.UTC, metadata = meta("sl"),
            )
            records += RestingHeartRateRecord(now.minusSeconds(7_200), ZoneOffset.UTC, 52, meta("r1"))
            records += RestingHeartRateRecord(now.minusSeconds(3_600), ZoneOffset.UTC, 56, meta("r2"))
        }
        val reader = HealthReader(src, clock)
        val run = HealthReader.Run(src.granted)
        val weighIn = reader.readWeighIns(run, 30) { false }.single()
        val today = reader.readDays(run, 1, null).single()

        val entity = weighIn.toEntity(now = 99L)
        assertThat(entity.id).isEqualTo("hc-weight-abc")
        assertThat(entity.weightKg).isEqualTo(82.3)
        assertThat(entity.bodyFatPercent).isEqualTo(21.5)
        assertThat(entity.source).isEqualTo("HEALTH_CONNECT")
        assertThat(entity.measuredAt).isEqualTo(now.minusSeconds(3_600).toEpochMilli())

        val row = today.toEntity(existing = null, newId = { "new" }, now = 99L)
        assertThat(row.id).isEqualTo("new")
        assertThat(row.steps).isEqualTo(4_000)
        assertThat(row.sleepMinutes).isEqualTo(480)
        assertThat(row.restingHeartRate).isEqualTo(54.0)
        assertThat(row.dayStartMillis).isEqualTo(clock.todayRange().startInclusive)
    }

    @Test fun `weigh-ins we already have are not imported twice`() = runTest {
        val src = FakeSource().apply {
            granted = read(WeightRecord::class)
            records += weight(60, 80.0, "a")
            records += weight(120, 81.0, "b")
        }
        val known = now.minusSeconds(60 * 60).toEpochMilli()
        val drafts = HealthReader(src, clock).readWeighIns(HealthReader.Run(src.granted), 30) { it == known }
        assertThat(drafts.map { it.healthConnectId }).containsExactly("b")
    }

    @Test fun `weigh-ins written by this app are not read back`() = runTest {
        val src = FakeSource().apply {
            granted = read(WeightRecord::class)
            records += weight(60, 80.0, "mine", pkg = "com.zeroindex.intake")
            records += weight(120, 81.0, "scale")
        }
        val drafts = HealthReader(src, clock).readWeighIns(HealthReader.Run(src.granted), 30) { false }
        assertThat(drafts.map { it.healthConnectId }).containsExactly("scale")
    }

    @Test fun `a failing aggregate falls back to adding up step records`() = runTest {
        val src = FakeSource().apply {
            granted = read(StepsRecord::class)
            aggregateFails = true
            records += steps(0, 700, "a")
            records += steps(0, 300, "b")
        }
        val days = HealthReader(src, clock).readDays(HealthReader.Run(src.granted), 1, null)
        assertThat(days.single().steps).isEqualTo(1_000)
    }

    @Test fun `one type failing is reported in words and does not stop the others`() = runTest {
        val src = FakeSource().apply {
            granted = read(StepsRecord::class, SleepSessionRecord::class)
            failing += SleepSessionRecord::class
            records += steps(0, 900)
        }
        val run = HealthReader.Run(src.granted)
        val days = HealthReader(src, clock).readDays(run, 2, null)
        val report = run.report(0, 2)

        assertThat(days.first().steps).isEqualTo(900)
        val sleep = report.outcomes.first { it.label == "Sleep" }
        assertThat(sleep.kind).isEqualTo(OutcomeKind.FAILED)
        assertThat(sleep.describe()).contains("Health Connect refused it")
        assertThat(report.failures).hasSize(1)
    }

    @Test fun `the report reads as one line`() {
        val report = SyncReport(
            at = 1,
            historyDays = 30,
            outcomes = listOf(
                TypeOutcome("Steps", OutcomeKind.READ, daysRead = 30, daysWithData = 30, records = 41),
                TypeOutcome("Weight", OutcomeKind.READ, records = 6),
                TypeOutcome("Sleep", OutcomeKind.NO_PERMISSION),
                TypeOutcome("Heart rate", OutcomeKind.READ, daysRead = 30, daysWithData = 12),
            ),
        )
        assertThat(report.summary()).isEqualTo(
            "Steps: 30 days, 41 records · Weight: 6 records · Sleep: none (no permission) · Heart rate: 12 of 30 days",
        )
    }
}
