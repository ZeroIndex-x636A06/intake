package com.zeroindex.intake.domain.training.log

import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.PrDetector
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.WeeklyVolume
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A logged set, with just what the log and the exercise history need. */
data class LogSet(
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val isWarmup: Boolean,
    val completedAt: Long,
)

data class LogSession(val id: String, val startedAt: Long, val finishedAt: Long?, val dayId: String?)

/** A program day's names, for a session's title. */
data class LogDay(val dayName: String, val programName: String?, val standalone: Boolean)

/** One line of the Training log. */
data class SessionLogRow(
    val sessionId: String,
    val startedAt: Long,
    val title: String,
    /** The program the day belongs to; null for a freestyle workout or a saved workout. */
    val subtitle: String?,
    val minutes: Int?,
    val workingSets: Int,
    val volumeKg: Double,
    val prCount: Int,
    val unfinished: Boolean,
)

data class LogWeek(val weekStart: Long, val label: String, val sessions: List<SessionLogRow>) {
    val volumeKg: Double get() = sessions.sumOf { it.volumeKg }
}

/**
 * The Training log: every session, newest first, grouped by calendar week (Monday start). Sessions started from the watch
 * are ordinary sessions on the phone, so they are in the list like any other; a session that was never finished still shows
 * when it has sets (marked unfinished), and an empty one that was never finished is left out.
 */
object TrainingLog {

    fun rows(sessions: List<LogSession>, sets: List<LogSet>, days: Map<String, LogDay>): List<SessionLogRow> {
        val setsBySession = sets.groupBy { it.sessionId }
        val shown = sessions.filter { it.finishedAt != null || !setsBySession[it.id].isNullOrEmpty() }
        val prs = prCounts(sessions, sets)
        return shown.sortedByDescending { it.startedAt }.map { s ->
            val working = setsBySession[s.id].orEmpty().filter { !it.isWarmup }
            val day = s.dayId?.let { days[it] }
            SessionLogRow(
                sessionId = s.id,
                startedAt = s.startedAt,
                title = day?.dayName ?: "Workout",
                subtitle = day?.programName?.takeIf { !day.standalone && it != day.dayName },
                minutes = s.finishedAt?.let { ((it - s.startedAt) / 60_000).toInt().coerceAtLeast(0) },
                workingSets = working.size,
                volumeKg = working.sumOf { it.weightKg * it.reps },
                prCount = prs[s.id] ?: 0,
                unfinished = s.finishedAt == null,
            )
        }
    }

    /** Records per session, counted in the order the sets were done: a set is a record against everything before it. */
    fun prCounts(sessions: List<LogSession>, sets: List<LogSet>): Map<String, Int> {
        val startOf = sessions.associate { it.id to it.startedAt }
        val ordered = sets.filter { !it.isWarmup && it.sessionId in startOf }
            .sortedWith(compareBy({ startOf.getValue(it.sessionId) }, { it.completedAt }, { it.setIndex }))
        val history = HashMap<String, MutableList<SetResult>>()
        val out = HashMap<String, Int>()
        for (s in ordered) {
            val past = history.getOrPut(s.exerciseId) { mutableListOf() }
            if (PrDetector.check(s.weightKg, s.reps, past) != PrKind.NONE) out[s.sessionId] = (out[s.sessionId] ?: 0) + 1
            past += SetResult(s.weightKg, s.reps)
        }
        return out
    }

    /** Groups newest-first rows by calendar week; labels are "This week", "Last week", then "3 Nov to 9 Nov". */
    fun weeks(rows: List<SessionLogRow>, now: Long, zone: ZoneId): List<LogWeek> {
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        val thisWeek = WeeklyVolume.weekStart(now, zone)
        return rows.groupBy { WeeklyVolume.weekStart(it.startedAt, zone) }
            .toSortedMap(compareByDescending { it })
            .map { (start, list) ->
                val first = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val label = when (start) {
                    thisWeek -> "This week"
                    WeeklyVolume.weekStart(Instant.ofEpochMilli(thisWeek).atZone(zone).toLocalDate().minusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli(), zone) -> "Last week"
                    else -> "${fmt.format(first)} to ${fmt.format(first.plusDays(6))}"
                }
                LogWeek(start, label, list.sortedByDescending { it.startedAt })
            }
    }
}

/** One best set, with its estimated max. */
data class BestSet(val weightKg: Double, val reps: Int, val estimatedMaxKg: Double, val at: Long)

/** One session's sets of one exercise. [records] are the indexes (into [sets]) of the working sets that were records then. */
data class ExerciseSession(val sessionId: String, val at: Long, val sets: List<LogSet>, val records: Set<Int>)

data class ExerciseStats(
    val bestSet: BestSet?,
    val heaviestKg: Double?,
    /** The most reps done at each weight, heaviest first. */
    val bestRepsAtWeight: List<Pair<Double, Int>>,
    val totalVolumeKg: Double,
    val totalSets: Int,
    val sessionCount: Int,
    /** (time, best estimated max of that session), oldest first: the chart. */
    val estimatedMaxSeries: List<Pair<Long, Double>>,
    /** The last sessions with this exercise, newest first. */
    val sessions: List<ExerciseSession>,
    val recordCount: Int,
) {
    val isEmpty: Boolean get() = sessionCount == 0
}

/** Everything the "Your history" section of an exercise shows, from its logged sets. Warm-ups are never counted. */
object ExerciseStatsBuilder {
    const val RECENT_SESSIONS = 10

    fun build(sets: List<LogSet>, limit: Int = RECENT_SESSIONS): ExerciseStats {
        val working = sets.filter { !it.isWarmup }
        if (working.isEmpty()) return ExerciseStats(null, null, emptyList(), 0.0, 0, 0, emptyList(), emptyList(), 0)

        val bySession = sets.groupBy { it.sessionId }
            .map { (id, list) -> id to list.sortedWith(compareBy({ it.completedAt }, { it.setIndex })) }
            .filter { (_, list) -> list.any { !it.isWarmup } }
            .sortedBy { (_, list) -> list.first().completedAt }

        // Records, in the order the sets were done.
        val past = mutableListOf<SetResult>()
        var recordCount = 0
        val sessions = bySession.map { (id, list) ->
            val records = mutableSetOf<Int>()
            list.forEachIndexed { i, s ->
                if (s.isWarmup) return@forEachIndexed
                if (PrDetector.check(s.weightKg, s.reps, past) != PrKind.NONE) {
                    records += i
                    recordCount++
                }
                past += SetResult(s.weightKg, s.reps)
            }
            ExerciseSession(id, list.first().completedAt, list, records)
        }

        val rated = working.filter { it.weightKg > 0 && it.reps >= 1 }
        val best = rated.maxByOrNull { OneRepMax.epley(it.weightKg, it.reps) }
        val series = sessions.mapNotNull { s ->
            val e = s.sets.filter { !it.isWarmup && it.weightKg > 0 && it.reps >= 1 }.maxOfOrNull { OneRepMax.epley(it.weightKg, it.reps) }
            e?.let { s.at to it }
        }
        val repsAt = rated.groupBy { Math.round(it.weightKg * 100) / 100.0 }
            .map { (w, list) -> w to list.maxOf { it.reps } }
            .sortedByDescending { it.first }

        return ExerciseStats(
            bestSet = best?.let { BestSet(it.weightKg, it.reps, OneRepMax.epley(it.weightKg, it.reps), it.completedAt) },
            heaviestKg = rated.maxOfOrNull { it.weightKg },
            bestRepsAtWeight = repsAt,
            totalVolumeKg = working.sumOf { it.weightKg * it.reps },
            totalSets = working.size,
            sessionCount = sessions.size,
            estimatedMaxSeries = series,
            sessions = sessions.sortedByDescending { it.at }.take(limit),
            recordCount = recordCount,
        )
    }
}
