package com.zeroindex.intake.domain.training.history

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Why a change to a finished session's time was refused. */
sealed interface TimeProblem {
    /** The session is still running: its times are the clock, not something to edit. */
    data object NotFinished : TimeProblem

    /** The length must be a whole number of minutes from 1 up to [SessionEditor.MAX_DURATION_MIN]. */
    data object BadDuration : TimeProblem

    /** The workout would end after now. */
    data object InFuture : TimeProblem

    /** A logged set would fall after the end: the workout must last at least [minMinutes]. */
    data class TooShort(val minMinutes: Int) : TimeProblem
}

/** The new times of a finished session, and how far its sets move with it. */
data class TimeEdit(val startedAt: Long, val finishedAt: Long, val setShiftMillis: Long)

sealed interface TimeOutcome {
    data class Ok(val edit: TimeEdit) : TimeOutcome
    data class Refused(val problem: TimeProblem) : TimeOutcome
}

/**
 * Editing a finished session (history): its date and start time, its length, and the sets in it. Pure rules, so every
 * check is tested; the repository only writes what these return. Times are UTC epoch millis, shown in local time.
 */
object SessionEditor {
    const val MAX_DURATION_MIN = 24 * 60
    private const val MINUTE = 60_000L

    /** A local date and time of day as UTC millis. A time that does not exist (the clocks went forward) moves on. */
    fun combine(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /**
     * New start and length for a finished session. The sets keep their place in the workout, so they move by the same
     * amount as the start. Refused when the length is not 1 minute to 24 hours, when the end would be after [now], or when
     * the length would cut off the last logged set ([lastSetAt] is the set's time before the move).
     */
    fun retime(
        startedAt: Long,
        finishedAt: Long?,
        lastSetAt: Long?,
        newStartedAt: Long,
        newDurationMin: Int,
        now: Long,
    ): TimeOutcome {
        if (finishedAt == null) return TimeOutcome.Refused(TimeProblem.NotFinished)
        if (newDurationMin < 1 || newDurationMin > MAX_DURATION_MIN) return TimeOutcome.Refused(TimeProblem.BadDuration)
        val newFinish = newStartedAt + newDurationMin * MINUTE
        if (newFinish > now) return TimeOutcome.Refused(TimeProblem.InFuture)
        val shift = newStartedAt - startedAt
        if (lastSetAt != null) {
            val lastNow = lastSetAt + shift
            if (lastNow > newFinish) {
                val needed = Math.ceil((lastNow - newStartedAt) / MINUTE.toDouble()).toInt().coerceAtLeast(1)
                return TimeOutcome.Refused(TimeProblem.TooShort(needed))
            }
        }
        return TimeOutcome.Ok(TimeEdit(newStartedAt, newFinish, shift))
    }

    /** The length of a session in whole minutes, never below 1 for a finished one. */
    fun durationMin(startedAt: Long, finishedAt: Long): Int = ((finishedAt - startedAt) / MINUTE).toInt().coerceAtLeast(1)

    /**
     * When a set added afterwards counts as done: a minute after the latest set already in the session, kept inside the
     * session (not before it started, not after it ended).
     */
    fun newSetTime(startedAt: Long, finishedAt: Long, existingTimes: List<Long>): Long {
        val wanted = (existingTimes.maxOrNull()?.plus(MINUTE)) ?: startedAt
        return wanted.coerceIn(startedAt, maxOf(startedAt, finishedAt))
    }

    /** The index for a new set of an exercise: one after the highest it has, 0 for the first. */
    fun nextSetIndex(existingIndexes: List<Int>): Int = (existingIndexes.maxOrNull() ?: -1) + 1

    /** True when moving a set to [newExerciseId] changes something. */
    fun canChangeExercise(currentExerciseId: String, newExerciseId: String): Boolean =
        newExerciseId.isNotBlank() && newExerciseId != currentExerciseId

    /** Plain words for a refused time change. */
    fun words(problem: TimeProblem): String = when (problem) {
        TimeProblem.NotFinished -> "This workout is still running."
        TimeProblem.BadDuration -> "The length must be from 1 minute to 24 hours."
        TimeProblem.InFuture -> "That workout would end in the future. Pick an earlier start or a shorter length."
        is TimeProblem.TooShort -> "A logged set falls after that end. The workout must last at least ${problem.minMinutes} min."
    }
}
