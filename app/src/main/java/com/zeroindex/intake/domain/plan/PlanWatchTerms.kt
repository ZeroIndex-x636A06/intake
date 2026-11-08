package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.allergen.HiddenCarriers

/**
 * Turns the imported plan's own "Watch for" strings (PlanMeta.hiddenDairyChecklist)
 * into extra "Check the label" terms - conservatively.
 *
 * Free-text checklist lines such as "Flour tortillas and hamburger, hot dog and
 * hoagie rolls" cannot be split into nouns reliably ("hamburger" alone would flag
 * every burger patty). So the only thing extracted is a phrase the author put in
 * quotation marks, e.g. `Anything labeled "natural flavoring" on pre-seasoned meat`.
 * Everything else is shown beside the item instead (the Plan tab's checklist). The
 * built-in [HiddenCarriers] list already covers the common unquoted items.
 */
object PlanWatchTerms {
    private val QUOTED = Regex("[\"\\u201C]([^\"\\u201C\\u201D]{4,40})[\"\\u201D]")
    private val WORDS_ONLY = Regex("[a-z][a-z' -]*[a-z]")
    private val SPACES = Regex("\\s+")

    /** Quoted phrases that read as a food, not as a negation or a bare allergen word. */
    fun extract(checklist: List<String>, allergen: String = HiddenCarriers.DAIRY): List<CheckLabelTerm> =
        checklist.flatMap { line ->
            QUOTED.findAll(line).mapNotNull { m ->
                val phrase = m.groupValues[1].trim().lowercase().replace(SPACES, " ")
                when {
                    !WORDS_ONLY.matches(phrase) -> null
                    phrase.split(' ').size > 4 -> null
                    // "non-dairy", "dairy free" and the like are already stronger matches.
                    phrase.startsWith("non") || phrase.startsWith("no ") || phrase.endsWith(" free") -> null
                    else -> CheckLabelTerm(
                        allergen = allergen,
                        phrase = phrase,
                        reason = "On your plan's watch-for list: ${line.trim().take(90)}",
                    )
                }
            }.toList()
        }.distinctBy { it.phrase }
}
