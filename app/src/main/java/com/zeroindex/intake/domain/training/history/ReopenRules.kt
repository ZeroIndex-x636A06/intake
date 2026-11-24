package com.zeroindex.intake.domain.training.history

sealed interface ReopenOutcome {
    data object Allowed : ReopenOutcome
    data object NotFinished : ReopenOutcome
    data object WindowPassed : ReopenOutcome

    /** Only one workout can be running: this one is, and the person must decide what happens to it. */
    data class AnotherActive(val activeSessionId: String) : ReopenOutcome
}

/** Where a program stands: the week and the index of the day offered next. */
data class RotationPosition(val week: Int, val dayIndex: Int)

/** A reopened session's times: the new start, and how far its sets move with it. */
data class ResumedTimes(val startedAt: Long, val setShiftMillis: Long)

/**
 * Undoing Finish: when a finished session may be reopened as the active one, and how the program's rotation is put back.
 * A session can be reopened for 24 hours after it ended.
 */
object ReopenRules {
    const val WINDOW_MILLIS = 24L * 60 * 60 * 1000

    fun check(finishedAt: Long?, now: Long, activeSessionId: String?, thisSessionId: String): ReopenOutcome {
        if (finishedAt == null) return ReopenOutcome.NotFinished
        if (now - finishedAt > WINDOW_MILLIS) return ReopenOutcome.WindowPassed
        if (activeSessionId != null && activeSessionId != thisSessionId) return ReopenOutcome.AnotherActive(activeSessionId)
        return ReopenOutcome.Allowed
    }

    /**
     * The times of a session that is reopened at [now]. The time it sat finished is not workout time, so the start (and the
     * sets with it, as in [SessionEditor.retime]) moves later by that gap: the clock resumes at the length the workout had,
     * including a length the person edited, and Finish counts only the time trained since.
     */
    fun resume(startedAt: Long, finishedAt: Long, now: Long): ResumedTimes {
        val gap = (now - finishedAt).coerceAtLeast(0L)
        return ResumedTimes(startedAt + gap, gap)
    }

    /** What Finish did to a program's rotation: the next day, and a new week when it wrapped. */
    fun positionAfterFinish(week: Int, dayIndex: Int, dayCount: Int): RotationPosition {
        val next = (dayIndex + 1) % dayCount
        return RotationPosition(if (next == 0) week + 1 else week, next)
    }

    /**
     * The position before this day was finished, or null when the rotation has moved on since (another day was finished
     * after it), in which case it is left alone. [current] is where the program stands now.
     */
    fun rewind(current: RotationPosition, dayIndex: Int, dayCount: Int): RotationPosition? {
        if (dayCount <= 0 || dayIndex !in 0 until dayCount) return null
        val before = if (dayIndex == dayCount - 1 && current.week > 1) current.week - 1 else current.week
        val expected = positionAfterFinish(before, dayIndex, dayCount)
        if (expected != current) return null
        return RotationPosition(before, dayIndex)
    }
}
