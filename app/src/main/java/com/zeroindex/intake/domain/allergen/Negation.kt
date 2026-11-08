package com.zeroindex.intake.domain.allergen

import java.text.Normalizer

/**
 * Deterministic, clause-local reading of free text, used to tell "butter" from "not butter"
 * (docs/ALLERGENS.md). No model is involved and nothing here ever runs on a product's ingredient
 * panel: the matcher only asks for it on text a person wrote.
 *
 * All positions refer to the string returned by [normalize], never to the caller's original text.
 */
internal object Negation {

    /** Lower-case, accents folded, curly quotes straightened. Same length is NOT guaranteed. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('’', '\'')
            .replace('‘', '\'')
            .lowercase()

    /**
     * Words after which " and " starts a new clause: a verb or a subject. "skip the cheese and sour
     * cream" stays one clause (sour is not on this list), "no cheese and add bacon" splits.
     */
    private val NEW_CLAUSE_AFTER_AND = setOf(
        "add", "adding", "use", "using", "melt", "top", "serve", "serving", "make", "mix", "stir", "season",
        "cook", "bake", "fry", "grill", "pour", "sprinkle", "toss", "layer", "spread", "swap", "replace",
        "order", "get", "ask", "try", "keep", "leave", "drizzle", "finish", "have", "eat", "take", "heat",
        "simmer", "roast", "blend", "whisk", "fold", "combine", "load", "stuff", "saute", "brown", "boil",
        "steam", "drain", "rinse", "chop", "slice", "dice", "wrap", "enjoy", "pair", "dip", "squeeze",
        "garnish", "substitute", "skip", "hold", "omit", "avoid", "check", "choose", "pick", "build",
        "it", "they", "you", "we", "i", "he", "she", "this", "that", "there", "then", "also", "just",
    )

    /** Words that end the scope of a negator ("no cheese on the side" does not reach "side"). */
    private val SCOPE_ENDERS = setOf(
        "with", "using", "plus", "topped", "over", "on", "in", "into", "from", "because", "since", "so",
        "if", "when", "while", "unless", "which", "that", "where", "then", "but",
    )

    /** Words that do not count toward the scope window. */
    private val FILLER = setOf(
        "the", "a", "an", "any", "some", "all", "extra", "more", "your", "their", "its", "this", "that",
        "of", "and", "or", "to", "any", "our", "my", "his", "her", "real", "regular", "plain", "added",
    )

    /** How many content words after a negator it can still reach. */
    private const val WINDOW = 4

    private val BOUNDARY = Regex(
        "[,;:()\\[\\]!?\\n\\u2014\\u2013]|\\.(?![\\p{N}])|\\s[-]\\s|\\b(?:but|then|however|although|though|whereas)\\b|\\band\\s+([\\p{L}']+)",
    )

    private val TOKEN = Regex("[\\p{L}\\p{N}']+")

    /** The clause ranges of [text], as half-open ranges. Empty clauses are dropped. */
    fun clauses(text: String): List<IntRange> {
        val cuts = ArrayList<IntRange>() // each cut is the range of boundary characters to skip
        for (m in BOUNDARY.findAll(text)) {
            val andWord = m.groups[1]?.value
            if (m.value.startsWith("and")) {
                if (andWord != null && andWord in NEW_CLAUSE_AFTER_AND) {
                    cuts += m.range.first until m.range.first + 3 // just the word "and"
                }
            } else {
                cuts += m.range
            }
        }
        val out = ArrayList<IntRange>()
        var start = 0
        for (cut in cuts) {
            if (cut.first > start) out += start until cut.first
            start = cut.last + 1
        }
        if (start < text.length) out += start until text.length
        return out.filter { r -> text.substring(r.first, r.last + 1).isNotBlank() }
    }

    private val NEGATOR = Regex(
        "\\b(?:no(?=\\s)|not|without|skip(?:s|ping|ped)?|hold(?:ing)?\\s+the|minus|omit(?:s|ting|ted)?|" +
            "leav(?:e|es|ing)\\s+out|left\\s+out|instead\\s+of|rather\\s+than|free\\s+(?:of|from)|avoid(?:s|ing)?|never)\\b",
    )

    private val CHECK_WORD = Regex("\\bcheck(?:s|ed|ing)?\\b")
    private val FOR_WORD = Regex("\\bfor\\b")

    /** What a clause says about avoidance: ranges where a term counts as negated, and spans already being checked. */
    class Reading(val negated: List<IntRange>, val checking: List<IntRange>) {
        fun isNegated(position: Int) = negated.any { position in it }
        fun isBeingChecked(position: Int) = checking.any { position in it }

        companion object { val NONE = Reading(emptyList(), emptyList()) }
    }

    /** Reads one clause of [text]. */
    fun read(text: String, clause: IntRange): Reading {
        val from = clause.first
        val to = clause.last + 1
        val segment = text.substring(from, to)
        val negated = ArrayList<IntRange>()
        val checking = ArrayList<IntRange>()

        NEGATOR.findAll(segment).forEach { m ->
            negated += scopeAfter(text, from + m.range.last + 1, to)
        }
        // "check the bun for milk": the thing after "for" is what is being avoided, and the thing between
        // is already being checked.
        CHECK_WORD.find(segment)?.let { c ->
            val forMatch = FOR_WORD.find(segment, c.range.last + 1)
            if (forMatch != null && segment.substring(c.range.last + 1, forMatch.range.first).split(' ').count { it.isNotBlank() } <= 6) {
                checking += (from + c.range.last + 1) until (from + forMatch.range.first)
                negated += scopeAfter(text, from + forMatch.range.last + 1, to)
            }
        }
        return Reading(negated, checking)
    }

    /** From [start], up to [limit]: the span a negator reaches. */
    private fun scopeAfter(text: String, start: Int, limit: Int): IntRange {
        var count = 0
        var end = limit
        for (t in TOKEN.findAll(text.substring(start, limit))) {
            val word = t.value
            if (word in SCOPE_ENDERS) { end = start + t.range.first; break }
            if (word !in FILLER) {
                count++
                if (count > WINDOW) { end = start + t.range.first; break }
            }
        }
        return start until maxOf(start, end)
    }

    /** Word tokens of [text] from [start] up to [limit], with their absolute positions. */
    fun tokens(text: String, start: Int, limit: Int): List<Pair<String, Int>> =
        if (start >= limit) emptyList()
        else TOKEN.findAll(text.substring(start, limit)).map { it.value to start + it.range.first }.toList()
}
