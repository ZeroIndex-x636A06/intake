package com.zeroindex.intake.domain.recipe

import kotlin.math.roundToInt

/** A saved recipe as Log food sees it: per-serving numbers that may each be unknown (null), never zero. */
data class RecipeCandidate(
    val id: String,
    val name: String,
    val kcal: Double?,
    val protein: Double?,
    /** When a serving of it was last logged, if ever. Breaks ties between equally good name matches. */
    val lastLoggedAt: Long? = null,
    /** True when [kcal] and [protein] are worked out from the ingredient lines, not stored on the recipe. Shown with "~". */
    val estimated: Boolean = false,
    /** Why there is no calorie figure ("Could not match carrots, stock to foods"), shown under the row. Null when there is one. */
    val whyNoKcal: String? = null,
) {
    /** "420 kcal · 35 g protein", "~62 kcal" when estimated, or "kcal not listed" where the recipe has no number. Never "0". */
    val summary: String
        get() {
            val tilde = if (estimated) "~" else ""
            return (kcal?.let { "$tilde${it.roundToInt()} kcal" } ?: "kcal not listed") + " · " +
                (protein?.let { "$tilde${it.roundToInt()} g protein" } ?: "protein not listed")
        }

    /** A serving can be logged only with a calorie figure (the existing logServing rule), stored or estimated. */
    val loggable: Boolean get() = kcal != null

    companion object {
        /**
         * The reason a recipe has no calorie figure, naming what is actually missing: the ingredient lines the estimate
         * could not match to a food, or that the recipe has no ingredient lines at all.
         */
        fun reasonNoKcal(lineCount: Int, notCountedLines: List<String>): String = when {
            lineCount == 0 -> "No ingredients to estimate from. Add ingredients in Recipes."
            notCountedLines.isEmpty() -> "Could not estimate calories. Open it in Recipes to pick foods."
            else -> {
                val shown = notCountedLines.take(2).joinToString(", ")
                val more = if (notCountedLines.size > 2) " and ${notCountedLines.size - 2} more" else ""
                "No calories: could not match $shown$more to a food. Open it in Recipes to fix."
            }
        }
    }
}

/**
 * Saved recipes in Log food: ranked against the words typed, and listed in recents. Pure, so the order is tested
 * without a database. Offline and deterministic: no model is involved.
 */
object RecipeSearch {

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun key(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Lower is better, null means no match. Every typed word must match. Whole name beats name prefix beats word
     * prefix beats a word fragment.
     */
    internal fun score(query: String, name: String): Int? {
        val q = tokens(query)
        if (q.isEmpty()) return null
        val words = tokens(name)
        if (words.isEmpty()) return null
        val flat = words.joinToString(" ")
        val typed = q.joinToString(" ")
        if (flat == typed) return 0
        if (flat.startsWith(typed)) return 1
        var worst = 2
        for (t in q) {
            worst = maxOf(
                worst,
                when {
                    words.any { it.startsWith(t) } -> 2
                    words.any { it.contains(t) } -> 3
                    else -> return null
                },
            )
        }
        return worst
    }

    /**
     * Matches first by how well the name matches, then recently logged before never logged (latest first), then
     * shorter names, then alphabetically. A blank query matches nothing: recents and Mine list recipes instead.
     */
    fun rank(query: String, recipes: List<RecipeCandidate>): List<RecipeCandidate> =
        recipes.mapNotNull { r -> score(query, r.name)?.let { it to r } }
            .sortedWith(
                compareBy<Pair<Int, RecipeCandidate>> { it.first }
                    .thenByDescending { it.second.lastLoggedAt ?: Long.MIN_VALUE }
                    .thenBy { it.second.name.length }
                    .thenBy { it.second.name.lowercase() },
            )
            .map { it.second }

    /** A logged row with no food behind it (a recipe serving, a quick add): its name and when. */
    data class LoggedName(val name: String, val at: Long)

    private fun latestByName(logged: List<LoggedName>): Map<String, Long> {
        val latest = HashMap<String, Long>()
        logged.forEach { l -> latest.merge(key(l.name), l.at) { a, b -> maxOf(a, b) } }
        return latest
    }

    /**
     * Recipes whose servings were logged lately, latest first. A logged recipe serving keeps the recipe's name, so
     * names are compared ignoring case and spacing; a quick add that happens to share a name counts too, which is
     * harmless: the recipe really is what that name means.
     */
    fun recents(recipes: List<RecipeCandidate>, logged: List<LoggedName>, limit: Int = 5): List<RecipeCandidate> {
        val latest = latestByName(logged)
        return recipes.filter { key(it.name) in latest }
            .map { it.copy(lastLoggedAt = latest[key(it.name)]) }
            .sortedWith(compareByDescending<RecipeCandidate> { it.lastLoggedAt }.thenBy { it.name.lowercase() })
            .take(limit)
    }

    /** Recipes carrying their last-logged time, for [rank]'s tie-break. Same name matching as [recents]. */
    fun withLastLogged(recipes: List<RecipeCandidate>, logged: List<LoggedName>): List<RecipeCandidate> {
        val latest = latestByName(logged)
        return recipes.map { it.copy(lastLoggedAt = latest[key(it.name)]) }
    }
}
