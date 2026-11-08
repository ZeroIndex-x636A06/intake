package com.zeroindex.intake.data.ai.insight

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.InsightDigest
import com.zeroindex.intake.domain.ai.InsightInputs
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class InsightReply(val insight: String, val focus: String, val basedOn: List<String>)

/**
 * Amendment §3: one actionable suggestion a day, from everything the app
 * knows - reduced first to a numeric digest ([InsightDigest]).
 *
 * Opt-in (Settings → AI → Daily insight), at most once a day, and only with
 * at least three logged days. The suggestion is checked before it is shown:
 * if it names a food the allergen engine matches, it is asked again once
 * with that pointed out, and dropped if it still does. A model is never
 * allowed to suggest eating something the user is allergic to.
 */
@Singleton
class DailyInsights @Inject constructor(
    private val gateway: AiGateway,
    private val dao: AiInsightDao,
    private val foodLog: FoodLogRepository,
    private val targets: TargetRepository,
    private val body: BodyRepository,
    private val tdee: TdeeRepository,
    private val workoutDao: WorkoutDao,
    private val healthDao: HealthSummaryDao,
    private val hydrationDao: HydrationDao,
    private val hydration: HydrationRepository,
    private val allergens: AllergenRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    fun observeToday(): Flow<AiInsightEntity?> = dao.observeForDay(clock.todayRange().startInclusive)

    /** Generates today's insight if it is wanted, possible, and not already made. */
    suspend fun ensureToday() = mutex.withLock {
        if (!settings.settings.first().dailyInsightEnabled || !gateway.isConfigured()) return@withLock
        val dayStart = clock.todayRange().startInclusive
        if (dao.forDay(dayStart) != null) return@withLock
        val inputs = inputs()
        if (!InsightDigest.hasEnough(inputs)) return@withLock
        val digest = InsightDigest.lines(inputs)
        val avoided = allergens.avoidedAllergens()

        var reply = ask(digest, avoided, feedback = null) ?: return@withLock
        var scan = allergens.scan(name = null, ingredients = reply.first.insight)
        if (scan.blocks) {
            reply = ask(digest, avoided, feedback = scan.contains.joinToString { it.matchedTerm }) ?: return@withLock
            scan = allergens.scan(name = null, ingredients = reply.first.insight)
            if (scan.blocks) return@withLock
        }
        val now = clock.nowMillis()
        dao.upsert(
            AiInsightEntity(
                dayStartMillis = dayStart,
                text = reply.first.insight.trim().take(400),
                rationale = (digest + listOf("Focus: ${reply.first.focus}")).joinToString("\n"),
                model = reply.second,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun dismiss(id: String) = dao.dismiss(id, clock.nowMillis())

    private suspend fun ask(digest: List<String>, avoided: Set<String>, feedback: String?): Pair<InsightReply, String>? =
        try {
            val outcome = gateway.structured(
                task = AiTask.DAILY_INSIGHT,
                serializer = InsightReply.serializer(),
                schema = schema,
                system = systemPrompt(avoided),
                parts = listOfNotNull(
                    AiPart.Text("The last two weeks:\n" + digest.joinToString("\n")),
                    feedback?.let { AiPart.Text("Your last suggestion mentioned $it, which the user cannot eat. Suggest something else.") },
                ),
            )
            outcome.value to outcome.response.model
        } catch (_: AiError) {
            null
        }

    private suspend fun inputs(): InsightInputs {
        val today = clock.today()
        val from = today.minusDays(13)
        val zone = clock.zone()
        val start = clock.rangeFor(from).startInclusive
        val end = clock.rangeFor(today).endExclusive
        val dates = (0L..13L).map { from.plusDays(it) }

        val kcalByDay = foodLog.dailyIntake(from, today).associate { localDateOf(it.dayStartMillis, zone) to it.kcal }
        val proteinByDay = foodLog.proteinByDay(from, today)
        val target = targets.targetFor(today)
        val points = body.weightPointsSince(from)
        val trend = WeightTrend.smooth(points)
        val weekly = if (trend.size >= 2) {
            val days = (trend.last().dayIndex - trend.first().dayIndex).toDouble()
            if (days > 0) (trend.last().trendKg - trend.first().trendKg) / days * 7 else null
        } else {
            null
        }
        val latest = body.latest()
        val estimate = tdee.latest()

        val sessions = workoutDao.allSessions().filter { it.deletedAt == null && it.startedAt in start until end }
        val weekCut = clock.rangeFor(today.minusDays(6)).startInclusive
        val sets = workoutDao.workingSetsInRange(start, end)
        val weeklyRpe = sets.groupBy {
            localDateOf(it.completedAt, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.toSortedMap().values.map { week -> week.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average() }

        val health = healthDao.inRange(start, end)
        val firstWeek = health.filter { it.dayStartMillis < weekCut }
        val lastWeek = health.filter { it.dayStartMillis >= weekCut }
        val water = hydrationDao.logsInRange(start, end).groupBy { localDateOf(it.loggedAt, zone) }
            .values.map { day -> day.sumOf { it.volumeMl } }

        return InsightInputs(
            days = dates.size,
            kcalTarget = target?.kcal,
            proteinTarget = target?.proteinG,
            dailyKcal = dates.map { d -> kcalByDay[d]?.takeIf { it > 0 } },
            dailyProtein = dates.map { d -> proteinByDay[d]?.takeIf { kcalByDay[d] != null } },
            weightTrendKgPerWeek = weekly,
            tdeeKcal = estimate?.estimatedTdee,
            tdeeConfidence = estimate?.confidence?.name?.lowercase(),
            bodyFatPercent = latest?.bodyFatPercent,
            leanMassKg = latest?.leanBodyMassKg,
            sessionsLastWeek = sessions.count { it.startedAt >= weekCut },
            sessionsWeekBefore = sessions.count { it.startedAt < weekCut },
            weeklyAvgRpe = weeklyRpe,
            sleepMinutesAvg = health.mapNotNull { it.sleepMinutes?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            restingHrFirstWeek = firstWeek.mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }?.average(),
            restingHrLastWeek = lastWeek.mapNotNull { it.restingHeartRate }.takeIf { it.isNotEmpty() }?.average(),
            stepsAvg = health.mapNotNull { it.steps?.toDouble() }.takeIf { it.isNotEmpty() }?.average(),
            waterMlAvg = water.takeIf { it.isNotEmpty() }?.average(),
            waterTargetMl = hydration.observeDay(today).first().target.totalMl,
        )
    }

    private fun systemPrompt(avoided: Set<String>) = buildString {
        appendLine("You are a supportive nutrition and training coach writing one suggestion for today.")
        appendLine("Pick the single change most likely to help, based on the data. One or two sentences, under 240 characters.")
        appendLine("Be specific and actionable. Mention the numbers that support it. Encouraging, never scolding.")
        appendLine("Never diagnose or give medical advice. Never suggest eating under 1,200 kcal or skipping meals.")
        if (avoided.isNotEmpty()) appendLine("The user cannot eat ${avoided.joinToString()}; never suggest foods containing them.")
        append("List in basedOn the data lines you used. Reply with JSON only.")
    }

    private val schema = S.obj(
        "insight" to S.string(),
        "focus" to S.enumOf("protein", "calories", "training", "recovery", "hydration", "consistency"),
        "basedOn" to S.array(S.string()),
    )
}
