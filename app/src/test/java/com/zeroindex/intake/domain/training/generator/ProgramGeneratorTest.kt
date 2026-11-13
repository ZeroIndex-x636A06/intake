package com.zeroindex.intake.domain.training.generator

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.model.ProgressionType
import org.junit.Test

class ProgramGeneratorTest {

    private val combos = buildList {
        for (goal in TrainingGoal.entries) for (exp in Experience.entries) for (days in 2..6) for (minutes in listOf(30, 45, 60, 90)) {
            add(GeneratorInputs(goal = goal, experience = exp, daysPerWeek = days, sessionMinutes = minutes))
        }
    }

    private fun GeneratedProgram.exercises() = days.flatMap { it.exercises }

    // ---------------------------------------------------------------- split

    @Test
    fun `one day per requested training day`() {
        for (d in 2..6) assertThat(generateProgram(GeneratorInputs(daysPerWeek = d)).days).hasSize(d)
    }

    @Test
    fun `split follows the day count`() {
        fun names(d: Int, exp: Experience = Experience.SOME) =
            generateProgram(GeneratorInputs(daysPerWeek = d, experience = exp)).days.map { it.name }
        assertThat(names(2)).containsExactly("Full body A", "Full body B").inOrder()
        assertThat(names(3)).containsExactly("Full body A", "Full body B", "Full body C").inOrder()
        assertThat(names(4)).containsExactly("Upper A", "Lower A", "Upper B", "Lower B").inOrder()
        assertThat(names(5)).containsExactly("Push", "Pull", "Legs", "Upper", "Lower").inOrder()
        assertThat(names(5, Experience.NEW)).containsExactly("Upper A", "Lower A", "Full body", "Upper B", "Lower B").inOrder()
        assertThat(names(6)).containsExactly("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B").inOrder()
    }

    @Test
    fun `each big muscle is trained at least twice a week from four days up`() {
        for (d in 4..6) {
            val p = generateProgram(GeneratorInputs(daysPerWeek = d, sessionMinutes = 90, experience = Experience.EXPERIENCED))
            for (m in listOf("chest", "back", "quads", "hamstrings", "shoulders")) {
                val sessions = p.days.count { day -> day.exercises.any { it.muscle == m } }
                assertThat(sessions).isAtLeast(2)
            }
        }
    }

    // --------------------------------------------------------------- volume

    @Test
    fun `weekly sets stay inside the evidence range`() {
        for (goal in TrainingGoal.entries) for (exp in Experience.entries) for (days in 4..6) {
            val p = generateProgram(GeneratorInputs(goal = goal, experience = exp, daysPerWeek = days, sessionMinutes = 90))
            for ((muscle, sets) in p.weeklySets) {
                val allowed = VolumeGuide.allowed(exp, muscle, focus = false)
                assertThat(sets).isAtMost(allowed.last)
                if (p.warnings.none { "-minute sessions" in it }) assertThat(sets).isAtLeast(allowed.first)
            }
        }
    }

    @Test
    fun `ranges by experience match the brief`() {
        assertThat(VolumeGuide.range(Experience.NEW)).isEqualTo(8..10)
        assertThat(VolumeGuide.range(Experience.SOME)).isEqualTo(10..14)
        assertThat(VolumeGuide.range(Experience.EXPERIENCED)).isEqualTo(12..20)
    }

    @Test
    fun `more experience means more sets for the same muscle`() {
        fun chest(e: Experience) = generateProgram(GeneratorInputs(experience = e, daysPerWeek = 4, sessionMinutes = 90, goal = TrainingGoal.BUILD_MUSCLE))
            .weeklySets.getValue("chest")
        assertThat(chest(Experience.NEW)).isLessThan(chest(Experience.SOME))
        assertThat(chest(Experience.SOME)).isLessThan(chest(Experience.EXPERIENCED))
    }

    @Test
    fun `focus muscles get more sets than they would otherwise`() {
        for (exp in Experience.entries) {
            val base = GeneratorInputs(experience = exp, daysPerWeek = 5, sessionMinutes = 90)
            val plain = generateProgram(base).weeklySets.getValue("shoulders")
            val focused = generateProgram(base.copy(focusMuscles = setOf("shoulders"))).weeklySets.getValue("shoulders")
            assertThat(focused).isGreaterThan(plain)
            val allowed = VolumeGuide.allowed(exp, "shoulders", focus = true)
            assertThat(focused).isAtMost(allowed.last)
        }
    }

    @Test
    fun `advanced volume override replaces the target`() {
        val p = generateProgram(GeneratorInputs(daysPerWeek = 4, sessionMinutes = 90, weeklySetOverrides = mapOf("calves" to 0, "chest" to 6)))
        assertThat(p.weeklySets).doesNotContainKey("calves")
        assertThat(p.weeklySets.getValue("chest")).isEqualTo(6)
    }

    // ----------------------------------------------------------------- time

    @Test
    fun `every day fits its session length`() {
        for (inputs in combos) {
            val p = generateProgram(inputs)
            for (day in p.days) {
                if (day.exercises.size > 1) {
                    assertThat(day.estimatedMinutes).isAtMost(inputs.sessionMinutes)
                }
            }
        }
    }

    @Test
    fun `short sessions get fewer sets than long ones and say so`() {
        val short = generateProgram(GeneratorInputs(daysPerWeek = 3, sessionMinutes = 30, experience = Experience.EXPERIENCED))
        val long = generateProgram(GeneratorInputs(daysPerWeek = 3, sessionMinutes = 90, experience = Experience.EXPERIENCED))
        assertThat(short.weeklySets.values.sum()).isLessThan(long.weeklySets.values.sum())
        assertThat(short.warnings.any { "30-minute" in it }).isTrue()
    }

    @Test
    fun `time trimming protects focus muscles`() {
        val base = GeneratorInputs(daysPerWeek = 3, sessionMinutes = 45, experience = Experience.SOME)
        val plain = generateProgram(base).weeklySets["biceps"] ?: 0
        val focused = generateProgram(base.copy(focusMuscles = setOf("biceps"))).weeklySets.getValue("biceps")
        assertThat(focused).isGreaterThan(0)
        assertThat(focused).isAtLeast(plain)
    }

    // ------------------------------------------------------------ equipment

    @Test
    fun `only exercises that use the chosen equipment are picked`() {
        val sets = listOf(Equipment.HOME_DUMBBELLS, Equipment.BODYWEIGHT_ONLY, setOf(Equipment.BARBELL), setOf(Equipment.MACHINE, Equipment.CABLE))
        for (equipment in sets) for (goal in TrainingGoal.entries) for (days in listOf(3, 5)) {
            val p = generateProgram(GeneratorInputs(goal = goal, equipment = equipment, daysPerWeek = days))
            for (e in p.exercises()) {
                assertThat(equipment).contains(ExerciseCatalog.byId(e.exerciseId)!!.equipment)
            }
        }
    }

    @Test
    fun `bodyweight only still trains the main muscles`() {
        val p = generateProgram(GeneratorInputs(equipment = Equipment.BODYWEIGHT_ONLY, daysPerWeek = 4, sessionMinutes = 60))
        assertThat(p.weeklySets.keys).containsAtLeast("chest", "back", "quads", "hamstrings")
        // No weights to add, so reps run higher.
        assertThat(p.exercises().filter { !it.explosive }.all { it.repsMax >= 15 || it.repsMin >= 30 }).isTrue()
    }

    @Test
    fun `an empty equipment set falls back to bodyweight`() {
        val p = generateProgram(GeneratorInputs(equipment = emptySet()))
        assertThat(p.exercises()).isNotEmpty()
        assertThat(p.exercises().all { ExerciseCatalog.byId(it.exerciseId)!!.equipment == Equipment.BODYWEIGHT }).isTrue()
    }

    @Test
    fun `hidden exercises are never picked`() {
        val full = generateProgram(GeneratorInputs(daysPerWeek = 4))
        val hidden = full.exercises().map { it.exerciseId }.toSet()
        val again = generateProgram(GeneratorInputs(daysPerWeek = 4, hiddenExerciseIds = hidden))
        assertThat(again.exercises().map { it.exerciseId }.toSet().intersect(hidden)).isEmpty()
    }

    // ---------------------------------------------------------------- avoid

    @Test
    fun `exercises that load an avoided area are left out`() {
        for (area in BodyArea.entries) for (equipment in listOf(Equipment.FULL_GYM, Equipment.HOME_DUMBBELLS)) {
            val p = generateProgram(GeneratorInputs(avoid = setOf(area), equipment = equipment, daysPerWeek = 5, sessionMinutes = 90))
            for (e in p.exercises()) assertThat(ExerciseCatalog.byId(e.exerciseId)!!.loads).doesNotContain(area)
        }
    }

    @Test
    fun `avoiding the shoulder removes pressing but keeps training the chest and says what happened`() {
        val p = generateProgram(GeneratorInputs(avoid = setOf(BodyArea.SHOULDER), daysPerWeek = 4, sessionMinutes = 90))
        val names = p.exercises().map { it.name }
        assertThat(names).doesNotContain("Bench press")
        assertThat(names).doesNotContain("Overhead press")
        assertThat(p.whys.any { "shoulder" in it }).isTrue()
    }

    @Test
    fun `a muscle with no safe exercise is reported, not invented`() {
        val p = generateProgram(
            GeneratorInputs(equipment = setOf(Equipment.BARBELL), avoid = setOf(BodyArea.SHOULDER, BodyArea.WRIST, BodyArea.LOWER_BACK, BodyArea.ELBOW), daysPerWeek = 4),
        )
        assertThat(p.warnings.any { "No " in it && "left out" in it }).isTrue()
    }

    // ------------------------------------------------------- reps and effort

    @Test
    fun `strength main lifts are low rep at effort 7 to 8`() {
        for (exp in Experience.entries) {
            val p = generateProgram(GeneratorInputs(goal = TrainingGoal.GET_STRONGER, experience = exp, daysPerWeek = 4, sessionMinutes = 90))
            val first = p.days.first().exercises.first()
            assertThat(first.repsMin).isAtLeast(3)
            assertThat(first.repsMax).isAtMost(6)
            assertThat(first.rpe).isAnyOf(7.0, 8.0)
        }
    }

    @Test
    fun `muscle building uses 6 to 12 on compounds and 10 to 20 on isolations`() {
        for (goal in listOf(TrainingGoal.BUILD_MUSCLE, TrainingGoal.LOSE_FAT)) {
            val p = generateProgram(GeneratorInputs(goal = goal, daysPerWeek = 4, sessionMinutes = 90))
            for (e in p.exercises()) {
                val compound = ExerciseCatalog.byId(e.exerciseId)!!.compound
                if (e.repsMin >= 30) continue
                if (compound) {
                    assertThat(e.repsMin).isAtLeast(6)
                    assertThat(e.repsMax).isAtMost(12)
                } else {
                    assertThat(e.repsMin).isAtLeast(10)
                    assertThat(e.repsMax).isAtMost(20)
                }
                assertThat(e.rpe).isAtLeast(7.0)
                assertThat(e.rpe).isAtMost(9.0)
            }
        }
    }

    @Test
    fun `fat loss training matches muscle building`() {
        val build = generateProgram(GeneratorInputs(goal = TrainingGoal.BUILD_MUSCLE, daysPerWeek = 4, sessionMinutes = 90))
        val cut = generateProgram(GeneratorInputs(goal = TrainingGoal.LOSE_FAT, daysPerWeek = 4, sessionMinutes = 90))
        assertThat(cut.exercises().map { it.repsMin to it.repsMax }.toSet()).isEqualTo(build.exercises().map { it.repsMin to it.repsMax }.toSet())
    }

    @Test
    fun `athletic programs start lower days with explosive work`() {
        val p = generateProgram(GeneratorInputs(goal = TrainingGoal.ATHLETIC, daysPerWeek = 4, sessionMinutes = 90, experience = Experience.SOME))
        val lower = p.days.first { it.name.startsWith("Lower") }
        assertThat(lower.exercises.first().explosive).isTrue()
        assertThat(lower.exercises.first().repsMax).isAtMost(5)
        assertThat(lower.exercises.first().progression).isNull()
        // Power work is not counted as hard sets.
        assertThat(p.weeklySets.values.sum()).isEqualTo(p.exercises().filter { !it.explosive }.sumOf { it.sets })
    }

    @Test
    fun `other goals have no explosive work`() {
        val p = generateProgram(GeneratorInputs(goal = TrainingGoal.BUILD_MUSCLE, daysPerWeek = 4))
        assertThat(p.exercises().none { it.explosive }).isTrue()
    }

    @Test
    fun `advanced rep ranges apply`() {
        val p = generateProgram(
            GeneratorInputs(daysPerWeek = 4, sessionMinutes = 90, compoundReps = RepRange(4, 6), isolationReps = RepRange(15, 25)),
        )
        for (e in p.exercises()) {
            val compound = ExerciseCatalog.byId(e.exerciseId)!!.compound
            if (e.repsMin >= 30) continue
            if (compound) assertThat(e.repsMin to e.repsMax).isEqualTo(4 to 6) else assertThat(e.repsMin to e.repsMax).isEqualTo(15 to 25)
        }
    }

    // ---------------------------------------------------------- progression

    @Test
    fun `progression follows goal and experience`() {
        fun types(goal: TrainingGoal, exp: Experience) =
            generateProgram(GeneratorInputs(goal = goal, experience = exp, daysPerWeek = 4, sessionMinutes = 90))
                .exercises().filter { !it.explosive && it.repsMin < 30 }

        // New lifters add weight every session on compounds.
        assertThat(types(TrainingGoal.BUILD_MUSCLE, Experience.NEW).filter { ExerciseCatalog.byId(it.exerciseId)!!.compound && !ExerciseCatalog.byId(it.exerciseId)!!.isBodyweight }
            .all { it.progression == ProgressionType.LINEAR }).isTrue()
        // Hypertrophy otherwise uses double progression.
        assertThat(types(TrainingGoal.BUILD_MUSCLE, Experience.SOME).all { it.progression == ProgressionType.DOUBLE_PROGRESSION }).isTrue()
        // Experienced strength goes by effort on the barbell lifts.
        val strong = types(TrainingGoal.GET_STRONGER, Experience.EXPERIENCED)
        assertThat(strong.first().progression).isEqualTo(ProgressionType.RPE_BASED)
        // Every RPE rule has an effort target to work against.
        assertThat(strong.filter { it.progression == ProgressionType.RPE_BASED }.all { it.rpe != null }).isTrue()
    }

    // ---------------------------------------------------------------- deload

    @Test
    fun `deload every five or six weeks by experience`() {
        assertThat(generateProgram(GeneratorInputs(experience = Experience.NEW)).deloadEveryWeeks).isEqualTo(6)
        assertThat(generateProgram(GeneratorInputs(experience = Experience.SOME)).deloadEveryWeeks).isEqualTo(6)
        assertThat(generateProgram(GeneratorInputs(experience = Experience.EXPERIENCED)).deloadEveryWeeks).isEqualTo(5)
        val p = generateProgram(GeneratorInputs(deloadEveryWeeks = 4))
        assertThat(p.deloadEveryWeeks).isEqualTo(4)
        assertThat(p.description).contains("Easy week: every 4 weeks")
    }

    // ------------------------------------------------- determinism and shape

    @Test
    fun `same inputs always give the same program`() {
        for (inputs in combos.filterIndexed { i, _ -> i % 7 == 0 }) {
            assertThat(generateProgram(inputs)).isEqualTo(generateProgram(inputs))
        }
    }

    @Test
    fun `every exercise id exists in the seeded library`() {
        val seeded = ExerciseCatalog.ALL.map { it.id }.toSet()
        for (inputs in combos) {
            for (e in generateProgram(inputs).exercises()) assertThat(seeded).contains(e.exerciseId)
        }
    }

    @Test
    fun `catalog covers every kind of equipment and muscle with a primary muscle`() {
        for (eq in Equipment.ALL) assertThat(ExerciseCatalog.ALL.count { it.equipment == eq }).isAtLeast(8)
        for (m in Muscles.ALL) assertThat(ExerciseCatalog.ALL.count { it.primaryMuscle == m }).isAtLeast(2)
        assertThat(ExerciseCatalog.ALL.all { it.primaryMuscle in Muscles.ALL && it.equipment in Equipment.ALL }).isTrue()
        assertThat(ExerciseCatalog.ALL.map { it.id }.toSet()).hasSize(ExerciseCatalog.ALL.size)
        // The old starter library keeps its ids, so existing installs match.
        assertThat(ExerciseCatalog.byName("Back squat")!!.id).isEqualTo(com.zeroindex.intake.data.db.entity.seedId("exercise", "Back squat"))
    }

    @Test
    fun `no exercise repeats within a day and every choice has a reason`() {
        for (inputs in combos.filterIndexed { i, _ -> i % 5 == 0 }) {
            val p = generateProgram(inputs)
            for (d in p.days) {
                assertThat(d.exercises.map { it.exerciseId }).containsNoDuplicates()
                assertThat(d.exercises.all { it.why.isNotBlank() && it.sets >= 1 }).isTrue()
                assertThat(d.why).isNotEmpty()
            }
            assertThat(p.whys).isNotEmpty()
        }
    }

    @Test
    fun `compounds come before isolations`() {
        val p = generateProgram(GeneratorInputs(daysPerWeek = 4, sessionMinutes = 90))
        for (d in p.days) {
            val flags = d.exercises.map { ExerciseCatalog.byId(it.exerciseId)!!.compound }
            val firstIso = flags.indexOf(false)
            if (firstIso >= 0) assertThat(flags.drop(firstIso).none { it }).isTrue()
        }
    }

    @Test
    fun `variants rotate between the two sessions of a muscle`() {
        val p = generateProgram(GeneratorInputs(daysPerWeek = 4, sessionMinutes = 90))
        val a = p.days[0].exercises.first { it.muscle == "chest" }.exerciseId
        val b = p.days[2].exercises.first { it.muscle == "chest" }.exerciseId
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `out of range inputs are clamped`() {
        assertThat(generateProgram(GeneratorInputs(daysPerWeek = 9)).days).hasSize(6)
        assertThat(generateProgram(GeneratorInputs(daysPerWeek = 0)).days).hasSize(2)
    }
}
