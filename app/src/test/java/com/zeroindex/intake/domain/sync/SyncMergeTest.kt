package com.zeroindex.intake.domain.sync

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.backup.RowVersion
import org.junit.Test

class SyncMergeTest {

    private fun local(id: String, at: Long, hash: String, key: String? = null) = LocalRow(RowVersion(id, at, key, hash), "{\"local\":\"$hash\"}")
    private fun remote(id: String, at: Long, hash: String, key: String? = null) = IncomingRow(RowVersion(id, at, key, hash), "{\"remote\":\"$hash\"}")

    @Test
    fun `new rows are written and identical ones skipped`() {
        val plan = SyncMerge.plan(
            local = listOf(local("a", 10, "h1")),
            incoming = listOf(remote("a", 10, "h1"), remote("b", 5, "h2")),
            pushedUpTo = 0,
        )
        assertThat(plan.writes).containsExactly(1 to "b")
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun `a remote change to a row not edited here is just applied`() {
        val plan = SyncMerge.plan(listOf(local("a", 10, "h1")), listOf(remote("a", 20, "h2")), pushedUpTo = 15)
        assertThat(plan.writes).containsExactly(0 to "a")
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun `concurrent edit where the remote one is newer keeps ours as a conflict`() {
        val plan = SyncMerge.plan(listOf(local("a", 30, "mine")), listOf(remote("a", 40, "theirs")), pushedUpTo = 20)
        assertThat(plan.writes).containsExactly(0 to "a")
        val c = plan.conflicts.single()
        assertThat(c.otherSource).isEqualTo(SyncMerge.THIS_DEVICE)
        assertThat(c.keptJson).contains("theirs")
        assertThat(c.otherJson).contains("mine")
    }

    @Test
    fun `concurrent edit where ours is newer keeps theirs as a conflict and writes nothing`() {
        val plan = SyncMerge.plan(listOf(local("a", 50, "mine")), listOf(remote("a", 40, "theirs")), pushedUpTo = 20)
        assertThat(plan.writes).isEmpty()
        assertThat(plan.conflicts.single().otherSource).isEqualTo(SyncMerge.OTHER_DEVICE)
    }

    @Test
    fun `an old echo of a row already pushed is not a conflict`() {
        val plan = SyncMerge.plan(listOf(local("a", 50, "new")), listOf(remote("a", 40, "old")), pushedUpTo = 60)
        assertThat(plan.writes).isEmpty()
        assertThat(plan.conflicts).isEmpty()
    }

    @Test
    fun `natural keys merge the same day from two devices into the local row`() {
        val plan = SyncMerge.plan(
            listOf(local("phone-id", 10, "p", key = "day-1")),
            listOf(remote("tablet-id", 20, "t", key = "day-1")),
            pushedUpTo = 100,
        )
        assertThat(plan.writes).containsExactly(0 to "phone-id")
    }

    @Test
    fun `when one pull carries two versions of a row the winner is applied`() {
        val plan = SyncMerge.plan(emptyList(), listOf(remote("a", 10, "x"), remote("a", 30, "y"), remote("a", 20, "z")), pushedUpTo = 0)
        assertThat(plan.writes).containsExactly(1 to "a")
    }

    @Test
    fun `conflict tracking can be switched off for derived tables`() {
        val plan = SyncMerge.plan(listOf(local("a", 30, "mine")), listOf(remote("a", 40, "theirs")), pushedUpTo = 20, trackConflicts = false)
        assertThat(plan.writes).hasSize(1)
        assertThat(plan.conflicts).isEmpty()
    }
}
