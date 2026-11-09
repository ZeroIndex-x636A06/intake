package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.generator.GeneratedDay

/**
 * Saved workouts: single-session routines such as "Arm day" or "Hotel gym" (docs/WORKOUTS.md).
 *
 * A workout is a **program with `isStandalone = true` and exactly one day**. The program, day and
 * exercise rows, the builder, progression rules, history, sync and backup all work on it unchanged; it
 * is just never active, never in the program list and never advances a rotation. Everything here is
 * pure so it is tested without a database.
 */
object WorkoutBuilder {

    /** An empty workout with its one day, named like the workout. */
    fun blank(name: String, now: Long, nextId: () -> String = ::newId): ProgramRows {
        val program = ProgramEntity(
            id = nextId(), name = name.trim().ifBlank { "My workout" }, description = "",
            isActive = false, isStandalone = true, createdAt = now, updatedAt = now,
        )
        val day = ProgramDayEntity(
            id = nextId(), programId = program.id, dayIndex = 0, name = program.name, createdAt = now, updatedAt = now,
        )
        return ProgramRows(program, listOf(day), emptyList())
    }

    /** A workout holding a copy of one program day (the original is untouched). */
    fun copyOfDay(
        name: String,
        sourceDay: ProgramDayEntity,
        sourceExercises: List<ProgramExerciseEntity>,
        now: Long,
        nextId: () -> String = ::newId,
    ): ProgramRows {
        val base = blank(name.ifBlank { sourceDay.name }, now, nextId)
        val day = base.days.single()
        val copies = sourceExercises.filter { it.deletedAt == null }.sortedBy { it.displayOrder }
            .mapIndexed { order, e ->
                e.copy(id = nextId(), programDayId = day.id, displayOrder = order, createdAt = now, updatedAt = now, deletedAt = null)
            }
        return base.copy(exercises = copies)
    }

    /** A workout from a generated day (what "Save as workout" builds from a freestyle session). */
    fun fromGeneratedDay(
        name: String,
        generated: GeneratedDay,
        ruleIds: Map<ProgressionType, String>,
        now: Long,
        nextId: () -> String = ::newId,
    ): ProgramRows {
        val base = blank(name, now, nextId)
        val rows = ProgramBlueprintBuilder.dayRows(base.program.id, 0, generated.copy(weekday = null), ruleIds, now, nextId)
        // The day takes the workout's name; dayRows made its own id, so use that day.
        val day = rows.first.copy(name = base.program.name)
        return ProgramRows(base.program, listOf(day), rows.second)
    }

    /** The day to start a session on, or null if the workout has no live day. */
    fun startDay(days: List<ProgramDayEntity>): ProgramDayEntity? =
        days.filter { it.deletedAt == null }.minByOrNull { it.dayIndex }

    /** Soft delete: stamps every row, removes none. Sessions already logged against it keep their day. */
    fun softDelete(rows: ProgramRows, now: Long): ProgramRows = ProgramRows(
        program = rows.program.copy(deletedAt = now, updatedAt = now),
        days = rows.days.map { it.copy(deletedAt = now, updatedAt = now) },
        exercises = rows.exercises.map { it.copy(deletedAt = now, updatedAt = now) },
    )

    /** What "My workouts" lists: live standalone programs, by name. */
    fun visible(programs: List<ProgramEntity>): List<ProgramEntity> =
        programs.filter { it.isStandalone && it.deletedAt == null }.sortedBy { it.name.lowercase() }

    /** A name for a duplicate that does not clash: "Arm day (copy)", "Arm day (copy 2)". */
    fun duplicateName(name: String, existing: List<String>): String = ProgramEdits.nextCopyName(name, existing)
}
