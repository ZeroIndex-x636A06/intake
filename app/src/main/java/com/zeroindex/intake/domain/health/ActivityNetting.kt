package com.zeroindex.intake.domain.health

import kotlin.math.max
import kotlin.math.min

/** A span of time with an energy figure, from Health Connect. */
data class EnergySample(val startMillis: Long, val endMillis: Long, val kcal: Double)

/** A tracked workout: the windows whose burn may be added to a target. */
data class SessionWindow(val startMillis: Long, val endMillis: Long)

/** Where the BMR figure came from, so the screen can say. */
enum class BmrBasis(val explanation: String) {
    HEALTH_CONNECT("from your scale or watch via Health Connect"),
    KATCH_MCARDLE("estimated from your weight and body fat"),
    BODYWEIGHT("estimated at 22 kcal per kg of bodyweight"),
}

data class NetActivity(
    /** What the devices reported for the session windows. */
    val rawKcal: Double,
    /** BMR over the parts of those windows that came from total-burn data. */
    val bmrKcal: Double,
    /** What may be added to the day's target. Never negative. */
    val netKcal: Double,
)

/**
 * Amendment §2: count only the calories a workout burned *on top of* the
 * resting burn the TDEE already includes.
 *
 * Health Connect has two kinds of burn record, and they need different
 * handling - getting this wrong is exactly the double-count the amendment
 * is about, in one direction or the other:
 *
 * - **Total calories burned** includes BMR. For the minutes that overlap a
 *   session, BMR for those minutes is subtracted.
 * - **Active calories burned** is defined by Health Connect as burn *above*
 *   BMR already. Subtracting BMR again would undercount, so it is used as is.
 *
 * When a source writes both for the same minutes, total-minus-BMR is used and
 * the active record ignored, so nothing is counted twice. Only burn inside a
 * tracked session counts: all-day "active" estimates from step counting are
 * the least reliable figures a wearable produces, and SPEC §12 is explicit
 * that adding them is how cuts stall.
 */
object ActivityNetting {

    const val MINUTES_PER_DAY = 1_440.0
    const val KCAL_PER_KG_FALLBACK = 22.0

    fun net(
        sessions: List<SessionWindow>,
        totalSamples: List<EnergySample>,
        activeSamples: List<EnergySample>,
        bmrKcalPerDay: Double,
    ): NetActivity {
        var raw = 0.0
        var bmr = 0.0
        var net = 0.0
        val perMinuteBmr = bmrKcalPerDay / MINUTES_PER_DAY

        sessions.forEach { session ->
            val totalInWindow = totalSamples.sumOf { overlapShare(it, session) }
            if (totalInWindow > 0) {
                val coveredMinutes = totalSamples.sumOf { overlapMillis(it, session) } / 60_000.0
                val bmrHere = perMinuteBmr * coveredMinutes
                raw += totalInWindow
                bmr += bmrHere
                net += max(0.0, totalInWindow - bmrHere)
            } else {
                val activeInWindow = activeSamples.sumOf { overlapShare(it, session) }
                raw += activeInWindow
                net += max(0.0, activeInWindow)
            }
        }
        return NetActivity(rawKcal = raw, bmrKcal = bmr, netKcal = net)
    }

    /**
     * BMR per day, from the best source available:
     * a measured BMR record, then Katch-McArdle from lean mass (370 + 21.6 ×
     * lean kg), then a bodyweight rule of thumb. Null with no weight at all.
     */
    fun bmrPerDay(measured: Double?, weightKg: Double?, bodyFatPercent: Double?): Pair<Double, BmrBasis>? = when {
        measured != null && measured > 0 -> measured to BmrBasis.HEALTH_CONNECT
        weightKg != null && bodyFatPercent != null && bodyFatPercent in 3.0..60.0 ->
            (370 + 21.6 * weightKg * (1 - bodyFatPercent / 100)) to BmrBasis.KATCH_MCARDLE
        weightKg != null && weightKg > 0 -> weightKg * KCAL_PER_KG_FALLBACK to BmrBasis.BODYWEIGHT
        else -> null
    }

    /** The part of a sample's energy that falls inside the window, pro rata. */
    private fun overlapShare(sample: EnergySample, window: SessionWindow): Double {
        val length = sample.endMillis - sample.startMillis
        if (length <= 0) {
            return if (sample.startMillis in window.startMillis until window.endMillis) sample.kcal else 0.0
        }
        return sample.kcal * overlapMillis(sample, window) / length
    }

    private fun overlapMillis(sample: EnergySample, window: SessionWindow): Double =
        max(0L, min(sample.endMillis, window.endMillis) - max(sample.startMillis, window.startMillis)).toDouble()
}
