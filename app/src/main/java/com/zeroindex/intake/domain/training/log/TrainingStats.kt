package com.zeroindex.intake.domain.training.log

import com.zeroindex.intake.domain.training.ExerciseRecords
import com.zeroindex.intake.domain.training.ExerciseRecordsCalc
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

/** The four records of one exercise, for the Training stats screen. Bodyweight exercises are here too. */
data class LiftRecords(val exerciseId: String, val name: String, val records: ExerciseRecords)

data class TrainingStatsData(
    val lifts: List<LiftEstimate>,
    val recentPrs: List<RecentPr>,
    /** Records of the most-trained exercises, most sets first. */
    val records: List<LiftRecords> = emptyList(),
) {
    val isEmpty: Boolean get() = lifts.isEmpty() && records.isEmpty()
}

/** Estimated 1RM per most-trained lift and the latest personal records, from logged working sets. Warm-ups never count. */
object TrainingStats {
    const val MAX_LIFTS = 4
    const val MAX_PRS = 5

    fun build(sets: List<StatSet>, lifts: Int = MAX_LIFTS, prs: Int = MAX_PRS): TrainingStatsData {
        val logged = sets.filter { !it.isWarmup && it.weightKg >= 0 && it.reps >= 1 }
        val records = recordsOf(logged, lifts)
        val working = logged.filter { it.weightKg > 0 }
        if (working.isEmpty()) return TrainingStatsData(emptyList(), emptyList(), records)

        val estimates = working.groupBy { it.exerciseId }.map { (id, list) ->
            val best = list.maxBy { OneRepMax.estimate(it.weightKg, it.reps) }
            LiftEstimate(id, best.exerciseName, OneRepMax.estimate(best.weightKg, best.reps), best.weightKg, best.reps, best.completedAt, list.size)
        }.sortedWith(compareByDescending<LiftEstimate> { it.workingSets }.thenBy { it.name }).take(lifts)

        val history = HashMap<String, MutableList<SetResult>>()
        val recentPrs = ArrayList<RecentPr>()
        for (s in logged.sortedWith(compareBy({ it.sessionStartedAt }, { it.completedAt }, { it.setIndex }))) {
            val past = history.getOrPut(s.exerciseId) { mutableListOf() }
            val kind = PrDetector.check(s.weightKg, s.reps, past)
            if (kind != PrKind.NONE) recentPrs += RecentPr(s.exerciseName, s.weightKg, s.reps, s.completedAt, kind)
            past += SetResult(s.weightKg, s.reps)
        }
        return TrainingStatsData(estimates, recentPrs.sortedByDescending { it.at }.take(prs), records)
    }

    /** The records of the [count] exercises with the most logged sets, bodyweight ones included. */
    private fun recordsOf(logged: List<StatSet>, count: Int): List<LiftRecords> =
        logged.groupBy { it.exerciseId }.entries
            .sortedWith(compareByDescending<Map.Entry<String, List<StatSet>>> { it.value.size }.thenBy { it.value.first().exerciseName })
            .take(count)
            .map { (id, list) ->
                LiftRecords(id, list.first().exerciseName, ExerciseRecordsCalc.compute(list.map { SetResult(it.weightKg, it.reps, sessionId = it.sessionId) }))
            }
}
