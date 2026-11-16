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
 * The week's headline figures, computed once from a [WeeklyReport] so the check-in's top card, its "Your week" list and the
 * PDF quote the same numbers. Averages count only days with food logged (today's partial day included, as in the PDF).
 */
data class WeekFigures(
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val daysLogged: Int,
    val daysInWeek: Int,
    val weighIns: Int,
    val workouts: Int,
    val avgSleepHours: Double?,
    val weightChangeKg: Double?,
)

/** Days on which protein reached the hit share of the target, out of the days with food logged. */
data class ProteinHit(val hitDays: Int, val loggedDays: Int) {
    val percent: Int get() = if (loggedDays == 0) 0 else Math.round(hitDays * 100.0 / loggedDays).toInt()
    val label: String get() = "Protein target hit $hitDays of $loggedDays logged day${if (loggedDays == 1) "" else "s"} · $percent%"
}

/** "+0.4 lb", "−0.4 lb", or "no change" when the change rounds to zero. [displayDelta] is already in the display unit. */
fun formatWeightChange(displayDelta: Double, unitLabel: String): String {
    val v = Math.round(displayDelta * 10.0) / 10.0
    if (v == 0.0) return "no change"
    return (if (v > 0) "+" else "−") + "%.1f".format(java.util.Locale.US, Math.abs(v)) + " $unitLabel"
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

    val figures: WeekFigures get() = WeekFigures(
        avgKcal = average { it.kcal }?.let { Math.round(it).toInt() },
        avgProteinG = average { it.protein }?.let { Math.round(it).toInt() },
        daysLogged = daysLogged,
        daysInWeek = days.size,
        weighIns = days.count { it.weightKg != null },
        workouts = sessions.size,
        avgSleepHours = average { it.sleepMinutes }?.div(60.0),
        weightChangeKg = weightChangeKg,
    )
    val totalVolumeKg: Double get() = sessions.sumOf { it.volumeKg }
    val totalSets: Int get() = sessions.sumOf { it.sets }

    /** Start of week to end of week on the smoothed trend. The trend has one point per weigh-in day, so one weigh-in gives null. */
    val weightChangeKg: Double? get() =
        if (weightTrend.size >= 2) weightTrend.last().second - weightTrend.first().second else null

    /**
     * Protein hit count over the same days as the average intake (days with food logged), never over the whole week.
     * Null without a protein target or without any logged day.
     */
    fun proteinHit(fraction: Double = 0.9): ProteinHit? {
        val target = proteinTarget ?: return null
        val logged = days.filter { (it.kcal ?: 0.0) > 0 }
        if (logged.isEmpty()) return null
        return ProteinHit(logged.count { (it.protein ?: 0.0) >= target * fraction }, logged.size)
    }

    /** True when the logged days average far under the calorie target: likely under-logging, not real eating. */
    fun intakeLooksUnderLogged(share: Double = 0.7): Boolean {
        val target = kcalTarget?.takeIf { it > 0 } ?: return false
        val avg = days.filter { (it.kcal ?: 0.0) > 0 }.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.average() ?: return false
        return avg < share * target
    }

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
