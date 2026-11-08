package com.zeroindex.intake.domain.performance

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * Amendment §2: vertical jump, standing reach and total jumping reach.
 *
 * They are one equation - total = standing + vertical - so any two give the
 * third. A jump-and-reach test (a Vertec, or chalk on a wall) measures
 * standing and total directly; a jump mat measures vertical. Either way the
 * user enters what they measured and this fills in the rest.
 */
object JumpMetrics {

    /** How far apart three entered values may be before they are flagged, in cm. */
    const val CONSISTENCY_TOLERANCE_CM = 1.5

    data class Complete(
        val verticalJumpCm: Double?,
        val standingReachCm: Double?,
        val totalReachCm: Double?,
        /** Which value was worked out rather than entered, if any. */
        val derived: Field?,
        /** All three entered but they do not add up. */
        val inconsistent: Boolean,
    )

    enum class Field { VERTICAL, STANDING, TOTAL }

    fun complete(vertical: Double?, standing: Double?, total: Double?): Complete {
        val v = vertical?.takeIf { it > 0 }
        val s = standing?.takeIf { it > 0 }
        val t = total?.takeIf { it > 0 }
        return when {
            v != null && s != null && t != null ->
                Complete(v, s, t, null, abs(s + v - t) > CONSISTENCY_TOLERANCE_CM)
            s != null && t != null -> Complete((t - s).takeIf { it > 0 }, s, t, Field.VERTICAL, t <= s)
            v != null && s != null -> Complete(v, s, s + v, Field.TOTAL, false)
            v != null && t != null -> Complete(v, (t - v).takeIf { it > 0 }, t, Field.STANDING, t <= v)
            else -> Complete(v, s, t, null, false)
        }
    }
}

/** One jump test on the timeline. */
data class JumpPoint(val measuredAt: Long, val verticalJumpCm: Double)

/** A working set, reduced to what tonnage needs. */
data class VolumeSample(val completedAt: Long, val weightKg: Double, val reps: Int)

/** One week of the overlay: total load lifted and the best jump that week. */
data class OverlayWeek(
    /** Monday of the ISO week, local time. */
    val weekStart: LocalDate,
    val tonnageKg: Double,
    val workingSets: Int,
    val bestVerticalCm: Double?,
)

/**
 * Lines gym volume up against jump height week by week, so the Progress chart
 * can show whether heavier training weeks come with better or worse jumps.
 *
 * Weeks run Monday to Sunday in the user's zone. Every week between the first
 * and last data point appears, including empty ones, so a deload or a missed
 * week shows as a gap rather than being silently squeezed out of the x-axis.
 */
object PerformanceOverlay {

    fun build(
        jumps: List<JumpPoint>,
        sets: List<VolumeSample>,
        zone: ZoneId,
    ): List<OverlayWeek> {
        fun weekOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val volumeByWeek = sets.groupBy { weekOf(it.completedAt) }
        val jumpByWeek = jumps.groupBy { weekOf(it.measuredAt) }
        val weeks = (volumeByWeek.keys + jumpByWeek.keys)
        if (weeks.isEmpty()) return emptyList()

        val first = weeks.min()
        val last = weeks.max()
        return generateSequence(first) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(last) }
            .map { week ->
                val weekSets = volumeByWeek[week].orEmpty()
                OverlayWeek(
                    weekStart = week,
                    tonnageKg = weekSets.sumOf { it.weightKg * it.reps },
                    workingSets = weekSets.size,
                    bestVerticalCm = jumpByWeek[week]?.maxOf { it.verticalJumpCm },
                )
            }
            .toList()
    }
}
