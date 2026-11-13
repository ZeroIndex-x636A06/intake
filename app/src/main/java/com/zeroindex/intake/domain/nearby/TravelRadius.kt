package com.zeroindex.intake.domain.nearby

import kotlin.math.roundToInt

/** How the person gets there. The search radius is a travel time, not a distance. */
enum class TravelMode(val id: String, val label: String, val verb: String, val speedKmh: Double) {
    /** An easy walking pace on pavements, including crossings: 4.8 km/h (3 mph). */
    WALK("walk", "Walk", "walk", 4.8),

    /** The average of a city and suburban drive, with lights and parking: 40 km/h (25 mph), not a speed limit. */
    DRIVE("drive", "Drive", "drive", 40.0),
    ;

    companion object {
        fun fromId(id: String?): TravelMode = entries.firstOrNull { it.id == id } ?: DRIVE
    }
}

/** A travel time and a mode, as chosen in Settings > Nutrition. */
data class TravelBudget(val minutes: Int, val mode: TravelMode) {
    /** "10 min drive". */
    val label: String get() = "$minutes min ${mode.verb}"

    /** The straight-line radius to search, in metres. */
    val meters: Int get() = TravelRadius.meters(minutes, mode)

    companion object {
        val DEFAULT = TravelBudget(10, TravelMode.DRIVE)
    }
}

/**
 * Turns "10 or 15 minutes, walking or driving" into a search radius.
 *
 * The map search is a circle around the person, but the trip follows roads, which are longer than the straight line.
 * So the radius is the distance the average speed covers in the time, divided by [CIRCUITY]: 1.3 is a common figure
 * for how much longer a street route is than the straight line in a town. That gives about 0.6 km for a 10-minute walk
 * and about 5.1 km for a 10-minute drive (7.7 km for 15). They are averages, not a routing answer: no route is asked for.
 */
object TravelRadius {
    const val CIRCUITY = 1.3
    val MINUTES_CHOICES = listOf(10, 15)

    fun meters(minutes: Int, mode: TravelMode): Int =
        (mode.speedKmh * 1000.0 * minutes / 60.0 / CIRCUITY).roundToInt()

    /** Normalises a stored minutes value to a choice the Settings control offers. */
    fun clampMinutes(minutes: Int?): Int = if (minutes != null && minutes >= 13) 15 else 10

    /**
     * The old setting was a radius in miles (0.5, 1, 2, 5). The new one is never narrower in reach than it was asked
     * to be: a walking-sized radius (under three quarters of a mile) becomes a 10 minute walk, anything larger a 10
     * minute drive, which already reaches about three miles in a straight line.
     */
    fun fromLegacyMiles(miles: Double?): TravelBudget = when {
        miles == null || miles <= 0.0 -> TravelBudget.DEFAULT
        miles < 0.75 -> TravelBudget(10, TravelMode.WALK)
        miles <= 3.5 -> TravelBudget(10, TravelMode.DRIVE)
        else -> TravelBudget(15, TravelMode.DRIVE)
    }

    fun metersToMiles(meters: Double): Double = meters / 1609.344
}
