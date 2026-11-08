package com.zeroindex.intake.domain.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MergePlanTest {

    @Test
    fun `new, newer, older and identical rows are each handled`() {
        val local = listOf(RowVersion("a", 100), RowVersion("b", 200), RowVersion("c", 300))
        val incoming = listOf(
            RowVersion("a", 150), // newer in backup -> update
            RowVersion("b", 150), // older in backup -> keep local
            RowVersion("c", 300), // identical -> nothing
            RowVersion("d", 50),  // new -> add
        )
        val plan = MergePlan.plan("t", local, incoming)
        assertThat(plan.added).isEqualTo(1)
        assertThat(plan.updated).isEqualTo(1)
        assertThat(plan.keptLocal).isEqualTo(1)
        assertThat(plan.unchanged).isEqualTo(1)
        assertThat(plan.writes).containsExactly("a", "a", "d", "d")
    }

    @Test
    fun `restoring an old backup never undoes a later deletion`() {
        // Deleted locally at 500; the backup has the live row from 400.
        val plan = MergePlan.plan("t", listOf(RowVersion("x", 500)), listOf(RowVersion("x", 400)))
        assertThat(plan.writes).isEmpty()
        assertThat(plan.keptLocal).isEqualTo(1)
    }

    @Test
    fun `a natural key merges a row with a different id into the local one`() {
        val plan = MergePlan.plan(
            "health_daily_summary",
            listOf(RowVersion("local-id", 100, naturalKey = "day-1")),
            listOf(RowVersion("other-device-id", 200, naturalKey = "day-1")),
        )
        assertThat(plan.updated).isEqualTo(1)
        assertThat(plan.writes).containsExactly("other-device-id", "local-id")
    }
}
