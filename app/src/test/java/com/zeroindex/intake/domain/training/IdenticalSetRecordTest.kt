package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Q10-7: a set equal to the best already done is not a better lift. */
class IdenticalSetRecordTest {
    private val best = listOf(SetResult(22.68, 8, sessionId = "old"), SetResult(22.68, 8, sessionId = "old"), SetResult(22.68, 8, sessionId = "old"))

    @Test fun `an identical set is not a record`() {
        assertThat(PrDetector.kinds(22.68, 8, best)).isEmpty()
        assertThat(PrDetector.check(22.68, 8, best)).isEqualTo(PrKind.NONE)
    }

    @Test fun `a fourth identical set earns no lift record, only the day's volume`() {
        fun set(n: Int) = SummarySet("e", "Incline Dumbbell Press", "chest", emptyList(), 22.68, 8)
        val three = SessionSummaries.build(List(3) { set(it) }, mapOf("e" to best), 0L, 900_000L)
        assertThat(three.records).isEmpty()
        val four = SessionSummaries.build(List(4) { set(it) }, mapOf("e" to best), 0L, 900_000L)
        assertThat(four.records.map { it.kind }).containsExactly(PrKind.BEST_VOLUME)
        assertThat(RecordText.chipLabel(four.records.map { it.kind })).isEqualTo("Volume PR")
    }

    @Test fun `the chip says PR for a better set and nothing for no record`() {
        assertThat(RecordText.chipLabel(listOf(PrKind.REP_RECORD, PrKind.BEST_VOLUME))).isEqualTo("PR")
        assertThat(RecordText.chipLabel(emptyList())).isNull()
    }
}
