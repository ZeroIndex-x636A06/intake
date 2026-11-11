package com.zeroindex.intake.domain.menu

/**
 * The allergens a restaurant's own chart states for one item: "Contains: Milk, Egg, Wheat; May contain: Sesame".
 *
 * They reach the allergen engine as structured "contains" and "may contain" inputs. A stated list can raise a verdict
 * and can never clear one: an item with no list is simply an item the chart says nothing about.
 */
data class StatedAllergens(val contains: List<String>, val mayContain: List<String>) {
    val isEmpty: Boolean get() = contains.isEmpty() && mayContain.isEmpty()

    /** The text stored with the menu item, read back by [parse] when the item is scanned again. */
    fun toText(): String = buildList {
        if (contains.isNotEmpty()) add("Contains: " + contains.joinToString(", "))
        if (mayContain.isNotEmpty()) add("May contain: " + mayContain.joinToString(", "))
    }.joinToString("; ")

    companion object {
        val NONE = StatedAllergens(emptyList(), emptyList())

        private val CONTAINS = Regex("(?i)\\bcontains\\b\\s*:?")
        private val MAY_CONTAIN = Regex("(?i)\\bmay\\s+contain\\b\\s*:?")
        private val SHARED_FRYER = Regex("†")

        /**
         * Reads the "Contains:" and "May contain:" clauses out of [text], wherever they sit in it. A "†" list (the
         * item is cooked in the same oil as items that contain these) counts as "may contain". Words that only
         * qualify an allergen ("(where available)") are dropped.
         */
        fun parse(text: String): StatedAllergens {
            if (text.isBlank()) return NONE
            val contains = LinkedHashSet<String>()
            val may = LinkedHashSet<String>()

            // Cut the text at every marker; each piece belongs to the marker before it.
            data class Mark(val start: Int, val end: Int, val kind: Int) // 0 contains, 1 may contain, 2 dagger
            val marks = buildList {
                CONTAINS.findAll(text).forEach { m ->
                    // "May contain:" also holds the word "contain:" but not "contains:"; the regexes do not overlap.
                    add(Mark(m.range.first, m.range.last + 1, 0))
                }
                MAY_CONTAIN.findAll(text).forEach { add(Mark(it.range.first, it.range.last + 1, 1)) }
                SHARED_FRYER.findAll(text).forEach { add(Mark(it.range.first, it.range.last + 1, 2)) }
            }.sortedBy { it.start }

            marks.forEachIndexed { i, mark ->
                val stop = marks.getOrNull(i + 1)?.start ?: text.length
                // A ";" ends a clause: "Contains: Milk; May contain: Sesame".
                val body = text.substring(mark.end, stop).substringBefore(';')
                val into = if (mark.kind == 0) contains else may
                splitNames(body).forEach { (name, footnoted) -> if (footnoted) may += name else into += name }
            }
            // Contains wins over may-contain for the same name.
            may.removeAll { m -> contains.any { it.equals(m, ignoreCase = true) } }
            return StatedAllergens(contains.toList(), may.toList())
        }

        /**
         * The names in one clause. A name marked "**" is a footnote ("Buttered Buns available at some locations"):
         * it is returned flagged, and the caller files it as "may contain".
         */
        private fun splitNames(body: String): List<Pair<String, Boolean>> = body
            .replace(Regex("\\(.*?\\)"), "")
            .replace("**", ", **")
            .split(',', '/', '&')
            .flatMap { it.split(Regex("\\s+and\\s+")) }
            .map { it.trim() }
            .map { raw -> raw.trim('.', '*', '†', '•').trim() to raw.startsWith("**") }
            .filter { (n, _) -> n.isNotEmpty() && n.length <= 40 && n.any(Char::isLetter) && n != "u" }
            .map { (n, f) -> n.lowercase().replaceFirstChar(Char::titlecase) to f }
    }
}
