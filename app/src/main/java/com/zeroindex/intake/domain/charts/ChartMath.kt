package com.zeroindex.intake.domain.charts

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** The pure maths behind the Progress line charts: scrubbing, gaps and axis labels. No Android, so it is unit-tested. */
object ChartMath {

    /** A line is not drawn solid across a stretch with no data longer than this. */
    const val GAP_DAYS = 14
    const val DAY_MS = 86_400_000.0

    /**
     * The index of the value in [xs] (ascending) nearest to [x], or null when [xs] is empty. A tie goes to the earlier
     * point. Binary search, so dragging over a year of points stays cheap.
     */
    fun nearestIndex(xs: List<Double>, x: Double): Int? {
        if (xs.isEmpty()) return null
        var lo = 0
        var hi = xs.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] < x) lo = mid + 1 else hi = mid
        }
        // lo is the first index with xs[lo] >= x; the answer is it or the one before.
        if (lo == 0) return 0
        return if (abs(xs[lo - 1] - x) <= abs(xs[lo] - x)) lo - 1 else lo
    }

    /**
     * Splits ascending [xs] into runs of indexes where no two neighbours are more than [maxGap] apart. A trend line is
     * drawn solid inside a run and not at all (or faded) between runs.
     */
    fun runs(xs: List<Double>, maxGap: Double): List<IntRange> {
        if (xs.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var start = 0
        for (i in 1 until xs.size) {
            if (xs[i] - xs[i - 1] > maxGap) {
                out += start..(i - 1)
                start = i
            }
        }
        out += start..xs.lastIndex
        return out
    }

    /**
     * "Nice" tick values (1, 2 or 5 times a power of ten apart) inside [min]..[max], at most about [maxTicks] of them.
     * Empty when the range is degenerate or not finite.
     */
    fun niceTicks(min: Double, max: Double, maxTicks: Int = 4): List<Double> {
        if (!min.isFinite() || !max.isFinite() || max <= min || maxTicks < 2) return emptyList()
        val rough = (max - min) / (maxTicks - 1)
        val magnitude = 10.0.pow(floor(log10(rough)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= rough - 1e-12 }
        val first = ceil(min / step - 1e-9)
        val out = mutableListOf<Double>()
        var k = first
        while (k * step <= max + step * 1e-9) {
            // Always an integer multiple of the step, so 0.1 steps do not drift into 0.30000000000000004.
            out += Math.round(k * step * 1e6) / 1e6
            k += 1.0
        }
        return out
    }

    /** Decimal places a tick label needs so neighbouring ticks look different: 0 for whole steps, else 1 or 2. */
    fun tickDecimals(ticks: List<Double>): Int {
        if (ticks.size < 2) return 0
        val step = abs(ticks[1] - ticks[0])
        return when {
            step >= 1.0 - 1e-9 && abs(step - Math.rint(step)) < 1e-9 -> 0
            step >= 0.1 - 1e-9 -> 1
            else -> 2
        }
    }

    /** Millis at the start of local day number [dayIndex] (days since the epoch, as the weight trend counts them). */
    fun localDayStartMillis(dayIndex: Long, zone: ZoneId): Long =
        LocalDate.ofEpochDay(dayIndex).atStartOfDay(zone).toInstant().toEpochMilli()

    /** The local calendar date of an instant, for a tooltip. */
    fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** What a tooltip calls a stored row source: "HEALTH_CONNECT" is Health Connect, "MANUAL" or blank is Manual. */
    fun sourceLabel(source: String?): String = when (val s = source?.trim().orEmpty()) {
        "", "MANUAL" -> "Manual"
        "HEALTH_CONNECT" -> "Health Connect"
        else -> s.lowercase().split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
