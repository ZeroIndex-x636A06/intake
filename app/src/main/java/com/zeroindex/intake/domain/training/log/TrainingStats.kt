package com.zeroindex.intake.domain.training.log

import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.PrDetector
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.SetResult

/** A logged set with its exercise name and the time its session started, for the Progress training block. */
data class StatSet(
    val sessionId: String,
    val sessionStartedAt: Long,
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAt: Long,
)

/** The best estimated one-rep max of one exercise, with the set it came from. */
data class LiftEstimate(
    val exerciseId: String,
    val name: String,
    val estimatedMaxKg: Double,
    val weightKg: Double,
    val reps: Int,
    val at: Long,
    val workingSets: Int,
)

data class RecentPr(val exerciseName: String, val weightKg: Double, val reps: Int, val at: Long, val kind: PrKind)

data class TrainingStatsData(val lifts: List<LiftEstimate>, val recentPrs: List<RecentPr>) {
    val isEmpty: Boolean get() = lifts.isEmpty()
}

/** Estimated 1RM per most-trained lift and the latest personal records, from logged working sets. Warm-ups never count. */
object TrainingStats {
    const val MAX_LIFTS = 4
    const val MAX_PRS = 5

    fun build(sets: List<StatSet>, lifts: Int = MAX_LIFTS, prs: Int = MAX_PRS): TrainingStatsData {
        val working = sets.filter { !it.isWarmup && it.weightKg > 0 && it.reps >= 1 }
        if (working.isEmpty()) return TrainingStatsData(emptyList(), emptyList())

        val estimates = working.groupBy { it.exerciseId }.map { (id, list) ->
            val best = list.maxBy { OneRepMax.epley(it.weightKg, it.reps) }
            LiftEstimate(id, best.exerciseName, OneRepMax.epley(best.weightKg, best.reps), best.weightKg, best.reps, best.completedAt, list.size)
        }.sortedWith(compareByDescending<LiftEstimate> { it.workingSets }.thenBy { it.name }).take(lifts)

        val history = HashMap<String, MutableList<SetResult>>()
        val records = ArrayList<RecentPr>()
        for (s in working.sortedWith(compareBy({ it.sessionStartedAt }, { it.completedAt }, { it.setIndex }))) {
            val past = history.getOrPut(s.exerciseId) { mutableListOf() }
            val kind = PrDetector.check(s.weightKg, s.reps, past)
            if (kind != PrKind.NONE) records += RecentPr(s.exerciseName, s.weightKg, s.reps, s.completedAt, kind)
            past += SetResult(s.weightKg, s.reps)
        }
        return TrainingStatsData(estimates, records.sortedByDescending { it.at }.take(prs))
    }
}
