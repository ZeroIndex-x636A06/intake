package com.zeroindex.intake.domain.drinks

import com.zeroindex.intake.domain.units.VolumeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** One quick size button on the Drinks page: a label ("12 fl oz can") and the volume it logs. */
data class DrinkSize(val label: String, val ml: Double)

/**
 * The quick size buttons. The volume unit in Settings picks the default set (US: a glass, a can and two bottles;
 * metric: 250, 330, 500, 750 ml). Sizes the person has added in Settings > Water and drinks > Quick sizes join them
 * when they are not the same amount, so the list they edit there is the list they see here. "Custom size" is added
 * by the screen.
 */
object DrinkSizes {

    fun defaults(unit: VolumeUnit): List<DrinkSize> = when (unit) {
        VolumeUnit.US_FLUID_OUNCES -> listOf(
            DrinkSize("8 fl oz glass", VolumeUnit.US_FLUID_OUNCES.toMl(8.0)),
            DrinkSize("12 fl oz can", VolumeUnit.US_FLUID_OUNCES.toMl(12.0)),
            DrinkSize("16.9 fl oz bottle", 500.0),
            DrinkSize("20 fl oz bottle", VolumeUnit.US_FLUID_OUNCES.toMl(20.0)),
        )
        VolumeUnit.MILLILITRES -> listOf(
            DrinkSize("250 ml", 250.0),
            DrinkSize("330 ml", 330.0),
            DrinkSize("500 ml", 500.0),
            DrinkSize("750 ml", 750.0),
        )
    }

    /** The defaults plus any saved quick size whose volume is not already there (within 2 ml), smallest first. */
    fun buttons(unit: VolumeUnit, saved: List<DrinkSize>): List<DrinkSize> {
        val out = defaults(unit).toMutableList()
        saved.filter { it.ml > 0 }.forEach { s ->
            if (out.none { abs(it.ml - s.ml) < 2.0 }) out += s
        }
        return out.sortedBy { it.ml }
    }

    /** Rounds a typed amount in the person's unit to whole millilitres of stored volume (so 12 fl oz is 355 ml). */
    fun toMl(unit: VolumeUnit, typed: Double): Double = unit.toMl(typed).roundToInt().toDouble().coerceAtLeast(1.0)
}
