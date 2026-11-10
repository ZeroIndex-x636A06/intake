package com.zeroindex.intake.domain.swap

import com.zeroindex.intake.domain.nutrition.Macros
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a swap option comes from; the sheet groups by these. */
enum class SwapKind { PLAN_RECIPE, SAVED_MEAL, SAVED_EAT_OUT, NEARBY }

/**
 * An option's allergen verdict, worst last so a larger ordinal is always a stronger warning. Only the local engine sets
 * [CONTAINS]; a model's claim can raise this (see [SwapMath.raise]) and never lower it.
 */
enum class SwapAllergen { CLEAR, CHECK_LABEL, MAY_CONTAIN, CONTAINS }

/** One thing you could eat instead of the planned meal. */
data class SwapOption(
    val key: String,
    val title: String,
    val subtitle: String?,
    val kind: SwapKind,
    val macros: Macros,
    /** True for numbers a model or a restaurant menu gave: shown as an estimate. */
    val estimate: Boolean,
    val allergen: SwapAllergen,
    /** Why it is flagged ("Contains sesame"), or null when clear. */
    val allergenReason: String? = null,
) {
    /** A "contains" option is shown but cannot be chosen. */
    val blocked: Boolean get() = SwapMath.isBlocked(allergen)
}

/** Whole-number differences against the planned meal: positive means the option has more. */
data class SwapDelta(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)

object SwapMath {

    fun delta(option: Macros, planned: Macros): SwapDelta = SwapDelta(
        kcal = (option.kcal - planned.kcal).roundToInt(),
        protein = (option.protein - planned.protein).roundToInt(),
        carbs = (option.carbs - planned.carbs).roundToInt(),
        fat = (option.fat - planned.fat).roundToInt(),
    )

    /** "+120 kcal, −8 g protein": the headline pair the sheet shows. Zero reads "same". */
    fun label(d: SwapDelta): String = "${signed(d.kcal)} kcal, ${signed(d.protein)} g protein"

    /** Separate chips: "+120 kcal", "−8 g protein". */
    fun chips(d: SwapDelta): List<String> = listOf("${signed(d.kcal)} kcal", "${signed(d.protein)} g protein")

    private fun signed(v: Int): String = when {
        v > 0 -> "+$v"
        v < 0 -> "−${abs(v)}"
        else -> "±0"
    }

    /** Only a "contains" verdict blocks; the sheet shows it greyed with its reason. */
    fun isBlocked(allergen: SwapAllergen): Boolean = allergen == SwapAllergen.CONTAINS

    /** The stronger of two verdicts: a model can add a warning but can never clear the local one. */
    fun raise(local: SwapAllergen, claimed: SwapAllergen): SwapAllergen = if (claimed.ordinal > local.ordinal) claimed else local

    /** Reads an allergen scan's three tiers into one verdict. */
    fun verdict(contains: Boolean, mayContain: Boolean, checkLabel: Boolean): SwapAllergen = when {
        contains -> SwapAllergen.CONTAINS
        mayContain -> SwapAllergen.MAY_CONTAIN
        checkLabel -> SwapAllergen.CHECK_LABEL
        else -> SwapAllergen.CLEAR
    }

    /** Choosable options first, closest to the planned calories first; blocked ones last, in the same order. */
    fun order(options: List<SwapOption>, planned: Macros): List<SwapOption> =
        options.sortedWith(
            compareBy<SwapOption> { it.blocked }
                .thenBy { abs(it.macros.kcal - planned.kcal) }
                .thenBy { it.title.lowercase() },
        )
}
