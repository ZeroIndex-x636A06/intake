package com.zeroindex.intake.ui.screens.plan

import com.zeroindex.intake.data.db.entity.AllergenStatus

/**
 * The quiet MENTIONED_AVOIDED verdict from the allergen engine: a recipe or plan item names an allergen
 * only to say it is left out ("dairy-free", "no peanuts"). It is not a warning and never blocks anything,
 * but it is shown instead of a bare "Passed" so the person can see why the words matched.
 *
 * Pure so the copy is unit-tested. Warnings (CONTAINS, MAY_CONTAIN, CHECK_LABEL) are untouched by this.
 */
object MentionedAvoided {
    const val PILL = "Mentioned as avoided"

    fun applies(status: AllergenStatus?): Boolean = status == AllergenStatus.MENTIONED_AVOIDED

    /** One or two sentences: what it means and what to do. */
    fun note(terms: List<String>): String {
        val named = terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val what = if (named.isEmpty()) "something you avoid" else named.joinToString(", ")
        return "This names $what only to say it is left out, for example \"dairy-free\". " +
            "That is not a warning. Read the label if you are unsure."
    }
}
