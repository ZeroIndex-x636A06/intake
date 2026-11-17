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
}
