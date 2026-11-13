package com.zeroindex.intake.domain.swap

import com.zeroindex.intake.domain.nutrition.Macros

/** One section of the Swap sheet, in the order the sheet shows them. */
data class SwapSection(val kind: SwapKind, val title: String, val options: List<SwapOption>)

/**
 * Groups swap options into the sheet's sections: Plan and recipes, Meal templates, Saved eat-out options (they work
 * offline) and the nearby results. Inside a section, choosable options come first, nearest to the planned calories
 * first, and blocked ones (a "contains" verdict) last. The first three sections are always returned so the sheet can say
 * what is missing; NEARBY is returned only when it has results.
 */
object SwapSections {
    private val titles = mapOf(
        SwapKind.PLAN_RECIPE to "Plan & recipes",
        SwapKind.SAVED_MEAL to "Meal templates",
        SwapKind.SAVED_EAT_OUT to "Saved eat-out options",
        SwapKind.NEARBY to "Found nearby",
    )

    fun group(options: List<SwapOption>, planned: Macros): List<SwapSection> {
        val byKind = options.groupBy { it.kind }
        return SwapKind.entries
            .filter { it != SwapKind.NEARBY || byKind[it].orEmpty().isNotEmpty() }
            .map { k -> SwapSection(k, titles.getValue(k), SwapMath.order(byKind[k].orEmpty(), planned)) }
    }

    /** What a tap on an option does: only an option that is not blocked can be chosen. */
    fun canChoose(option: SwapOption): Boolean = !option.blocked

    /** The line under a blocked option: why it cannot be chosen. */
    fun blockedReason(option: SwapOption): String? =
        if (option.blocked) option.allergenReason ?: "Contains an allergen you avoid" else null
}
