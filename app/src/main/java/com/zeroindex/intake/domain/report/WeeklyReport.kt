package com.zeroindex.intake.domain.report

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

data class ReportDay(
    val date: LocalDate,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    /** Calories out for the day; see [burnedFromDevice]. */
    val burned: Double? = null,
    /** True for the device's own estimate, false for maintenance + net workouts. */
    val burnedFromDevice: Boolean = false,
    val steps: Long? = null,
    val sleepMinutes: Long? = null,
    val sleepStartMillis: Long? = null,
    val restingHr: Double? = null,
    val avgHr: Double? = null,
    val weightKg: Double? = null,
    val waterMl: Double? = null,
)

data class ReportSet(val weightKg: Double, val reps: Int, val rpe: Double?)

data class ReportExercise(val name: String, val sets: List<ReportSet>) {
    val volumeKg: Double get() = sets.sumOf { it.weightKg * it.reps }

    /** "3 × 5 @ 82.5 kg", or each set listed when they differ. */
    fun describe(format: (Double) -> String): String {
        val distinct = sets.map { it.weightKg to it.reps }.distinct()
        return if (distinct.size == 1) {
            "${sets.size} × ${sets.first().reps} @ ${format(sets.first().weightKg)}"
        } else {
            sets.joinToString(", ") { "${format(it.weightKg)} × ${it.reps}" }
        }
    }
}

data class ReportSession(
    val date: LocalDate,
    val title: String,
    val minutes: Long?,
    val exercises: List<ReportExercise>,
    val notes: String?,
) {
    val volumeKg: Double get() = exercises.sumOf { it.volumeKg }
    val sets: Int get() = exercises.sumOf { it.sets.size }
}

/**
 * Amendment §3's weekly check-in, as data. Everything the PDF prints is
 * computed here, so the numbers on paper are the tested ones.
 */
data class WeeklyReport(
    val weekStart: LocalDate,
    val days: List<ReportDay>,
    val sessions: List<ReportSession>,
    val kcalTarget: Int?,
    val proteinTarget: Int?,
    /** Smoothed trend weight per day where known. */
    val weightTrend: List<Pair<LocalDate, Double>>,
    val zone: ZoneId,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    fun average(selector: (ReportDay) -> Number?): Double? =
        days.mapNotNull { selector(it)?.toDouble() }.takeIf { it.isNotEmpty() }?.average()

    val daysLogged: Int get() = days.count { it.kcal != null }
    val totalVolumeKg: Double get() = sessions.sumOf { it.volumeKg }
    val totalSets: Int get() = sessions.sumOf { it.sets }

    /** Start of week to end of week on the smoothed trend, if both ends exist. */
    val weightChangeKg: Double? get() =
        if (weightTrend.size >= 2) weightTrend.last().second - weightTrend.first().second else null

    /**
     * How regular bedtime was: the standard deviation of sleep start, in
     * minutes. Times are measured from noon so 23:30 and 00:30 are an hour
     * apart, not twenty-three.
     */
    val bedtimeSpreadMinutes: Double? get() {
        val minutes = days.mapNotNull { it.sleepStartMillis }.map { start ->
            val t = Instant.ofEpochMilli(start).atZone(zone).toLocalTime()
            val fromMidnight = t.hour * 60 + t.minute
            ((fromMidnight - 12 * 60) + 24 * 60) % (24 * 60)
        }
        if (minutes.size < 2) return null
        val mean = minutes.average()
        return sqrt(minutes.sumOf { (it - mean) * (it - mean) } / minutes.size)
    }
}
