package com.zeroindex.intake.domain.training

import kotlin.math.abs

/** A weight and the reps done with it. A [weightKg] of 0 is a bodyweight set with nothing added. */
data class WeightReps(val weightKg: Double, val reps: Int)

/** The best estimated one-rep max, with the set it came from. */
data class BestEstimate(val estimatedKg: Double, val weightKg: Double, val reps: Int)

/** The biggest total weight x reps of one exercise in one session. */
data class BestVolume(val volumeKg: Double, val sets: Int)

/** A session whose volume for an exercise beat every earlier session. */
data class VolumeRecord(val volumeKg: Double, val previousKg: Double)

/**
 * The four records of one exercise: heaviest weight, most reps at each weight, best estimated 1RM and best session
 * volume. Computed from the logged sets every time, never stored. Bodyweight sets (no added weight) can only earn the
 * rep record: they have no weight to be heaviest, no 1RM to estimate and no volume to add up.
 */
data class ExerciseRecords(
    /** The heaviest set, with the most reps done at that weight. Null with no weighted set. */
    val heaviest: WeightReps?,
    /** Most reps at each weight lifted, lightest first. A weight of 0 is the bodyweight record. */
    val repRecords: List<WeightReps>,
    val bestEstimatedMax: BestEstimate?,
    val bestVolume: BestVolume?,
) {
    val isEmpty: Boolean get() = heaviest == null && repRecords.isEmpty() && bestEstimatedMax == null && bestVolume == null

    /** Most reps in one set with no added weight, or null if that was never logged. */
    val bodyweightReps: Int? get() = repRecords.firstOrNull { it.weightKg == 0.0 }?.reps
}

object ExerciseRecordsCalc {
    private const val EPS = 0.05

    /** The records over [sets], which must be working sets only (the caller leaves warm-ups out). */
    fun compute(sets: List<SetResult>): ExerciseRecords {
        val valid = sets.filter { it.reps >= 1 && it.weightKg >= 0 }
        if (valid.isEmpty()) return ExerciseRecords(null, emptyList(), null, null)
        val weighted = valid.filter { it.weightKg > 0 }

        val heaviestKg = weighted.maxOfOrNull { it.weightKg }
        val heaviest = heaviestKg?.let { w -> WeightReps(w, weighted.filter { abs(it.weightKg - w) < 0.01 }.maxOf { it.reps }) }

        val repRecords = valid.groupBy { Math.round(it.weightKg * 100) }
            .map { (_, same) -> WeightReps(same.maxOf { it.weightKg }, same.maxOf { it.reps }) }
            .sortedBy { it.weightKg }

        val top = weighted.maxByOrNull { OneRepMax.estimate(it.weightKg, it.reps) }
        val estimate = top?.let { BestEstimate(OneRepMax.estimate(it.weightKg, it.reps), it.weightKg, it.reps) }

        val volume = sessionVolumes(valid).values.maxByOrNull { it.first }?.takeIf { it.first > 0 }?.let { BestVolume(it.first, it.second) }
        return ExerciseRecords(heaviest, repRecords, estimate, volume)
    }

    /** Volume (kg x reps) and set count per session. Sets of unknown session count as one session. */
    private fun sessionVolumes(sets: List<SetResult>): Map<String, Pair<Double, Int>> =
        sets.groupBy { it.sessionId.orEmpty() }.mapValues { (_, list) -> list.sumOf { it.weightKg * it.reps } to list.size }

    /**
     * Whether a session's sets of one exercise ([session]) have a bigger volume than any earlier session in [history].
     * Needs an earlier session with weight lifted: the first time ever is not a record, and a bodyweight exercise has no
     * volume to beat.
     */
    fun volumeRecord(history: List<SetResult>, session: List<SetResult>): VolumeRecord? {
        val past = history.filter { it.reps >= 1 && it.weightKg >= 0 }
        val best = sessionVolumes(past).values.maxOfOrNull { it.first } ?: return null
        if (best <= 0.0) return null
        val now = session.filter { it.reps >= 1 && it.weightKg > 0 }.sumOf { it.weightKg * it.reps }
        return if (now > best + EPS) VolumeRecord(now, best) else null
    }
}
