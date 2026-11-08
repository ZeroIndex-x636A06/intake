package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.ExerciseDao
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

@Singleton
class TrainingRepository @Inject constructor(
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
    ): ExerciseEntity {
        val now = clock.nowMillis()
        val exercise = ExerciseEntity(
            id = newId(),
            name = name,
            primaryMuscle = primaryMuscle,
            equipment = equipment,
            isBodyweight = isBodyweight,
            defaultRestSec = defaultRestSec,
            createdAt = now,
            updatedAt = now,
        )
        exerciseDao.upsert(exercise)
        return exercise
    }

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
        val settings = settingsRepository.settings.first()
        val increment = settings.platesKgList.minOrNull()?.times(2) ?: 2.5
        val program = activeProgram()

        if (programDayId == null) {
            // Freestyle session: no program, so no prescriptions - just the
            // exercises the user has, with their last numbers for reference.
            return emptyList()
        }

        val programExercises = programDao.exercisesOf(programDayId)
        return programExercises.mapNotNull { pe ->
            val exercise = exerciseDao.findById(pe.exerciseId) ?: return@mapNotNull null
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
