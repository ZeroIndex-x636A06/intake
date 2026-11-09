package com.zeroindex.intake.data.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratedDay
import com.zeroindex.intake.domain.training.generator.GeneratedExercise
import org.junit.Test

class WorkoutBuilderTest {
    private var n = 0
    private val ids = { "id-${++n}" }

    private fun day() = ProgramDayEntity(id = "d0", programId = "p0", dayIndex = 2, name = "Upper A", weekday = 3, createdAt = 1, updatedAt = 1)

    private fun pe(id: String, order: Int, name: String, deleted: Long? = null) = ProgramExerciseEntity(
        id = id, programDayId = "d0", exerciseId = ExerciseCatalog.requireByName(name).id, displayOrder = order,
        targetSets = 3, targetRepsMin = 8, targetRepsMax = 12, restSec = 90, startWeightKg = 40.0, notes = "slow",
        createdAt = 1, updatedAt = 1, deletedAt = deleted,
    )

    @Test
    fun `a new workout is a standalone, inactive program with one empty day`() {
        val rows = WorkoutBuilder.blank("  Arm day ", now = 100, nextId = ids)
        assertThat(rows.program.isStandalone).isTrue()
        assertThat(rows.program.isActive).isFalse()
        assertThat(rows.program.name).isEqualTo("Arm day")
        assertThat(rows.days).hasSize(1)
        assertThat(rows.days.single().programId).isEqualTo(rows.program.id)
        assertThat(rows.days.single().name).isEqualTo("Arm day")
        assertThat(WorkoutBuilder.blank("   ", 1, ids).program.name).isEqualTo("My workout")
    }

    @Test
    fun `copying a program day copies the live exercises in order and leaves the original alone`() {
        val source = listOf(pe("a", 1, "Hammer curl"), pe("b", 0, "Barbell curl"), pe("gone", 2, "Wrist curl", deleted = 5))
        val rows = WorkoutBuilder.copyOfDay("Arm day", day(), source, now = 9, nextId = ids)
        assertThat(rows.program.isStandalone).isTrue()
        val day = rows.days.single()
        assertThat(day.dayIndex).isEqualTo(0)
        assertThat(day.weekday).isNull()
        assertThat(rows.exercises.map { it.exerciseId }).containsExactly(
            ExerciseCatalog.requireByName("Barbell curl").id, ExerciseCatalog.requireByName("Hammer curl").id,
        ).inOrder()
        assertThat(rows.exercises.map { it.displayOrder }).containsExactly(0, 1).inOrder()
        assertThat(rows.exercises.all { it.programDayId == day.id && it.id !in listOf("a", "b") }).isTrue()
        assertThat(rows.exercises.all { it.notes == "slow" && it.startWeightKg == 40.0 }).isTrue()
        assertThat(source.first().programDayId).isEqualTo("d0")
    }

    @Test
    fun `a generated day becomes a workout whose day carries the workout name`() {
        val generated = GeneratedDay(
            name = "Workout 3 Nov",
            exercises = listOf(
                GeneratedExercise(
                    exerciseId = ExerciseCatalog.requireByName("Bench press").id, name = "Bench press", muscle = "chest",
                    sets = 3, repsMin = 5, repsMax = 8, rpe = null, restSec = 150, progression = ProgressionType.DOUBLE_PROGRESSION,
                    why = ProgramBlueprintBuilder.FREESTYLE_WHY,
                ),
            ),
            estimatedMinutes = 0, why = "", weekday = 4,
        )
        val rows = WorkoutBuilder.fromGeneratedDay("Push", generated, mapOf(ProgressionType.DOUBLE_PROGRESSION to "rule-1"), 5, ids)
        assertThat(rows.program.isStandalone).isTrue()
        assertThat(rows.days.single().name).isEqualTo("Push")
        assertThat(rows.days.single().weekday).isNull()
        assertThat(rows.exercises.single().programDayId).isEqualTo(rows.days.single().id)
        assertThat(rows.exercises.single().progressionRuleId).isEqualTo("rule-1")
        assertThat(rows.exercises.single().notes).isNull()
    }

    @Test
    fun `a saved workout can be started on its one live day`() {
        val rows = WorkoutBuilder.blank("Hotel gym", 1, ids)
        assertThat(WorkoutBuilder.startDay(rows.days)).isEqualTo(rows.days.single())
        assertThat(WorkoutBuilder.startDay(WorkoutBuilder.softDelete(rows, 9).days)).isNull()
    }

    @Test
    fun `soft delete stamps every row, removes none and hides the workout`() {
        val rows = WorkoutBuilder.copyOfDay("Arm day", day(), listOf(pe("a", 0, "Hammer curl")), 1, ids)
        val gone = WorkoutBuilder.softDelete(rows, now = 77)
        assertThat(gone.program.deletedAt).isEqualTo(77)
        assertThat(gone.days.single().deletedAt).isEqualTo(77)
        assertThat(gone.exercises.single().deletedAt).isEqualTo(77)
        assertThat(gone.program.id).isEqualTo(rows.program.id)
        assertThat(WorkoutBuilder.visible(listOf(rows.program, gone.program.copy(id = "x")))).containsExactly(rows.program)
    }

    @Test
    fun `the list shows only live standalone programs by name`() {
        val a = WorkoutBuilder.blank("b workout", 1, ids).program
        val b = WorkoutBuilder.blank("A workout", 1, ids).program
        val program = a.copy(id = "prog", isStandalone = false)
        assertThat(WorkoutBuilder.visible(listOf(a, b, program)).map { it.name }).containsExactly("A workout", "b workout").inOrder()
    }

    @Test
    fun `duplicate names do not clash`() {
        assertThat(WorkoutBuilder.duplicateName("Arm day", listOf("Arm day"))).isNotEqualTo("Arm day")
    }
}
