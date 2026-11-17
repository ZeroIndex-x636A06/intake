package com.zeroindex.intake.domain.training.log

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrainingStatsTest {
    private fun set(session: String, day: Long, ex: String, kg: Double, reps: Int, warm: Boolean = false, idx: Int = 0) =
        StatSet(session, day * 86_400_000L, ex, ex.uppercase(), idx, kg, reps, warm, day * 86_400_000L + idx)

    @Test
    fun estimatesBestEpleyPerLiftAndIgnoresWarmups() {
        val data = TrainingStats.build(
            listOf(
                set("a", 1, "bench", 60.0, 5),
                set("a", 1, "bench", 100.0, 1, warm = true, idx = 1),
                set("b", 8, "bench", 62.5, 5),
            ),
        )
        assertThat(data.lifts).hasSize(1)
        assertThat(data.lifts[0].estimatedMaxKg).isWithin(0.01).of(62.5 * (1 + 5 / 30.0))
        assertThat(data.lifts[0].workingSets).isEqualTo(2)
    }

    @Test
    fun recentPrsAreRecordsAgainstEarlierSetsNewestFirst() {
        val data = TrainingStats.build(
            listOf(
                set("a", 1, "squat", 100.0, 5),
                set("b", 8, "squat", 105.0, 5),
                set("c", 15, "squat", 100.0, 3),
                set("d", 22, "squat", 110.0, 5),
            ),
        )
        assertThat(data.recentPrs.map { it.weightKg }).containsExactly(110.0, 105.0).inOrder()
    }

    @Test
    fun emptyWhenNothingLogged() {
        assertThat(TrainingStats.build(emptyList()).isEmpty).isTrue()
    }
}
