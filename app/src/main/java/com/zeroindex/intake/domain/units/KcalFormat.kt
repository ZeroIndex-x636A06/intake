package com.zeroindex.intake.domain.units

import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * The one way a kcal figure is written on screen: grouped thousands in the device locale ("2,393"), never "2393"
 * (QA Q1-19). Every kcal display goes through here so the rings, cards and labels agree.
 */
object KcalFormat {
    fun format(kcal: Int): String = NumberFormat.getIntegerInstance().format(kcal)

    fun format(kcal: Double): String = format(kcal.roundToInt())
}
