package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.units.WeightUnit

/**
 * The bar and plates a gym has depend on the unit it works in: a 20 kg bar with 25/20/15/10/5/2.5/1.25 kg plates,
 * or a 45 lb bar with 45/35/25/10/5/2.5 lb plates. These are the defaults until the person sets their own, so
 * the plate maths and the smallest step up match what is on the rack. Everything is still stored in kg.
 */
object PlateDefaults {
    const val KG_PLATES = "25,20,15,10,5,2.5,1.25"
    private val LB_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)
    private const val LB_BAR = 45.0
    private const val KG_BAR = 20.0

    fun barKg(unit: WeightUnit): Double = when (unit) {
        WeightUnit.KILOGRAMS -> KG_BAR
        WeightUnit.POUNDS -> unit.toKg(LB_BAR)
    }

    /** Comma-joined plate weights in kg, as the setting stores them. */
    fun platesCsvKg(unit: WeightUnit): String = when (unit) {
        WeightUnit.KILOGRAMS -> KG_PLATES
        WeightUnit.POUNDS -> LB_PLATES.joinToString(",") { unit.toKg(it).toString() }
    }

    /** The stored kg list written in the person's unit: "45,35,25,10,5,2.5". */
    fun toDisplayCsv(csvKg: String, unit: WeightUnit): String =
        parse(csvKg).joinToString(",") { number(unit.fromKg(it)) }

    /** What the person typed in their unit, back to the stored kg list. */
    fun fromDisplayCsv(text: String, unit: WeightUnit): String =
        parse(text).joinToString(",") { if (unit == WeightUnit.KILOGRAMS) number(it) else unit.toKg(it).toString() }

    private fun parse(csv: String): List<Double> = csv.split(',').mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }

    /** Two decimals at most, without trailing zeros, so 20.4117 kg reads as 45 lb and 1.25 stays 1.25. */
    private fun number(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
    }
}
