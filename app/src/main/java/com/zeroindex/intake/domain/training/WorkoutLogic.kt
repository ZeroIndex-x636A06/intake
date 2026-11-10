package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.training.muscles.MuscleHighlight
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** "Per side: 45 + 25 + 2.5": the plate line under a barbell set. [display] converts a kg plate into the shown unit. */
object PlateLine {
    fun text(perSide: List<Double>, display: (Double) -> Double = { it }): String {
        if (perSide.isEmpty()) return "Per side: no plates, just the bar"
        return "Per side: " + perSide.sortedDescending().joinToString(" + ") { fmt(display(it)) }
    }

    private fun fmt(v: Double): String {
        val r = (v * 4).roundToInt() / 4.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().trimEnd('0')
    }
}

/**
 * The "Previous" column of a set row: the same set of the last session. If last time had fewer sets, the last one it had.
 * Warm-ups are not passed in. [lastSession] is in set order.
 */
object PreviousSets {
    fun forSet(lastSession: List<SetResult>, setIndex: Int): SetResult? =
        lastSession.getOrNull(setIndex) ?: lastSession.lastOrNull()
}

enum class PrKind(val label: String) {
    NONE(""),
    ESTIMATED_MAX("New estimated max"),
    REP_RECORD("Rep record"),
}

/**
 * Live PR flag while logging. A set is a record when it beats the best estimated 1-rep max of the history, or has more reps
 * than any earlier set at the same weight. With no history the first set is not a record ("first time ever" is not one).
 */
object PrDetector {
    private const val EPS = 0.05

    fun check(weightKg: Double, reps: Int, history: List<SetResult>): PrKind {
        if (weightKg <= 0.0 || reps < 1) return PrKind.NONE
        val past = history.filter { it.weightKg > 0 && it.reps >= 1 }
        if (past.isEmpty()) return PrKind.NONE
        val bestMax = OneRepMax.best(past) ?: return PrKind.NONE
        if (OneRepMax.epley(weightKg, reps) > bestMax + EPS) return PrKind.ESTIMATED_MAX
        val sameWeight = past.filter { abs(it.weightKg - weightKg) < 0.01 }
        if (sameWeight.isNotEmpty() && reps > sameWeight.maxOf { it.reps }) return PrKind.REP_RECORD
        return PrKind.NONE
    }
}

/** What a swap candidate is, for ranking. */
data class SwapCandidate(
    val id: String,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val equipment: String,
    val lastUsedAt: Long? = null,
)

data class RankedSwap(val candidate: SwapCandidate, val similarity: Int, val ownsEquipment: Boolean)

/**
 * Swap exercise: the alternatives for the exercise being replaced, best first. Ranked by muscle similarity (same main muscle,
 * then helpers shared), and within the same similarity by whether the person owns the equipment (Settings > Training >
 * My equipment), then by the most recently used, then by name. Candidates that share no muscle are left out.
 */
object SwapRanking {
    const val SAME_PRIMARY = 10
    const val CANDIDATE_IS_HELPER = 4
    const val CURRENT_IS_HELPER_OF_CANDIDATE = 3
    const val SHARED_HELPER = 1

    fun similarity(current: SwapCandidate, other: SwapCandidate): Int {
        var s = 0
        val p = current.primaryMuscle.lowercase()
        val op = other.primaryMuscle.lowercase()
        val sec = current.secondaryMuscles.map { it.lowercase() }.toSet()
        val osec = other.secondaryMuscles.map { it.lowercase() }.toSet()
        if (p == op) s += SAME_PRIMARY
        if (op in sec && p != op) s += CANDIDATE_IS_HELPER
        if (p in osec && p != op) s += CURRENT_IS_HELPER_OF_CANDIDATE
        s += (sec intersect osec).size * SHARED_HELPER
        return s
    }

    /** Whether the person can do an exercise: no equipment named, bodyweight, or an owned kind. */
    fun owns(equipment: String, owned: Set<String>): Boolean {
        val e = equipment.trim().lowercase()
        return e.isEmpty() || e == "bodyweight" || e == "none" || owned.any { it.lowercase() == e }
    }

    fun rank(
        current: SwapCandidate,
        candidates: List<SwapCandidate>,
        owned: Set<String>,
        excludeIds: Set<String> = emptySet(),
    ): List<RankedSwap> = candidates
        .filter { it.id != current.id && it.id !in excludeIds }
        .map { RankedSwap(it, similarity(current, it), owns(it.equipment, owned)) }
        .filter { it.similarity > 0 }
        .sortedWith(
            compareByDescending<RankedSwap> { it.similarity }
                .thenByDescending { it.ownsEquipment }
                .thenByDescending { it.candidate.lastUsedAt ?: 0L }
                .thenBy { it.candidate.name },
        )
}

/**
 * The weight to carry over to a swapped-in exercise: its own last working weight if there is one; otherwise a conservative
 * estimate from the weight of the exercise it replaces, scaled by how the equipment differs (a dumbbell is held per hand, a
 * machine moves less than a free bar) and cut by 15%, rounded down to the plate step. Null when there is nothing to go on.
 */
object SwapWeight {
    private const val CONSERVATIVE = 0.85

    fun carryOver(
        ownLastKg: Double?,
        replacedKg: Double?,
        fromEquipment: String,
        toEquipment: String,
        stepKg: Double = 2.5,
    ): Double? {
        if (ownLastKg != null && ownLastKg > 0) return ownLastKg
        val base = replacedKg?.takeIf { it > 0 } ?: return null
        val ratio = ratio(fromEquipment.lowercase().trim(), toEquipment.lowercase().trim())
        if (toEquipment.trim().lowercase() == "bodyweight") return null
        val raw = base * ratio * CONSERVATIVE
        val step = stepKg.coerceAtLeast(0.25)
        val rounded = Math.floor(raw / step) * step
        return rounded.takeIf { it > 0 }
    }

    private fun ratio(from: String, to: String): Double = when {
        from == to -> 1.0
        from == "barbell" && to == "dumbbell" -> 0.4
        from == "dumbbell" && to == "barbell" -> 2.2
        from == "barbell" && to == "machine" -> 0.9
        from == "machine" && to == "barbell" -> 0.8
        from == "barbell" && to == "cable" -> 0.5
        from == "cable" && to == "barbell" -> 1.5
        from == "dumbbell" && to == "cable" -> 1.0
        from == "cable" && to == "dumbbell" -> 1.0
        from == "dumbbell" && to == "machine" -> 1.8
        from == "machine" && to == "dumbbell" -> 0.55
        else -> 0.7
    }
}

/** A rough "about 55 min" for today's card: each set takes about 40 s of work plus its rest, and a warm-up is added. */
object SessionEstimate {
    private const val SET_WORK_SEC = 40
    private const val WARMUP_MIN = 5

    data class Slot(val sets: Int, val restSec: Int)

    fun minutes(slots: List<Slot>): Int {
        if (slots.isEmpty()) return 0
        val sec = slots.sumOf { it.sets * (SET_WORK_SEC + it.restSec) }
        val minutes = (sec / 60.0).roundToInt() + WARMUP_MIN
        return ((minutes + 2) / 5) * 5
    }
}

/** One finished set, for the summary. */
data class SummarySet(
    val exerciseId: String,
    val exerciseName: String,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean = false,
)

data class RecordLine(
    val exerciseName: String,
    val kind: PrKind,
    /** The new best (kg for [PrKind.ESTIMATED_MAX], reps for [PrKind.REP_RECORD]). */
    val value: Double,
    /** The earlier best, or null for a rep record at a new weight. */
    val previous: Double?,
    val weightKg: Double,
)

data class NextTimeChange(val exerciseName: String, val fromKg: Double, val toKg: Double, val reps: Int, val reason: String)

data class SessionSummary(
    val volumeKg: Double,
    val workingSets: Int,
    val totalReps: Int,
    val durationMin: Int,
    val records: List<RecordLine>,
    /** Weighted sets this session per region, most first. */
    val regionSets: Map<BodyRegion, Double>,
    val highlight: MuscleHighlight,
    /** Sets of this session by region as text, for the muscle list: "Chest 9 sets, Triceps 6". */
    val exercises: List<String>,
)

/** The numbers on the post-workout summary. Pure: the repository feeds it this session's sets and the history before it. */
object SessionSummaries {
    fun build(
        sets: List<SummarySet>,
        historyBefore: Map<String, List<SetResult>>,
        startedAt: Long,
        finishedAt: Long,
    ): SessionSummary {
        val working = sets.filter { !it.isWarmup }
        val volume = working.sumOf { it.weightKg * it.reps }
        val records = mutableListOf<RecordLine>()
        working.groupBy { it.exerciseId }.forEach { (id, exerciseSets) ->
            val before = historyBefore[id].orEmpty().filter { it.weightKg > 0 && it.reps >= 1 }
            if (before.isEmpty()) return@forEach // the first time ever is not a record
            val name = exerciseSets.first().exerciseName
            val bestBefore = OneRepMax.best(before)
            val topSet = exerciseSets.filter { it.weightKg > 0 && it.reps >= 1 }.maxByOrNull { OneRepMax.epley(it.weightKg, it.reps) }
            if (topSet != null && bestBefore != null) {
                val e = OneRepMax.epley(topSet.weightKg, topSet.reps)
                if (e > bestBefore + 0.05) records += RecordLine(name, PrKind.ESTIMATED_MAX, e, bestBefore, topSet.weightKg)
            }
            // Rep records: the best reps this session at a weight that was lifted before.
            exerciseSets.groupBy { Math.round(it.weightKg * 100) }.forEach { (_, same) ->
                val w = same.first().weightKg
                val earlier = before.filter { abs(it.weightKg - w) < 0.01 }
                val best = same.maxOf { it.reps }
                if (w > 0 && earlier.isNotEmpty() && best > earlier.maxOf { it.reps } &&
                    records.none { it.exerciseName == name && it.kind == PrKind.ESTIMATED_MAX && abs(it.weightKg - w) < 0.01 }
                ) {
                    records += RecordLine(name, PrKind.REP_RECORD, best.toDouble(), earlier.maxOf { it.reps }.toDouble(), w)
                }
            }
        }
        val regionSets = linkedMapOf<BodyRegion, Double>()
        working.forEach { s ->
            MuscleWeights.regionCredit(s.primaryMuscle, s.secondaryMuscles).forEach { (r, w) -> regionSets[r] = (regionSets[r] ?: 0.0) + w }
        }
        val primary = linkedSetOf<com.zeroindex.intake.domain.training.muscles.MuscleRegion>()
        val secondary = linkedSetOf<com.zeroindex.intake.domain.training.muscles.MuscleRegion>()
        working.map { it.exerciseId to it }.distinctBy { it.first }.forEach { (_, s) ->
            val h = MuscleMap.highlightFor(s.exerciseName, s.primaryMuscle, s.secondaryMuscles)
            primary += h.primary
            secondary += h.secondary
        }
        val minutes = ((finishedAt - startedAt).coerceAtLeast(0) / 60_000.0).roundToInt()
        return SessionSummary(
            volumeKg = volume,
            workingSets = working.size,
            totalReps = working.sumOf { it.reps },
            durationMin = minutes,
            records = records,
            regionSets = regionSets.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            highlight = MuscleHighlight(primary, secondary - primary),
            exercises = working.groupBy { it.exerciseName }.map { (n, l) -> "$n: ${l.size} sets" },
        )
    }

    /** "Bench press next time: 142.5 lb × 8", or null when the prescription did not change. */
    fun nextTime(
        exerciseName: String,
        usedKg: Double,
        nextKg: Double,
        reps: Int,
        reason: String,
    ): NextTimeChange? =
        if (nextKg > 0 && abs(nextKg - usedKg) >= 0.01) NextTimeChange(exerciseName, usedKg, nextKg, reps, reason) else null
}
