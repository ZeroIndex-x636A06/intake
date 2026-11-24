package com.zeroindex.intake.domain.training.history

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface BackdateProblem {
    data object InFuture : BackdateProblem
    data object TooOld : BackdateProblem
    data object BadDuration : BackdateProblem
}

sealed interface BackdateOutcome {
    data class Ok(val startedAt: Long, val finishedAt: Long) : BackdateOutcome
    data class Refused(val problem: BackdateProblem) : BackdateOutcome
}

/**
 * "Log a past workout": a session that is already over, at a date and time the person chose. It is created finished, so it
 * never competes with a running workout for the single active slot; its sets are then added in the editor.
 */
object BackdatedLog {
    const val DEFAULT_DURATION_MIN = 60
    const val MAX_YEARS_BACK = 20L

    fun plan(date: LocalDate, time: LocalTime, durationMin: Int, now: Long, zone: ZoneId): BackdateOutcome {
        if (durationMin < 1 || durationMin > SessionEditor.MAX_DURATION_MIN) return BackdateOutcome.Refused(BackdateProblem.BadDuration)
        val start = SessionEditor.combine(date, time, zone)
        val finish = start + durationMin * 60_000L
        if (finish > now) return BackdateOutcome.Refused(BackdateProblem.InFuture)
        val oldest = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().minusYears(MAX_YEARS_BACK)
        if (date.isBefore(oldest)) return BackdateOutcome.Refused(BackdateProblem.TooOld)
        return BackdateOutcome.Ok(start, finish)
    }

    /**
     * The start the form opens with for [date]: one that is not in the future, so the first thing to do is
     * not to fix an error. For a past day it is a typical evening hour; for today it is the latest start (rounded down to five
     * minutes) from which a [durationMin] workout has already ended, or midnight when that would fall on the day before.
     */
    fun defaultStart(date: LocalDate, now: Long, zone: ZoneId, durationMin: Int = DEFAULT_DURATION_MIN): LocalTime {
        val nowLocal = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        if (date.isBefore(nowLocal.toLocalDate())) return LocalTime.of(18, 0)
        val latest = nowLocal.minusMinutes(durationMin.toLong())
        if (latest.toLocalDate().isBefore(date)) return LocalTime.MIDNIGHT
        val t = latest.toLocalTime()
        return LocalTime.of(t.hour, t.minute - t.minute % 5)
    }

    /** Plain words for a refusal. */
    fun words(problem: BackdateProblem): String = when (problem) {
        BackdateProblem.InFuture -> "That workout would end in the future. Pick an earlier time or a shorter length."
        BackdateProblem.TooOld -> "That date is more than $MAX_YEARS_BACK years ago."
        BackdateProblem.BadDuration -> "The length must be from 1 minute to 24 hours."
    }
}
