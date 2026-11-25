package com.zeroindex.intake.domain.videos

/**
 * Captions to a workout draft, with no model. It finds the library's exercises (and a short list of common ones the
 * library may lack) in the spoken text with [ExerciseNameMatcher], reads the numbers said near each one ("3 sets of 12",
 * "for 45 seconds", "rest 30 seconds", "each side", "AMRAP", "3 rounds", "40 on, 20 off"), and remembers the second at
 * which each exercise is first named, so a saved workout can open the video there.
 *
 * It only proposes. The person reviews every row, and a row that is only talked about (named once, no numbers) is listed
 * but left switched off.
 */
object TranscriptDrafter {
    private const val MAX_WINDOW = 4
    private const val MAX_DISTANCE = 110

    /** A single spoken word is trusted as a partial name only for these; others need the library's exact name. */
    private val SINGLE_WORD_PARTIAL = setOf("squat", "lunge", "deadlift", "plank", "crunch", "burpee", "pushup", "situp", "pullup")

    /** Exercises a follow-along names that the library may not have. A hit here becomes a "New, will be created" row. */
    private val COMMON = listOf(
        "Jumping jacks", "Burpees", "Mountain climbers", "High knees", "Butt kicks", "Squat jumps", "Skaters", "Plank jacks",
        "Bicycle crunches", "Russian twists", "Flutter kicks", "Glute bridge", "Sit-ups", "Bear crawl", "Inchworm", "Star jumps",
        "Jump rope", "Shadow boxing", "Wall sit", "Lunges", "Squats", "Push-ups", "Planks", "Side plank", "Superman", "Leg raises",
        "Tuck jumps", "Speed skaters", "Hollow hold", "V-ups", "Toe taps", "Shoulder taps", "Side lunges", "Curtsy lunges",
        "Reverse crunches", "Calf raises", "Donkey kicks", "Fire hydrants", "Arm circles", "Jump lunges",
    )

    private val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
    )
    private val TENS = mapOf("thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "ninety" to 90)

    private val TOKEN = Regex("""\d+|[A-Za-z']+""")

    private val REST_AFTER = Regex("""\b(?:rest|break|recover|recovery)(?: for| of)? (\d+) (seconds?|secs?|s|minutes?|mins?)\b""")
    private val REST_BEFORE = Regex("""\b(\d+) (seconds?|secs?|s|minutes?|mins?) (?:of )?(?:rest|break|recovery)\b""")
    private val ON_OFF = Regex("""\b(\d+) (?:seconds? |secs? |s )?(?:on|of work|work),? (?:and )?(\d+) (?:seconds? |secs? |s )?(?:off|rest|of rest)\b""")
    private val SETS_REPS = Regex("""\b(\d+) sets? of (\d+)\b|\b(\d+) x (\d+)\b""")
    private val ROUNDS = Regex("""\b(\d+) (?:rounds?|circuits?)\b|\brepeat (?:this |it |that )?(\d+) times\b|\b(?:rounds?|circuits?) of (\d+)\b""")
    private val REPS = Regex("""\b(\d+) (?:reps?|repetitions?)\b""")
    // "a ten minute HIIT workout" is the length of the video, not an exercise time.
    private val SECONDS = Regex("""\b(\d+) (seconds?|secs?|s|minutes?|mins?)\b(?! (?:hiit|workout|video|session|routine|challenge|program|plan|circuit|total|long|burn|cardio|class))""")
    private val EACH = Regex("""\b(?:each|per|every) (?:side|leg|arm|direction)\b|\bboth sides\b|\bon each\b""")
    private val AMRAP = Regex("""\bamrap\b|\bas many (?:reps |rounds |as )?(?:as )?(?:you can|possible)\b""")

    /** Movement words that are one row whichever variant a chapter or the captions picked. */
    private val SAME_FAMILY = setOf("squat", "lunge", "plank", "crunch", "deadlift")

    private class Tok(val text: String, val seg: Int)

    private class Mention(val key: String, val exerciseId: String?, val name: String, val isNew: Boolean, val first: Int, val last: Int, val start: Int, val end: Int)

    private class Phrase(val kind: String, val start: Int, val end: Int, val a: Int = 0, val b: Int = 0)

    private class Acc(val key: String, val exerciseId: String?, val name: String, val isNew: Boolean) {
        var firstSeg = Int.MAX_VALUE
        var snippet = ""
        var count = 0
        var sets: Int? = null
        var reps: Int? = null
        var seconds: Int? = null
        var rest: Int? = null
        var each = false
        var amrap = false
        val prescribed: Boolean get() = sets != null || reps != null || seconds != null || amrap
    }

    fun draft(transcript: Transcript, library: List<ExerciseEntry>): VideoWorkoutDraft {
        val segs = transcript.segments
        if (segs.isEmpty()) return VideoWorkoutDraft(DraftSource.NOTHING, emptyList())

        val toks = tokenize(segs)
        if (toks.isEmpty()) return VideoWorkoutDraft(DraftSource.NOTHING, emptyList())
        val starts = IntArray(toks.size)
        val text = StringBuilder()
        toks.forEachIndexed { i, t ->
            if (i > 0) text.append(' ')
            starts[i] = text.length
            text.append(t.text)
        }
        val norm = text.toString()

        val mentions = findMentions(toks, starts, library)
        if (mentions.isEmpty()) return VideoWorkoutDraft(DraftSource.NOTHING, emptyList())

        // Phrases, claimed in order so a rest time is not also read as work time.
        val taken = BooleanArray(norm.length)
        val phrases = mutableListOf<Phrase>()
        fun claim(regex: Regex, make: (MatchResult) -> Phrase?) {
            for (m in regex.findAll(norm)) {
                if ((m.range.first..m.range.last).any { taken[it] }) continue
                val p = make(m) ?: continue
                for (k in m.range.first..m.range.last) taken[k] = true
                phrases += p
            }
        }
        fun secs(n: String, unit: String): Int = n.toInt() * (if (unit.startsWith("m")) 60 else 1)
        claim(ON_OFF) { Phrase("onoff", it.range.first, it.range.last, it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        claim(REST_AFTER) { Phrase("rest", it.range.first, it.range.last, secs(it.groupValues[1], it.groupValues[2])) }
        claim(REST_BEFORE) { Phrase("rest", it.range.first, it.range.last, secs(it.groupValues[1], it.groupValues[2])) }
        claim(SETS_REPS) { m ->
            val v = m.groupValues
            val sets = (v[1].ifEmpty { v[3] }).toInt()
            val reps = (v[2].ifEmpty { v[4] }).toInt()
            if (sets in 1..20 && reps in 1..100) Phrase("setsreps", m.range.first, m.range.last, sets, reps) else null
        }
        claim(ROUNDS) { m ->
            val n = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toInt() ?: return@claim null
            if (n in 1..20) Phrase("rounds", m.range.first, m.range.last, n) else null
        }
        claim(REPS) { m -> m.groupValues[1].toInt().takeIf { it in 1..100 }?.let { Phrase("reps", m.range.first, m.range.last, it) } }
        claim(SECONDS) { m -> secs(m.groupValues[1], m.groupValues[2]).takeIf { it in 5..900 }?.let { Phrase("seconds", m.range.first, m.range.last, it) } }
        claim(EACH) { Phrase("each", it.range.first, it.range.last) }
        claim(AMRAP) { Phrase("amrap", it.range.first, it.range.last) }

        val globalRounds = phrases.firstOrNull { it.kind == "rounds" }?.a
        val onOff = phrases.firstOrNull { it.kind == "onoff" }

        // Per exercise, in order of first mention.
        val accs = LinkedHashMap<String, Acc>()
        val order = mentions.sortedBy { it.start }
        for (m in order) {
            val a = accs.getOrPut(m.key) { Acc(m.key, m.exerciseId, m.name, m.isNew) }
            a.count++
            if (toks[m.first].seg < a.firstSeg) {
                a.firstSeg = toks[m.first].seg
                a.snippet = snippet(norm, m.start, m.end)
            }
        }
        // A number said straight before the name ("12 squats") is its reps.
        val bareReps = HashMap<Int, Int>()
        order.forEachIndexed { idx, m ->
            val prev = m.first - 1
            if (prev >= 0 && toks[prev].text.all { it.isDigit() } && !taken[starts[prev]]) {
                toks[prev].text.toInt().takeIf { it in 1..100 }?.let { bareReps[idx] = it }
            }
        }

        fun nearest(p: Phrase, preferBefore: Boolean): Int? {
            var best: Int? = null
            var bestDist = Int.MAX_VALUE
            order.forEachIndexed { idx, m ->
                if (preferBefore && m.end > p.start) return@forEachIndexed
                val d = when {
                    m.end <= p.start -> p.start - m.end
                    m.start >= p.end -> m.start - p.end
                    else -> 0
                }
                // Ties go to the later mention ("10 lunges": the number comes first).
                if (d <= bestDist && d <= MAX_DISTANCE) {
                    bestDist = d
                    best = idx
                }
            }
            if (best == null && preferBefore) return nearest(p, false)
            return best
        }

        for (p in phrases) {
            if (p.kind == "rounds" || p.kind == "onoff") continue
            val idx = nearest(p, preferBefore = p.kind == "rest") ?: continue
            val a = accs.getValue(order[idx].key)
            when (p.kind) {
                "setsreps" -> { if (a.sets == null) a.sets = p.a; if (a.reps == null) a.reps = p.b }
                "reps" -> if (a.reps == null) a.reps = p.a
                "seconds" -> if (a.seconds == null) a.seconds = p.a
                "rest" -> if (a.rest == null) a.rest = p.a
                "each" -> a.each = true
                "amrap" -> a.amrap = true
            }
        }
        bareReps.forEach { (idx, n) ->
            val a = accs.getValue(order[idx].key)
            if (a.reps == null && a.seconds == null) a.reps = n
        }

        val items = accs.values.sortedBy { it.firstSeg }.mapIndexed { i, a ->
            val timed = a.seconds != null && a.reps == null && !a.amrap
            val globalWork = onOff?.a?.takeIf { a.seconds == null && a.reps == null && !a.amrap }
            val isTimed = timed || globalWork != null
            val seconds = if (isTimed) a.seconds ?: globalWork else null
            val kind = if (a.exerciseId != null) DraftKind.MATCHED else DraftKind.NEW
            val include = a.prescribed || onOff != null || a.count >= 2
            DraftItem(
                key = i,
                text = a.snippet,
                kind = kind,
                exerciseId = a.exerciseId,
                exerciseName = a.name,
                sets = a.sets ?: globalRounds ?: if (isTimed) 1 else VideoWorkoutDrafter.DEFAULT_SETS,
                reps = if (isTimed) 1 else if (a.amrap && a.reps == null) 1 else a.reps ?: VideoWorkoutDrafter.DEFAULT_REPS,
                seconds = seconds,
                restSec = a.rest ?: onOff?.b ?: if (isTimed) 0 else VideoWorkoutDrafter.DEFAULT_REST_SEC,
                include = include,
                startSec = segs[a.firstSeg].startSec.toInt(),
                primaryMuscle = MuscleGuess.of(a.name),
                eachSide = a.each,
                amrap = a.amrap,
            )
        }
        return VideoWorkoutDraft(DraftSource.TRANSCRIPT, items)
    }

    /**
     * Combines the draft read from chapters or the description ([base]) with the one read from captions. Chapters win:
     * their rows, numbers and times are kept, and the captions only add a time to a base row that has none. Captions
     * add rows the base lacks only when they were clearly prescribed (chapters: left off until the person turns them on).
     */
    fun merge(base: VideoWorkoutDraft, captions: VideoWorkoutDraft): VideoWorkoutDraft {
        if (captions.items.isEmpty()) return base
        if (base.items.isEmpty()) return captions
        fun same(a: DraftItem, b: DraftItem): Boolean {
            val ta = ExerciseNameMatcher.tokens(a.exerciseName)
            val tb = ExerciseNameMatcher.tokens(b.exerciseName)
            return (a.exerciseId != null && a.exerciseId == b.exerciseId) ||
                (ta.isNotEmpty() && ta == tb) ||
                // "Squats" in a chapter and "Bodyweight squat" in the captions are the same row.
                (ta.isNotEmpty() && tb.isNotEmpty() && ta.last() == tb.last() && ta.last() in SAME_FAMILY)
        }
        val filled = base.items.map { b ->
            if (b.startSec != null) b else b.copy(startSec = captions.items.firstOrNull { same(b, it) }?.startSec)
        }
        val extras = captions.items.filter { c -> filled.none { same(it, c) } }
            .filter { it.include }
            .map { if (base.source == DraftSource.CHAPTERS) it.copy(include = false) else it }
        val all = (filled + extras).mapIndexed { i, it -> it.copy(key = i) }
        val source = when (base.source) {
            DraftSource.CHAPTERS -> DraftSource.CHAPTERS_AND_CAPTIONS
            DraftSource.DESCRIPTION -> DraftSource.DESCRIPTION_AND_CAPTIONS
            else -> base.source
        }
        return VideoWorkoutDraft(source, all)
    }

    // --- reading the text ---------------------------------------------------------------------------------

    /** Words and numbers, with spoken numbers turned into digits ("forty five" is 45), each tagged with its segment. */
    private fun tokenize(segs: List<TranscriptSegment>): List<Tok> {
        val raw = mutableListOf<Tok>()
        segs.forEachIndexed { i, s -> TOKEN.findAll(s.text).forEach { raw += Tok(it.value.lowercase(), i) } }
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < raw.size) {
            val w = raw[i].text
            val tens = TENS[w]
            val next = raw.getOrNull(i + 1)?.text
            when {
                tens != null && next != null && ONES[next] in 1..9 -> { out += Tok((tens + ONES.getValue(next)).toString(), raw[i].seg); i += 2 }
                tens != null -> { out += Tok(tens.toString(), raw[i].seg); i++ }
                ONES.containsKey(w) -> { out += Tok(ONES.getValue(w).toString(), raw[i].seg); i++ }
                else -> { out += raw[i]; i++ }
            }
        }
        return out
    }

    private fun findMentions(toks: List<Tok>, starts: IntArray, library: List<ExerciseEntry>): List<Mention> {
        val libMatcher = ExerciseNameMatcher(library)
        val common = COMMON.map { ExerciseEntry("common:$it", it) }
        val commonMatcher = ExerciseNameMatcher(common)
        val vocab = (library + common).flatMap { ExerciseNameMatcher.tokens(it.name) }.toSet()
        // One word standing for one library exercise: "squat" is the plain or bodyweight squat, not the barbell one.
        val single = HashMap<String, ExerciseEntry>()
        for (e in library) {
            val t = ExerciseNameMatcher.tokens(e.name)
            val w = when {
                t.size == 1 -> t[0]
                t.size == 2 && t[0] == "bodyweight" -> t[1]
                else -> null
            } ?: continue
            if (w in SINGLE_WORD_PARTIAL) single.putIfAbsent(w, e)
        }
        val memo = HashMap<String, Triple<String, String?, Boolean>?>()

        fun content(tok: Tok): Boolean {
            if (tok.text.first().isDigit()) return false
            val t = ExerciseNameMatcher.tokens(tok.text)
            return t.isNotEmpty() && t.all { it in vocab }
        }

        fun resolve(words: String, n: Int): Triple<String, String?, Boolean>? = memo.getOrPut("$n|$words") {
            if (n == 1) {
                val w = ExerciseNameMatcher.tokens(words).singleOrNull()
                w?.let { single[it] }?.let { return@getOrPut Triple(it.name, it.id, false) }
                libMatcher.match(words)?.takeIf { it.score >= 0.95 }?.let { return@getOrPut Triple(it.entry.name, it.entry.id, false) }
                commonMatcher.match(words)?.takeIf { it.score >= 0.95 }?.let { return@getOrPut Triple(it.entry.name, null, true) }
                return@getOrPut null
            }
            // A longer window must be the name and little else: "up is jumping jacks" is not "Jumping jacks".
            libMatcher.match(words)?.takeIf { tight(words, it.entry.name) }?.let { return@getOrPut Triple(it.entry.name, it.entry.id, false) }
            commonMatcher.match(words)?.takeIf { tight(words, it.entry.name) }?.let { return@getOrPut Triple(it.entry.name, null, true) }
            null
        }

        val found = mutableListOf<Mention>()
        var i = 0
        while (i < toks.size) {
            var hit = false
            for (n in minOf(MAX_WINDOW, toks.size - i) downTo 1) {
                if (!content(toks[i]) || !content(toks[i + n - 1])) continue
                val words = (i until i + n).joinToString(" ") { toks[it].text }
                val r = resolve(words, n) ?: continue
                val key = r.second ?: ("new:" + ExerciseNameMatcher.tokens(r.first).joinToString(" "))
                found += Mention(key, r.second, r.first, r.third, i, i + n - 1, starts[i], starts[i + n - 1] + toks[i + n - 1].text.length)
                i += n
                hit = true
                break
            }
            if (!hit) i++
        }
        return found
    }

    /** Words a speaker adds in front of a name ("barbell back squat") without it being another exercise. */
    private val MODIFIERS = setOf(
        "barbell", "dumbbell", "kettlebell", "bodyweight", "cable", "machine", "weighted", "single", "alternating", "regular",
        "basic", "standard", "full", "slow", "fast", "simple", "easy", "classic",
    )

    private fun tight(words: String, name: String): Boolean {
        val extra = ExerciseNameMatcher.tokens(words).toSet() - ExerciseNameMatcher.tokens(name).toSet()
        val nameTokens = ExerciseNameMatcher.tokens(name)
        // A spelling variant ("ups" for "up") is not an extra word.
        return extra.all { e -> e in MODIFIERS || nameTokens.any { ExerciseNameMatcher.levenshtein(e, it) <= 1 } }
    }

    private fun snippet(norm: String, start: Int, end: Int): String {
        val a = (start - 30).coerceAtLeast(0)
        val b = (end + 50).coerceAtMost(norm.length)
        return norm.substring(a, b).trim()
    }
}
