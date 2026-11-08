package com.zeroindex.intake.data.training

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.seed.ProgramTemplates
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.generateProgram
import org.junit.Test

class ProgramBlueprintTest {

    private val rules = ProgressionType.entries.associateWith { "rule-${it.name}" }

    @Test
    fun `copying a template makes ordinary editable rows`() {
        for (template in ProgramTemplates.ALL) {
            var n = 0
            val rows = ProgramBlueprintBuilder.build(template.blueprint, rules, now = 1_000L, nextId = { "id-${n++}" })

            assertThat(rows.program.name).isEqualTo(template.blueprint.name)
            assertThat(rows.program.isActive).isFalse()
            assertThat(rows.program.deletedAt).isNull()
            assertThat(rows.days).hasSize(template.dayCount)
            assertThat(rows.days.map { it.dayIndex }).containsExactlyElementsIn((0 until template.dayCount).toList()).inOrder()
            assertThat(rows.days.all { it.programId == rows.program.id && it.deletedAt == null }).isTrue()

            val dayIds = rows.days.map { it.id }.toSet()
            assertThat(rows.exercises.all { it.programDayId in dayIds && it.deletedAt == null }).isTrue()
            assertThat(rows.exercises.map { it.exerciseId }.toSet() - ExerciseCatalog.ALL.map { it.id }.toSet()).isEmpty()
            // Fresh ids, never the fixed seed ids, so a copy can never collide with the template or another copy.
            val allIds = listOf(rows.program.id) + dayIds + rows.exercises.map { it.id }
            assertThat(allIds).containsNoDuplicates()
            assertThat(rows.exercises.map { it.id }).containsNoneIn(ExerciseCatalog.ALL.map { it.id })
            // Rules are attached from the shared rule rows.
            assertThat(rows.exercises.mapNotNull { it.progressionRuleId }.toSet() - rules.values.toSet()).isEmpty()
        }
    }

    @Test
    fun `two copies of the same template do not share ids`() {
        val a = ProgramBlueprintBuilder.build(ProgramTemplates.ALL.first().blueprint, rules, 1L)
        val b = ProgramBlueprintBuilder.build(ProgramTemplates.ALL.first().blueprint, rules, 1L)
        assertThat(a.program.id).isNotEqualTo(b.program.id)
        assertThat(a.exercises.map { it.id }.intersect(b.exercises.map { it.id }.toSet())).isEmpty()
    }

    @Test
    fun `editing a copy leaves the template alone`() {
        val template = ProgramTemplates.ALL.first()
        val rows = ProgramBlueprintBuilder.build(template.blueprint, rules, 1L)
        val edited = rows.exercises.first().copy(targetSets = 9)
        assertThat(edited.targetSets).isEqualTo(9)
        assertThat(template.blueprint.days.first().exercises.first().sets).isNotEqualTo(9)
    }

    @Test
    fun `the old starter program is the first template with the same lifts`() {
        val t = ProgramTemplates.byId("upper-lower-4")!!
        assertThat(t.blueprint.days.map { it.name }).containsExactly("Lower A", "Upper push", "Lower B", "Upper pull").inOrder()
        assertThat(t.blueprint.days.first().exercises.first().name).isEqualTo("Back squat")
        assertThat(t.blueprint.days.first().exercises.first().exerciseId).isEqualTo(seedId("exercise", "Back squat"))
    }

    @Test
    fun `a generated program is saved through the same path`() {
        val program = generateProgram(GeneratorInputs(daysPerWeek = 4))
        val rows = ProgramBlueprintBuilder.build(program, rules, 5L, activate = true)
        assertThat(rows.program.isActive).isTrue()
        assertThat(rows.exercises).hasSize(program.days.sumOf { it.exercises.size })
        assertThat(rows.exercises.first().progressionRuleId).isNotNull()
        // Explosive and timed work carry no rule.
        val athletic = generateProgram(GeneratorInputs(goal = com.zeroindex.intake.domain.training.generator.TrainingGoal.ATHLETIC, sessionMinutes = 90))
        val athleticRows = ProgramBlueprintBuilder.build(athletic, rules, 5L)
        assertThat(athleticRows.exercises.any { it.progressionRuleId == null }).isTrue()
    }

    // ------------------------------------------------------------ freestyle

    private fun exercise(name: String, rest: Int = 90) = ExerciseCatalog.requireByName(name).let {
        ExerciseEntity(id = it.id, name = it.name, primaryMuscle = it.primaryMuscle, equipment = it.equipment, defaultRestSec = rest, createdAt = 0, updatedAt = 0)
    }

    private fun set(exercise: String, index: Int, kg: Double, reps: Int, at: Long, warmup: Boolean = false, deleted: Boolean = false) = SetLogEntity(
        id = "s-$exercise-$index-$at", sessionId = "session", exerciseId = ExerciseCatalog.requireByName(exercise).id,
        setIndex = index, weightKg = kg, reps = reps, isWarmup = warmup, completedAt = at, createdAt = at, updatedAt = at,
        deletedAt = if (deleted) at else null,
    )

    @Test
    fun `a freestyle session becomes a day with one row per lift in the order lifted`() {
        val sets = listOf(
            set("Bench press", 0, 40.0, 10, 100), set("Bench press", 1, 40.0, 9, 200), set("Bench press", 2, 40.0, 8, 300),
            set("Lat pulldown", 0, 50.0, 12, 400), set("Lat pulldown", 1, 50.0, 10, 500),
            set("Lat pulldown", 2, 20.0, 15, 450, warmup = true),
            set("Lateral raise", 0, 8.0, 15, 600, deleted = true),
        )
        val ex = listOf("Bench press", "Lat pulldown", "Lateral raise").associate { ExerciseCatalog.requireByName(it).id to exercise(it) }
        val day = FreestyleDayBuilder.toDay("  Chest and back ", sets, ex)

        assertThat(day.name).isEqualTo("Chest and back")
        assertThat(day.exercises.map { it.name }).containsExactly("Bench press", "Lat pulldown").inOrder()
        val bench = day.exercises[0]
        assertThat(bench.sets).isEqualTo(3)
        assertThat(bench.repsMin).isEqualTo(8)
        assertThat(bench.repsMax).isEqualTo(10)
        assertThat(day.exercises[1].sets).isEqualTo(2) // the warm-up is not counted
        assertThat(bench.progression).isEqualTo(ProgressionType.DOUBLE_PROGRESSION)
    }

    @Test
    fun `saving a freestyle day writes editable rows at the end of a program`() {
        val sets = listOf(set("Back squat", 0, 100.0, 5, 10), set("Back squat", 1, 100.0, 5, 20))
        val ex = mapOf(ExerciseCatalog.requireByName("Back squat").id to exercise("Back squat", rest = 180))
        val day = FreestyleDayBuilder.toDay("", sets, ex)
        assertThat(day.name).isEqualTo("Freestyle day")

        val (dayRow, exerciseRows) = ProgramBlueprintBuilder.dayRows("program-1", 3, day, rules, now = 77L)
        assertThat(dayRow.programId).isEqualTo("program-1")
        assertThat(dayRow.dayIndex).isEqualTo(3)
        assertThat(dayRow.deletedAt).isNull()
        assertThat(exerciseRows).hasSize(1)
        assertThat(exerciseRows[0].programDayId).isEqualTo(dayRow.id)
        assertThat(exerciseRows[0].targetSets).isEqualTo(2)
        assertThat(exerciseRows[0].restSec).isEqualTo(180)
        assertThat(exerciseRows[0].progressionRuleId).isEqualTo("rule-DOUBLE_PROGRESSION")
    }

    @Test
    fun `a session with only warm-ups saves nothing`() {
        val sets = listOf(set("Back squat", 0, 40.0, 10, 10, warmup = true))
        val ex = mapOf(ExerciseCatalog.requireByName("Back squat").id to exercise("Back squat"))
        assertThat(FreestyleDayBuilder.toDay("x", sets, ex).exercises).isEmpty()
    }

    // ------------------------------------------------- library and history

    @Test
    fun `exercise search matches names and filters by muscle and equipment`() {
        val all = ExerciseCatalog.ALL.map { exercise(it.name) }
        val chest = ExerciseFilter.apply(all, "", "chest", null)
        assertThat(chest.map { it.primaryMuscle }.toSet() - setOf("chest", "triceps", "shoulders")).isEmpty()
        assertThat(chest.map { it.name }).contains("Bench press")

        val cableBack = ExerciseFilter.apply(all, "", "back", "cable")
        assertThat(cableBack.map { it.name }).contains("Lat pulldown")
        assertThat(cableBack.all { it.equipment == "cable" }).isTrue()

        assertThat(ExerciseFilter.apply(all, "pull up", null, null).map { it.name }).contains("Pull-up")
        assertThat(ExerciseFilter.apply(all, "BENCH", null, null).map { it.name }).contains("Bench press")
        assertThat(ExerciseFilter.apply(all, "zzzz", null, null)).isEmpty()
    }

    @Test
    fun `custom exercises are told apart from built in ones`() {
        assertThat(ExerciseFilter.isCustom(exercise("Bench press"))).isFalse()
        assertThat(ExerciseFilter.isCustom(exercise("Bench press").copy(id = "my-own"))).isTrue()
    }

    @Test
    fun `history shows the best estimated max and groups sets by day`() {
        val day = 86_400_000L
        val sets = listOf(
            set("Bench press", 0, 60.0, 10, 1 * day + 5), set("Bench press", 1, 60.0, 8, 1 * day + 9),
            set("Bench press", 0, 80.0, 5, 3 * day + 5),
            set("Bench press", 0, 20.0, 15, 3 * day + 1, warmup = true),
        )
        val h = ExerciseHistoryCalc.summarize(sets)
        assertThat(h.days).hasSize(2)
        assertThat(h.days.first().startedAt).isEqualTo(3 * day + 5)
        assertThat(h.totalSets).isEqualTo(3)
        assertThat(h.heaviestKg).isEqualTo(80.0)
        // Epley: 80 x (1 + 5/30) = 93.33; 60 x (1 + 10/30) = 80.
        assertThat(h.bestEstimatedMaxKg).isWithin(0.01).of(93.33)
        assertThat(ExerciseHistoryCalc.summarize(emptyList()).bestEstimatedMaxKg).isNull()
    }
}
