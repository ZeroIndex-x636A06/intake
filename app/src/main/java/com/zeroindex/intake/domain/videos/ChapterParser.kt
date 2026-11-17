package com.zeroindex.intake.domain.videos

/**
 * Reads a video description the way a person would: timestamp lines ("0:00 Warm up", "1:30 Squats x 12") are chapters, and
 * a line like "Squats 3x12" or "Plank 45 sec" is an exercise. Deterministic, on the phone, no model.
 */
object ChapterParser {
    private const val TS = """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})"""
    private val LEADING = Regex("""^[\s\-–—•*▶►>·(\[]*(?:\d{1,2}[.)]\s+)?$TS[\s\-–—:)\]|.]*(.+)$""")
    private val TRAILING = Regex("""^(.+?)[\s\-–—:|(\[]+$TS[)\]]?\s*$""")

    private fun seconds(h: String, m: String, s: String): Int =
        (h.toIntOrNull() ?: 0) * 3600 + m.toInt() * 60 + s.toInt()

    /**
     * The chapters in order. YouTube needs three or more, but two lines are enough to be useful here; a single timestamp
     * (a "watch at 2:30" link in a description) is not a list. Lines whose time does not go up are ignored.
     */
    fun chapters(description: String): List<Chapter> {
        val found = mutableListOf<Chapter>()
        for (raw in description.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val lead = LEADING.matchEntire(line)
            val chapter = when {
                lead != null -> Chapter(seconds(lead.groupValues[1], lead.groupValues[2], lead.groupValues[3]), clean(lead.groupValues[4]))
                else -> TRAILING.matchEntire(line)?.let { Chapter(seconds(it.groupValues[2], it.groupValues[3], it.groupValues[4]), clean(it.groupValues[1])) }
            } ?: continue
            if (chapter.title.isBlank()) continue
            if (found.isNotEmpty() && chapter.startSec <= found.last().startSec) continue
            found += chapter
        }
        return if (found.size >= 2) found else emptyList()
    }

    private fun clean(title: String): String = title.trim().trim('-', '–', '—', ':', '|', '•', '*').trim()
}

/** Turns one line ("Squats x 12", "3 sets of 10 lunges", "Plank, 45 sec", "40s on / 20s off") into numbers and a name. */
object ExerciseLineParser {
    private val SETS_OF = Regex("""(\d{1,2})\s*sets?\s*(?:of|x|×)\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
    private val SETS_X_REPS = Regex("""(\d{1,2})\s*[x×]\s*(\d{1,3})(?!\s*(?:sec|secs|seconds|s\b|min|mins|minutes?))""", RegexOption.IGNORE_CASE)
    private val X_REPS = Regex("""[x×]\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val REPS = Regex("""(\d{1,3})\s*reps?\b""", RegexOption.IGNORE_CASE)
    private val ON_OFF = Regex(
        """(\d{1,3})\s*(?:sec|secs|seconds|s)?\s*on\s*[/,]?\s*(\d{1,3})\s*(?:sec|secs|seconds|s)?\s*(?:off|rest)""",
        RegexOption.IGNORE_CASE,
    )
    private val SECONDS = Regex("""(\d{1,3})\s*(?:sec|secs|seconds|s)\b""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d{1,2})\s*(?:min|mins|minutes?)\b""", RegexOption.IGNORE_CASE)

    /** True when the text states sets, reps or a time, i.e. it reads like a prescription and not like prose. */
    fun hasPrescription(text: String): Boolean =
        SETS_OF.containsMatchIn(text) || SETS_X_REPS.containsMatchIn(text) || X_REPS.containsMatchIn(text) ||
            REPS.containsMatchIn(text) || ON_OFF.containsMatchIn(text) || SECONDS.containsMatchIn(text) || MINUTES.containsMatchIn(text)

    fun parse(text: String, startSec: Int? = null): ParsedExerciseLine? {
        var rest = text.trim()
        var sets: Int? = null
        var reps: Int? = null
        var seconds: Int? = null
        var restSec: Int? = null

        fun take(regex: Regex, apply: (MatchResult) -> Unit): Boolean {
            val m = regex.find(rest) ?: return false
            apply(m)
            rest = rest.removeRange(m.range)
            return true
        }
        take(SETS_OF) { sets = it.groupValues[1].toInt(); reps = it.groupValues[2].toInt() } ||
            take(SETS_X_REPS) { sets = it.groupValues[1].toInt(); reps = it.groupValues[2].toInt() }
        if (reps == null) take(X_REPS) { reps = it.groupValues[1].toInt() } || take(REPS) { reps = it.groupValues[1].toInt() }
        take(ON_OFF) { seconds = it.groupValues[1].toInt(); restSec = it.groupValues[2].toInt() }
        if (seconds == null) take(SECONDS) { seconds = it.groupValues[1].toInt() }
        if (seconds == null) take(MINUTES) { seconds = it.groupValues[1].toInt() * 60 }

        val name = rest
            .replace(Regex("""[()\[\]{}|•*▶►·]"""), " ")
            .replace(Regex("""\s+[-–—:,]\s+"""), " ")
            .replace(Regex("""^\W+|\W+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (name.isBlank() || name.none { it.isLetter() }) return null
        return ParsedExerciseLine(text.trim(), name, sets, reps, seconds, restSec, startSec)
    }
}
