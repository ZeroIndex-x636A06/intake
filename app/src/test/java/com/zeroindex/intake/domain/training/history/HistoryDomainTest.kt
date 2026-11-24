package com.zeroindex.intake.domain.training.history

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test

class HistoryDomainTest {
    private val utc = ZoneOffset.UTC
    private val min = 60_000L
    private val hour = 60 * min
    private val now = LocalDate.of(2026, 11, 16).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDate.of(y, m, d).atTime(h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    // ---- session time edits

    @Test
    fun `moving the start moves the sets with it and keeps the length`() {
        val start = at(2026, 11, 10, 18)
        val finish = start + 60 * min
        val newStart = at(2026, 11, 9, 7)
        val out = SessionEditor.retime(start, finish, start + 50 * min, newStart, 60, now) as TimeOutcome.Ok
        assertThat(out.edit.startedAt).isEqualTo(newStart)
        assertThat(out.edit.finishedAt).isEqualTo(newStart + 60 * min)
        assertThat(out.edit.setShiftMillis).isEqualTo(newStart - start)
    }

    @Test
    fun `a new length changes only the end`() {
        val start = at(2026, 11, 10, 18)
        val out = SessionEditor.retime(start, start + 60 * min, start + 30 * min, start, 90, now) as TimeOutcome.Ok
        assertThat(out.edit.setShiftMillis).isEqualTo(0)
        assertThat(out.edit.finishedAt).isEqualTo(start + 90 * min)
    }

    @Test
    fun `a length that would cut off the last set is refused with the minimum`() {
        val start = at(2026, 11, 10, 18)
        val out = SessionEditor.retime(start, start + 60 * min, start + 45 * min + 30_000, start, 30, now)
        assertThat(out).isEqualTo(TimeOutcome.Refused(TimeProblem.TooShort(46)))
    }

    @Test
    fun `an end in the future is refused`() {
        val start = at(2026, 11, 10, 18)
        val out = SessionEditor.retime(start, start + hour, null, now - 30 * min, 60, now)
        assertThat(out).isEqualTo(TimeOutcome.Refused(TimeProblem.InFuture))
    }

    @Test
    fun `length bounds and unfinished sessions are refused`() {
        val start = at(2026, 11, 10, 18)
        assertThat(SessionEditor.retime(start, start + hour, null, start, 0, now)).isEqualTo(TimeOutcome.Refused(TimeProblem.BadDuration))
        assertThat(SessionEditor.retime(start, start + hour, null, start, 24 * 60 + 1, now)).isEqualTo(TimeOutcome.Refused(TimeProblem.BadDuration))
        assertThat(SessionEditor.retime(start, null, null, start, 30, now)).isEqualTo(TimeOutcome.Refused(TimeProblem.NotFinished))
    }

    @Test
    fun `a session with no sets can be given any valid length`() {
        val start = at(2026, 11, 10, 18)
        assertThat(SessionEditor.retime(start, start + hour, null, start, 5, now)).isInstanceOf(TimeOutcome.Ok::class.java)
    }

    @Test
    fun `combine uses the local zone`() {
        val ny = ZoneId.of("America/New_York")
        val ms = SessionEditor.combine(LocalDate.of(2026, 7, 1), LocalTime.of(7, 30), ny)
        assertThat(ms).isEqualTo(at(2026, 7, 1, 11, 30))
    }

    @Test
    fun `a set added later lands a minute after the last one and inside the session`() {
        val start = at(2026, 11, 10, 18)
        val finish = start + 60 * min
        assertThat(SessionEditor.newSetTime(start, finish, emptyList())).isEqualTo(start)
        assertThat(SessionEditor.newSetTime(start, finish, listOf(start + 10 * min, start + 20 * min))).isEqualTo(start + 21 * min)
        assertThat(SessionEditor.newSetTime(start, finish, listOf(finish))).isEqualTo(finish)
    }

    @Test
    fun `next set index and exercise change`() {
        assertThat(SessionEditor.nextSetIndex(emptyList())).isEqualTo(0)
        assertThat(SessionEditor.nextSetIndex(listOf(0, 1, 4))).isEqualTo(5)
        assertThat(SessionEditor.canChangeExercise("a", "b")).isTrue()
        assertThat(SessionEditor.canChangeExercise("a", "a")).isFalse()
        assertThat(SessionEditor.canChangeExercise("a", " ")).isFalse()
    }

    // ---- backdated log

    @Test
    fun `a past workout gets a start and an end`() {
        val out = BackdatedLog.plan(LocalDate.of(2026, 11, 14), LocalTime.of(17, 30), 75, now, utc) as BackdateOutcome.Ok
        assertThat(out.startedAt).isEqualTo(at(2026, 11, 14, 17, 30))
        assertThat(out.finishedAt).isEqualTo(out.startedAt + 75 * min)
    }

    @Test
    fun `a backdated workout cannot end in the future`() {
        val out = BackdatedLog.plan(LocalDate.of(2026, 11, 16), LocalTime.of(11, 30), 60, now, utc)
        assertThat(out).isEqualTo(BackdateOutcome.Refused(BackdateProblem.InFuture))
        assertThat(BackdatedLog.plan(LocalDate.of(2026, 11, 16), LocalTime.of(10, 0), 60, now, utc)).isInstanceOf(BackdateOutcome.Ok::class.java)
    }

    @Test
    fun `a backdated workout has limits on age and length`() {
        assertThat(BackdatedLog.plan(LocalDate.of(1999, 1, 1), LocalTime.NOON, 60, now, utc))
            .isEqualTo(BackdateOutcome.Refused(BackdateProblem.TooOld))
        assertThat(BackdatedLog.plan(LocalDate.of(2026, 1, 1), LocalTime.NOON, 0, now, utc))
            .isEqualTo(BackdateOutcome.Refused(BackdateProblem.BadDuration))
    }

    // ---- reopen rules

    @Test
    fun `a session finished a short while ago can be reopened`() {
        assertThat(ReopenRules.check(now - 5 * min, now, null, "s")).isEqualTo(ReopenOutcome.Allowed)
        assertThat(ReopenRules.check(now - 23 * hour, now, null, "s")).isEqualTo(ReopenOutcome.Allowed)
    }

    @Test
    fun `after 24 hours or when not finished it cannot`() {
        assertThat(ReopenRules.check(now - 25 * hour, now, null, "s")).isEqualTo(ReopenOutcome.WindowPassed)
        assertThat(ReopenRules.check(null, now, null, "s")).isEqualTo(ReopenOutcome.NotFinished)
    }

    @Test
    fun `another active session is a conflict that names it`() {
        assertThat(ReopenRules.check(now - min, now, "other", "s")).isEqualTo(ReopenOutcome.AnotherActive("other"))
        assertThat(ReopenRules.check(now - min, now, "s", "s")).isEqualTo(ReopenOutcome.Allowed)
    }

    @Test
    fun `reopening resumes at the length the workout had and Finish does not count the idle time`() {
        // Q7-9: a 45 minute workout (edited), reopened 32 minutes after it ended.
        val start = now - 77 * min
        val end = start + 45 * min
        val resumed = ReopenRules.resume(start, end, now)
        assertThat(resumed.setShiftMillis).isEqualTo(now - end)
        // Finishing right away: finish minus start is still the 45 minutes, not 77.
        assertThat(now - resumed.startedAt).isEqualTo(45 * min)
        // Trained 10 more minutes before Finish: 55 minutes.
        assertThat(now + 10 * min - resumed.startedAt).isEqualTo(55 * min)
    }

    @Test
    fun `the length in the edit dialog rounds like the summary`() {
        // Q7-19: 7 minutes 32 seconds read "8 minutes" in the summary and "7" in the dialog.
        assertThat(SessionEditor.durationMin(0L, 7 * min + 32_000)).isEqualTo(8)
        assertThat(SessionEditor.durationMin(0L, 7 * min + 20_000)).isEqualTo(7)
        assertThat(SessionEditor.durationMin(0L, 10_000L)).isEqualTo(1)
    }

    @Test
    fun `reopening never moves the start backwards`() {
        val resumed = ReopenRules.resume(now - 10 * min, now + min, now)
        assertThat(resumed.startedAt).isEqualTo(now - 10 * min)
        assertThat(resumed.setShiftMillis).isEqualTo(0L)
    }

    @Test
    fun `rewinding puts the rotation back only when nothing moved it since`() {
        // Day 1 of 3 was finished from week 2, day 0: the program now offers day 1 in week 2.
        assertThat(ReopenRules.rewind(RotationPosition(2, 1), 0, 3)).isEqualTo(RotationPosition(2, 0))
        // The last day wrapped into the next week.
        assertThat(ReopenRules.rewind(RotationPosition(3, 0), 2, 3)).isEqualTo(RotationPosition(2, 2))
        // Another day was finished after it: leave the rotation alone.
        assertThat(ReopenRules.rewind(RotationPosition(2, 2), 0, 3)).isNull()
        // A single-day program wraps to itself.
        assertThat(ReopenRules.rewind(RotationPosition(5, 0), 0, 1)).isEqualTo(RotationPosition(4, 0))
        assertThat(ReopenRules.rewind(RotationPosition(1, 0), 0, 0)).isNull()
    }

    @Test
    fun `finish then rewind is a round trip`() {
        for (count in 1..4) for (day in 0 until count) for (week in 2..3) {
            val after = ReopenRules.positionAfterFinish(week, day, count)
            assertThat(ReopenRules.rewind(after, day, count)).isEqualTo(RotationPosition(week, day))
        }
    }

    // ---- idle threshold

    private val threshold = ForgottenWorkoutPolicy.thresholdMillis(3)

    @Test
    fun `a workout is not forgotten before the threshold`() {
        val last = now - 2 * hour
        assertThat(ForgottenWorkoutPolicy.check(now - 4 * hour, last, 90, null, now, threshold)).isNull()
    }

    @Test
    fun `an idle workout ends at the last set plus its rest, never now`() {
        val last = now - 4 * hour
        val out = ForgottenWorkoutPolicy.check(now - 5 * hour, last, 90, null, now, threshold)!!
        assertThat(out.finishAt).isEqualTo(last + 90_000)
        assertThat(out.idleSince).isEqualTo(last)
    }

    @Test
    fun `the rest counted is capped`() {
        val last = now - 4 * hour
        val out = ForgottenWorkoutPolicy.check(now - 5 * hour, last, 3600, null, now, threshold)!!
        assertThat(out.finishAt).isEqualTo(last + 300_000)
        val none = ForgottenWorkoutPolicy.check(now - 5 * hour, last, null, null, now, threshold)!!
        assertThat(none.finishAt).isEqualTo(last)
    }

    @Test
    fun `keep going restarts the idle clock`() {
        val last = now - 5 * hour
        assertThat(ForgottenWorkoutPolicy.check(now - 6 * hour, last, null, now - hour, now, threshold)).isNull()
        assertThat(ForgottenWorkoutPolicy.check(now - 6 * hour, last, null, now - 4 * hour, now, threshold)).isNotNull()
    }

    @Test
    fun `a workout with no sets is flagged but has nothing to finish at`() {
        val out = ForgottenWorkoutPolicy.check(now - 4 * hour, null, null, null, now, threshold)!!
        assertThat(out.finishAt).isNull()
        assertThat(ForgottenWorkoutPolicy.check(now - hour, null, null, null, now, threshold)).isNull()
    }

    @Test
    fun `the threshold is kept between 1 and 12 hours`() {
        assertThat(ForgottenWorkoutPolicy.thresholdMillis(0)).isEqualTo(hour)
        assertThat(ForgottenWorkoutPolicy.thresholdMillis(99)).isEqualTo(12 * hour)
        assertThat(ForgottenWorkoutPolicy.thresholdMillis(3)).isEqualTo(3 * hour)
    }

    // ---- calendar

    @Test
    fun `the month grid is whole weeks starting on Monday and marks training days`() {
        val starts = listOf(at(2026, 11, 3, 18), at(2026, 11, 3, 20), at(2026, 11, 16, 7), at(2026, 10, 30, 7))
        val weeks = TrainingCalendar.month(YearMonth.of(2026, 11), starts, utc)
        assertThat(weeks.all { it.size == 7 }).isTrue()
        assertThat(weeks.first().first().date).isEqualTo(LocalDate.of(2026, 10, 26))
        assertThat(weeks.first().first().date.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        val all = weeks.flatten()
        assertThat(all.first { it.date == LocalDate.of(2026, 11, 3) }.sessions).isEqualTo(2)
        assertThat(all.first { it.date == LocalDate.of(2026, 11, 16) }.sessions).isEqualTo(1)
        assertThat(all.first { it.date == LocalDate.of(2026, 10, 30) }.inMonth).isFalse()
        assertThat(all.count { it.inMonth }).isEqualTo(30)
    }

    @Test
    fun `a month that starts on the week start has no lead days`() {
        // 2026-06-01 is a Monday.
        val weeks = TrainingCalendar.month(YearMonth.of(2026, 6), emptyList(), utc)
        assertThat(weeks.first().first().date).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(weeks).hasSize(5)
    }

    @Test
    fun `days are local days`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val late = at(2026, 11, 3, 20) // 05:00 on the 4th in Tokyo
        assertThat(TrainingCalendar.dayOf(late, tokyo)).isEqualTo(LocalDate.of(2026, 11, 4))
        val on = TrainingCalendar.on(LocalDate.of(2026, 11, 4), listOf(late, at(2026, 11, 3, 1)), tokyo) { it }
        assertThat(on).containsExactly(late)
    }

    @Test
    fun `the calendar opens on the newest session's month`() {
        val fallback = YearMonth.of(2026, 11)
        assertThat(TrainingCalendar.openingMonth(listOf(at(2026, 3, 5, 1), at(2026, 8, 9, 1)), utc, fallback)).isEqualTo(YearMonth.of(2026, 8))
        assertThat(TrainingCalendar.openingMonth(emptyList(), utc, fallback)).isEqualTo(fallback)
    }
}
