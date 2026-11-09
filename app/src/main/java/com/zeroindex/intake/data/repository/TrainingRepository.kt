package com.zeroindex.intake.data.repository

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.training.ExistingProgramState
import com.zeroindex.intake.data.training.ProgramExportRows
import com.zeroindex.intake.data.training.FreestyleDayBuilder
import com.zeroindex.intake.data.training.ProgramBlueprintBuilder
import com.zeroindex.intake.data.training.toEntity
import com.zeroindex.intake.domain.training.ProgressionLabels
import com.zeroindex.intake.domain.training.generator.ExerciseCatalog
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.training.ProgramExporter
import com.zeroindex.intake.data.training.ProgramImportBatch
import com.zeroindex.intake.data.training.ProgramImportBuilder
import com.zeroindex.intake.domain.script.Diagnostic
import com.zeroindex.intake.domain.script.IntakeScript
import com.zeroindex.intake.domain.script.ProgramScript
import com.zeroindex.intake.domain.script.ScriptDocument
import com.zeroindex.intake.data.db.dao.ProgramDao
import com.zeroindex.intake.data.db.dao.ProgressionRuleDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.db.projection.MuscleSetCount
import com.zeroindex.intake.data.db.projection.SetPoint
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.ExerciseTarget
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.Prescription
import com.zeroindex.intake.domain.training.ProgressionEvaluator
import com.zeroindex.intake.domain.training.ProgressionOutcome
import com.zeroindex.intake.domain.training.RuleParams
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.StartingWeight
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/** A prescription plus what it was derived from, for the active workout screen. */
data class ExercisePrescription(
    val programExercise: ProgramExerciseEntity?,
    val exercise: ExerciseEntity,
    val prescription: Prescription?,
    val ruleError: String?,
    /** SPEC §10: last session's numbers, shown inline for comparison. */
    val lastSession: List<SetLogEntity>,
)

sealed interface ProgramImportPreview {
    data class Ready(val batch: ProgramImportBatch, val warnings: List<Diagnostic>) : ProgramImportPreview
    data class ScriptErrors(val diagnostics: List<Diagnostic>) : ProgramImportPreview
    data class Invalid(val message: String) : ProgramImportPreview
}

@Singleton
class TrainingRepository @Inject constructor(
    private val db: IntakeDatabase,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val ruleDao: ProgressionRuleDao,
    private val workoutDao: WorkoutDao,
    private val settingsRepository: SettingsRepository,
    private val bodyRepository: BodyRepository,
    private val json: Json,
    private val clock: Clock,
) {
    // ----------------------------------------------------------- exercises

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    suspend fun exercises(): List<ExerciseEntity> = exerciseDao.all()

    suspend fun exerciseCount(): Int = exerciseDao.count()

    suspend fun saveExercises(exercises: List<ExerciseEntity>) = exerciseDao.upsertAll(exercises)

    suspend fun createExercise(
        name: String,
        primaryMuscle: String,
        equipment: String = "",
        isBodyweight: Boolean = false,
        defaultRestSec: Int = 120,
        secondaryMuscles: String = "",
    ): ExerciseEntity {
        val now = clock.nowMillis()
        val exercise = ExerciseEntity(
            id = newId(),
            name = name,
            primaryMuscle = primaryMuscle,
            equipment = equipment,
            isBodyweight = isBodyweight,
            defaultRestSec = defaultRestSec,
            secondaryMuscles = secondaryMuscles,
            createdAt = now,
            updatedAt = now,
        )
        exerciseDao.upsert(exercise)
        return exercise
    }

    /** Adds the catalog exercises this database does not have; never touches existing or hidden rows. */
    suspend fun ensureSeedExercises() {
        val have = exerciseDao.allIds().toSet()
        val missing = ExerciseCatalog.ALL.filter { it.id !in have }
        if (missing.isNotEmpty()) exerciseDao.upsertAll(missing.map { it.toEntity(SEED_TIMESTAMP) })
    }

    fun observeHiddenExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeHidden()
    suspend fun hiddenExerciseIds(): Set<String> = exerciseDao.hiddenIds().toSet()

    /** Includes hidden exercises: a program or past session may still point at one. */
    suspend fun exerciseAnyState(id: String): ExerciseEntity? = exerciseDao.findAnyById(id)

    suspend fun updateExercise(exercise: ExerciseEntity) =
        exerciseDao.upsert(exercise.copy(updatedAt = clock.nowMillis()))

    /** Soft delete: the row stays, so history and programs that use it keep working. */
    suspend fun hideExercise(id: String) = exerciseDao.softDelete(id, clock.nowMillis())

    suspend fun restoreExercise(id: String) = exerciseDao.restore(id, clock.nowMillis())

    /** Every set ever logged for an exercise, newest first, for the exercise detail. */
    fun observeExerciseSets(exerciseId: String): Flow<List<SetLogEntity>> =
        workoutDao.observeSetsForExercise(exerciseId)

    // ------------------------------------------------------------ programs

    fun observePrograms(): Flow<List<ProgramEntity>> = programDao.observeAll()
    fun observeActiveProgram(): Flow<ProgramEntity?> = programDao.observeActive()
    fun observeDays(programId: String): Flow<List<ProgramDayEntity>> = programDao.observeDays(programId)
    fun observeRules(): Flow<List<ProgressionRuleEntity>> = ruleDao.observeAll()

    suspend fun rules(): List<ProgressionRuleEntity> = ruleDao.all()

    suspend fun activeProgram(): ProgramEntity? = programDao.active()
    suspend fun days(programId: String): List<ProgramDayEntity> = programDao.daysOf(programId)
    suspend fun day(id: String): ProgramDayEntity? = programDao.findDay(id)
    suspend fun programExercises(dayId: String): List<ProgramExerciseEntity> =
        programDao.exercisesOf(dayId)

    suspend fun createProgram(name: String, description: String): ProgramEntity {
        val now = clock.nowMillis()
        val program = ProgramEntity(
            id = newId(),
            name = name,
            description = description,
            isActive = false,
            createdAt = now,
            updatedAt = now,
        )
        programDao.upsertProgram(program)
        return program
    }

    suspend fun activate(programId: String) {
        val now = clock.nowMillis()
        programDao.clearActive(now)
        programDao.markActive(programId, now)
    }

    suspend fun addDay(programId: String, dayIndex: Int, name: String): ProgramDayEntity {
        val now = clock.nowMillis()
        val day = ProgramDayEntity(
            id = newId(),
            programId = programId,
            dayIndex = dayIndex,
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        programDao.upsertDay(day)
        return day
    }

    suspend fun addProgramExercise(
        programDayId: String,
        exerciseId: String,
        displayOrder: Int,
        targetSets: Int,
        targetRepsMin: Int,
        targetRepsMax: Int,
        targetRpe: Double?,
        progressionRuleId: String?,
        restSec: Int,
    ) {
        val now = clock.nowMillis()
        programDao.upsertExercise(
            ProgramExerciseEntity(
                id = newId(),
                programDayId = programDayId,
                exerciseId = exerciseId,
                displayOrder = displayOrder,
                targetSets = targetSets,
                targetRepsMin = targetRepsMin,
                targetRepsMax = targetRepsMax,
                targetRpe = targetRpe,
                progressionRuleId = progressionRuleId,
                restSec = restSec,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveRule(
        name: String,
        type: ProgressionType,
        paramsJson: String,
    ): ProgressionRuleEntity {
        val now = clock.nowMillis()
        val rule = ProgressionRuleEntity(
            id = newId(),
            name = name,
            type = type,
            params = paramsJson,
            createdAt = now,
            updatedAt = now,
        )
        ruleDao.upsert(rule)
        return rule
    }

    suspend fun saveRules(rules: List<ProgressionRuleEntity>) = ruleDao.upsertAll(rules)

    // ------------------------------------------------ rules, editing, templates

    /** The default rules, keyed by type. Missing ones are added; existing ones are left alone. */
    suspend fun ensureDefaultRules(): Map<ProgressionType, ProgressionRuleEntity> {
        val existing = ruleDao.all()
        val now = SEED_TIMESTAMP
        fun seeded(type: ProgressionType, name: String, params: String) = ProgressionRuleEntity(
            id = seedId("progression-rule", type.name),
            name = name,
            type = type,
            params = params,
            createdAt = now,
            updatedAt = now,
        )
        val wanted = listOf(
            seeded(ProgressionType.LINEAR, "Linear, +2.5 kg", """{"incrementKg":2.5,"deloadPercent":10,"failuresBeforeDeload":2}"""),
            seeded(ProgressionType.DOUBLE_PROGRESSION, "Double progression", """{"incrementKg":2.5}"""),
            seeded(ProgressionType.RPE_BASED, "RPE 8 target", "{}"),
            seeded(ProgressionType.PERCENTAGE_1RM, "75% of estimated max", """{"percentageOf1rm":75}"""),
        )
        val ids = existing.map { it.id }.toSet()
        val missing = wanted.filter { it.id !in ids }
        if (missing.isNotEmpty()) ruleDao.upsertAll(missing)
        val all = existing + missing
        return ProgressionType.entries.mapNotNull { t -> all.firstOrNull { it.type == t }?.let { t to it } }.toMap()
    }

    /**
     * The rule for a plain-language choice. The default increment reuses the shared rule; any other
     * increment, percentage or formula finds or creates a matching rule row.
     */
    suspend fun ruleIdFor(
        type: ProgressionType,
        incrementKg: Double? = null,
        percentOfMax: Double? = null,
        expression: String? = null,
    ): String {
        val base = ensureDefaultRules()[type]
        if (type != ProgressionType.CUSTOM && base != null) {
            val defaultInc = ProgressionLabels.incrementOf(base.params)
            val defaultPct = ProgressionLabels.percentOf(base.params)
            val sameInc = incrementKg == null || incrementKg == defaultInc || type == ProgressionType.RPE_BASED
            val samePct = percentOfMax == null || percentOfMax == defaultPct
            if (sameInc && samePct) return base.id
        }
        val parts = mutableListOf<String>()
        incrementKg?.let { parts += "\"incrementKg\":$it" }
        if (type == ProgressionType.LINEAR) parts += "\"deloadPercent\":10,\"failuresBeforeDeload\":2"
        percentOfMax?.let { parts += "\"percentageOf1rm\":$it" }
        if (!expression.isNullOrBlank()) parts += "\"expression\":${kotlinx.serialization.json.JsonPrimitive(expression)}"
        val params = "{" + parts.joinToString(",") + "}"
        val existing = ruleDao.all().firstOrNull { it.type == type && it.params == params }
        if (existing != null) return existing.id
        val name = when (type) {
            ProgressionType.LINEAR -> "Linear, +${incrementKg ?: 2.5} kg"
            ProgressionType.DOUBLE_PROGRESSION -> "Double progression, +${incrementKg ?: 2.5} kg"
            ProgressionType.PERCENTAGE_1RM -> "${percentOfMax ?: 75.0}% of estimated max"
            ProgressionType.RPE_BASED -> "Go by effort"
            ProgressionType.CUSTOM -> "Custom formula"
        }
        return saveRule(name, type, params).id
    }

    suspend fun rule(id: String): ProgressionRuleEntity? = ruleDao.findById(id)

    suspend fun program(id: String): ProgramEntity? = programDao.findById(id)

    fun observeProgram(id: String): Flow<ProgramEntity?> = programDao.observeById(id)

    fun observeProgramExercises(dayId: String): Flow<List<ProgramExerciseEntity>> = programDao.observeExercises(dayId)

    suspend fun renameProgram(id: String, name: String, description: String) {
        val p = programDao.findById(id) ?: return
        programDao.upsertProgram(
            p.copy(name = name.trim().ifBlank { p.name }, description = description.trim(), updatedAt = clock.nowMillis()),
        )
    }

    /** Soft delete. The program's days, exercises and any sessions logged against them stay in the database. */
    suspend fun archiveProgram(id: String) {
        programDao.softDeleteProgram(id, clock.nowMillis())
    }

    suspend fun renameDay(dayId: String, name: String) {
        val d = programDao.findDay(dayId) ?: return
        programDao.upsertDay(d.copy(name = name.trim().ifBlank { d.name }, updatedAt = clock.nowMillis()))
    }

    suspend fun moveDay(dayId: String, delta: Int) {
        db.withTransaction {
            val d = programDao.findDay(dayId) ?: return@withTransaction
            val days = programDao.daysOf(d.programId).toMutableList()
            val from = days.indexOfFirst { it.id == dayId }
            val to = (from + delta).coerceIn(0, days.lastIndex)
            if (from < 0 || from == to) return@withTransaction
            days.add(to, days.removeAt(from))
            renumberDays(days)
        }
    }

    private suspend fun renumberDays(days: List<ProgramDayEntity>) {
        val now = clock.nowMillis()
        programDao.upsertDays(days.mapIndexed { i, d -> if (d.dayIndex == i) d else d.copy(dayIndex = i, updatedAt = now) })
    }

    suspend fun addDayTo(programId: String, name: String): ProgramDayEntity {
        val index = programDao.daysOf(programId).size
        return addDay(programId, index, name)
    }

    /** Copies a day and its exercises right after the original. */
    suspend fun duplicateDay(dayId: String): ProgramDayEntity? = db.withTransaction {
        val d = programDao.findDay(dayId) ?: return@withTransaction null
        val now = clock.nowMillis()
        val siblings = programDao.daysOf(d.programId)
        val copy = d.copy(id = newId(), name = nextCopyName(d.name, siblings.map { it.name }), createdAt = now, updatedAt = now)
        val days = siblings.toMutableList()
        days.add(days.indexOfFirst { it.id == dayId } + 1, copy)
        programDao.upsertDay(copy)
        renumberDays(days)
        programDao.upsertExercises(
            programDao.exercisesOf(dayId).map { it.copy(id = newId(), programDayId = copy.id, createdAt = now, updatedAt = now) },
        )
        copy
    }

    suspend fun deleteDay(dayId: String) {
        db.withTransaction {
            val d = programDao.findDay(dayId) ?: return@withTransaction
            val now = clock.nowMillis()
            programDao.exercisesOf(dayId).forEach { programDao.softDeleteExercise(it.id, now) }
            programDao.softDeleteDay(dayId, now)
            val remaining = programDao.daysOf(d.programId)
            renumberDays(remaining)
            val p = programDao.findById(d.programId)
            if (p != null && p.currentDayIndex >= remaining.size) programDao.updatePosition(p.id, p.currentWeek, 0, now)
        }
    }

    suspend fun updateProgramExercise(pe: ProgramExerciseEntity) {
        val repsMin = pe.targetRepsMin.coerceAtLeast(1)
        programDao.upsertExercise(
            pe.copy(
                targetSets = pe.targetSets.coerceIn(1, 20),
                targetRepsMin = repsMin,
                targetRepsMax = maxOf(repsMin, pe.targetRepsMax),
                restSec = pe.restSec.coerceIn(0, 900),
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    suspend fun deleteProgramExercise(id: String) {
        db.withTransaction {
            val pe = programDao.allProgramExercises().firstOrNull { it.id == id } ?: return@withTransaction
            programDao.softDeleteExercise(id, clock.nowMillis())
            renumberExercises(programDao.exercisesOf(pe.programDayId))
        }
    }

    private suspend fun renumberExercises(list: List<ProgramExerciseEntity>) {
        val now = clock.nowMillis()
        programDao.upsertExercises(list.mapIndexed { i, e -> if (e.displayOrder == i) e else e.copy(displayOrder = i, updatedAt = now) })
    }

    suspend fun moveProgramExercise(id: String, delta: Int) {
        db.withTransaction {
            val pe = programDao.allProgramExercises().firstOrNull { it.id == id } ?: return@withTransaction
            val list = programDao.exercisesOf(pe.programDayId).toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = (from + delta).coerceIn(0, list.lastIndex)
            if (from < 0 || from == to) return@withTransaction
            list.add(to, list.removeAt(from))
            renumberExercises(list)
        }
    }

    /**
     * Copies sets, reps, effort, rest and progression from one exercise to the others, either on
     * the same day or across the program. Returns how many exercises changed.
     */
    suspend fun copySettingsToOthers(sourceId: String, wholeProgram: Boolean): Int = db.withTransaction {
        val source = programDao.allProgramExercises().firstOrNull { it.id == sourceId } ?: return@withTransaction 0
        val day = programDao.findDay(source.programDayId) ?: return@withTransaction 0
        val dayIds = if (wholeProgram) programDao.daysOf(day.programId).map { it.id } else listOf(day.id)
        val now = clock.nowMillis()
        val targets = dayIds.flatMap { programDao.exercisesOf(it) }.filter { it.id != sourceId }
        programDao.upsertExercises(
            targets.map {
                it.copy(
                    targetSets = source.targetSets,
                    targetRepsMin = source.targetRepsMin,
                    targetRepsMax = source.targetRepsMax,
                    targetRpe = source.targetRpe,
                    restSec = source.restSec,
                    progressionRuleId = source.progressionRuleId,
                    updatedAt = now,
                )
            },
        )
        targets.size
    }

    /** Adds an exercise to the end of a day, starting from the settings of the day's last exercise. */
    suspend fun addExerciseToDay(dayId: String, exerciseId: String): ProgramExerciseEntity? {
        val exercise = exerciseDao.findAnyById(exerciseId) ?: return null
        val existing = programDao.exercisesOf(dayId)
        val rules = ensureDefaultRules()
        val now = clock.nowMillis()
        val last = existing.lastOrNull()
        val row = ProgramExerciseEntity(
            id = newId(),
            programDayId = dayId,
            exerciseId = exerciseId,
            displayOrder = existing.size,
            targetSets = last?.targetSets ?: 3,
            targetRepsMin = last?.targetRepsMin ?: 8,
            targetRepsMax = last?.targetRepsMax ?: 12,
            targetRpe = null,
            progressionRuleId = last?.progressionRuleId ?: rules[ProgressionType.DOUBLE_PROGRESSION]?.id,
            restSec = exercise.defaultRestSec,
            createdAt = now,
            updatedAt = now,
        )
        programDao.upsertExercise(row)
        return row
    }

    suspend fun duplicateProgram(programId: String): ProgramEntity? = db.withTransaction {
        val p = programDao.findById(programId) ?: return@withTransaction null
        val now = clock.nowMillis()
        val copy = p.copy(
            id = newId(),
            name = nextCopyName(p.name, programDao.all().map { it.name }),
            isActive = false,
            currentWeek = 1,
            currentDayIndex = 0,
            createdAt = now,
            updatedAt = now,
        )
        programDao.upsertProgram(copy)
        programDao.daysOf(programId).forEach { d ->
            val nd = d.copy(id = newId(), programId = copy.id, createdAt = now, updatedAt = now)
            programDao.upsertDay(nd)
            programDao.upsertExercises(
                programDao.exercisesOf(d.id).map { it.copy(id = newId(), programDayId = nd.id, createdAt = now, updatedAt = now) },
            )
        }
        copy
    }

    private fun nextCopyName(name: String, existing: List<String>): String {
        val base = name.removeSuffix(" (copy)")
        var candidate = "$base (copy)"
        var n = 2
        while (existing.any { it.equals(candidate, ignoreCase = true) }) {
            candidate = "$base (copy $n)"
            n++
        }
        return candidate
    }

    /**
     * Creates a normal, editable program from a generator result or a template. Exercises the
     * blueprint uses are added to the library if missing, and un-hidden if the person hid them.
     */
    suspend fun createProgramFromBlueprint(
        blueprint: GeneratedProgram,
        activate: Boolean,
        name: String = blueprint.name,
    ): ProgramEntity = db.withTransaction {
        ensureSeedExercises()
        val rules = ensureDefaultRules().mapValues { it.value.id }
        val now = clock.nowMillis()
        val hidden = exerciseDao.hiddenIds().toSet()
        blueprint.days.flatMap { d -> d.exercises.map { it.exerciseId } }.distinct()
            .filter { it in hidden }.forEach { exerciseDao.restore(it, now) }
        val rows = ProgramBlueprintBuilder.build(blueprint, rules, now, activate = activate, name = name)
        if (activate) programDao.clearActive(now)
        programDao.upsertProgram(rows.program)
        programDao.upsertDays(rows.days)
        programDao.upsertExercises(rows.exercises)
        rows.program
    }

    // ------------------------------------------------------------ freestyle

    /** A freestyle exercise starts from last time's numbers, or empty if it has no history. */
    suspend fun freestylePrescription(exerciseId: String, excludeSessionId: String?): ExercisePrescription? {
        val exercise = exerciseDao.findAnyById(exerciseId) ?: return null
        val last = workoutDao.lastWorkingSetsFor(exerciseId, excludeSessionId)
        val top = last.maxOfOrNull { it.weightKg } ?: 0.0
        val repsMin = last.minOfOrNull { it.reps }?.coerceAtLeast(1) ?: 8
        val repsMax = maxOf(repsMin, last.maxOfOrNull { it.reps } ?: 12)
        return ExercisePrescription(
            programExercise = null,
            exercise = exercise,
            prescription = Prescription(
                weightKg = top,
                sets = maxOf(3, last.size),
                repsMin = repsMin,
                repsMax = repsMax,
                targetRpe = null,
                reason = if (last.isEmpty()) {
                    "Freestyle: pick a weight, log what you do, and it is remembered for next time."
                } else {
                    "Freestyle: last time's numbers. Change them as you like."
                },
            ),
            ruleError = null,
            lastSession = last,
        )
    }

    /** The exercises already logged in a session, in the order they were first lifted. */
    private suspend fun freestyleExerciseIds(sessionId: String): List<String> =
        workoutDao.setsOf(sessionId).sortedBy { it.completedAt }.map { it.exerciseId }.distinct()

    /**
     * Saves what a freestyle session contained as a new day of [programId] (default: the active
     * program; if there is none, a new program is created and activated). Returns the new day,
     * or null when the session had no working sets.
     */
    suspend fun saveSessionAsProgramDay(sessionId: String, dayName: String, programId: String? = null): ProgramDayEntity? =
        db.withTransaction {
            val sets = workoutDao.setsOf(sessionId)
            if (sets.none { !it.isWarmup }) return@withTransaction null
            val exercises = sets.map { it.exerciseId }.distinct().mapNotNull { exerciseDao.findAnyById(it) }.associateBy { it.id }
            val day = FreestyleDayBuilder.toDay(dayName, sets, exercises)
            if (day.exercises.isEmpty()) return@withTransaction null
            val now = clock.nowMillis()
            val existing = programId?.let { programDao.findById(it) } ?: programDao.active()
            val target = existing ?: ProgramEntity(
                id = newId(), name = "My program", description = "", isActive = true, createdAt = now, updatedAt = now,
            ).also {
                programDao.clearActive(now)
                programDao.upsertProgram(it)
            }
            val rules = ensureDefaultRules().mapValues { it.value.id }
            val index = programDao.daysOf(target.id).size
            val rows = ProgramBlueprintBuilder.dayRows(target.id, index, day, rules, now)
            programDao.upsertDay(rows.first)
            programDao.upsertExercises(rows.second)
            rows.first
        }

    // ------------------------------------------------------ script import / export

    /** Parses a program script and works out what importing it would do. Writes nothing. */
    suspend fun previewProgramImport(text: String): ProgramImportPreview {
        val result = IntakeScript.parse(text)
        if (result.errors.isNotEmpty()) return ProgramImportPreview.ScriptErrors(result.diagnostics)
        return when (val doc = result.document) {
            is ScriptDocument.Program -> {
                val pid = ProgramImportBuilder.programId(doc.doc.name)
                val old = programDao.findById(pid)
                val days = if (old != null) programDao.daysOf(pid) else emptyList()
                val exercises = days.flatMap { programDao.exercisesOf(it.id) }
                val active = programDao.active()
                val batch = ProgramImportBuilder.build(
                    doc.doc, exerciseDao.all(),
                    ExistingProgramState(old, days, exercises, anotherProgramActive = active != null && active.id != pid),
                    clock.nowMillis(),
                )
                ProgramImportPreview.Ready(batch, result.warnings)
            }
            is ScriptDocument.MealPlan ->
                ProgramImportPreview.Invalid("This is a meal plan. Import it from the Plan tab instead.")
            null -> ProgramImportPreview.Invalid("This file could not be read.")
        }
    }

    /** One transaction. Ids are deterministic, so importing the same file again updates in place. */
    suspend fun applyProgramImport(batch: ProgramImportBatch) {
        db.withTransaction {
            if (batch.program.isActive) programDao.clearActive(batch.program.updatedAt)
            exerciseDao.upsertAll(batch.newExercises)
            ruleDao.upsertAll(batch.rules)
            programDao.upsertProgram(batch.program)
            programDao.upsertDays(batch.days)
            programDao.upsertDays(batch.removedDays)
            programDao.upsertExercises(batch.programExercises)
            programDao.upsertExercises(batch.removedExercises)
        }
    }

    /** The program as script text, or null when it no longer exists. */
    suspend fun exportProgramScript(programId: String): Pair<String, String>? {
        val program = programDao.findById(programId) ?: return null
        val days = programDao.daysOf(programId)
        val byDay = days.associate { it.id to programDao.exercisesOf(it.id) }
        val doc = ProgramExporter.toDoc(
            ProgramExportRows(
                program = program,
                days = days,
                exercisesByDay = byDay,
                exercises = exerciseDao.all().associateBy { it.id },
                rules = ruleDao.all().associateBy { it.id },
            ),
        )
        return ProgramScript.print(doc) to ProgramExporter.fileName(program.name)
    }

    // ------------------------------------------------------------ sessions

    fun observeSessions(): Flow<List<WorkoutSessionEntity>> = workoutDao.observeSessions()
    fun observeInProgress(): Flow<WorkoutSessionEntity?> = workoutDao.observeInProgress()
    fun observeSets(sessionId: String): Flow<List<SetLogEntity>> = workoutDao.observeSets(sessionId)
    fun observeSetPoints(exerciseId: String): Flow<List<SetPoint>> =
        workoutDao.observeSetPoints(exerciseId)

    /** SPEC §10: weekly set counts per muscle, to flag anything under ten. */
    fun observeWeeklySetsPerMuscle(): Flow<List<MuscleSetCount>> {
        val today = clock.today()
        val from = today.minusDays(6)
        return workoutDao.observeWeeklySetsPerMuscle(
            clock.rangeFor(from).startInclusive,
            clock.rangeFor(today).endExclusive,
        )
    }

    suspend fun session(id: String): WorkoutSessionEntity? = workoutDao.findSession(id)
    suspend fun sets(sessionId: String): List<SetLogEntity> = workoutDao.setsOf(sessionId)

    /** Captures bodyweight at session start, for bodyweight lift maths (SPEC §4). */
    suspend fun startSession(programDayId: String?): WorkoutSessionEntity {
        workoutDao.inProgress()?.let { return it }
        val now = clock.nowMillis()
        val session = WorkoutSessionEntity(
            id = newId(),
            programDayId = programDayId,
            startedAt = now,
            bodyweightKg = bodyRepository.latest()?.weightKg,
            createdAt = now,
            updatedAt = now,
        )
        workoutDao.upsertSession(session)
        return session
    }

    suspend fun finishSession(sessionId: String) {
        workoutDao.finishSession(sessionId, clock.nowMillis())
        // Advance the program's position so the next session offers the next day.
        val session = workoutDao.findSession(sessionId) ?: return
        val dayId = session.programDayId ?: return
        val day = programDao.findDay(dayId) ?: return
        val program = programDao.findById(day.programId) ?: return
        val days = programDao.daysOf(program.id)
        if (days.isEmpty()) return
        val nextIndex = (days.indexOfFirst { it.id == dayId } + 1) % days.size
        val nextWeek = if (nextIndex == 0) program.currentWeek + 1 else program.currentWeek
        programDao.updatePosition(program.id, nextWeek, nextIndex, clock.nowMillis())
    }

    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        setIndex: Int,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        isWarmup: Boolean,
        isFailure: Boolean,
        restTakenSec: Int?,
    ): SetLogEntity {
        val now = clock.nowMillis()
        val set = SetLogEntity(
            id = newId(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            setIndex = setIndex,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            isWarmup = isWarmup,
            isFailure = isFailure,
            restTakenSec = restTakenSec,
            completedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        workoutDao.upsertSet(set)
        return set
    }

    /**
     * A set logged on the watch (amendment §2). The watch's id becomes the
     * set's id, so the Data Layer delivering the same item twice writes the
     * same row twice rather than two rows. Ignored if the session is unknown.
     */
    suspend fun logSetFromWatch(
        id: String,
        sessionId: String,
        exerciseId: String,
        setIndex: Int,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        completedAt: Long,
        restTakenSec: Int?,
    ): Boolean {
        if (workoutDao.findSession(sessionId) == null) return false
        val now = clock.nowMillis()
        workoutDao.upsertSet(
            SetLogEntity(
                id = id,
                sessionId = sessionId,
                exerciseId = exerciseId,
                setIndex = setIndex,
                weightKg = weightKg,
                reps = reps,
                rpe = rpe,
                restTakenSec = restTakenSec,
                completedAt = completedAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return true
    }

    suspend fun deleteSet(id: String) = workoutDao.softDeleteSet(id, clock.nowMillis())

    suspend fun saveSessions(sessions: List<WorkoutSessionEntity>) =
        workoutDao.upsertSessions(sessions)

    suspend fun saveSets(sets: List<SetLogEntity>) = workoutDao.upsertSets(sets)

    // ------------------------------------------------------- prescriptions

    /**
     * Builds the prescription for every exercise on a program day, by running
     * its rule against the last session that trained it.
     */
    suspend fun prescriptionsFor(
        programDayId: String?,
        excludeSessionId: String?,
    ): List<ExercisePrescription> {
        val base = basePrescriptionsFor(programDayId, excludeSessionId)
        // The coach's deload week or easier day, when the person applied one (docs/COACH.md).
        val coach = settingsRepository.settings.first()
        val now = clock.nowMillis()
        val increment = coach.platesKgList.minOrNull()?.times(2) ?: 2.5
        return base.map { p ->
            val adjusted = p.prescription?.let {
                com.zeroindex.intake.domain.coach.CoachAdjustments.apply(it, coach.coachState, now, increment)
            }
            if (adjusted === p.prescription) p else p.copy(prescription = adjusted)
        }
    }

    private suspend fun basePrescriptionsFor(
        programDayId: String?,
        excludeSessionId: String?,
    ): List<ExercisePrescription> {
        val settings = settingsRepository.settings.first()
        val increment = settings.platesKgList.minOrNull()?.times(2) ?: 2.5
        val program = activeProgram()

        if (programDayId == null) {
            // Freestyle session: no program, so no rules. The list is whatever has been logged so
            // far (the phone adds exercises as you go; the watch sees them once a set exists).
            if (excludeSessionId == null) return emptyList()
            return freestyleExerciseIds(excludeSessionId).mapNotNull { freestylePrescription(it, excludeSessionId) }
        }

        val programExercises = programDao.exercisesOf(programDayId)
        return programExercises.mapNotNull { pe ->
            val exercise = exerciseDao.findAnyById(pe.exerciseId) ?: return@mapNotNull null
            val lastSets = workoutDao.lastWorkingSetsFor(pe.exerciseId, excludeSessionId)
            val rule = pe.progressionRuleId?.let { ruleDao.findById(it) }

            val target = ExerciseTarget(
                sets = pe.targetSets,
                repsMin = pe.targetRepsMin,
                repsMax = pe.targetRepsMax,
                targetRpe = pe.targetRpe,
                incrementKg = increment,
            )
            val results = lastSets.map {
                SetResult(it.weightKg, it.reps, it.rpe, it.isFailure)
            }

            // No history yet and the program names a starting weight: that is the first prescription.
            if (lastSets.isEmpty()) {
                StartingWeight.prescription(pe.startWeightKg, target)?.let { start ->
                    return@mapNotNull ExercisePrescription(
                        programExercise = pe,
                        exercise = exercise,
                        prescription = start,
                        ruleError = null,
                        lastSession = lastSets,
                    )
                }
            }

            if (rule == null) {
                // No rule attached: repeat last time's top set, and say so.
                val top = lastSets.maxOfOrNull { it.weightKg } ?: 0.0
                return@mapNotNull ExercisePrescription(
                    programExercise = pe,
                    exercise = exercise,
                    prescription = Prescription(
                        weightKg = top,
                        sets = pe.targetSets,
                        repsMin = pe.targetRepsMin,
                        repsMax = pe.targetRepsMax,
                        targetRpe = pe.targetRpe,
                        reason = if (top > 0) {
                            "Same as last time. Attach a progression rule and this will " +
                                "move on its own."
                        } else {
                            "No rule and no history yet - pick a working weight."
                        },
                    ),
                    ruleError = null,
                    lastSession = lastSets,
                )
            }

            val params = parseParams(rule, pe, program?.currentWeek ?: 1, results, increment)
            val estimated1rm = OneRepMax.best(results)

            when (
                val outcome = ProgressionEvaluator.evaluate(
                    type = rule.type,
                    previousSets = results,
                    target = target,
                    params = params,
                    estimated1rm = estimated1rm,
                )
            ) {
                is ProgressionOutcome.Next -> ExercisePrescription(
                    programExercise = pe,
                    exercise = exercise,
                    prescription = outcome.prescription,
                    ruleError = null,
                    lastSession = lastSets,
                )
                is ProgressionOutcome.Invalid -> ExercisePrescription(
                    programExercise = pe,
                    exercise = exercise,
                    prescription = null,
                    // SPEC §10: a bad rule surfaces as a validation error.
                    ruleError = outcome.error.message,
                    lastSession = lastSets,
                )
            }
        }
    }

    /**
     * Rule params come from a JSON blob so a new rule type needs no migration
     * (SPEC §4). A malformed blob falls back to defaults rather than throwing.
     */
    private fun parseParams(
        rule: ProgressionRuleEntity,
        programExercise: ProgramExerciseEntity,
        week: Int,
        previous: List<SetResult>,
        increment: Double,
    ): RuleParams {
        val defaults = RuleParams(
            incrementKg = increment,
            week = week,
            failureCount = consecutiveFailures(previous, programExercise.targetRepsMin),
        )
        val obj = runCatching {
            json.parseToJsonElement(rule.params) as? kotlinx.serialization.json.JsonObject
        }.getOrNull() ?: return defaults

        fun num(key: String): Double? =
            runCatching { obj[key]?.jsonPrimitive?.content?.toDoubleOrNull() }.getOrNull()
        fun str(key: String): String? =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()

        return defaults.copy(
            incrementKg = num("incrementKg") ?: defaults.incrementKg,
            deloadPercent = num("deloadPercent") ?: defaults.deloadPercent,
            failuresBeforeDeload = num("failuresBeforeDeload")?.toInt()
                ?: defaults.failuresBeforeDeload,
            percentageOf1rm = num("percentageOf1rm") ?: defaults.percentageOf1rm,
            expression = str("expression") ?: defaults.expression,
        )
    }

    /** One session short of target counts as one failure; the engine deloads on N. */
    private fun consecutiveFailures(previous: List<SetResult>, targetReps: Int): Int {
        if (previous.isEmpty()) return 0
        return if (previous.any { it.reps < targetReps }) 1 else 0
    }
}
