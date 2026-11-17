package com.zeroindex.intake.domain.training

/**
 * Pauses of one workout, kept outside the database (no schema change): the milliseconds already spent paused, and the
 * moment the current pause began, if one is running. Pure; the phone keeps one of these for the running session.
 */
data class WorkoutPause(val sessionId: String, val pausedMs: Long = 0L, val pausedSince: Long? = null) {
    val isPaused: Boolean get() = pausedSince != null

    fun pause(now: Long): WorkoutPause = if (isPaused) this else copy(pausedSince = now)

    fun resume(now: Long): WorkoutPause {
        val since = pausedSince ?: return this
        return copy(pausedMs = pausedMs + (now - since).coerceAtLeast(0), pausedSince = null)
    }

    /** All the time spent paused up to [now], including the pause that is still running. */
    fun totalPausedMs(now: Long): Long = pausedMs + (pausedSince?.let { (now - it).coerceAtLeast(0) } ?: 0L)
}

/** The workout clock: elapsed time minus the time spent paused. */
object WorkoutClock {

    /** Trained time so far. With no pause on record it is plain elapsed time. */
    fun trainedMs(startedAt: Long, pause: WorkoutPause?, now: Long): Long =
        (now - startedAt - (pause?.totalPausedMs(now) ?: 0L)).coerceAtLeast(0L)

    /**
     * The start time to save so that finish minus start is the trained time. The saved duration everywhere (summary,
     * history, Health Connect) is finish minus start, so shifting the start later by the paused time makes it count only
     * trained time without a new column. It never moves past the first logged set, so no set ends up before its own
     * workout began; a long pause before the first set is then only partly taken off.
     */
    fun netStart(startedAt: Long, pausedMs: Long, earliestSetAt: Long?, finishedAt: Long): Long {
        if (pausedMs <= 0L) return startedAt
        val shifted = startedAt + pausedMs
        val limit = minOf(earliestSetAt ?: finishedAt, finishedAt)
        return maxOf(startedAt, minOf(shifted, limit))
    }
}

/** The next set to do, for the "Up next" line in the rest strip. */
data class UpNextSet(val exerciseName: String, val setNumber: Int, val weightKg: Double, val reps: Int?, val bodyweight: Boolean)

/** One exercise as the up-next finder sees it, in session order. */
data class UpNextExercise(
    val name: String,
    val loggedSetIndexes: Set<Int>,
    val plannedSets: Int,
    val weightKg: Double,
    val reps: Int?,
    val bodyweight: Boolean,
)

object UpNext {

    /**
     * The first set still to do, starting at the exercise in hand ([currentIndex]) and moving on to the next ones when it is
     * finished. The numbers are the plan's (or last time's) target, not what is typed in a row. Null when everything is done.
     */
    fun find(exercises: List<UpNextExercise>, currentIndex: Int): UpNextSet? {
        if (exercises.isEmpty()) return null
        val start = currentIndex.coerceIn(0, exercises.lastIndex)
        val order = exercises.indices.drop(start) + exercises.indices.take(start)
        for (i in order) {
            val e = exercises[i]
            val open = (0 until e.plannedSets).firstOrNull { it !in e.loggedSetIndexes } ?: continue
            return UpNextSet(e.name, open + 1, e.weightKg, e.reps, e.bodyweight)
        }
        return null
    }

    /** "Bench press, set 3: 80 kg × 8". The weight text comes from the caller so it follows the person's unit. */
    fun text(next: UpNextSet, weightText: String): String = buildString {
        append(next.exerciseName).append(", set ").append(next.setNumber)
        val numbers = listOfNotNull(weightText.takeIf { it.isNotBlank() }, next.reps?.let { "× $it" }).joinToString(" ")
        if (numbers.isNotBlank()) append(": ").append(numbers)
    }
}
