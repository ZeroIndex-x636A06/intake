package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Q11-3: a set-level record chip goes to the set that set the record, never to a neighbour. */
class SetRecordPlacementTest {
    private fun set(kg: Double, reps: Int) = SummarySet("sq", "Back Squat", "quads", emptyList(), kg, reps)
    private val before = mapOf("sq" to listOf(SetResult(70.0, 5, sessionId = "old"), SetResult(72.0, 5, sessionId = "old")))

    @Test fun `the heaviest best set gets the chip, not the lighter set after it`() {
        // Set 1 is the heaviest weight and the best estimated max; Set 2 is lighter.
        val summary = SessionSummaries.build(listOf(set(72.5, 7), set(72.5, 6), set(72.5, 5)), before, 0L, 900_000L)
        val bySet = SessionSummaries.setRecords(summary.records, "Back Squat")
        assertThat(bySet.keys).containsExactly(0)
        assertThat(bySet[0]).isEqualTo(PrKind.ESTIMATED_MAX)
    }

    @Test fun `a later set that earns the record gets it`() {
        val summary = SessionSummaries.build(listOf(set(70.0, 5), set(75.0, 5)), before, 0L, 900_000L)
        val bySet = SessionSummaries.setRecords(summary.records, "Back Squat")
        assertThat(bySet.keys).containsExactly(1)
    }

    @Test fun `the session volume record is not on any set`() {
        val summary = SessionSummaries.build(List(6) { set(72.0, 5) }, before, 0L, 900_000L)
        assertThat(summary.records.map { it.kind }).containsExactly(PrKind.BEST_VOLUME)
        assertThat(SessionSummaries.setRecords(summary.records, "Back Squat")).isEmpty()
    }

    @Test fun `a repeated best set is a record only on its first`() {
        val summary = SessionSummaries.build(listOf(set(80.0, 5), set(80.0, 5)), before, 0L, 900_000L)
        val bySet = SessionSummaries.setRecords(summary.records, "Back Squat")
        assertThat(bySet.keys).containsExactly(0)
    }
}
