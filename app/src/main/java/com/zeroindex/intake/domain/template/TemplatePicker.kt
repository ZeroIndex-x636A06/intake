package com.zeroindex.intake.domain.template

import com.zeroindex.intake.domain.recipe.RecipeSearch

/** One saved food or recipe the meal template editor can add. [lastUsedAt] is when it was last logged, if ever. */
data class PickItem(
    val kind: Kind,
    val id: String,
    val name: String,
    /** The second line ("120 kcal per 100 g · 1 slice"). Built by the caller, so an unknown number reads "not listed". */
    val detail: String,
    val lastUsedAt: Long? = null,
    /** Extra words a typed search also matches (a brand). Never shown. */
    val searchText: String = "",
) {
    enum class Kind { FOOD, RECIPE }

    /** The key a list row uses: a food and a recipe never collide. */
    val key: String get() = "${kind.name}:$id"
}

/** What the editor's picker lists: [recent] (may be empty, then it is not shown) and [allSaved]. */
data class PickLists(val recent: List<PickItem>, val allSaved: List<PickItem>)

/**
 * The lists behind the meal template editor's "Add a food or recipe" picker. Pure and offline.
 *
 * With nothing typed: "Recent" (what was logged lately, latest first, foods and recipes together) and then "All saved" (every
 * saved food and recipe, alphabetical). With no recents only "All saved" shows. Typed words filter both lists, best match first.
 * The offline USDA results are added by the caller, after these.
 */
object TemplatePicker {

    const val RECENT_LIMIT = 8

    fun build(query: String, saved: List<PickItem>, recentLimit: Int = RECENT_LIMIT): PickLists {
        val q = query.trim()
        val alphabetical = saved.sortedWith(compareBy<PickItem> { it.name.lowercase() }.thenBy { it.kind }.thenBy { it.id })
        val recentAll = saved.filter { it.lastUsedAt != null }
            .sortedWith(compareByDescending<PickItem> { it.lastUsedAt }.thenBy { it.name.lowercase() }.thenBy { it.id })
        if (q.isEmpty()) return PickLists(recentAll.take(recentLimit), alphabetical)

        fun matches(items: List<PickItem>): List<PickItem> = items
            .mapNotNull { item -> score(q, item)?.let { it to item } }
            .sortedWith(compareBy<Pair<Int, PickItem>> { it.first }.thenBy { it.second.name.lowercase() }.thenBy { it.second.id })
            .map { it.second }
        // Recent is filtered from the same recent window the empty search shows, so typing never promotes an old item into it.
        return PickLists(matches(recentAll.take(recentLimit)), matches(alphabetical))
    }

    private fun score(query: String, item: PickItem): Int? =
        RecipeSearch.score(query, item.name)
            ?: item.searchText.takeIf { it.isNotBlank() }?.let { RecipeSearch.score(query, "${item.name} $it") }?.plus(10)
}
