package com.zeroindex.intake.data.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import org.junit.Test

/** The row-building parts of moveDay, duplicateDay and copySettingsToOthers (no database needed). */
class ProgramEditsTest {

    private fun day(id: String, index: Int, name: String = id) =
        ProgramDayEntity(id = id, programId = "p", dayIndex = index, name = name, createdAt = 1, updatedAt = 1)

    private fun ex(id: String, dayId: String, order: Int, sets: Int = 3, min: Int = 8, max: Int = 12) = ProgramExerciseEntity(
        id = id, programDayId = dayId, exerciseId = "squat", displayOrder = order, targetSets = sets,
        targetRepsMin = min, targetRepsMax = max, restSec = 120, createdAt = 1, updatedAt = 1,
    )

    private val days = listOf(day("a", 0), day("b", 1), day("c", 2))

    @Test fun moveDayRenumbersAndOnlyTouchesChangedRows() {
        val moved = ProgramEdits.moveDay(days, "a", 1, now = 50)!!
        assertThat(moved.map { it.id }).containsExactly("b", "a", "c").inOrder()
        assertThat(moved.map { it.dayIndex }).containsExactly(0, 1, 2).inOrder()
        // "c" kept index 2, so it is returned as it was.
        assertThat(moved.first { it.id == "c" }.updatedAt).isEqualTo(1)
        assertThat(moved.first { it.id == "a" }.updatedAt).isEqualTo(50)
    }

    @Test fun moveDayClampsAtTheEndsAndIgnoresNoOps() {
        assertThat(ProgramEdits.moveDay(days, "a", -1, 50)).isNull()
        assertThat(ProgramEdits.moveDay(days, "c", 1, 50)).isNull()
        assertThat(ProgramEdits.moveDay(days, "missing", 1, 50)).isNull()
        assertThat(ProgramEdits.moveDay(emptyList(), "a", 1, 50)).isNull()
        val far = ProgramEdits.moveDay(days, "a", 99, 50)!!
        assertThat(far.map { it.id }).containsExactly("b", "c", "a").inOrder()
    }

    @Test fun duplicateDayPutsTheCopyRightAfterTheOriginal() {
        var n = 0
        val exercises = listOf(ex("e1", "a", 0), ex("e2", "a", 1))
        val rows = ProgramEdits.duplicateDay(days[0], days, exercises, now = 99) { "new-${n++}" }

        assertThat(rows.copy.name).isEqualTo("a (copy)")
        assertThat(rows.copy.id).isEqualTo("new-0")
        assertThat(rows.daysInOrder.map { it.id }).containsExactly("a", "new-0", "b", "c").inOrder()
        assertThat(rows.daysInOrder.map { it.dayIndex }).containsExactly(0, 1, 2, 3).inOrder()
        assertThat(rows.exercises.map { it.programDayId }.toSet()).containsExactly("new-0")
        assertThat(rows.exercises.map { it.id }).containsNoneOf("e1", "e2")
        assertThat(rows.exercises.map { it.id }.toSet()).hasSize(2)
        // Settings carry over.
        assertThat(rows.exercises.map { it.targetSets }).containsExactly(3, 3)
        // The original day and its exercises are not part of what is written for the copy.
        assertThat(rows.exercises.none { it.programDayId == "a" }).isTrue()
    }

    @Test fun duplicateNamesNeverClash() {
        val withCopy = days + day("d", 3, "a (copy)")
        val rows = ProgramEdits.duplicateDay(withCopy[0], withCopy, emptyList(), 1) { "x" }
        assertThat(rows.copy.name).isEqualTo("a (copy 2)")
        assertThat(ProgramEdits.nextCopyName("Push (copy)", listOf("Push", "Push (copy)"))).isEqualTo("Push (copy 2)")
        assertThat(ProgramEdits.nextCopyName("Push", listOf("PUSH (COPY)"))).isEqualTo("Push (copy 2)")
    }

    @Test fun copySettingsChangesOnlyTheSettingsAndSkipsTheSource() {
        val source = ex("s", "a", 0, sets = 5, min = 3, max = 5).copy(targetRpe = 8.0, restSec = 180, progressionRuleId = "rule")
        val other = ex("o", "a", 1, sets = 2, min = 10, max = 15).copy(startWeightKg = 40.0)
        val out = ProgramEdits.copySettings(source, listOf(source, other), now = 77)

        assertThat(out).hasSize(1)
        val changed = out.single()
        assertThat(changed.id).isEqualTo("o")
        assertThat(changed.targetSets).isEqualTo(5)
        assertThat(changed.targetRepsMin).isEqualTo(3)
        assertThat(changed.targetRepsMax).isEqualTo(5)
        assertThat(changed.targetRpe).isEqualTo(8.0)
        assertThat(changed.restSec).isEqualTo(180)
        assertThat(changed.progressionRuleId).isEqualTo("rule")
        assertThat(changed.updatedAt).isEqualTo(77)
        // Not touched: the lift, its place, its start weight and its day.
        assertThat(changed.exerciseId).isEqualTo(other.exerciseId)
        assertThat(changed.displayOrder).isEqualTo(1)
        assertThat(changed.startWeightKg).isEqualTo(40.0)
        assertThat(changed.programDayId).isEqualTo("a")
    }

    @Test fun renumberExercisesFixesGapsOnly() {
        val list = listOf(ex("x", "a", 0), ex("y", "a", 4), ex("z", "a", 5))
        val out = ProgramEdits.renumberExercises(list, 9)
        assertThat(out.map { it.displayOrder }).containsExactly(0, 1, 2).inOrder()
        assertThat(out[0].updatedAt).isEqualTo(1)
        assertThat(out[1].updatedAt).isEqualTo(9)
    }
}
