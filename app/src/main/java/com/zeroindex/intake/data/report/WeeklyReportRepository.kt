package com.zeroindex.intake.data.report

import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.ExerciseDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.ProgramDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.domain.report.ReportDay
import com.zeroindex.intake.domain.report.ReportExercise
import com.zeroindex.intake.domain.report.ReportSession
import com.zeroindex.intake.domain.report.ReportSet
import com.zeroindex.intake.domain.report.WeeklyReport
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.tdee.WeightTrend
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/** Gathers one Monday-to-Sunday week into a [WeeklyReport]. Reads only. */
@Singleton
class WeeklyReportRepository @Inject constructor(
    private val foodLogDao: FoodLogDao,
    private val healthDao: HealthSummaryDao,
    private val bodyDao: BodyMetricDao,
    private val hydrationDao: HydrationDao,
    private val workoutDao: WorkoutDao,
    private val exerciseDao: ExerciseDao,
    private val programDao: ProgramDao,
    private val targets: TargetRepository,
    private val tdee: TdeeRepository,
    private val clock: Clock,
) {
    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    suspend fun build(weekStart: LocalDate): WeeklyReport {
        val zone = clock.zone()
        val start = clock.rangeFor(weekStart).startInclusive
        val end = clock.rangeFor(weekStart.plusDays(6)).endExclusive
        val dates = (0L..6L).map { weekStart.plusDays(it) }

        val logs = foodLogDao.inRange(start, end).groupBy { localDateOf(it.loggedAt, zone) }
        val health = healthDao.inRange(start, end).associateBy { localDateOf(it.dayStartMillis, zone) }
        val weights = bodyDao.inRange(start, end).groupBy { localDateOf(it.measuredAt, zone) }
        val water = hydrationDao.logsInRange(start, end).groupBy { localDateOf(it.loggedAt, zone) }
        val maintenance = tdee.latest()?.estimatedTdee

        val days = dates.map { date ->
            val day = logs[date].orEmpty()
            val h = health[date]
            ReportDay(
                date = date,
                kcal = day.takeIf { it.isNotEmpty() }?.sumOf { it.kcal },
                protein = day.takeIf { it.isNotEmpty() }?.sumOf { it.protein },
                carbs = day.takeIf { it.isNotEmpty() }?.sumOf { it.carbs },
                fat = day.takeIf { it.isNotEmpty() }?.sumOf { it.fat },
                fiber = day.takeIf { it.isNotEmpty() }?.sumOf { it.fiber },
                sugar = day.takeIf { it.isNotEmpty() }?.sumOf { it.sugar },
                // The device's figure where there is one; otherwise the app's
                // own model of the day - measured maintenance plus net workouts.
                burned = h?.totalKcalBurned ?: maintenance?.plus(h?.activeKcalNet ?: 0.0),
                burnedFromDevice = h?.totalKcalBurned != null,
                steps = h?.steps,
                sleepMinutes = h?.sleepMinutes,
                sleepStartMillis = h?.sleepStartMillis,
                restingHr = h?.restingHeartRate,
                avgHr = h?.avgHeartRate,
                weightKg = weights[date]?.maxByOrNull { it.measuredAt }?.weightKg,
                waterMl = water[date]?.sumOf { it.volumeMl },
            )
        }

        // Smooth with two weeks of lead-in so the first day is not a raw value.
        val leadIn = clock.rangeFor(weekStart.minusDays(14)).startInclusive
        val trend = WeightTrend.smooth(
            bodyDao.inRange(leadIn, end).map { WeightPoint(localDateOf(it.measuredAt, zone).toEpochDay(), it.weightKg) },
        ).filter { it.dayIndex in weekStart.toEpochDay()..weekStart.plusDays(6).toEpochDay() }
            .map { LocalDate.ofEpochDay(it.dayIndex) to it.trendKg }

        val names = exerciseDao.all().associate { it.id to it.name }
        val sets = workoutDao.workingSetsInRange(start, end).groupBy { it.sessionId }
        val sessions = workoutDao.allSessions()
            .filter { it.deletedAt == null && it.startedAt in start until end }
            .sortedBy { it.startedAt }
            .map { s ->
                val sessionSets = sets[s.id].orEmpty()
                ReportSession(
                    date = localDateOf(s.startedAt, zone),
                    title = s.programDayId?.let { programDao.findDay(it)?.name } ?: "Freestyle session",
                    minutes = s.finishedAt?.let { (it - s.startedAt) / 60_000 },
                    exercises = sessionSets.groupBy { it.exerciseId }.map { (exerciseId, list) ->
                        ReportExercise(
                            name = names[exerciseId] ?: "Exercise",
                            sets = list.sortedBy { it.setIndex }.map { ReportSet(it.weightKg, it.reps, it.rpe) },
                        )
                    },
                    notes = s.notes?.takeIf { it.isNotBlank() },
                )
            }

        val target = targets.targetFor(weekStart.plusDays(6))
        return WeeklyReport(
            weekStart = weekStart,
            days = days,
            sessions = sessions,
            kcalTarget = target?.kcal,
            proteinTarget = target?.proteinG,
            weightTrend = trend,
            zone = zone,
        )
    }
}
