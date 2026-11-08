package com.zeroindex.intake.data.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.script.DocsFiles
import com.zeroindex.intake.domain.script.ProgramDoc
import com.zeroindex.intake.domain.script.ProgramScript
import org.junit.Test

class ProgramImportTest {

    private fun parse(text: String): ProgramDoc = ProgramScript.parse(text).document!!
    private val example get() = parse(DocsFiles.read("examples/strength-block.md"))

    private fun library(vararg names: String) = names.map {
        ExerciseEntity(id = "lib-$it", name = it, primaryMuscle = "chest", defaultRestSec = 100, createdAt = 1, updatedAt = 1)
    }

    private fun build(doc: ProgramDoc, lib: List<ExerciseEntity> = emptyList(), existing: ExistingProgramState = ExistingProgramState(), now: Long = 1000L) =
        ProgramImportBuilder.build(doc, lib, existing, now)

    private fun existingOf(b: ProgramImportBatch) = ExistingProgramState(b.program, b.days, b.programExercises)

    @Test fun `maps the example into rows`() {
        val doc = example
        val b = build(doc, library("bench press"))
        assertThat(b.program.name).isEqualTo("Strength Block A")
        assertThat(b.program.description).contains("Four weeks")
        assertThat(b.days.map { it.dayIndex }).containsExactly(0, 1, 2).inOrder()
        assertThat(b.programExercises).hasSize(doc.days.sumOf { it.exercises.size })

        val bench = b.programExercises.first { it.exerciseId == "lib-bench press" }
        assertThat(bench.targetSets).isEqualTo(4)
        assertThat(bench.targetRepsMin).isEqualTo(6)
        assertThat(bench.targetRepsMax).isEqualTo(8)
        assertThat(bench.targetRpe).isEqualTo(8.0)
        assertThat(bench.restSec).isEqualTo(150)
        // 185 lb, converted and stored as kg.
        assertThat(bench.startWeightKg).isWithin(0.01).of(83.91)
        // Case-insensitive match to the library, so it is not created again.
        assertThat(b.summary.matchedExercises).containsExactly("bench press")
        assertThat(b.newExercises.map { it.name }).doesNotContain("Bench Press")
        assertThat(b.summary.newExercises).contains("Barbell Row")
    }

    @Test fun `program default rest and rule apply and rules are shared`() {
        val b = build(example)
        val row = b.programExercises.first { it.exerciseId == b.newExercises.first { e -> e.name == "Barbell Row" }.id }
        assertThat(row.restSec).isEqualTo(120)
        assertThat(b.rules.map { it.type }).contains(ProgressionType.DOUBLE_PROGRESSION)
        val doubles = b.rules.filter { it.type == ProgressionType.DOUBLE_PROGRESSION }
        assertThat(doubles.map { it.params }.distinct()).hasSize(doubles.size)
        assertThat(b.rules.map { it.id }.distinct()).hasSize(b.rules.size)
    }

    @Test fun `none means no rule, max means AMRAP, bodyweight is set on a new exercise`() {
        val b = build(example)
        val pullUp = b.newExercises.first { it.name == "Pull-up" }
        assertThat(pullUp.isBodyweight).isTrue()
        val row = b.programExercises.first { it.exerciseId == pullUp.id }
        assertThat(row.progressionRuleId).isNull()
        assertThat(row.targetRepsMin).isEqualTo(1)
        assertThat(row.targetRepsMax).isEqualTo(99)
        assertThat(row.startWeightKg).isNull()
    }

    @Test fun `the same file twice gives the same ids and removes nothing`() {
        val first = build(example, now = 1000)
        val second = build(example, existing = existingOf(first), now = 2000)
        assertThat(second.program.id).isEqualTo(first.program.id)
        assertThat(second.days.map { it.id }).isEqualTo(first.days.map { it.id })
        assertThat(second.programExercises.map { it.id }).isEqualTo(first.programExercises.map { it.id })
        assertThat(second.removedDays).isEmpty()
        assertThat(second.removedExercises).isEmpty()
        assertThat(second.summary.updatingExisting).isTrue()
        // Created stays, updated moves, via the supplied clock value.
        assertThat(second.program.createdAt).isEqualTo(1000)
        assertThat(second.program.updatedAt).isEqualTo(2000)
    }

    @Test fun `exercises missing from the new file are soft deleted, never dropped`() {
        val first = build(example, now = 1000)
        val trimmed = example.let { d -> d.copy(days = d.days.map { it.copy(exercises = it.exercises.drop(1)) }) }
        val second = build(trimmed, existing = existingOf(first), now = 2000)
        assertThat(second.removedExercises).hasSize(first.days.size)
        assertThat(second.removedExercises.all { it.deletedAt == 2000L }).isTrue()
        val oneDay = example.copy(days = example.days.take(1))
        val third = build(oneDay, existing = existingOf(first), now = 3000)
        assertThat(third.removedDays.map { it.dayIndex }).containsExactly(1, 2)
        assertThat(third.removedDays.all { it.deletedAt == 3000L }).isTrue()
        assertThat(third.program.currentDayIndex).isEqualTo(0)
    }

    @Test fun `activates only when nothing else is active`() {
        assertThat(build(example).program.isActive).isTrue()
        val other = build(example, existing = ExistingProgramState(anotherProgramActive = true))
        assertThat(other.program.isActive).isFalse()
        assertThat(other.summary.notices.any { it.contains("Another program is active") }).isTrue()
    }

    @Test fun `round trip through rows and the printer`() {
        val doc = example
        val rows = build(doc, library("Bench Press"))
        val exercises = (library("Bench Press") + rows.newExercises).associateBy { it.id }
        val export = ProgramExporter.toDoc(
            ProgramExportRows(
                program = rows.program,
                days = rows.days,
                exercisesByDay = rows.programExercises.groupBy { it.programDayId },
                exercises = exercises,
                rules = rows.rules.associateBy { it.id },
            ),
        )
        // The exported file prints and parses back to exactly itself.
        val reparsed = parse(ProgramScript.print(export))
        assertThat(reparsed).isEqualTo(export)

        // And means the same as the original on every pure part.
        assertThat(export.name).isEqualTo(doc.name)
        assertThat(export.days.map { it.name }).isEqualTo(doc.days.map { it.name })
        doc.days.zip(export.days).forEach { (d, e) ->
            assertThat(e.exercises.map { it.name.lowercase() }).isEqualTo(d.exercises.map { it.name.lowercase() })
            d.exercises.zip(e.exercises).forEach { (a, b) ->
                assertThat(b.sets).isEqualTo(a.sets)
                assertThat(b.reps).isEqualTo(a.reps)
                assertThat(b.rpe).isEqualTo(a.rpe)
                assertThat(b.bodyweight).isEqualTo(a.bodyweight)
                assertThat(b.restSec).isEqualTo(doc.effectiveRest(a) ?: 120)
                assertThat(b.weight?.kg ?: 0.0).isWithin(0.01).of(a.weight?.kg ?: 0.0)
                val spec = doc.effectiveProgress(a)
                if (spec?.type == null) assertThat(b.progress).isEqualTo(com.zeroindex.intake.domain.script.RuleSpec.NoProgression)
                else assertThat(b.progress!!.paramsJson()).isEqualTo(spec.paramsJson())
            }
        }
    }

    @Test fun `file name drops characters a file system dislikes`() {
        assertThat(ProgramExporter.fileName("Upper / lower: 4 days")).isEqualTo("Upper lower 4 days.md")
        assertThat(ProgramExporter.fileName("///")).isEqualTo("program.md")
    }
}
