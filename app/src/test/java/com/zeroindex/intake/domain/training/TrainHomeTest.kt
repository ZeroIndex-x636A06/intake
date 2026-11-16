package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrainHomeTest {

    @Test
    fun `prescription and exercise lines read like the mockup`() {
        assertThat(TrainText.prescription(4, 8, 8)).isEqualTo("4 × 8")
        assertThat(TrainText.prescription(3, 8, 10)).isEqualTo("3 × 8–10")
        assertThat(TrainText.line("Bench press", 4, 8, 8)).isEqualTo("Bench press 4 × 8")
        assertThat(TrainText.prescription(2, 1, 99)).isEqualTo("2 × max")
    }

    @Test
    fun `the elapsed clock`() {
        assertThat(TrainText.clock(0)).isEqualTo("0:00")
        assertThat(TrainText.clock(24 * 60_000L + 10_000)).isEqualTo("24:10")
        assertThat(TrainText.clock(3_723_000)).isEqualTo("1:02:03")
        assertThat(TrainText.clock(-5)).isEqualTo("0:00")
    }

    @Test
    fun `rest day line names the next day and when`() {
        assertThat(TrainText.restLine("Pull A", 4, 2)).isEqualTo("Rest day, next: Pull A on Thursday")
        assertThat(TrainText.restLine("Pull A", 4, 1)).isEqualTo("Rest day, next: Pull A tomorrow")
        assertThat(TrainText.restLine("Pull A", null, 3)).isEqualTo("Rest day, next: Pull A")
    }

    @Test
    fun `set progress counts the set you are on`() {
        assertThat(TrainText.setsProgress(2, 15)).isEqualTo("set 3 of 15")
        assertThat(TrainText.setsProgress(15, 15)).isEqualTo("set 15 of 15")
        assertThat(TrainText.setsProgress(1, 0)).isEqualTo("1 set logged")
        assertThat(TrainText.setsProgress(0, 0)).isEqualTo("0 sets logged")
    }

    @Test
    fun `records title and volume formatting`() {
        assertThat(TrainText.recordsTitle(1)).isEqualTo("1 new record")
        assertThat(TrainText.recordsTitle(2)).isEqualTo("2 new records")
        assertThat(TrainText.thousands(11_420.0)).isEqualTo("11,420")
    }

    @Test
    fun `session totals use the larger of plan and logged`() {
        val slots = listOf(SessionProgress.Slot(4, 4), SessionProgress.Slot(3, 5), SessionProgress.Slot(3, 0))
        assertThat(SessionProgress.total(slots)).isEqualTo(12)
        assertThat(SessionProgress.done(slots)).isEqualTo(9)
    }

    @Test
    fun `the current exercise is the first with sets left`() {
        assertThat(SessionProgress.currentIndex(listOf(SessionProgress.Slot(3, 3), SessionProgress.Slot(3, 1), SessionProgress.Slot(3, 0)))).isEqualTo(1)
        assertThat(SessionProgress.currentIndex(listOf(SessionProgress.Slot(3, 3), SessionProgress.Slot(3, 3)))).isEqualTo(1)
        assertThat(SessionProgress.currentIndex(emptyList())).isEqualTo(0)
    }

    @Test
    fun `set rows are the plan plus a spare and keep a high logged index`() {
        assertThat(SetRows.plan(3, emptySet()).map { it.index }).containsExactly(0, 1, 2).inOrder()
        assertThat(SetRows.plan(3, setOf(0, 1)).map { it.logged }).containsExactly(true, true, false).inOrder()
        // An extra set added with "+ Add set".
        assertThat(SetRows.plan(3, setOf(0, 1, 2), addedExtra = 1)).hasSize(4)
        // A set logged beyond the plan (watch, or a past session) still shows.
        assertThat(SetRows.plan(2, setOf(0, 1, 4))).hasSize(5)
        // No plan at all (freestyle) still has a row to fill in.
        assertThat(SetRows.plan(0, emptySet())).hasSize(1)
    }
}
