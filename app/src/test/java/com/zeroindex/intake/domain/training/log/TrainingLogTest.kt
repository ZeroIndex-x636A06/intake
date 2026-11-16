package com.zeroindex.intake.domain.training.log

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

class TrainingLogTest {
    private val zone = ZoneId.of("UTC")
    private fun at(y: Int, m: Int, d: Int, h: Int = 10) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val min = 60_000L

    private fun set(session: String, ex: String, i: Int, kg: Double, reps: Int, t: Long, warm: Boolean = false) =
        LogSet(session, ex, i, kg, reps, warm, t)

    // Wed 12 Nov 2026 is "now". Monday of that week is 9 Nov.
    private val now = at(2026, 11, 12)

    private val s1 = LogSession("s1", at(2026, 10, 26), at(2026, 10, 26) + 50 * min, "dayA")
    private val s2 = LogSession("s2", at(2026, 11, 10), at(2026, 11, 10) + 62 * min, "dayA")  // this week
    private val s3 = LogSession("s3", at(2026, 11, 11), null, null)                          // watch-started, unfinished, has sets
    private val s4 = LogSession("s4", at(2026, 11, 3), null, null)                           // never finished, no sets: hidden
    private val s5 = LogSession("s5", at(2026, 11, 5), at(2026, 11, 5) + 30 * min, null)     // last week, empty but finished

    private val sets = listOf(
        set("s1", "bench", 0, 60.0, 8, s1.startedAt + 5 * min),
        set("s1", "bench", 1, 60.0, 8, s1.startedAt + 10 * min),
        set("s2", "bench", 0, 60.0, 9, s2.startedAt + 5 * min),   // more reps at 60: a record
        set("s2", "bench", 1, 65.0, 8, s2.startedAt + 10 * min),  // a higher estimated max: a record
        set("s2", "bench", 2, 40.0, 10, s2.startedAt + 2 * min, warm = true),
        set("s3", "row", 0, 50.0, 10, s3.startedAt + 5 * min),
    )
    private val days = mapOf("dayA" to LogDay("Push A", "Upper Lower", false))

    @Test
    fun `lists every session newest first with totals and records, watch sessions included`() {
        val rows = TrainingLog.rows(listOf(s1, s2, s3, s4, s5), sets, days)
        assertThat(rows.map { it.sessionId }).containsExactly("s3", "s2", "s5", "s1").inOrder()
        val r2 = rows.first { it.sessionId == "s2" }
        assertThat(r2.title).isEqualTo("Push A")
        assertThat(r2.subtitle).isEqualTo("Upper Lower")
        assertThat(r2.minutes).isEqualTo(62)
        assertThat(r2.workingSets).isEqualTo(2)
        assertThat(r2.volumeKg).isWithin(0.001).of(60.0 * 9 + 65.0 * 8)
        assertThat(r2.prCount).isEqualTo(2)
        val r1 = rows.first { it.sessionId == "s1" }
        assertThat(r1.prCount).isEqualTo(0) // the first time ever is never a record
        val r3 = rows.first { it.sessionId == "s3" }
        assertThat(r3.unfinished).isTrue()
        assertThat(r3.title).isEqualTo("Workout")
        assertThat(r3.minutes).isNull()
    }

    @Test
    fun `groups by calendar week`() {
        val rows = TrainingLog.rows(listOf(s1, s2, s3, s5), sets, days)
        val weeks = TrainingLog.weeks(rows, now, zone)
        assertThat(weeks.map { it.label }.take(2)).containsExactly("This week", "Last week").inOrder()
        assertThat(weeks[0].sessions.map { it.sessionId }).containsExactly("s3", "s2").inOrder()
        assertThat(weeks[1].sessions.map { it.sessionId }).containsExactly("s5")
        assertThat(weeks[2].label).isEqualTo("26 Oct to 1 Nov")
        assertThat(weeks[2].sessions.map { it.sessionId }).containsExactly("s1")
    }

    @Test
    fun `exercise history has best set, heaviest, reps at each weight, volume and the last sessions`() {
        val stats = ExerciseStatsBuilder.build(sets.filter { it.exerciseId == "bench" })
        assertThat(stats.sessionCount).isEqualTo(2)
        assertThat(stats.totalSets).isEqualTo(4) // the warm-up is not counted
        assertThat(stats.heaviestKg).isEqualTo(65.0)
        assertThat(stats.bestSet!!.weightKg).isEqualTo(65.0)
        assertThat(stats.bestSet!!.reps).isEqualTo(8)
        assertThat(stats.bestRepsAtWeight).containsExactly(65.0 to 8, 60.0 to 9).inOrder()
        assertThat(stats.totalVolumeKg).isWithin(0.001).of(60.0 * 8 * 2 + 60.0 * 9 + 65.0 * 8)
        assertThat(stats.estimatedMaxSeries).hasSize(2)
        assertThat(stats.estimatedMaxSeries[0].first).isLessThan(stats.estimatedMaxSeries[1].first)
        assertThat(stats.sessions.map { it.sessionId }).containsExactly("s2", "s1").inOrder()
        assertThat(stats.recordCount).isEqualTo(2)
        assertThat(stats.sessions.first().records).isNotEmpty()
        assertThat(stats.sessions.last().records).isEmpty()
    }

    @Test
    fun `only the last ten sessions are listed but the chart and totals use all`() {
        val many = (0 until 14).map { i -> set("x$i", "sq", 0, 100.0 + i, 5, 1_000_000L * (i + 1)) }
        val stats = ExerciseStatsBuilder.build(many)
        assertThat(stats.sessions).hasSize(10)
        assertThat(stats.sessions.first().sessionId).isEqualTo("x13")
        assertThat(stats.estimatedMaxSeries).hasSize(14)
        assertThat(stats.sessionCount).isEqualTo(14)
    }

    @Test
    fun `no history is empty`() {
        assertThat(ExerciseStatsBuilder.build(emptyList()).isEmpty).isTrue()
        val warmOnly = ExerciseStatsBuilder.build(listOf(set("a", "e", 0, 20.0, 10, 5L, warm = true)))
        assertThat(warmOnly.isEmpty).isTrue()
    }

    @Test
    fun `bodyweight sets count reps but have no estimated max`() {
        val stats = ExerciseStatsBuilder.build(listOf(set("a", "pu", 0, 0.0, 15, 5L), set("a", "pu", 1, 0.0, 12, 6L)))
        assertThat(stats.totalSets).isEqualTo(2)
        assertThat(stats.bestSet).isNull()
        assertThat(stats.estimatedMaxSeries).isEmpty()
        assertThat(stats.sessions).hasSize(1)
    }
}
