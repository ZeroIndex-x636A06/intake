package com.zeroindex.intake.domain.units

import kotlin.math.roundToInt

/**
 * SPEC §3.5: weights are stored in kilograms and lengths in centimetres, always.
 * Display units are a setting and conversion happens at the edge - here - so no
 * imperial number is ever written to the database.
 */
object UnitConverter {

    const val KG_PER_LB = 0.45359237
    const val CM_PER_INCH = 2.54

    /** 7700 kcal per kg of bodyweight, the figure SPEC §8 uses for the TDEE maths. */
    const val KCAL_PER_KG = 7700.0

    fun lbToKg(pounds: Double): Double = pounds * KG_PER_LB

    fun kgToLb(kilograms: Double): Double = kilograms / KG_PER_LB

    fun inchesToCm(inches: Double): Double = inches * CM_PER_INCH

    fun cmToInches(centimetres: Double): Double = centimetres / CM_PER_INCH

    /** Rounds to [decimals] places, away from zero on a tie. */
    fun round(value: Double, decimals: Int): Double {
        require(decimals >= 0) { "decimals must not be negative, was $decimals" }
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return (value * factor).roundToInt() / factor
    }
}

/** The unit a weight is shown in. SPEC §3.5 defaults the display to pounds. */
enum class WeightUnit(val label: String) {
    KILOGRAMS("kg"),
    POUNDS("lb"),
    ;

    fun fromKg(kilograms: Double): Double = when (this) {
        KILOGRAMS -> kilograms
        POUNDS -> UnitConverter.kgToLb(kilograms)
    }

    fun toKg(value: Double): Double = when (this) {
        KILOGRAMS -> value
        POUNDS -> UnitConverter.lbToKg(value)
    }
}

/** The unit a body measurement is shown in. SPEC §3.5 defaults to inches. */
enum class LengthUnit(val label: String) {
    CENTIMETRES("cm"),
    INCHES("in"),
    ;

    fun fromCm(centimetres: Double): Double = when (this) {
        CENTIMETRES -> centimetres
        INCHES -> UnitConverter.cmToInches(centimetres)
    }

    fun toCm(value: Double): Double = when (this) {
        CENTIMETRES -> value
        INCHES -> UnitConverter.inchesToCm(value)
    }
}
