package com.zeroindex.intake.data.seed

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A starter exercise library and one four-day upper/lower program, so the Train
 * tab does something useful before anyone sits down to build a program. Nothing
 * here is special-cased: it writes the same rows the builder writes.
 */
@Singleton
class ProgramSeed @Inject constructor(
    private val repository: TrainingRepository,
    private val clock: Clock,
) {
    suspend fun ensureExerciseLibrary(): List<ExerciseEntity> {
        val existing = repository.exercises()
        if (existing.isNotEmpty()) return existing
        val now = clock.nowMillis()
        val library = LIBRARY.map { (name, muscle, equipment, bodyweight) ->
            ExerciseEntity(
                id = newId(),
                name = name,
                primaryMuscle = muscle,
                equipment = equipment,
                isBodyweight = bodyweight,
                defaultRestSec = if (muscle in setOf("quads", "back", "chest")) 180 else 90,
                createdAt = now,
                updatedAt = now,
            )
        }
        repository.saveExercises(library)
        return library
    }

    suspend fun ensureRules(): Map<ProgressionType, ProgressionRuleEntity> {
        val existing = repository.rules()
        if (existing.isNotEmpty()) return existing.associateBy { it.type }
        val now = clock.nowMillis()
        val rules = listOf(
            ProgressionRuleEntity(
                id = newId(),
                name = "Linear, +2.5 kg",
                type = ProgressionType.LINEAR,
                params = """{"incrementKg":2.5,"deloadPercent":10,"failuresBeforeDeload":2}""",
                createdAt = now,
                updatedAt = now,
            ),
            ProgressionRuleEntity(
                id = newId(),
                name = "Double progression",
                type = ProgressionType.DOUBLE_PROGRESSION,
                params = """{"incrementKg":2.5}""",
                createdAt = now,
                updatedAt = now,
            ),
            ProgressionRuleEntity(
                id = newId(),
                name = "RPE 8 target",
                type = ProgressionType.RPE_BASED,
                params = "{}",
                createdAt = now,
                updatedAt = now,
            ),
        )
        repository.saveRules(rules)
        return rules.associateBy { it.type }
    }

    /** Four days: lower, upper push, lower, upper pull. */
    suspend fun createUpperLowerProgram() {
        val library = ensureExerciseLibrary().associateBy { it.name }
        val rules = ensureRules()
        val linear = rules[ProgressionType.LINEAR]?.id
        val double = rules[ProgressionType.DOUBLE_PROGRESSION]?.id

        val program = repository.createProgram(
            name = "Upper / lower, four days",
            description = "Compound lifts on linear progression, accessories on double " +
                "progression. A starting point, not a prescription.",
        )
        repository.activate(program.id)

        DAYS.forEachIndexed { dayIndex, (dayName, entries) ->
            val day = repository.addDay(program.id, dayIndex, dayName)
            entries.forEachIndexed { order, entry ->
                val exercise = library[entry.exercise] ?: return@forEachIndexed
                repository.addProgramExercise(
                    programDayId = day.id,
                    exerciseId = exercise.id,
                    displayOrder = order,
                    targetSets = entry.sets,
                    targetRepsMin = entry.repsMin,
                    targetRepsMax = entry.repsMax,
                    targetRpe = entry.rpe,
                    progressionRuleId = if (entry.compound) linear else double,
                    restSec = if (entry.compound) 180 else 90,
                )
            }
        }
    }

    private data class Entry(
        val exercise: String,
        val sets: Int,
        val repsMin: Int,
        val repsMax: Int,
        val compound: Boolean,
        val rpe: Double? = null,
    )

    private companion object {
        /** name, primary muscle, equipment, bodyweight */
        val LIBRARY = listOf(
            Quad("Back squat", "quads", "barbell", false),
            Quad("Front squat", "quads", "barbell", false),
            Quad("Romanian deadlift", "hamstrings", "barbell", false),
            Quad("Deadlift", "back", "barbell", false),
            Quad("Leg press", "quads", "machine", false),
            Quad("Leg curl", "hamstrings", "machine", false),
            Quad("Calf raise", "calves", "machine", false),
            Quad("Bench press", "chest", "barbell", false),
            Quad("Incline dumbbell press", "chest", "dumbbell", false),
            Quad("Overhead press", "shoulders", "barbell", false),
            Quad("Lateral raise", "shoulders", "dumbbell", false),
            Quad("Triceps pushdown", "triceps", "cable", false),
            Quad("Pull-up", "back", "bodyweight", true),
            Quad("Barbell row", "back", "barbell", false),
            Quad("Lat pulldown", "back", "cable", false),
            Quad("Face pull", "shoulders", "cable", false),
            Quad("Barbell curl", "biceps", "barbell", false),
            Quad("Hammer curl", "biceps", "dumbbell", false),
            Quad("Plank", "core", "bodyweight", true),
            Quad("Hanging leg raise", "core", "bodyweight", true),
        )

        val DAYS = listOf(
            "Lower A" to listOf(
                Entry("Back squat", 3, 5, 5, compound = true),
                Entry("Romanian deadlift", 3, 8, 10, compound = true),
                Entry("Leg curl", 3, 10, 12, compound = false),
                Entry("Calf raise", 4, 10, 15, compound = false),
                Entry("Hanging leg raise", 3, 8, 12, compound = false),
            ),
            "Upper push" to listOf(
                Entry("Bench press", 3, 5, 5, compound = true),
                Entry("Overhead press", 3, 8, 10, compound = true),
                Entry("Incline dumbbell press", 3, 10, 12, compound = false),
                Entry("Lateral raise", 3, 12, 15, compound = false),
                Entry("Triceps pushdown", 3, 10, 12, compound = false),
            ),
            "Lower B" to listOf(
                Entry("Deadlift", 3, 5, 5, compound = true),
                Entry("Front squat", 3, 6, 8, compound = true),
                Entry("Leg press", 3, 10, 12, compound = false),
                Entry("Calf raise", 4, 10, 15, compound = false),
                Entry("Plank", 3, 1, 1, compound = false),
            ),
            "Upper pull" to listOf(
                Entry("Barbell row", 3, 6, 8, compound = true),
                Entry("Pull-up", 3, 6, 10, compound = true),
                Entry("Lat pulldown", 3, 10, 12, compound = false),
                Entry("Face pull", 3, 12, 15, compound = false),
                Entry("Barbell curl", 3, 10, 12, compound = false),
            ),
        )
    }
}

/** Tiny holder so the library table reads as rows rather than nested pairs. */
private data class Quad(
    val name: String,
    val muscle: String,
    val equipment: String,
    val bodyweight: Boolean,
)
