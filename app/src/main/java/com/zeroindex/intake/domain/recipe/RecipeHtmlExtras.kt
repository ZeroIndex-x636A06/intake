package com.zeroindex.intake.domain.recipe

/** One ingredient group read from a recipe card's HTML. [name] is null for an unnamed group. */
data class HtmlIngredientGroup(val name: String?, val ingredients: List<String>)

/** What the recipe card's HTML has that the page's JSON-LD does not. */
data class HtmlExtras(
    val notes: String? = null,
    val groups: List<HtmlIngredientGroup> = emptyList(),
)

/**
 * Reads the two things recipe plugins keep out of their JSON-LD, straight from the page's HTML, without a
 * DOM library and without a network:
 *
 * - the Notes section: WP Recipe Maker's `wprm-recipe-notes`, Tasty Recipes' `tasty-recipes-notes-body`;
 * - ingredient groups ("For the Sauce"): WP Recipe Maker's `wprm-recipe-ingredient-group` blocks with a
 *   `wprm-recipe-group-name` header, and Tasty Recipes' `tasty-recipes-ingredients` with h4 headers.
 *
 * Deterministic and total: markup it does not recognise gives an empty result.
 */
object RecipeHtmlExtras {
    private val TAG = Regex("""<([a-zA-Z][a-zA-Z0-9]*)\b((?:[^>"']|"[^"]*"|'[^']*')*)>""")
    private val CLASS = Regex("""(?<![\w-])class\s*=\s*(?:"([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)
    private val NOISE = Regex(
        """<(script|style|noscript|svg|template)\b.*?</\1\s*>|<!--.*?-->""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HEADING = Regex("""<(h[1-6])\b[^>]*>.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BLOCK_SPAN = Regex(
        """<span\b[^>]*display\s*:\s*block[^>]*>.*?</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TASTY_ITEM = Regex(
        """<(h[3-6])\b[^>]*>(.*?)</\1\s*>|<li\b[^>]*>(.*?)</li\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** An element found by class: where it starts and ends in the text, and the markup between. */
    internal class Element(val tag: String, val outerStart: Int, val innerStart: Int, val innerEnd: Int, val outerEnd: Int, val text: String) {
        val inner: String get() = text.substring(innerStart, innerEnd)
    }

    fun read(html: String): HtmlExtras {
        val body = NOISE.replace(html, " ")
        return HtmlExtras(notes = notes(body), groups = wprmGroups(body).ifEmpty { tastyGroups(body) })
    }

    // ---- element search ----------------------------------------------------------------------

    private val closers = HashMap<String, Regex>()

    private fun closer(tag: String): Regex =
        closers.getOrPut(tag.lowercase()) { Regex("""<(/?)${Regex.escape(tag)}\b[^>]*>""", RegexOption.IGNORE_CASE) }

    private fun closeOf(tag: String, from: Int, text: String): Pair<Int, Int> {
        val re = closer(tag)
        var depth = 1
        var pos = from
        while (pos <= text.length) {
            val m = re.find(text, pos) ?: return text.length to text.length
            if (m.groupValues[1] == "/") depth-- else if (!m.value.endsWith("/>")) depth++
            if (depth == 0) return m.range.first to (m.range.last + 1)
            pos = m.range.last + 1
        }
        return text.length to text.length
    }

    /** Every element in [text] whose class list has [token] as a whole word, in document order. */
    internal fun withClass(text: String, token: String): List<Element> {
        val out = ArrayList<Element>()
        var skipUntil = -1
        for (m in TAG.findAll(text)) {
            if (m.range.first < skipUntil) continue
            val attrs = m.groupValues[2]
            val classes = CLASS.find(attrs)?.let { it.groups[1]?.value ?: it.groups[2]?.value } ?: continue
            if (classes.split(Regex("""\s+""")).none { it == token }) continue
            val tag = m.groupValues[1]
            if (attrs.trimEnd().endsWith("/")) continue
            val (innerEnd, outerEnd) = closeOf(tag, m.range.last + 1, text)
            out += Element(tag, m.range.first, m.range.last + 1, innerEnd, outerEnd, text)
            // A match inside the same element (a nested copy) is not a second one.
            skipUntil = outerEnd
        }
        return out
    }

    internal fun first(text: String, token: String): Element? = withClass(text, token).firstOrNull()

    /** [markup] with every element of the given classes cut out (checkboxes and screen-reader text). */
    private fun without(markup: String, vararg tokens: String): String {
        var t = markup
        for (token in tokens) {
            val cuts = withClass(t, token)
            for (e in cuts.asReversed()) t = t.removeRange(e.outerStart, e.outerEnd.coerceAtMost(t.length))
        }
        return t
    }

    // ---- notes -------------------------------------------------------------------------------

    private fun notes(body: String): String? {
        val inner = first(body, "wprm-recipe-notes")?.inner
            ?: first(body, "tasty-recipes-notes-body")?.inner
            ?: first(body, "tasty-recipes-notes")?.inner?.let { HEADING.replace(it, " ") }
            ?: return null
        // WP Recipe Maker writes each note as a display:block span, which has no line break of its own.
        val paragraphs = BLOCK_SPAN.replace(inner) { it.value + "<br>" }
        return HtmlText.plain(paragraphs, keepLines = true).takeIf { it.isNotBlank() }
    }

    // ---- groups ------------------------------------------------------------------------------

    internal fun wprmGroups(body: String): List<HtmlIngredientGroup> =
        withClass(body, "wprm-recipe-ingredient-group").map { group ->
            val inner = group.inner
            val name = first(inner, "wprm-recipe-group-name")?.let { IngredientCleaner.cleanLabel(it.inner) }
            val lines = withClass(inner, "wprm-recipe-ingredient").mapNotNull { wprmLine(it.inner) }
            HtmlIngredientGroup(name, lines)
        }.filter { it.ingredients.isNotEmpty() }

    /** "16 ounces dry fettuccine pasta", from the amount, unit and name spans, with a note in brackets. */
    internal fun wprmLine(li: String): String? {
        fun part(token: String) = first(li, token)?.let { HtmlText.plain(it.inner) }.orEmpty()
        val name = part("wprm-recipe-ingredient-name")
        val text = if (name.isNotEmpty()) {
            val head = listOf(part("wprm-recipe-ingredient-amount"), part("wprm-recipe-ingredient-unit"), name)
                .filter { it.isNotEmpty() }.joinToString(" ")
            val note = part("wprm-recipe-ingredient-notes").trim().trimStart(',').trim()
            if (note.isEmpty()) head else "$head ($note)"
        } else {
            HtmlText.plain(without(li, "wprm-checkbox-container"))
        }
        return IngredientCleaner.clean(text).takeIf { it.isNotEmpty() }
    }

    internal fun tastyGroups(body: String): List<HtmlIngredientGroup> {
        val container = first(body, "tasty-recipes-ingredients") ?: return emptyList()
        val scope = first(container.inner, "tasty-recipes-ingredients-body")?.inner ?: container.inner
        val groups = ArrayList<HtmlIngredientGroup>()
        var name: String? = null
        var lines = ArrayList<String>()
        fun flush() {
            if (lines.isNotEmpty()) groups += HtmlIngredientGroup(name, lines)
            lines = ArrayList()
        }
        for (m in TASTY_ITEM.findAll(scope)) {
            if (m.groups[1] != null) {
                val label = IngredientCleaner.cleanLabel(m.groupValues[2])
                // The card's own "Ingredients" title is not a group.
                if (label.equals("Ingredients", ignoreCase = true)) continue
                flush()
                name = label
            } else {
                IngredientCleaner.clean(HtmlText.plain(m.groupValues[3])).takeIf { it.isNotEmpty() }?.let { lines += it }
            }
        }
        flush()
        return groups
    }

    // ---- matching the groups to the structured list --------------------------------------------

    private fun key(line: String): String =
        IngredientLineParser.normalise(IngredientCleaner.clean(line)).lowercase().filter { it.isLetterOrDigit() }

    /**
     * The group of each line in [ingredients] (the JSON-LD list), found by matching its text against the
     * HTML's lines. If every line is matched by text, or both lists have the same length, the labels are
     * used; otherwise the groups are not trusted and every label is null. Null also when no group has a name.
     */
    fun labelsFor(ingredients: List<String>, groups: List<HtmlIngredientGroup>): List<String?> {
        val none = List<String?>(ingredients.size) { null }
        if (groups.none { it.name != null }) return none
        val flat = groups.flatMap { g -> g.ingredients.map { g.name to it } }
        val used = BooleanArray(flat.size)
        val byText = ingredients.map { line ->
            val k = key(line)
            val i = flat.indices.firstOrNull { !used[it] && key(flat[it].second) == k }
                ?: flat.indices.firstOrNull { !used[it] && k.length >= 6 && key(flat[it].second).let { o -> o.length >= 6 && (o.contains(k) || k.contains(o)) } }
            if (i != null) used[i] = true
            i
        }
        if (byText.all { it != null }) return byText.map { flat[it!!].first }
        if (flat.size == ingredients.size) return flat.map { it.first }
        return none
    }
}
