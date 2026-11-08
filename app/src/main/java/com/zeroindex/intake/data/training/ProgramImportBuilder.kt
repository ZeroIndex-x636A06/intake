package com.zeroindex.intake.data.training

import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.script.ProgramDoc
import com.zeroindex.intake.domain.script.RuleSpec
import com.zeroindex.intake.domain.script.ScriptText

/** What is already in the database for the program being imported (live rows only). */
data class ExistingProgramState(
    val program: ProgramEntity? = null,
    val days: List<ProgramDayEntity> = emptyList(),
    val exercises: List<ProgramExerciseEntity> = emptyList(),
    /** True when some other program is already active. */
    val anotherProgramActive: Boolean = false,
)

data class ImportedDayLine(val name: String, val exerciseCount: Int)

/** Everything the preview shows, computed without touching the database. */
data class ProgramImportSummary(
    val programName: String,
    val updatingExisting: Boolean,
    val days: List<ImportedDayLine>,
    val exerciseCount: Int,
    val newExercises: List<String>,
    val matchedExercises: List<String>,
    /** Distinct rules, as "Linear, +2.5 kg". */
    val rules: List<String>,
    val removedExercises: Int,
    val removedDays: Int,
    /** Things the file says that the app has nowhere to store. */
    val notices: List<String>,
)

/** The rows one import writes in a single transaction. */
data class ProgramImportBatch(
    val program: ProgramEntity,
    val days: List<ProgramDayEntity>,
    val newExercises: List<ExerciseEntity>,
    val rules: List<ProgressionRuleEntity>,
    val programExercises: List<ProgramExerciseEntity>,
    /** Rows of the previous version that the new file no longer has: same rows with deletedAt set. */
    val removedDays: List<ProgramDayEntity>,
    val removedExercises: List<ProgramExerciseEntity>,
    val summary: ProgramImportSummary,
)

/**
 * Turns a parsed [ProgramDoc] into rows. Pure: no database, no clock. Ids come from
 * [seedId] keyed on program name + day index + exercise name, so importing the same
 * file again updates the program in place; rows the new file no longer has are
 * soft-deleted, never removed.
 */
object ProgramImportBuilder {

    const val DEFAULT_REST_SEC = 120
    private const val DEFAULT_MUSCLE = "other"

    fun programId(name: String) = seedId("program", name)
    fun dayId(programName: String, dayIndex: Int) = seedId("program-day", "$programName|$dayIndex")
    fun programExerciseId(programName: String, dayIndex: Int, exerciseName: String, occurrence: Int = 1) =
        seedId("program-exercise", "$programName|$dayIndex|$exerciseName" + if (occurrence > 1) "|$occurrence" else "")

    fun ruleKey(spec: RuleSpec): String = "${spec.type}|${spec.paramsJson()}"
    fun ruleId(spec: RuleSpec) = seedId("script-rule", ruleKey(spec))

    fun ruleLabel(spec: RuleSpec): String = when (spec) {
        is RuleSpec.Linear -> "Linear, +${spec.increment.text()}" +
            (spec.deloadPercent?.let { ", deload ${ScriptText.fmt(it)}%" } ?: "")
        is RuleSpec.DoubleProgression -> "Double progression, +${spec.increment.text()}"
        RuleSpec.RpeBased -> "RPE based"
        is RuleSpec.PercentOf1rm -> "${ScriptText.fmt(spec.percent)}% of max"
        is RuleSpec.Custom -> "Custom: ${spec.expression}"
        RuleSpec.NoProgression -> "No progression"
    }

    fun build(
        doc: ProgramDoc,
        libraryExercises: List<ExerciseEntity>,
        existing: ExistingProgramState,
        now: Long,
    ): ProgramImportBatch {
        val pid = programId(doc.name)
        val byName = HashMap<String, ExerciseEntity>()
        libraryExercises.forEach { byName.putIfAbsent(it.name.trim().lowercase(), it) }

        val newExercises = LinkedHashMap<String, ExerciseEntity>()
        val matched = LinkedHashSet<String>()
        val rules = LinkedHashMap<String, ProgressionRuleEntity>()
        val days = ArrayList<ProgramDayEntity>()
        val rows = ArrayList<ProgramExerciseEntity>()
        val dayLines = ArrayList<ImportedDayLine>()

        doc.days.forEachIndexed { dayIndex, day ->
            val did = dayId(doc.name, dayIndex)
            val oldDay = existing.days.firstOrNull { it.id == did }
            days += ProgramDayEntity(
                id = did, programId = pid, dayIndex = dayIndex, name = day.name.trim(),
                createdAt = oldDay?.createdAt ?: now, updatedAt = now,
            )
            val seen = HashMap<String, Int>()
            day.exercises.forEachIndexed { order, e ->
                val key = e.name.trim().lowercase()
                val inLibrary = byName[key]
                val exercise = inLibrary ?: newExercises[key] ?: ExerciseEntity(
                    id = seedId("exercise", e.name),
                    name = e.name.trim(),
                    primaryMuscle = DEFAULT_MUSCLE,
                    equipment = if (e.bodyweight) "bodyweight" else "",
                    isBodyweight = e.bodyweight,
                    defaultRestSec = doc.effectiveRest(e) ?: DEFAULT_REST_SEC,
                    createdAt = now,
                    updatedAt = now,
                ).also { newExercises[key] = it }
                if (inLibrary != null) matched += exercise.name

                val occurrence = (seen[key] ?: 0) + 1
                seen[key] = occurrence
                val peId = programExerciseId(doc.name, dayIndex, e.name, occurrence)
                val oldRow = existing.exercises.firstOrNull { it.id == peId }

                val spec = doc.effectiveProgress(e)
                val ruleId = if (spec == null || spec.type == null) {
                    null
                } else {
                    val k = ruleKey(spec)
                    rules.getOrPut(k) {
                        ProgressionRuleEntity(
                            id = ruleId(spec), name = ruleLabel(spec), type = spec.type!!,
                            params = spec.paramsJson(), createdAt = now, updatedAt = now,
                        )
                    }.id
                }
                rows += ProgramExerciseEntity(
                    id = peId,
                    programDayId = did,
                    exerciseId = exercise.id,
                    displayOrder = order,
                    targetSets = e.sets,
                    targetRepsMin = e.reps.entityMin,
                    targetRepsMax = e.reps.entityMax,
                    targetRpe = e.rpe,
                    progressionRuleId = ruleId,
                    restSec = doc.effectiveRest(e) ?: exercise.defaultRestSec,
                    startWeightKg = e.weight?.kg?.takeIf { it > 0.0 },
                    createdAt = oldRow?.createdAt ?: now,
                    updatedAt = now,
                )
            }
            dayLines += ImportedDayLine(day.name.trim(), day.exercises.size)
        }

        val newDayIds = days.map { it.id }.toSet()
        val newRowIds = rows.map { it.id }.toSet()
        val removedDays = existing.days.filter { it.id !in newDayIds }.map { it.copy(deletedAt = now, updatedAt = now) }
        val removedRows = existing.exercises.filter { it.id !in newRowIds }.map { it.copy(deletedAt = now, updatedAt = now) }

        val old = existing.program
        val makeActive = old?.isActive ?: !existing.anotherProgramActive
        val program = ProgramEntity(
            id = pid,
            name = doc.name.trim(),
            description = doc.notes.joinToString("\n"),
            isActive = makeActive,
            currentWeek = old?.currentWeek ?: 1,
            currentDayIndex = (old?.currentDayIndex ?: 0).coerceIn(0, (doc.days.size - 1).coerceAtLeast(0)),
            createdAt = old?.createdAt ?: now,
            updatedAt = now,
        )

        val notices = ArrayList<String>()
        if (doc.days.any { it.notes.isNotEmpty() }) notices += "Day notes are not stored by the app."
        if (doc.days.any { d -> d.exercises.any { it.notes.isNotEmpty() } }) notices += "Exercise notes are not stored by the app."
        if (doc.days.any { d -> d.exercises.any { it.weight != null && it.bodyweight } }) {
            notices += "For bodyweight exercises the weight is the added load and becomes the starting weight."
        }
        if (existing.anotherProgramActive && old == null) {
            notices += "Another program is active. This one is saved but not active; switch to it in the program builder."
        }

        val summary = ProgramImportSummary(
            programName = program.name,
            updatingExisting = old != null,
            days = dayLines,
            exerciseCount = rows.size,
            newExercises = newExercises.values.map { it.name },
            matchedExercises = matched.toList(),
            rules = rules.values.map { it.name },
            removedExercises = removedRows.size,
            removedDays = removedDays.size,
            notices = notices,
        )
        return ProgramImportBatch(
            program = program,
            days = days,
            newExercises = newExercises.values.toList(),
            rules = rules.values.toList(),
            programExercises = rows,
            removedDays = removedDays,
            removedExercises = removedRows,
            summary = summary,
        )
    }
}
