package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity

/**
 * The row-building half of the program editing functions in `TrainingRepository` (`moveDay`,
 * `duplicateDay`, `copySettingsToOthers`), kept free of the database so it is unit-tested on the JVM.
 * `createProgramFromBlueprint` already builds its rows in [ProgramBlueprintBuilder]. The repository
 * only reads the rows, calls these, and writes what they return inside one transaction.
 */
object ProgramEdits {

    /** Day order is `dayIndex` 0..n-1. Rows already at the right index are returned unchanged. */
    fun renumberDays(days: List<ProgramDayEntity>, now: Long): List<ProgramDayEntity> =
        days.mapIndexed { i, d -> if (d.dayIndex == i) d else d.copy(dayIndex = i, updatedAt = now) }

    fun renumberExercises(list: List<ProgramExerciseEntity>, now: Long): List<ProgramExerciseEntity> =
        list.mapIndexed { i, e -> if (e.displayOrder == i) e else e.copy(displayOrder = i, updatedAt = now) }

    /**
     * Moves [dayId] by [delta] places (clamped to the ends) and returns every day renumbered, or null
     * when nothing would change (unknown day, or already at that end).
     */
    fun moveDay(days: List<ProgramDayEntity>, dayId: String, delta: Int, now: Long): List<ProgramDayEntity>? {
        if (days.isEmpty()) return null
        val list = days.toMutableList()
        val from = list.indexOfFirst { it.id == dayId }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return null
        list.add(to, list.removeAt(from))
        return renumberDays(list, now)
    }

    /** Everything a duplicate writes: the new day, all days in their new order, and the copied exercises. */
    data class DuplicatedDay(
        val copy: ProgramDayEntity,
        val daysInOrder: List<ProgramDayEntity>,
        val exercises: List<ProgramExerciseEntity>,
    )

    /**
     * Copies [source] and its [exercises] right after the original. The copy gets a fresh id and a
     * "(copy)" name that does not clash with its [siblings] (which include the source), and every
     * copied exercise gets a fresh id pointing at the copy.
     */
    fun duplicateDay(
        source: ProgramDayEntity,
        siblings: List<ProgramDayEntity>,
        exercises: List<ProgramExerciseEntity>,
        now: Long,
        nextId: () -> String,
    ): DuplicatedDay {
        val copy = source.copy(
            id = nextId(),
            name = nextCopyName(source.name, siblings.map { it.name }),
            createdAt = now,
            updatedAt = now,
        )
        val days = siblings.toMutableList()
        days.add(days.indexOfFirst { it.id == source.id } + 1, copy)
        return DuplicatedDay(
            copy = copy,
            daysInOrder = renumberDays(days, now),
            exercises = exercises.map { it.copy(id = nextId(), programDayId = copy.id, createdAt = now, updatedAt = now) },
        )
    }

    /**
     * Sets, reps, effort, rest and progression from [source] onto each of [targets]. The target's
     * own exercise, order, day and starting weight are untouched. [source] itself is never changed.
     */
    fun copySettings(source: ProgramExerciseEntity, targets: List<ProgramExerciseEntity>, now: Long): List<ProgramExerciseEntity> =
        targets.filter { it.id != source.id }.map {
            it.copy(
                targetSets = source.targetSets,
                targetRepsMin = source.targetRepsMin,
                targetRepsMax = source.targetRepsMax,
                targetRpe = source.targetRpe,
                restSec = source.restSec,
                progressionRuleId = source.progressionRuleId,
                updatedAt = now,
            )
        }

    /** "Push (copy)", then "Push (copy 2)" and so on; never repeats a name already in [existing]. */
    fun nextCopyName(name: String, existing: List<String>): String {
        val base = name.removeSuffix(" (copy)")
        var candidate = "$base (copy)"
        var n = 2
        while (existing.any { it.equals(candidate, ignoreCase = true) }) {
            candidate = "$base (copy $n)"
            n++
        }
        return candidate
    }
}
