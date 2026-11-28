package com.zeroindex.intake.domain.training

import com.zeroindex.intake.domain.units.WeightText
import com.zeroindex.intake.domain.units.WeightUnit

/**
 * The words and numbers of a set row, in one place so every row of a session agrees.
 *
 * A bodyweight exercise is stored with 0 kg for the body and any added load in kg. It reads "BW" when nothing is added
 * and "+10 lb" when something is, never "0 lb". Every other weight is one decimal ("50.0") through [WeightText.setRow].
 */
object SetRowText {

    /** The marker for a bodyweight set with nothing added. */
    const val BODYWEIGHT = "BW"

    /** The weight of a set: "50.0" for a barbell, "BW" or "+10 lb" for a bodyweight exercise. */
    fun load(kg: Double, bodyweight: Boolean, unit: WeightUnit): String = when {
        !bodyweight -> WeightText.setRow(kg, unit)
        kg <= 0.0 -> BODYWEIGHT
        else -> "+" + WeightText.format(kg, unit)
    }

    /** The Previous cell: "50.0 x 8", "BW x 10" or "+10 lb x 8". */
    fun previous(kg: Double, reps: Int, bodyweight: Boolean, unit: WeightUnit): String =
        "${load(kg, bodyweight, unit)} × $reps"

    /**
     * The hint under an exercise that has no progression rule. It must agree with the Previous column: a lift with
     * history is never told to "pick a working weight".
     */
    fun noRuleReason(topKg: Double, hasHistory: Boolean, bodyweight: Boolean): String = when {
        bodyweight && hasHistory ->
            "Bodyweight: same reps as last time. Attach a progression rule and this will move on its own."
        bodyweight -> "Bodyweight: log the reps you do, and add weight only if you hold some."
        topKg > 0 || hasHistory ->
            "Same as last time. Attach a progression rule and this will move on its own."
        else -> "No rule and no history yet - pick a working weight."
    }
}
