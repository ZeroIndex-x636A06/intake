package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.data.db.entity.EatOutOptionEntity

/**
 * "Can't eat what I cooked" (SPEC §9): which EatOutOption rows to offer for the
 * current week.
 *
 * The rule, in full:
 *  1. Options tied to this week (planWeekId equals the week's id) always show.
 *     The plan's author put them there for this week.
 *  2. Options for every week (null planWeekId) show when they are about the
 *     same protein as the week's cooked dish, or when they name no protein at
 *     all (a general option like "Garden salad, no cheese").
 *  3. A null-week option that names a different protein only (e.g. "Steak
 *     burrito bowl" in a chicken week) is left out: it would not stand in for
 *     the dish that went wrong.
 *  4. Options tied to other weeks never show.
 *  5. With includeAll (the sheet's "Show all options"), rule 3's hidden options
 *     are appended last. A plan whose options are all null-week, such as a
 *     single list of chains, would otherwise lose most venues in most weeks.
 *
 * "Names a protein" is read from venue, order wording and caution note against
 * a small vocabulary. Order: this week's own, then matching protein, then
 * general; each group alphabetical by venue.
 */
object EatOutFilter {

    /** Protein words -> the family they belong to, so "salmon" and "fish" agree. */
    private val FAMILIES: Map<String, String> = buildMap {
        fun family(name: String, vararg words: String) = words.forEach { put(it, name) }
        family("chicken", "chicken", "poultry")
        family("beef", "beef", "steak", "brisket", "barbacoa", "burger", "carne")
        family("pork", "pork", "bacon", "ham", "carnitas", "sausage")
        family("turkey", "turkey")
        family("fish", "fish", "salmon", "tuna", "cod", "tilapia", "halibut", "trout", "seafood")
        family("shrimp", "shrimp", "prawn", "prawns")
        family("lamb", "lamb", "mutton")
        family("egg", "egg", "eggs")
        family("tofu", "tofu", "tempeh", "seitan")
        family("beans", "beans", "lentils", "chickpeas", "falafel")
    }

    /** [OTHER] only appears when the caller asks for everything ("Show all options"). */
    enum class Match { THIS_WEEK, PROTEIN, GENERAL, OTHER }

    data class Result(val option: EatOutOptionEntity, val match: Match)

    fun forWeek(
        options: List<EatOutOptionEntity>,
        planWeekId: String?,
        proteinType: String?,
        includeAll: Boolean = false,
    ): List<Result> {
        val weekFamilies = familiesIn(proteinType.orEmpty())
        val weekWords = proteinType.orEmpty().lowercase().words()

        val results = options.filter { it.deletedAt == null }.mapNotNull { option ->
            when {
                planWeekId != null && option.planWeekId == planWeekId -> Result(option, Match.THIS_WEEK)
                option.planWeekId != null -> null
                else -> {
                    val text = "${option.venueName} ${option.whatToOrder} ${option.cautionNote}"
                    val named = familiesIn(text)
                    when {
                        named.isEmpty() -> Result(option, Match.GENERAL)
                        weekFamilies.isNotEmpty() && named.any { it in weekFamilies } ->
                            Result(option, Match.PROTEIN)
                        // A protein with no family entry: fall back to the week's literal words.
                        weekFamilies.isEmpty() && weekWords.isNotEmpty() &&
                            text.lowercase().words().any { it in weekWords } -> Result(option, Match.PROTEIN)
                        else -> if (includeAll) Result(option, Match.OTHER) else null
                    }
                }
            }
        }
        return results.sortedWith(
            compareBy<Result>({ it.match.ordinal }, { it.option.venueName.lowercase() }),
        )
    }

    private fun familiesIn(text: String): Set<String> =
        text.lowercase().words().mapNotNull { FAMILIES[it] }.toSet()

    private fun String.words(): Set<String> =
        split(Regex("[^a-z]+")).filter { it.length > 2 }.toSet()
}
