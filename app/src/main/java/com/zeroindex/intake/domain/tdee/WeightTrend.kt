package com.zeroindex.intake.domain.tdee

/** A weigh-in reduced to what the maths needs: a local day index and kilograms. */
data class WeightPoint(
    /** Days since the epoch, in the user's zone. */
    val dayIndex: Long,
    val weightKg: Double,
)

/** A smoothed point, paired with the raw value it came from. */
data class TrendPoint(
    val dayIndex: Long,
    val rawKg: Double,
    val trendKg: Double,
)

/**
 * SPEC §8: smooth daily weights with an exponentially weighted moving average,
 * alpha 0.1, to strip out water fluctuation. The trend line is what gets shown
 * everywhere except the raw log itself.
 */
object WeightTrend {

    const val DEFAULT_ALPHA = 0.1

    /**
     * Returns one [TrendPoint] per input, in order. The first point seeds the
     * average with itself, so a single weigh-in has a trend equal to its own
     * value rather than zero.
     *
     * Multiple weigh-ins on the same day are averaged first: two readings on a
     * Tuesday should not pull the trend twice as hard as one on Wednesday.
     */
    fun smooth(points: List<WeightPoint>, alpha: Double = DEFAULT_ALPHA): List<TrendPoint> {
        require(alpha > 0.0 && alpha <= 1.0) { "alpha must be in (0, 1], was $alpha" }
        if (points.isEmpty()) return emptyList()

        val perDay = points
            .groupBy { it.dayIndex }
            .map { (day, readings) -> WeightPoint(day, readings.map { it.weightKg }.average()) }
            .sortedBy { it.dayIndex }

        var trend = perDay.first().weightKg
        return perDay.map { point ->
            trend += alpha * (point.weightKg - trend)
            TrendPoint(dayIndex = point.dayIndex, rawKg = point.weightKg, trendKg = trend)
        }
    }

    /**
     * Least-squares slope of the smoothed trend, in kilograms per day. Negative
     * while losing. Null when there are fewer than two distinct days, because a
     * slope through one point is not a slope.
     */
    fun slopeKgPerDay(trend: List<TrendPoint>): Double? {
        if (trend.size < 2) return null
        val n = trend.size
        val meanX = trend.sumOf { it.dayIndex.toDouble() } / n
        val meanY = trend.sumOf { it.trendKg } / n
        var numerator = 0.0
        var denominator = 0.0
        trend.forEach { point ->
            val dx = point.dayIndex - meanX
            numerator += dx * (point.trendKg - meanY)
            denominator += dx * dx
        }
        if (denominator == 0.0) return null
        return numerator / denominator
    }
}
