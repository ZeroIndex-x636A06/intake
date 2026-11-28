package com.zeroindex.intake.domain.tdee

import com.zeroindex.intake.domain.units.WeightUnit
import kotlin.math.abs

/**
 * The one weekly weight rate (Q11-4). Today, the Insights weight card and its Averages row used to work it out three ways
 * (newest weigh-in minus one six days older; the whole chart range's first-to-last trend; the same again), so the same
 * body read "-1.9 lb this week", "-0.3 lb/week" and "-0.3 lb avg weight change". Now it is one number: the least-squares
 * slope of the smoothed trend ([WeightTrend.smoothByTime], the line the charts draw) over the last [WINDOW_DAYS] days,
 * in kilograms per week, whatever range is on screen. Water noise in a single week of raw weigh-ins does not move it.
 */
object WeightRate {
    /** The slope covers this many days ending today. */
    const val WINDOW_DAYS = 28

    /** The trend is smoothed over this many days so its start does not depend on the chart range. */
    const val SMOOTHING_DAYS = 56

    /** Weigh-in days needed in the window, and the days they must span, before a rate is shown. */
    const val MIN_POINTS = 3
    const val MIN_SPAN_DAYS = 7

    /** The words that say which period every figure is over. */
    const val PERIOD_LABEL = "4-week trend"

    /** Kilograms per week (negative while losing), or null without enough weigh-ins in the last [WINDOW_DAYS] days. */
    fun weeklyKg(points: List<WeightPoint>, todayIndex: Long): Double? {
        val recent = points.filter { it.dayIndex in (todayIndex - SMOOTHING_DAYS + 1)..todayIndex }
        val window = WeightTrend.smoothByTime(recent).filter { it.dayIndex > todayIndex - WINDOW_DAYS }
        if (window.size < MIN_POINTS) return null
        if (window.last().dayIndex - window.first().dayIndex < MIN_SPAN_DAYS) return null
        return WeightTrend.slopeKgPerDay(window)?.times(7.0)
    }

    /** "−0.3 lb/week", "+0.4 kg/week", or "0.0 lb/week" with no sign when it rounds to nothing. */
    fun text(kgPerWeek: Double, unit: WeightUnit): String {
        val value = unit.fromKg(kgPerWeek)
        val magnitude = "%.1f".format(abs(value))
        val sign = if (magnitude == "0.0") "" else if (value < 0) "−" else "+"
        return "$sign$magnitude ${unit.label}/week"
    }
}
