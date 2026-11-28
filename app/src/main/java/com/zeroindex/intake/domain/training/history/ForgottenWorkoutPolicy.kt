package com.zeroindex.intake.domain.training.history

/** What to offer for a workout that has sat idle. [finishAt] is null when nothing was logged, so there is nothing to end. */
data class ForgottenWorkout(val finishAt: Long?, val idleSince: Long)

/**
 * "Still training?": a running workout with no activity for a long time is probably a forgotten one. Finishing it uses the
 * time of the last logged set plus its rest (capped), never "now", so a forgotten workout does not turn into an eight hour
 * session. Nothing is ever discarded by this.
 */
object ForgottenWorkoutPolicy {
    const val DEFAULT_HOURS = 3
    const val MIN_HOURS = 1
    const val MAX_HOURS = 12

    /** The longest rest counted after the last set. */
    const val MAX_REST_SEC = 300

    fun thresholdMillis(hours: Int): Long = hours.coerceIn(MIN_HOURS, MAX_HOURS) * 3_600_000L

    /**
     * Null while the workout is active. [keepGoingAt] is when the person last said "keep going": the idle clock restarts
     * there, so the question comes back only after another full threshold.
     */
    fun check(
        startedAt: Long,
        lastSetAt: Long?,
        lastSetRestSec: Int?,
        keepGoingAt: Long?,
        now: Long,
        thresholdMillis: Long,
    ): ForgottenWorkout? {
        val lastActivity = lastSetAt ?: startedAt
        val idleSince = maxOf(lastActivity, keepGoingAt ?: Long.MIN_VALUE)
        if (now - idleSince < thresholdMillis) return null
        val finishAt = lastSetAt?.let {
            val rest = (lastSetRestSec ?: 0).coerceIn(0, MAX_REST_SEC) * 1000L
            minOf(it + rest, now)
        }
        return ForgottenWorkout(finishAt, idleSince)
    }
}
