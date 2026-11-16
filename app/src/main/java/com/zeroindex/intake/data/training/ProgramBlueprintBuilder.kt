package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.generator.CatalogExercise
import com.zeroindex.intake.domain.training.generator.GeneratedDay
import com.zeroindex.intake.domain.training.generator.GeneratedExercise
import com.zeroindex.intake.domain.training.generator.GeneratedProgram

/** The rows of one new program, ready to insert. Every row is a normal, editable row. */
data class ProgramRows(
    val program: ProgramEntity,
    val days: List<ProgramDayEntity>,
    val exercises: List<ProgramExerciseEntity>,
)

/**
 * Turns a [GeneratedProgram] into database rows. The generator, the built-in templates and "save
 * a freestyle workout as a day" all go through here, so a program made by any route looks exactly
 * like one built by hand: fresh random ids, nothing marked as built in, nothing read-only.
 */
object ProgramBlueprintBuilder {

    /** The placeholder reason a freestyle day carries; not worth storing as a note. */
    const val FREESTYLE_WHY = "Copied from a freestyle workout."

    fun build(
        blueprint: GeneratedProgram,
        ruleIds: Map<ProgressionType, String>,
        now: Long,
        activate: Boolean = false,
        name: String = blueprint.name,
        nextId: () -> String = ::newId,
    ): ProgramRows {
        val program = ProgramEntity(
            id = nextId(),
            name = name,
            description = blueprint.description,
            isActive = activate,
            deloadEveryWeeks = blueprint.deloadEveryWeeks.takeIf { it >= 2 },
            createdAt = now,
            updatedAt = now,
        )
        val days = mutableListOf<ProgramDayEntity>()
        val exercises = mutableListOf<ProgramExerciseEntity>()
        blueprint.days.forEachIndexed { index, day ->
            val rows = dayRows(program.id, index, day, ruleIds, now, nextId)
            days += rows.first
            exercises += rows.second
        }
        return ProgramRows(program, days, exercises)
    }

    fun dayRows(
        programId: String,
        dayIndex: Int,
        day: GeneratedDay,
        ruleIds: Map<ProgressionType, String>,
        now: Long,
        nextId: () -> String = ::newId,
    ): Pair<ProgramDayEntity, List<ProgramExerciseEntity>> {
        val dayRow = ProgramDayEntity(
            id = nextId(),
            programId = programId,
            dayIndex = dayIndex,
            name = day.name,
            weekday = day.weekday,
            createdAt = now,
            updatedAt = now,
        )
        val rows = day.exercises.mapIndexed { order, e ->
            ProgramExerciseEntity(
                id = nextId(),
                programDayId = dayRow.id,
                exerciseId = e.exerciseId,
                displayOrder = order,
                targetSets = e.sets,
                targetRepsMin = e.repsMin,
                targetRepsMax = e.repsMax,
                targetRpe = e.rpe,
                progressionRuleId = e.progression?.let { ruleIds[it] },
                restSec = e.restSec,
                startWeightKg = e.startWeightKg,
                notes = listOfNotNull(e.why.takeIf { it.isNotBlank() && it != FREESTYLE_WHY }, e.startNote)
                    .joinToString(" ").ifBlank { null },
                createdAt = now,
                updatedAt = now,
            )
        }
        return dayRow to rows
    }
}

/** "Save as a program day": what a finished freestyle session looked like, as a day. */
object FreestyleDayBuilder {

    /**
     * One exercise per lift, in the order it was first logged. Warm-ups are left out. Sets is the
     * number of working sets, reps the range actually lifted, and the rest comes from the exercise.
     */
    fun toDay(
        dayName: String,
        sets: List<SetLogEntity>,
        exercises: Map<String, ExerciseEntity>,
    ): GeneratedDay {
        val working = sets.filter { !it.isWarmup && it.deletedAt == null }.sortedBy { it.completedAt }
        val lifted = working.groupBy { it.exerciseId }.entries
            .sortedBy { (_, list) -> list.minOf { it.completedAt } }
        val rows = lifted.mapNotNull { (exerciseId, list) ->
            val exercise = exercises[exerciseId] ?: return@mapNotNull null
            val repsMin = list.minOf { it.reps }.coerceAtLeast(1)
            GeneratedExercise(
                exerciseId = exerciseId,
                name = exercise.name,
                muscle = exercise.primaryMuscle,
                sets = list.size,
                repsMin = repsMin,
                repsMax = maxOf(repsMin, list.maxOf { it.reps }),
                rpe = null,
                restSec = exercise.defaultRestSec,
                progression = ProgressionType.DOUBLE_PROGRESSION,
                why = ProgramBlueprintBuilder.FREESTYLE_WHY,
            )
        }
        return GeneratedDay(dayName.trim().ifBlank { "Freestyle day" }, rows, estimatedMinutes = 0, why = "Saved from a freestyle workout.")
    }
}

/** The library row for a catalog entry. Seed rows carry the fixed seed timestamp so devices agree. */
fun CatalogExercise.toEntity(timestamp: Long): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    primaryMuscle = primaryMuscle,
    secondaryMuscles = secondaryMuscles.joinToString(","),
    equipment = equipment,
    isBodyweight = isBodyweight,
    defaultRestSec = defaultRestSec,
    createdAt = timestamp,
    updatedAt = timestamp,
)
