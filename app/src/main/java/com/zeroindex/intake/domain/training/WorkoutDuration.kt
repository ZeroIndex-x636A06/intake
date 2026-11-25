package com.zeroindex.intake.domain.training

/**
 * How long a workout reads, everywhere (Q11-7). The summary rounded to the nearest minute ("23 minutes") while the recent
 * sessions list cut the same session down ("22 min"), and a one-minute session read "0 min". One rule now: whole minutes,
 * rounded to the nearest (the same as [com.zeroindex.intake.domain.training.history.SessionEditor.durationMin]), and a
 * session under a minute reads "<1 min".
 */
object WorkoutDuration {
    private const val MINUTE_MS = 60_000L

    /** Whole minutes, to the nearest; 0 only for a session under half a minute. */
    fun minutes(durationMs: Long): Int = Math.round(durationMs.coerceAtLeast(0L) / MINUTE_MS.toDouble()).toInt()

    fun isUnderAMinute(durationMs: Long): Boolean = durationMs < MINUTE_MS

    /** "23 min", or "<1 min". */
    fun short(durationMs: Long): String = if (isUnderAMinute(durationMs)) "<1 min" else "${minutes(durationMs)} min"

    /** "23 minutes", "1 minute", or "<1 minute". */
    fun long(durationMs: Long): String = when {
        isUnderAMinute(durationMs) -> "<1 minute"
        minutes(durationMs) == 1 -> "1 minute"
        else -> "${minutes(durationMs)} minutes"
    }

    /** The big figure and its caption on the summary: "23" over "minutes", "<1" over "minute". */
    fun statValue(durationMs: Long): String = if (isUnderAMinute(durationMs)) "<1" else minutes(durationMs).toString()

    fun statCaption(durationMs: Long): String = if (isUnderAMinute(durationMs) || minutes(durationMs) == 1) "minute" else "minutes"
}
