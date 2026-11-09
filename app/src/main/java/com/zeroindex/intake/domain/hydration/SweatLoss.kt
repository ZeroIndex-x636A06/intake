package com.zeroindex.intake.domain.hydration

import kotlin.math.min

/** One logged workout: how long, and the average effort (RPE) of its working sets if any were rated. */
data class SessionLoad(val minutes: Double, val avgRpe: Double?)

enum class SweatIntensity(val label: String, val litresPerHour: Double) {
    LIGHT("light", 0.5),
    MODERATE("moderate", 0.8),
    HARD("hard", 1.2),
}

/** The user's own weigh-in test: [preKg] and [postKg] body weight, fluid drunk during, and how long. */
data class SweatMeasurement(val preKg: Double, val postKg: Double, val fluidMl: Double, val minutes: Double) {
    /** (pre - post + fluid) / hours. 1 kg of weight lost is 1 litre of fluid. Null if the numbers cannot be right. */
    val litresPerHour: Double?
        get() {
            if (minutes < MIN_MINUTES || preKg <= 0 || postKg <= 0 || fluidMl < 0) return null
            val rate = (preKg - postKg + fluidMl / 1000.0) / (minutes / 60.0)
            return rate.takeIf { it in MIN_RATE..MAX_RATE }
        }

    /** The working, so the sheet can show how the number was reached. */
    fun calculation(weightLabel: (Double) -> String, litreLabel: (Double) -> String): String? {
        val rate = litresPerHour ?: return null
        val lost = preKg - postKg
        val total = lost + fluidMl / 1000.0
        return "(${weightLabel(preKg)} − ${weightLabel(postKg)}) + ${litreLabel(fluidMl / 1000.0)} drunk " +
            "= ${litreLabel(total)} over ${"%.2f".format(minutes / 60.0)} h = ${"%.2f".format(rate)} L/h"
    }

    companion object {
        const val MIN_MINUTES = 10.0
        const val MIN_RATE = 0.1
        const val MAX_RATE = 3.0
    }
}

/**
 * Water to replace sweat from logged workouts: minutes x a rate. The rate is by effort (average RPE under 6
 * is light 0.5 L/h, under 8 moderate 0.8 L/h, 8 and up hard 1.2 L/h; moderate when nothing was rated), or the
 * user's own measured rate. These are mid-range figures for exercise in mild weather, and heat is handled
 * separately by the weather line. A day's sweat is capped at 2 litres, and sessions under 5 minutes are ignored.
 */
object SweatLoss {
    const val DAY_CAP_ML = 2_000.0
    const val MIN_SESSION_MINUTES = 5.0

    fun intensityFor(avgRpe: Double?): SweatIntensity = when {
        avgRpe == null -> SweatIntensity.MODERATE
        avgRpe < 6.0 -> SweatIntensity.LIGHT
        avgRpe < 8.0 -> SweatIntensity.MODERATE
        else -> SweatIntensity.HARD
    }

    fun rateFor(s: SessionLoad, measuredLitresPerHour: Double?): Double =
        measuredLitresPerHour ?: intensityFor(s.avgRpe).litresPerHour

    fun sessionMl(s: SessionLoad, measuredLitresPerHour: Double?): Double =
        if (s.minutes < MIN_SESSION_MINUTES) 0.0 else s.minutes / 60.0 * rateFor(s, measuredLitresPerHour) * 1000.0

    fun totalMl(sessions: List<SessionLoad>, measuredLitresPerHour: Double?): Double =
        min(sessions.sumOf { sessionMl(it, measuredLitresPerHour) }, DAY_CAP_ML)

    fun totalMinutes(sessions: List<SessionLoad>): Double =
        sessions.filter { it.minutes >= MIN_SESSION_MINUTES }.sumOf { it.minutes }
}
