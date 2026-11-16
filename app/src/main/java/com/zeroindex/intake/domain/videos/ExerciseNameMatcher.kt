package com.zeroindex.intake.domain.videos

/** An exercise of the library, as the matcher sees it. */
data class ExerciseEntry(val id: String, val name: String)

data class ExerciseMatch(val entry: ExerciseEntry, val score: Double)

/**
 * Finds the library exercise a line of text means ("Barbell back squats" is "Barbell back squat", "pushups" is "Push-up",
 * "Bech press" is "Bench press"). Deterministic: normalise, then compare whole names, then token overlap, then spelling.
 * Below [THRESHOLD] there is no match and the caller offers a new exercise or a skip.
 */
class ExerciseNameMatcher(entries: List<ExerciseEntry>) {
    private class Indexed(val entry: ExerciseEntry, val tokens: List<String>, val joined: String)

    private val index = entries.map {
        val t = tokens(it.name)
        Indexed(it, t, t.joinToString(" "))
    }.filter { it.tokens.isNotEmpty() }

    fun match(text: String): ExerciseMatch? = candidates(text, 1).firstOrNull()?.takeIf { it.score >= THRESHOLD }

    /** The best few entries, best first, with scores of [FLOOR] and up (for a "did you mean" list). */
    fun candidates(text: String, limit: Int = 3): List<ExerciseMatch> {
        val t = normalise(words(text).map(::correct))
        if (t.isEmpty()) return emptyList()
        val joined = t.joinToString(" ")
        val textSet = t.toSet()
        return index.map { c -> ExerciseMatch(c.entry, score(t, textSet, joined, c)) }
            .filter { it.score >= FLOOR }
            .sortedWith(compareByDescending<ExerciseMatch> { it.score }.thenBy { it.entry.name.length }.thenBy { it.entry.name })
            .take(limit)
    }

    /** Every word the library uses, so a misspelt word can be pulled to the nearest one. */
    private val vocabulary: Set<String> = index.flatMap { it.tokens }.toSet()

    private fun correct(token: String): String {
        if (token.length < 4 || token in vocabulary || singular(token) in vocabulary) return token
        val allowed = if (token.length <= 6) 1 else 2
        // Compare with each library word and its plural, so "pres" finds "press" and "squatz" finds "squats".
        return vocabulary.filter { it.length >= 4 }
            .map { it to minOf(levenshtein(token, it), levenshtein(token, it + "s")) }
            .filter { it.second <= allowed }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })?.first ?: token
    }

    private fun score(t: List<String>, textSet: Set<String>, joined: String, c: Indexed): Double {
        if (joined == c.joined) return 1.0
        val candSet = c.tokens.toSet()
        var best = 0.0
        // The library name is wholly inside the text: "Barbell back squats for legs" holds "Barbell back squat".
        if (textSet.containsAll(candSet)) best = maxOf(best, 0.80 + 0.15 * candSet.size / textSet.size)
        // The text is wholly inside the library name: "Bench press" is inside "Barbell bench press".
        if (candSet.containsAll(textSet)) {
            val tooVague = textSet.size == 1 && (textSet.first() in GENERIC || candSet.size > 2)
            if (!tooVague) best = maxOf(best, 0.75 + 0.15 * textSet.size / candSet.size)
        }
        // A typo or a different spelling.
        val ratio = 1.0 - levenshtein(joined, c.joined).toDouble() / maxOf(joined.length, c.joined.length)
        if (ratio >= 0.85) best = maxOf(best, ratio * 0.95)
        return best
    }

    companion object {
        const val THRESHOLD = 0.80
        const val FLOOR = 0.60

        private val STOP = setOf("the", "a", "an", "with", "using", "your", "for", "to", "of", "and", "on", "in", "at", "exercise", "move")

        /** Single words that name a whole family, so alone they do not pick one exercise. */
        private val GENERIC = setOf("press", "curl", "raise", "row", "extension", "fly", "flye", "pull", "push", "lift", "kick", "twist", "jump", "hold", "pulldown", "crunch", "bridge", "walk", "run")

        private val SYNONYMS = mapOf(
            "pushup" to "push up", "pullup" to "pull up", "chinup" to "chin up", "situp" to "sit up",
            "db" to "dumbbell", "dumbell" to "dumbbell", "dumbbel" to "dumbbell", "bb" to "barbell", "kb" to "kettlebell",
            "rdl" to "romanian deadlift", "ohp" to "overhead press", "ez" to "ez",
            "skullcrusher" to "skull crusher", "pressup" to "push up", "bodyweight" to "bodyweight",
        )

        /** Lower case, no punctuation, simple plurals folded, synonyms applied. */
        fun tokens(text: String): List<String> = normalise(words(text))

        /** The cleaned words of a text, before plurals and synonyms are applied. */
        internal fun words(text: String): List<String> {
            val spaced = text.lowercase()
                .replace('’', '\'')
                .replace("press-up", "push up").replace("press up", "push up")
                .replace(Regex("""[^a-z0-9]+"""), " ")
                .trim()
            if (spaced.isEmpty()) return emptyList()
            return spaced.split(' ').filter { it !in STOP && it.isNotBlank() }
        }

        internal fun normalise(words: List<String>): List<String> = words.flatMap { w ->
            val one = singular(w)
            (SYNONYMS[one] ?: SYNONYMS[w] ?: one).split(' ')
        }

        private fun singular(w: String): String = when {
            w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
            w.length > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("sses") || w.endsWith("xes")) -> w.dropLast(2)
            w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") -> w.dropLast(1)
            else -> w
        }

        internal fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
                }
                val tmp = prev; prev = cur; cur = tmp
            }
            return prev[b.length]
        }
    }
}
