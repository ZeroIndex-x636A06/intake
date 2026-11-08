package com.zeroindex.intake.wear

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.domain.training.Prescription
import org.junit.Test

class WatchWorkoutMapperTest {
    private val squat = ExerciseEntity(id = "sq", name = "Back squat", primaryMuscle = "quads", defaultRestSec = 180, createdAt = 0, updatedAt = 0)
    private val pe = ProgramExerciseEntity(
        programDayId = "d", exerciseId = "sq", displayOrder = 0, targetSets = 3,
        targetRepsMin = 5, targetRepsMax = 5, restSec = 150, createdAt = 0, updatedAt = 0,
    )
    private val prescription = ExercisePrescription(pe, squat, Prescription(82.5, 3, 5, 5, reason = "+2.5 kg"), null, emptyList())

    private fun set(i: Int, reps: Int, warm: Boolean = false) = SetLogEntity(
        sessionId = "s", exerciseId = "sq", setIndex = i, weightKg = 82.5, reps = reps, rpe = 7.0,
        isWarmup = warm, completedAt = 1, createdAt = 1, updatedAt = 1,
    )

    @Test
    fun `prescribed sets with logged ones marked done`() {
        val w = WatchWorkoutMapper.build("s", "Day A", listOf(prescription), listOf(set(0, 5), set(0, 10, warm = true)), "kg", 9)
        val e = w.exercises.single()
        assertThat(e.restSec).isEqualTo(150)
        assertThat(e.sets.map { it.setIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(e.sets[0].done).isTrue()
        assertThat(e.sets[0].reps).isEqualTo(5) // the warm-up at index 0 is ignored
        assertThat(e.nextSet?.setIndex).isEqualTo(1)
        assertThat(e.sets[1].weightKg).isEqualTo(82.5)
    }

    @Test
    fun `extra sets beyond the plan still show`() {
        val logged = (0..3).map { set(it, 5) }
        val e = WatchWorkoutMapper.build("s", "Day A", listOf(prescription), logged, "kg", 9).exercises.single()
        assertThat(e.sets).hasSize(4)
        assertThat(e.nextSet).isNull()
    }
}
