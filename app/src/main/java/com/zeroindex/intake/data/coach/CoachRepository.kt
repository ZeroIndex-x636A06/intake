package com.zeroindex.intake.data.coach

import com.zeroindex.intake.data.db.dao.CoachDao
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.ProgramDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.data.foodref.FoodRefRepository
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.coach.CheckIn
import com.zeroindex.intake.domain.coach.CheckInRecord
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.CoachAdjustments
import com.zeroindex.intake.domain.coach.CoachConfig
import com.zeroindex.intake.domain.coach.CoachEngine
import com.zeroindex.intake.domain.coach.CoachSchedule
import com.zeroindex.intake.domain.coach.DailyFocus
import com.zeroindex.intake.domain.coach.DailyFocusPicker
import com.zeroindex.intake.domain.coach.ExerciseHistoryIn
import com.zeroindex.intake.domain.coach.ExerciseSession
import com.zeroindex.intake.domain.coach.FocusInputs
import com.zeroindex.intake.domain.coach.FoodDayIn
import com.zeroindex.intake.domain.coach.HealthDayIn
import com.zeroindex.intake.domain.coach.ProteinIdeas
import com.zeroindex.intake.domain.coach.SetRpe
import com.zeroindex.intake.domain.coach.Suggestion
import com.zeroindex.intake.domain.coach.SuggestionHistory
import com.zeroindex.intake.domain.coach.SuggestionStatus
import com.zeroindex.intake.domain.coach.SwapCandidate
import com.zeroindex.intake.domain.coach.WaterDayIn
import com.zeroindex.intake.domain.coach.WeekInputs
import com.zeroindex.intake.domain.coach.WeekSummary
import com.zeroindex.intake.domain.goals.GoalBuilder
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.goals.PhasePlanner
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.generator.Muscles
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** What "Apply" did, in one line for the snackbar. */
data class ApplyResult(val ok: Boolean, val message: String)

/**
 * Glue between the pure coach rules ([CoachEngine]) and the app's data. It reads the tables, asks the
 * rules for suggestions, keeps the history, and runs a suggestion's action only when the person taps
 * Apply. No network and no AI in here; the optional reword lives in [CoachReworder].
 */
@Singleton
class CoachRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val coachDao: CoachDao,
    private val foodLogDao: FoodLogDao,
    private val foodDao: FoodDao,
    private val healthDao: HealthSummaryDao,
    private val workoutDao: WorkoutDao,
    private val programDao: ProgramDao,
    private val exerciseDao: ExerciseDao,
    private val body: BodyRepository,
    private val targets: TargetRepository,
    private val hydration: HydrationRepository,
    private val training: TrainingRepository,
    private val profileRepository: ProfileRepository,
    private val allergens: AllergenRepository,
    private val foodRef: FoodRefRepository,
    private val json: Json,
    private val clock: Clock,
) {
    private val config = CoachConfig()

    // ------------------------------------------------------------------------------ observing

    fun observeHistory(): Flow<List<CoachSuggestionEntity>> = coachDao.observeAll()

    /** Pending suggestions from a check-in in the last two weeks. */
    fun observePendingCount(): Flow<Int> = coachDao.observePendingCount(clock.today().toEpochDay() - PENDING_DAYS)

    fun observeState() = settings.settings.map { it.coachState }

    fun actionOf(row: CoachSuggestionEntity): CoachAction = json.decodeFromString(CoachAction.serializer(), row.actionJson)

    // ------------------------------------------------------------------------------ the weekly check-in

    /** The weekday the check-in runs on (ISO 1..7). */
    suspend fun weekday(): Int {
        val s = settings.settings.first()
        val started = s.baseline.startedAtMillis?.let { localDateOf(it, clock.zone()).dayOfWeek.value }
        return CoachSchedule.weekday(s.coachWeekday, started)
    }

    suspend fun isDue(): Boolean {
        val state = settings.settings.first().coachState
        return CoachSchedule.isDue(clock.today().toEpochDay(), weekday(), state.lastCheckInDay) && hasAnyData()
    }

    /** True once there is some food or weigh-in from the last two weeks to look at. */
    private suspend fun hasAnyData(): Boolean {
        val today = clock.today()
        val from = clock.rangeFor(today.minusDays(13)).startInclusive
        val to = clock.rangeFor(today).endExclusive
        return foodLogDao.inRange(from, to).isNotEmpty() || body.weightPointsSince(today.minusDays(13)).isNotEmpty()
    }

    /** Makes this week's check-in if it is due. Returns it when one was just made. */
    suspend fun generateIfDue(): CheckIn? = if (isDue()) generate() else null

    /** Reviews the week now and stores the suggestions as pending. A second call on the same day keeps the first. */
    suspend fun generate(): CheckIn {
        val inputs = buildInputs()
        val review = CoachEngine.review(inputs, config)
        val today = inputs.today
        val existing = coachDao.forCheckIn(today)
        if (existing.isEmpty()) {
            val now = clock.nowMillis()
            val autoOn = settings.settings.first().autoCalculateTargets
            val rows = review.suggestions.map { s ->
                CoachSuggestionEntity(
                    checkInDay = today,
                    suggestionKey = s.key,
                    kind = s.kind,
                    title = s.title,
                    why = s.why,
                    details = withNightlyNote(s, autoOn),
                    actionJson = json.encodeToString(CoachAction.serializer(), s.action),
                    priority = s.priority,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            if (rows.isNotEmpty()) coachDao.upsertAll(rows)
        }
        settings.updateCoachState { st ->
            val record = CheckInRecord(today, review.suggestions.size, review.headline)
            st.copy(
                lastCheckInDay = today,
                checkIns = (listOf(record) + st.checkIns.filter { it.day != today }).take(CHECK_IN_LIMIT),
            )
        }
        return review
    }

    private fun withNightlyNote(s: Suggestion, autoOn: Boolean): String? {
        if (s.action !is CoachAction.SetCalories || !autoOn) return s.details
        val note = "Applying this turns off Intake's nightly automatic target update, so the two do not undo each other."
        return listOfNotNull(s.details, note).joinToString(" ")
    }

    /** The numbers above the suggestions, live. */
    suspend fun currentSummary(): WeekSummary = CoachEngine.review(buildInputs(), config).summary

    // ------------------------------------------------------------------------------ apply / dismiss

    suspend fun dismiss(id: String) {
        coachDao.decide(id, SuggestionStatus.DISMISSED.name, clock.nowMillis())
    }

    suspend fun apply(id: String): ApplyResult {
        val row = coachDao.findById(id) ?: return ApplyResult(false, "That suggestion is gone.")
        if (row.status != SuggestionStatus.PENDING) return ApplyResult(false, "Already decided.")
        val result = runCatching { run(actionOf(row)) }
            .getOrElse { ApplyResult(false, "Could not apply that: ${it.message ?: "unknown error"}. Nothing was changed.") }
        if (result.ok) coachDao.decide(id, SuggestionStatus.APPLIED.name, clock.nowMillis())
        return result
    }

    private suspend fun run(action: CoachAction): ApplyResult = when (action) {
        is CoachAction.Acknowledge -> ApplyResult(true, "Noted.")
        is CoachAction.SetCalories -> setCalories(action)
        is CoachAction.Deload -> {
            val now = clock.nowMillis()
            settings.updateCoachState {
                it.copy(
                    deloadUntilMillis = now + action.days * DAY_MILLIS,
                    deloadPercent = action.percent,
                    lastDeloadDay = clock.today().toEpochDay(),
                )
            }
            ApplyResult(true, "Deload week on: ${action.percent}% lighter for ${action.days} days.")
        }
        is CoachAction.EasierDay -> {
            val now = clock.nowMillis()
            settings.updateCoachState {
                it.copy(easyUntilMillis = now + action.days * DAY_MILLIS, easySetsLess = action.setsLess)
            }
            ApplyResult(true, "Your next session has ${action.setsLess} set fewer per exercise.")
        }
        is CoachAction.SwapExercise -> swap(action)
        is CoachAction.MuscleSets -> muscleSets(action)
    }

    private suspend fun setCalories(a: CoachAction.SetCalories): ApplyResult {
        val current = targets.latestEntity() ?: return ApplyResult(false, "You have no calorie target yet.")
        val weightKg = body.latest()?.weightKg
        val (p, c, f) = CoachEngine.macrosFor(current.proteinG, current.carbsG, current.fatG, current.kcal, a.toKcal, weightKg)
        targets.setTarget(MacroTarget(kcal = a.toKcal, proteinG = p, carbsG = c, fatG = f), isAutoCalculated = false)
        settings.setAutoCalculateTargets(false)
        return ApplyResult(true, "Calories are now ${a.toKcal} kcal (was ${current.kcal}). Protein $p g, carbs $c g, fat $f g.")
    }

    private suspend fun activeProgramExercises() = programDao.active()?.let { program ->
        programDao.daysOf(program.id).flatMap { programDao.exercisesOf(it.id) }
    }.orEmpty()

    private suspend fun swap(a: CoachAction.SwapExercise): ApplyResult {
        val rows = activeProgramExercises().filter { it.exerciseId == a.fromExerciseId }
        if (rows.isEmpty()) return ApplyResult(false, "${a.fromName} is no longer in your active program.")
        rows.forEach { training.updateProgramExercise(it.copy(exerciseId = a.toExerciseId, startWeightKg = null)) }
        return ApplyResult(true, "Swapped ${a.fromName} for ${a.toName} in ${rows.size} place${if (rows.size == 1) "" else "s"}.")
    }

    private suspend fun muscleSets(a: CoachAction.MuscleSets): ApplyResult {
        val byId = exerciseDao.all().associateBy { it.id }
        val rows = activeProgramExercises().filter { byId[it.exerciseId]?.primaryMuscle == a.muscle }
        if (rows.isEmpty()) return ApplyResult(false, "No ${a.muscle} exercises in your active program.")
        val newSets = CoachAdjustments.distributeSets(rows.map { it.id to it.targetSets }, a.deltaSets)
        var changed = 0
        rows.forEach { pe ->
            val n = newSets[pe.id] ?: pe.targetSets
            if (n != pe.targetSets) {
                training.updateProgramExercise(pe.copy(targetSets = n))
                changed += n - pe.targetSets
            }
        }
        return if (changed == 0) ApplyResult(false, "Those ${a.muscle} exercises are already at their limit.")
        else ApplyResult(true, "${a.muscle.replaceFirstChar { it.uppercase() }}: ${if (changed > 0) "+" else ""}$changed sets a week.")
    }

    // ------------------------------------------------------------------------------ inputs

    suspend fun buildInputs(): WeekInputs {
        val today = clock.today()
        val todayIdx = today.toEpochDay()
        val zone = clock.zone()
        val state = settings.settings.first()
        val profile = state.profile
        val target = targets.latestEntity()

        // Safety rails come from the goal builder, so the coach and the plan screen agree.
        val plan = profileRepository.goalInputs()?.let { runCatching { GoalBuilder.build(it) }.getOrNull() }
        val floor = when {
            profile.isAdvanced && profile.allowBelowFloor -> null
            plan != null -> (plan.bmr.kcal * PhasePlanner.FLOOR_OF_BMR).roundToInt()
            else -> FALLBACK_FLOOR_KCAL
        }
        val age = profile.birthYear?.let { today.year - it }

        val from14 = today.minusDays(13)
        val food = foodLogDao.inRange(clock.rangeFor(from14).startInclusive, clock.rangeFor(today).endExclusive)
            .groupBy { localDateOf(it.loggedAt, zone).toEpochDay() }
            .map { (day, logs) -> FoodDayIn(day, logs.sumOf { it.kcal }, logs.sumOf { it.protein }, logs.size) }

        val weighIns: List<WeightPoint> = body.weightPointsSince(today.minusDays(60))

        val water = (0L..6L).mapNotNull { back ->
            val d = today.minusDays(back)
            runCatching { hydration.observeDay(d).first() }.getOrNull()?.let {
                WaterDayIn(d.toEpochDay(), it.consumedMl, it.target.totalMl)
            }
        }

        val health = healthDao.inRange(clock.rangeFor(today.minusDays(34)).startInclusive, clock.rangeFor(today).endExclusive)
            .map { HealthDayIn(localDateOf(it.dayStartMillis, zone).toEpochDay(), it.sleepMinutes?.toInt(), it.restingHeartRate, null) }

        // Training.
        val exercises = exerciseDao.all()
        val byId = exercises.associateBy { it.id }
        val programRows = activeProgramExercises()
        val program = programDao.active()
        val inProgram = programRows.map { it.exerciseId }.toSet()
        val sessions = workoutDao.allSessions().filter { it.finishedAt != null }
        val sessionDay = sessions.associate { it.id to localDateOf(it.startedAt, zone).toEpochDay() }
        val sets42 = workoutDao.workingSetsInRange(
            clock.rangeFor(today.minusDays(config.stallWindowDays.toLong())).startInclusive,
            clock.rangeFor(today).endExclusive,
        ).filter { it.sessionId in sessionDay }

        val histories = sets42.groupBy { it.exerciseId }.mapNotNull { (exerciseId, sets) ->
            val ex = byId[exerciseId] ?: return@mapNotNull null
            val perSession = sets.groupBy { it.sessionId }.map { (sid, ss) -> sessionSummary(sessionDay.getValue(sid), ss.map { SetResult(it.weightKg, it.reps, it.rpe, it.isFailure) }) }
            ExerciseHistoryIn(
                exerciseId = exerciseId,
                name = ex.name,
                muscle = ex.primaryMuscle,
                equipment = ex.equipment,
                inActiveProgram = exerciseId in inProgram,
                sessions = perSession.sortedBy { it.dayIndex },
            )
        }
        val trained = histories.associate { it.exerciseId to it.sessions.size }
        val swapPool = exercises.map { SwapCandidate(it.id, it.name, it.primaryMuscle, it.equipment, trained[it.id] ?: 0) }

        val cutoff = todayIdx - 13
        val recentSets = sets42.filter { localDateOf(it.completedAt, zone).toEpochDay() >= cutoff }
        val setsByDay = recentSets.mapNotNull { s ->
            val muscle = byId[s.exerciseId]?.primaryMuscle?.takeIf { it in Muscles.PROGRAMMED } ?: return@mapNotNull null
            localDateOf(s.completedAt, zone).toEpochDay() to muscle
        }
        val rpes = recentSets.map { SetRpe(localDateOf(it.completedAt, zone).toEpochDay(), it.rpe) }
        val sessionDays = sessionDay.values.filter { it >= cutoff }

        // Decisions already made.
        val history = coachDao.since(todayIdx - 30).filter { it.status != SuggestionStatus.PENDING }.map {
            SuggestionHistory.Decided(it.suggestionKey, it.status, localDateOf(it.decidedAt ?: it.updatedAt, zone).toEpochDay())
        }
        val easyApplied = history.any { it.key == "recovery:easy" && it.status == SuggestionStatus.APPLIED && todayIdx - it.decidedDay <= 7 }

        val avgProtein = food.filter { it.dayIndex >= todayIdx - 6 && it.kcal >= config.loggedDayMinKcal }.map { it.proteinG }
        val needIdeas = target != null && avgProtein.size >= config.proteinMinLoggedDays &&
            avgProtein.average() < config.proteinShare * target.proteinG

        return WeekInputs(
            today = todayIdx,
            phase = profile.phase,
            pace = profile.pace,
            contestWeeklyLossPct = profile.contestWeeklyLossPct,
            reverseStepKcal = profile.reverseStepKcal,
            floorKcal = floor,
            canReduceCalories = age == null || age >= PhasePlanner.ADULT_AGE,
            maintenanceKcal = plan?.tdee?.roundToInt(),
            currentKcal = target?.kcal ?: 0,
            targetProteinG = target?.proteinG ?: 0,
            currentCarbsG = target?.carbsG ?: 0,
            currentFatG = target?.fatG ?: 0,
            weighIns = weighIns,
            foodDays = food,
            waterDays = water,
            health = health,
            experience = profile.experience,
            programId = program?.id,
            programName = program?.name,
            programMuscles = programRows.mapNotNull { byId[it.exerciseId]?.primaryMuscle }.toSet(),
            exercises = histories,
            swapPool = swapPool,
            setsByDay = setsByDay,
            sessionDays = sessionDays,
            setRpes = rpes,
            lastDeloadDay = state.coachState.lastDeloadDay,
            easyDayApplied = easyApplied,
            dietStartDay = dietStartDay(state.planHistory, profile.phase, zone),
            dietBreakEveryWeeks = profile.dietBreakEveryWeeks.takeIf { profile.isAdvanced },
            suppressedKeys = SuggestionHistory.suppressedKeys(history, todayIdx, config),
            calorieCooldown = SuggestionHistory.calorieCooldown(history, todayIdx, config),
            proteinIdeas = if (needIdeas) proteinIdeas() else emptyList(),
        )
    }

    private fun sessionSummary(day: Long, sets: List<SetResult>): ExerciseSession {
        val loaded = sets.filter { it.weightKg > 0 && it.reps >= 1 }
        return if (loaded.isNotEmpty()) {
            val top = loaded.maxByOrNull { OneRepMax.epley(it.weightKg, it.reps) }!!
            ExerciseSession(day, OneRepMax.epley(top.weightKg, top.reps), loaded.sumOf { it.weightKg * it.reps }, top.weightKg, top.reps)
        } else {
            // Bodyweight only: reps are the progress.
            val best = sets.maxOfOrNull { it.reps } ?: 0
            ExerciseSession(day, best.toDouble(), sets.sumOf { it.reps }.toDouble(), 0.0, best)
        }
    }

    /** Start of the current stretch of dieting: the oldest applied plan in the unbroken run of cut plans. */
    private fun dietStartDay(history: List<com.zeroindex.intake.domain.goals.AppliedPlan>, phase: Phase, zone: java.time.ZoneId): Long? {
        if (phase != Phase.CUT && phase != Phase.CONTEST_PREP) return null
        val run = history.takeWhile { it.phase == Phase.CUT || it.phase == Phase.CONTEST_PREP }
        return run.lastOrNull()?.let { localDateOf(it.appliedAtMillis, zone).toEpochDay() }
    }

    /** Protein ideas from the person's own frequent foods first, then the offline database. Allergens decide what is allowed. */
    private suspend fun proteinIdeas(): List<String> {
        val own = foodDao.observeMostLogged(60).first()
            .filter { allergens.scan(it).matches.isEmpty() }
            .map { ProteinIdeas.Food(it.name, it.proteinPer100g, it.kcalPer100g, it.servingSizeGrams) }
        val ideas = ProteinIdeas.pick(own, 3).toMutableList()
        if (ideas.size < 3) {
            val fallback = ProteinIdeas.FALLBACK_QUERIES.mapNotNull { q ->
                runCatching { foodRef.search(q, 5) }.getOrNull()
                    ?.firstOrNull { ref -> allergens.scan(name = ref.name).matches.isEmpty() }
                    ?.let { ProteinIdeas.Food(it.name, it.protein, it.kcal, it.defaultPortion.grams) }
            }
            ideas += ProteinIdeas.pick(fallback, 3 - ideas.size).filter { idea -> ideas.none { it.substringBefore(" (").equals(idea.substringBefore(" ("), true) } }
        }
        return ideas.take(3)
    }

    // ------------------------------------------------------------------------------ Today card

    suspend fun dailyFocus(): DailyFocus {
        val today = clock.today()
        val idx = today.toEpochDay()
        val zone = clock.zone()
        val s = settings.settings.first()
        val profile = s.profile
        val now = java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalTime()

        val pending = coachDao.since(idx - PENDING_DAYS).count { it.status == SuggestionStatus.PENDING }

        val food = foodLogDao.inRange(clock.rangeFor(today.minusDays(30)).startInclusive, clock.rangeFor(today).endExclusive)
            .groupBy { localDateOf(it.loggedAt, zone).toEpochDay() }
        val loggedDays = food.filter { (_, logs) -> logs.sumOf { it.kcal } >= config.loggedDayMinKcal }.keys
        val todayLogs = food[idx].orEmpty()
        val target = targets.targetFor(today)

        val weighInDays = body.weightPointsSince(today.minusDays(30)).map { it.dayIndex }
        val baseline = profileRepository.baselineSnapshot()
        val baselineWeighIns = baseline?.progress?.missing?.any { it.what.contains("weigh", ignoreCase = true) } == true
        val weighInMissing = DailyFocusPicker.weighInExpected(idx, weighInDays, baselineWeighIns) && now.hour >= 7

        val day = runCatching { hydration.observeDay(today).first() }.getOrNull()

        val isoDay = today.dayOfWeek.value
        val sessionsToday = workoutDao.allSessions().filter {
            it.finishedAt != null && localDateOf(it.startedAt, zone) == today
        }
        val program = programDao.active()
        val plan = program?.let { p ->
            val days = programDao.daysOf(p.id)
            com.zeroindex.intake.domain.training.WorkoutSchedule.planFor(
                days.map { com.zeroindex.intake.domain.training.ScheduleDay(it.dayIndex, it.name, it.weekday) },
                p.currentDayIndex, isoDay,
            )
        }
        // A program with weekdays set follows them; one without keeps the rotation and the profile's weekdays.
        val dayName = (plan as? com.zeroindex.intake.domain.training.TodayPlan.Train)?.day?.name
        val scheduledToday = when (plan) {
            is com.zeroindex.intake.domain.training.TodayPlan.Train ->
                if (plan.byWeekday) true else isoDay in profile.effectiveTrainingWeekdays
            else -> false
        }
        val weekStart = idx - 6
        val workoutsThisWeek = workoutDao.allSessions().count {
            it.finishedAt != null && localDateOf(it.startedAt, zone).toEpochDay() >= weekStart
        }

        return DailyFocusPicker.pick(
            FocusInputs(
                pendingSuggestions = pending,
                weighInMissing = weighInMissing,
                hourOfDay = now.hour,
                proteinSoFarG = todayLogs.sumOf { it.protein },
                proteinTargetG = target?.proteinG ?: 0,
                workoutScheduledToday = program != null && scheduledToday,
                workoutName = dayName,
                workoutDoneToday = sessionsToday.isNotEmpty(),
                waterSoFarMl = day?.consumedMl ?: 0.0,
                waterTargetMl = day?.target?.totalMl ?: 0.0,
                volumeUnit = settings.settings.first().volumeUnit,
                baselineMissing = baseline?.takeIf { !it.progress.ready }?.progress?.missing?.firstOrNull()?.what,
                loggingStreakDays = DailyFocusPicker.streak(idx, loggedDays),
                daysLoggedThisWeek = loggedDays.count { it >= weekStart },
                workoutsThisWeek = workoutsThisWeek,
            ),
        )
    }

    companion object {
        const val PENDING_DAYS = 14L
        const val CHECK_IN_LIMIT = 26
        const val DAY_MILLIS = 86_400_000L
        const val FALLBACK_FLOOR_KCAL = 1_200
    }
}
