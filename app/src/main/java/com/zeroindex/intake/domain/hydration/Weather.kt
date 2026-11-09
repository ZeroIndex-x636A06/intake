package com.zeroindex.intake.domain.hydration

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** One day's forecast: the high, and the day's mean relative humidity if the service gave it. */
data class WeatherDay(val tempMaxC: Double, val humidityPct: Double? = null)

/** What the weather line on the hydration card has to say, including "no adjustment, and why". */
sealed interface WeatherStatus {
    data object Off : WeatherStatus
    data object NeedsLocation : WeatherStatus
    data object Unavailable : WeatherStatus
    data class Ready(val day: WeatherDay, val place: String?) : WeatherStatus
}

/**
 * Extra water for hot days, deliberately conservative.
 *
 * Temperature bands (the day's high): below 25 C nothing; 25 to under 30 C +250 ml; 30 to 35 C +500 ml;
 * above 35 C +750 ml. When it is also humid (mean humidity of 70% or more) and above 27 C, sweat
 * evaporates less well, so +250 ml more. The total is capped at 1 litre.
 */
object WeatherAdjustment {
    const val CAP_ML = 1_000.0
    const val HUMID_BUMP_ML = 250.0
    const val HUMID_FROM_PCT = 70.0
    const val HUMID_ABOVE_C = 27.0

    fun bandMl(tempC: Double): Double = when {
        tempC < 25.0 -> 0.0
        tempC < 30.0 -> 250.0
        tempC <= 35.0 -> 500.0
        else -> 750.0
    }

    fun isHumid(w: WeatherDay): Boolean =
        w.humidityPct != null && w.humidityPct >= HUMID_FROM_PCT && w.tempMaxC > HUMID_ABOVE_C

    fun adjustmentMl(w: WeatherDay): Double =
        min(bandMl(w.tempMaxC) + if (isHumid(w)) HUMID_BUMP_ML else 0.0, CAP_ML)

    fun tempLabel(w: WeatherDay): String = "${w.tempMaxC.roundToInt()} °C today"
}

/** Coordinates are rounded to one decimal (about 10 km) before any request leaves the phone. */
object CoarseCoordinates {
    fun round(degrees: Double): Double = floor(degrees * 10.0 + 0.5) / 10.0
}
