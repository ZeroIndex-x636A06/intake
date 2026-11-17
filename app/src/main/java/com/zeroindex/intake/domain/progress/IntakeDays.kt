package com.zeroindex.intake.domain.progress

import com.zeroindex.intake.domain.time.localDateOf
import java.time.LocalDate
import java.time.ZoneId

/** One food log row reduced to what the day totals need. */
data class IntakeRow(val loggedAt: Long, val kcal: Double, val protein: Double)

/** Calories and protein summed over one local day. */
data class DayTotals(val kcal: Double, val protein: Double)

/**
 * Local-day bucketing for every Progress figure (days logged, averages, over-target
 * counts, the calorie bars and the grid). Each row goes to the local date it falls
 * on in [zone], so a day that is 23 or 25 hours long (the clocks changing) still
 * counts as one day and no day is merged into or lost from its neighbour. Doing
 * this with a fixed 24 hour divisor and one offset taken at the start of the range,
 * as the SQL used to, shifts every day after a DST change by an hour.
 */
object IntakeDays {

    fun byDay(rows: List<IntakeRow>, zone: ZoneId): Map<LocalDate, DayTotals> {
        val kcal = HashMap<LocalDate, Double>()
        val protein = HashMap<LocalDate, Double>()
        for (r in rows) {
            val d = localDateOf(r.loggedAt, zone)
            kcal[d] = (kcal[d] ?: 0.0) + r.kcal
            protein[d] = (protein[d] ?: 0.0) + r.protein
        }
        return kcal.mapValues { (d, k) -> DayTotals(k, protein.getValue(d)) }
    }

    /**
     * One slot per local day from [first] to [last] inclusive, oldest first. Null means
     * nothing was logged that day. [last] is always included, so today is never left out.
     */
    fun series(byDay: Map<LocalDate, DayTotals>, first: LocalDate, last: LocalDate): List<Double?> {
        if (last.isBefore(first)) return emptyList()
        val count = (last.toEpochDay() - first.toEpochDay() + 1).toInt()
        return (0 until count).map { off -> byDay[first.plusDays(off.toLong())]?.kcal?.takeIf { it > 0 } }
    }

    /** The figures every Progress card quotes for one series, so they cannot disagree. */
    data class Stats(
        val daysInSeries: Int,
        val loggedDays: Int,
        val averageKcal: Double?,
        val daysOverTarget: Int,
    )

    fun stats(series: List<Double?>, targetKcal: Int?): Stats {
        val logged = series.filterNotNull().filter { it > 0 }
        return Stats(
            daysInSeries = series.size,
            loggedDays = logged.size,
            averageKcal = logged.takeIf { it.isNotEmpty() }?.average(),
            daysOverTarget = if (targetKcal == null) 0 else logged.count { it > targetKcal },
        )
    }

    /** Share of logged days in [first]..[last] with protein at or above [targetProteinG] times [fraction]. */
    fun proteinHitRate(
        byDay: Map<LocalDate, DayTotals>,
        first: LocalDate,
        last: LocalDate,
        targetProteinG: Int?,
        fraction: Double = 0.9,
    ): Double? {
        if (targetProteinG == null) return null
        val days = byDay.filter { (d, t) -> !d.isBefore(first) && !d.isAfter(last) && t.kcal > 0 }.values
        if (days.isEmpty()) return null
        return days.count { it.protein >= targetProteinG * fraction }.toDouble() / days.size
    }
}
