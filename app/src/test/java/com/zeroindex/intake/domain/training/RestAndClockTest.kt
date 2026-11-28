package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RestAndClockTest {

    @Test
    fun `plus 15 grows the rest and the ring, up to fifteen minutes`() {
        assertThat(RestRules.adjust(60, 90, 15)).isEqualTo(RestAdjust(75, 105))
        assertThat(RestRules.adjust(895, 900, 15)).isEqualTo(RestAdjust(900, 900))
    }

    @Test
    fun `minus 15 shortens the rest and leaves the ring`() {
        assertThat(RestRules.adjust(60, 90, -15)).isEqualTo(RestAdjust(45, 90))
    }

    @Test
    fun `minus 15 at or under 15 seconds ends the rest`() {
        assertThat(RestRules.adjust(15, 90, -15)).isNull()
        assertThat(RestRules.adjust(10, 90, -15)).isNull()
    }

    @Test
    fun `labels and steps stay in range`() {
        assertThat(RestRules.label(0)).isEqualTo("No timer")
        assertThat(RestRules.label(45)).isEqualTo("45 s")
        assertThat(RestRules.label(90)).isEqualTo("1:30")
        assertThat(RestRules.label(900)).isEqualTo("15:00")
        assertThat(RestRules.step(895, 1)).isEqualTo(900)
        assertThat(RestRules.step(10, -1)).isEqualTo(0)
        assertThat(RestRules.presets.first()).isEqualTo(RestRules.NO_TIMER)
        assertThat(RestRules.presets.last()).isEqualTo(RestRules.MAX_SECONDS)
        assertThat(RestSound.fromName("CHIME")).isEqualTo(RestSound.CHIME)
        assertThat(RestSound.fromName("nope")).isEqualTo(RestSound.OFF)
    }

    @Test
    fun `trained time stops while paused and resumes after`() {
        val start = 1_000_000L
        var pause = WorkoutPause("s")
        assertThat(WorkoutClock.trainedMs(start, pause, start + 600_000)).isEqualTo(600_000)
        pause = pause.pause(start + 600_000)
        assertThat(pause.isPaused).isTrue()
        // Five minutes into the pause the clock has not moved.
        assertThat(WorkoutClock.trainedMs(start, pause, start + 900_000)).isEqualTo(600_000)
        pause = pause.resume(start + 900_000)
        assertThat(pause.pausedMs).isEqualTo(300_000)
        assertThat(WorkoutClock.trainedMs(start, pause, start + 1_000_000)).isEqualTo(700_000)
    }

    @Test
    fun `pausing twice or resuming when not paused changes nothing`() {
        val p = WorkoutPause("s").pause(100)
        assertThat(p.pause(500)).isEqualTo(p)
        assertThat(WorkoutPause("s").resume(500)).isEqualTo(WorkoutPause("s"))
        assertThat(p.totalPausedMs(400)).isEqualTo(300)
    }

    @Test
    fun `the saved start makes finish minus start the trained time`() {
        val start = 0L
        val finish = 3_600_000L
        val paused = 600_000L
        val net = WorkoutClock.netStart(start, paused, earliestSetAt = 700_000, finishedAt = finish)
        assertThat(finish - net).isEqualTo(3_000_000L)
    }

    @Test
    fun `the saved start never passes the first logged set`() {
        val net = WorkoutClock.netStart(0, 900_000, earliestSetAt = 200_000, finishedAt = 3_600_000)
        assertThat(net).isEqualTo(200_000)
        assertThat(WorkoutClock.netStart(0, 0, 200_000, 3_600_000)).isEqualTo(0)
        assertThat(WorkoutClock.netStart(50, 900_000, null, 400)).isEqualTo(400)
    }

    @Test
    fun `up next is the first open set of the exercise in hand, then the next exercise`() {
        val bench = UpNextExercise("Bench press", setOf(0, 1), 3, 80.0, 8, false)
        val row = UpNextExercise("Row", emptySet(), 3, 60.0, 10, false)
        assertThat(UpNext.find(listOf(bench, row), 0)).isEqualTo(UpNextSet("Bench press", 3, 80.0, 8, false))
        val benchDone = bench.copy(loggedSetIndexes = setOf(0, 1, 2))
        assertThat(UpNext.find(listOf(benchDone, row), 0)).isEqualTo(UpNextSet("Row", 1, 60.0, 10, false))
        assertThat(UpNext.find(listOf(benchDone, row.copy(loggedSetIndexes = setOf(0, 1, 2))), 0)).isNull()
        assertThat(UpNext.find(emptyList(), 0)).isNull()
    }

    @Test
    fun `up next wraps to an earlier exercise that still has sets`() {
        val a = UpNextExercise("A", setOf(0), 2, 20.0, 5, false)
        val b = UpNextExercise("B", setOf(0, 1), 2, 30.0, 5, false)
        assertThat(UpNext.find(listOf(a, b), 1)?.exerciseName).isEqualTo("A")
    }

    @Test
    fun `up next text reads weight times reps`() {
        val next = UpNextSet("Bench press", 3, 80.0, 8, false)
        assertThat(UpNext.text(next, "80 kg")).isEqualTo("Bench press, set 3: 80 kg × 8")
        assertThat(UpNext.text(next.copy(reps = null), "BW")).isEqualTo("Bench press, set 3: BW")
        assertThat(UpNext.text(next.copy(reps = null), "")).isEqualTo("Bench press, set 3")
    }

    @Test
    fun `a start in the future after the clock was set back is re-anchored to now`() {
        // Q8-10: the clock went back four hours, so the stored start was four hours ahead and the timer sat at 0:00.
        val now = 10_000_000L
        val start = now + 4 * 3_600_000L
        assertThat(WorkoutClock.trainedMs(start, null, now)).isEqualTo(0L)
        val anchored = WorkoutClock.reanchoredStart(start, now)
        assertThat(anchored).isEqualTo(now)
        // From the re-anchored start the clock counts up at once.
        assertThat(WorkoutClock.trainedMs(anchored, null, now + 90_000)).isEqualTo(90_000L)
    }

    @Test
    fun `a start a few seconds ahead of the clock is left alone`() {
        val now = 10_000_000L
        assertThat(WorkoutClock.reanchoredStart(now + 5_000, now)).isEqualTo(now + 5_000)
        assertThat(WorkoutClock.reanchoredStart(now - 5_000, now)).isEqualTo(now - 5_000)
    }

    @Test
    fun `a finished workout never ends before its start or its last set`() {
        assertThat(WorkoutClock.endNotBeforeSets(startedAt = 1_000, end = 500, lastSetAt = null)).isEqualTo(1_000L)
        assertThat(WorkoutClock.endNotBeforeSets(startedAt = 1_000, end = 1_000, lastSetAt = 61_000)).isEqualTo(61_000L)
        assertThat(WorkoutClock.endNotBeforeSets(startedAt = 1_000, end = 90_000, lastSetAt = 61_000)).isEqualTo(90_000L)
    }

    @Test
    fun `after the clock is set back the display counts from now at once`() {
        // What the ticker does: a start 2 h ahead of the wall clock shows 0:00 for no longer than one tick.
        val now = 50_000_000L
        val start = now + 2 * 3_600_000L
        val shownStart = WorkoutClock.reanchoredStart(start, now)
        assertThat(WorkoutClock.trainedMs(shownStart, null, now + 70_000)).isEqualTo(70_000L)
    }

    @Test
    fun `a running reopened workout counts trained time, not the time since the edited start (Q10-5)`() {
        val min = 60_000L
        val now = 1_000_000_000L
        val start = now - 90 * min // start edited to 90 minutes ago
        val pause = WorkoutPause("s", reopenGapMs = 64 * min) // idle between Finish and Reopen
        assertThat(WorkoutClock.loadMs(start, null, pause, now, 4 * 60 * min)).isEqualTo(26 * min)
    }

    @Test
    fun `a paused running workout leaves the pause out and a finished one is finish minus start`() {
        val min = 60_000L
        val now = 500 * min
        val paused = WorkoutPause("s", pausedMs = 10 * min, pausedSince = now - 5 * min)
        assertThat(WorkoutClock.loadMs(now - 45 * min, null, paused, now, 240 * min)).isEqualTo(30 * min)
        assertThat(WorkoutClock.loadMs(100 * min, 128 * min, null, now, 240 * min)).isEqualTo(28 * min)
        assertThat(WorkoutClock.loadMs(0L, 1000 * min, null, now, 240 * min)).isEqualTo(240 * min)
    }
}
