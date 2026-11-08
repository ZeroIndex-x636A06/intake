package com.zeroindex.intake.wearprotocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WearProtocolTest {
    private val workout = ActiveWorkout(
        sessionId = "s",
        title = "Day A · Lower",
        exercises = listOf(
            WatchExercise("sq", "Back squat", 180, listOf(WatchSet(0, 82.5, 5, done = true, rpe = 7.0), WatchSet(1, 82.5, 5), WatchSet(2, 82.5, 5))),
            WatchExercise("rdl", "Romanian deadlift", 120, listOf(WatchSet(0, 70.0, 8))),
        ),
        weightUnit = "kg",
        updatedAt = 1,
    )

    @Test
    fun `round trips and opens on the first unfinished set`() {
        val back = WearProtocol.decodeWorkout(WearProtocol.encode(workout))
        assertThat(back).isEqualTo(workout)
        assertThat(back.currentExerciseIndex).isEqualTo(0)
        assertThat(back.exercises[0].nextSet?.setIndex).isEqualTo(1)
    }

    @Test
    fun `a finished workout stays on the last exercise`() {
        val done = workout.copy(exercises = workout.exercises.map { e -> e.copy(sets = e.sets.map { it.copy(done = true) }) })
        assertThat(done.currentExerciseIndex).isEqualTo(1)
    }

    @Test
    fun `rpe choices and weights match the mockup`() {
        assertThat(RPE_CHOICES.first()).isEqualTo(6.0)
        assertThat(RPE_CHOICES.last()).isEqualTo(10.0)
        assertThat(RPE_CHOICES).hasSize(9)
        assertThat(formatWeight(82.5, "kg")).isEqualTo("82.5 kg")
        assertThat(formatWeight(82.5, "lb")).isEqualTo("182 lb")
    }

    @Test
    fun `logged sets survive the wire`() {
        val set = LoggedSet("c1", "s", "sq", 2, 82.5, 5, 7.5, 123L, 95)
        assertThat(WearProtocol.decodeSet(WearProtocol.encode(set))).isEqualTo(set)
    }
}
