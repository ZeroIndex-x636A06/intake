package com.zeroindex.intake.domain.training

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * How the person wants to rate the effort of a set: not at all, as reps in reserve (RIR), or as the 6 to 10 RPE scale.
 * Both scales describe the same thing and are stored the same way, as RPE in `set_log.rpe` (RPE = 10 - RIR), which is
 * also what the watch logs. Off is the default: nothing is asked and nothing changes for a person who never rates sets.
 */
enum class EffortScale(val label: String) {
    OFF("Off"),
    RIR("RIR"),
    RPE("RPE"),
    ;

    companion object {
        fun fromName(name: String?): EffortScale = entries.firstOrNull { it.name == name } ?: OFF
    }
}

/** How close to failure a set was. Drives the colour of the effort cell; the words always say the same thing. */
enum class EffortBand { EASY, MODERATE, HARD, VERY_HARD, MAX }

/** One choice in the picker: its stored RPE, the short cell text, and a plain line. */
data class EffortLevel(val rpe: Double, val short: String, val line: String, val band: EffortBand)

/** Pure effort maths and wording, shared by the set rows and anywhere else an RPE is shown. */
object Effort {

    private val levelsHardToEasy = listOf(
        Triple(10.0, "Nothing left in the tank", EffortBand.MAX),
        Triple(9.0, "1 left in the tank", EffortBand.VERY_HARD),
        Triple(8.0, "2 left in the tank", EffortBand.HARD),
        Triple(7.0, "3 left in the tank", EffortBand.MODERATE),
        Triple(6.0, "4 or more left, an easy set", EffortBand.EASY),
    )

    /** RPE for a number of reps in reserve: 10 minus the reps left, held to the 6 to 10 range the scale covers. */
    fun rpeFromRir(rir: Int): Double = (10 - rir).coerceIn(6, 10).toDouble()

    /** Reps in reserve for an RPE, nearest whole rep (a half-step RPE rounds towards the harder side). */
    fun rirFromRpe(rpe: Double): Int = floor(10.0 - rpe + 0.5 - 1e-9).toInt().coerceAtLeast(0)

    /** The choices for a scale, hardest first, or none when the scale is off. */
    fun levels(scale: EffortScale): List<EffortLevel> = when (scale) {
        EffortScale.OFF -> emptyList()
        else -> levelsHardToEasy.map { (rpe, line, band) -> EffortLevel(rpe, shortFor(rpe, scale), line, band) }
    }

    /** "RIR 2" or "RPE 8": the name of a level. RIR 4 reads "RIR 4+" because the scale stops there. */
    fun shortFor(rpe: Double, scale: EffortScale): String = when (scale) {
        EffortScale.OFF -> ""
        EffortScale.RPE -> "RPE ${number(rpe)}"
        EffortScale.RIR -> if (rpe <= 6.0) "RIR 4+" else "RIR ${rirFromRpe(rpe)}"
    }

    /** The compact text for the cell itself: just the number ("8", "2", "4+"). */
    fun cellText(rpe: Double, scale: EffortScale): String = when (scale) {
        EffortScale.OFF -> ""
        EffortScale.RPE -> number(rpe)
        EffortScale.RIR -> if (rpe <= 6.0) "4+" else rirFromRpe(rpe).toString()
    }

    /**
     * The plain-language line for a logged RPE, for any value the watch or an import could have stored, half steps
     * included: "2 left in the tank", "about 1 or 2 left in the tank".
     */
    fun line(rpe: Double): String {
        val exact = levelsHardToEasy.firstOrNull { abs(it.first - rpe) < 1e-9 }
        if (exact != null) return exact.second
        if (rpe >= 10.0) return levelsHardToEasy.first().second
        if (rpe <= 6.0) return levelsHardToEasy.last().second
        val low = floor(10.0 - rpe).toInt()
        return "about $low or ${low + 1} left in the tank"
    }

    fun band(rpe: Double): EffortBand = when {
        rpe >= 10.0 -> EffortBand.MAX
        rpe >= 9.0 -> EffortBand.VERY_HARD
        rpe >= 8.0 -> EffortBand.HARD
        rpe >= 7.0 -> EffortBand.MODERATE
        else -> EffortBand.EASY
    }

    /** "8.5", or "8" with no trailing ".0". */
    private fun number(rpe: Double): String =
        if (rpe == rpe.roundToInt().toDouble()) rpe.roundToInt().toString() else rpe.toString()
}
