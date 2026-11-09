package com.zeroindex.intake.data.profile

import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.goals.AppliedPlan
import com.zeroindex.intake.domain.goals.BaselineReadiness
import com.zeroindex.intake.domain.goals.BaselineState
import com.zeroindex.intake.domain.goals.GoalInputs
import com.zeroindex.intake.domain.goals.GoalPlan
import com.zeroindex.intake.domain.goals.MeasuredEnergy
import com.zeroindex.intake.domain.goals.MeasuredTdee
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** What the Today card and the profile screen show for a baseline week in progress. */
data class BaselineSnapshot(
    val state: BaselineState,
    val progress: BaselineReadiness.Progress,
    val workouts: Int,
    val averageSteps: Double?,
    val averageSleepMinutes: Double?,
    val averageRestingHr: Double?,
    val healthDays: Int,
    val measured: MeasuredTdee?,
)

/**
 * Glue between the pure goal engine and the app's data: it reads the profile from DataStore, the
 * baseline week from the food, weigh-in, Health Connect and workout tables, and writes an applied
 * plan back through the existing target, settings and training repositories.
 *
 * No network and no AI anywhere in here.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val settings: SettingsRepository,
    private val foodLogDao: FoodLogDao,
    private val bodyDao: BodyMetricDao,
    private val healthDao: HealthSummaryDao,
    private val workoutDao: WorkoutDao,
    private val bodyRepository: BodyRepository,
    private val targets: TargetRepository,
    private val tdee: TdeeRepository,
    private val training: TrainingRepository,
    private val clock: Clock,
) {
    fun observeSettings(): Flow<AppSettings> = settings.settings

    fun observeProfile(): Flow<UserProfile> = settings.settings.map { it.profile }.distinctUntilChanged()

    suspend fun profile(): UserProfile = settings.settings.first().profile

    suspend fun saveProfile(profile: UserProfile) = settings.setProfile(profile)

    suspend fun update(block: (UserProfile) -> UserProfile) = saveProfile(block(profile()))

    fun observeLatestWeigh(): Flow<BodyMetricEntity?> = bodyRepository.observeLatest()

    /** A weigh-in, stored in kilograms like every other one (SPEC §3.5). */
    suspend fun logWeighIn(weightKg: Double, bodyFatPercent: Double?) {
        bodyRepository.save(weightKg = weightKg, bodyFatPercent = bodyFatPercent)
    }

    // --- Baseline week ---------------------------------------------------------------------

    suspend fun startBaseline() {
        settings.setBaseline(BaselineState(startedAtMillis = clock.nowMillis()))
    }

    suspend fun cancelBaseline() = settings.setBaseline(BaselineState())

    suspend fun markBaselineNotified() {
        val current = settings.settings.first().baseline
        settings.setBaseline(current.copy(notifiedAtMillis = clock.nowMillis()))
    }

    /** Re-reads the tables whenever food, weigh-ins or the marker change. Null when no baseline is running. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBaseline(): Flow<BaselineSnapshot?> = settings.settings.map { it.baseline }.distinctUntilChanged()
        .flatMapLatest { state ->
            val started = state.startedAtMillis
            if (started == null || state.completed) {
                flowOf(null)
            } else {
                val from = clock.rangeFor(localDateOf(started, clock.zone())).startInclusive
                val to = clock.rangeFor(clock.today()).endExclusive
                combine(
                    foodLogDao.observeInRange(from, to),
                    bodyDao.observeInRange(from, to),
                    workoutDao.observeSessions(),
                ) { food, body, sessions ->
                    snapshot(state, food, body, sessions.count { it.finishedAt != null && it.startedAt >= started })
                }
            }
        }

    suspend fun baselineSnapshot(): BaselineSnapshot? {
        val state = settings.settings.first().baseline
        val started = state.startedAtMillis ?: return null
        if (state.completed) return null
        val from = clock.rangeFor(localDateOf(started, clock.zone())).startInclusive
        val to = clock.rangeFor(clock.today()).endExclusive
        val workouts = workoutDao.allSessions().count { it.finishedAt != null && it.startedAt >= started }
        return snapshot(state, foodLogDao.inRange(from, to), bodyDao.inRange(from, to), workouts)
    }

    private suspend fun snapshot(
        state: BaselineState,
        food: List<FoodLogEntity>,
        body: List<BodyMetricEntity>,
        workouts: Int,
    ): BaselineSnapshot {
        val zone = clock.zone()
        val startDay = localDateOf(state.startedAtMillis!!, zone).toEpochDay()
        val foodDays = foodDays(food)
        val weighInDays = body.map { localDateOf(it.measuredAt, zone).toEpochDay() }
        val progress = BaselineReadiness.evaluate(startDay, clock.today().toEpochDay(), foodDays, weighInDays)

        val health = healthDao.inRange(
            clock.rangeFor(LocalDate.ofEpochDay(startDay)).startInclusive,
            clock.rangeFor(clock.today()).endExclusive,
        )
        val steps = health.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toDouble() }
        val sleep = health.mapNotNull { it.sleepMinutes?.takeIf { m -> m > 0 }?.toDouble() }
        val hr = health.mapNotNull { it.restingHeartRate?.takeIf { r -> r > 0 } }
        val measured = MeasuredEnergy.fromBaseline(weightPoints(body), foodDays)
        return BaselineSnapshot(
            state = state,
            progress = progress,
            workouts = workouts,
            averageSteps = steps.takeIf { it.isNotEmpty() }?.average(),
            averageSleepMinutes = sleep.takeIf { it.isNotEmpty() }?.average(),
            averageRestingHr = hr.takeIf { it.isNotEmpty() }?.average(),
            healthDays = health.size,
            measured = measured,
        )
    }

    private fun foodDays(food: List<FoodLogEntity>): List<BaselineReadiness.FoodDay> {
        val zone = clock.zone()
        return food.groupBy { localDateOf(it.loggedAt, zone).toEpochDay() }.map { (day, logs) ->
            BaselineReadiness.FoodDay(day, entryCount = logs.size, kcal = logs.sumOf { it.kcal })
        }
    }

    private fun weightPoints(body: List<BodyMetricEntity>): List<WeightPoint> = body.map {
        WeightPoint(localDateOf(it.measuredAt, clock.zone()).toEpochDay(), it.weightKg)
    }

    // --- Goal builder inputs ---------------------------------------------------------------

    /** Null until the profile basics and one weigh-in exist. */
    suspend fun goalInputs(): GoalInputs? {
        val profile = profile()
        if (!profile.hasBasics) return null
        val latest = bodyRepository.latest() ?: return null
        val snap = baselineSnapshot()
        val adaptive = tdee.latest()?.let { MeasuredEnergy.Adaptive(it.estimatedTdee, it.confidence) }

        // Steps: this baseline's average if it has some, else the last week of Health Connect data.
        val steps = snap?.averageSteps ?: recentAverageSteps()
        return GoalInputs(
            profile = profile.copy(bodyFatPercent = profile.bodyFatPercent ?: latest.bodyFatPercent),
            weightKg = latest.weightKg,
            currentYear = clock.today().year,
            averageSteps = steps,
            baseline = snap?.measured,
            adaptive = adaptive,
        )
    }

    private suspend fun recentAverageSteps(): Double? {
        val today = clock.today()
        val rows = healthDao.inRange(clock.rangeFor(today.minusDays(7)).startInclusive, clock.rangeFor(today).endExclusive)
        return rows.mapNotNull { it.steps?.takeIf { s -> s > 0 }?.toDouble() }.takeIf { it.size >= 3 }?.average()
    }

    suspend fun hiddenExerciseIds(): Set<String> = training.hiddenExerciseIds()

    // --- Applying a plan -------------------------------------------------------------------

    /**
     * Writes the plan through the existing places: a new dated nutrition target (history is kept,
     * nothing is overwritten), the g/kg and rate settings the nightly updater reads, the water
     * base, and optionally the generated program. Returns the program id when one was created.
     *
     * The nightly target updater is switched off only when the plan cannot be expressed as a
     * simple rate: carb cycling, refeeds, contest prep or a reverse diet. The caller says so.
     */
    suspend fun applyPlan(plan: GoalPlan, program: GeneratedProgram?, summary: String): String? {
        val m = plan.macros
        targets.setTarget(
            MacroTarget(kcal = plan.calories.kcal, proteinG = m.proteinG, carbsG = m.carbsG, fatG = m.fatG),
            isAutoCalculated = false,
        )
        settings.setGoalRateKgPerWeek(((plan.calories.kgPerWeek * 20).roundToInt() / 20.0))
        // The nightly updater reads these as grams per kg of body weight, even when the plan used lean mass.
        settings.setProteinPerKg(m.proteinG / plan.weightKg)
        settings.setFatPerKg(m.fatG / plan.weightKg)
        settings.setHydrationBaseMl(plan.water.baseMl.toDouble())
        settings.setWeekPattern(plan.week?.pattern)
        if (needsManualTargets(plan)) settings.setAutoCalculateTargets(false)

        val created = program?.let { training.createProgramFromBlueprint(it, activate = true) }

        val baseline = settings.settings.first().baseline
        if (baseline.startedAtMillis != null && !baseline.completed) {
            settings.setBaseline(baseline.copy(completed = true))
        }
        settings.addAppliedPlan(
            AppliedPlan(
                appliedAtMillis = clock.nowMillis(),
                phase = plan.phase,
                kcal = plan.calories.kcal,
                proteinG = m.proteinG,
                carbsG = m.carbsG,
                fatG = m.fatG,
                waterMl = plan.water.baseMl,
                tdee = plan.tdee.roundToInt(),
                tdeeSource = plan.tdeeSource,
                summary = summary,
                programName = created?.name,
            ),
        )
        return created?.id
    }

    companion object {
        /** True when the nightly rate-based updater would undo what the plan says. */
        fun needsManualTargets(plan: GoalPlan): Boolean =
            plan.week != null || plan.phase == Phase.CONTEST_PREP || plan.phase == Phase.REVERSE_DIET
    }
}
