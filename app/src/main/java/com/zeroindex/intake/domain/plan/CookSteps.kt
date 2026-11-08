package com.zeroindex.intake.domain.plan

/** A duration found in a step's text, with where it sits so the UI can chip it inline. */
data class StepTimer(
    val seconds: Int,
    /** The text as written, e.g. "3-4 minutes". */
    val text: String,
    val start: Int,
    val end: Int,
)

object CookSteps {

    private val NUMBERING = Regex("""^\s*(?:step\s*)?\d{1,2}\s*[.):\-]\s*""", RegexOption.IGNORE_CASE)

    /**
     * Recipe instructions are stored as one text column. One step per line;
     * leading "1." / "Step 2:" numbering is stripped because the UI numbers
     * steps itself. Blank lines are dropped.
     */
    fun split(instructions: String): List<String> =
        instructions.lines()
            .map { it.replace(NUMBERING, "").trim() }
            .filter { it.isNotEmpty() }

    /** Inverse of [split], for storing a plan file's step list in the recipe column. */
    fun join(steps: List<String>): String =
        steps.map { it.trim() }.filter { it.isNotEmpty() }
            .mapIndexed { i, s -> "${i + 1}. $s" }
            .joinToString("\n")

    private const val NUMBER = """\d+(?:\.\d+)?"""
    private const val UNIT = """hours?|hrs?|minutes?|mins?|seconds?|secs?"""
    private val DURATION = Regex(
        """(?<![\d.])($NUMBER)(?:\s*(?:-|–|—|to)\s*($NUMBER))?\s*($UNIT)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val JOINER = Regex("""^\s*(?:and\s+|,\s*)?$""", RegexOption.IGNORE_CASE)

    /**
     * Finds durations like "5 minutes", "10 min", "1 hour", "30 seconds". A
     * range such as "3-4 minutes" uses the upper value, because an undercooked
     * timer is worse than a slightly long one. "1 hour 30 minutes" is one timer.
     */
    fun durations(text: String): List<StepTimer> {
        val merged = ArrayList<StepTimer>()
        var previousPerUnit = 0
        DURATION.findAll(text).forEach { m ->
            val upper = m.groupValues[2].ifEmpty { m.groupValues[1] }.toDoubleOrNull() ?: return@forEach
            val perUnit = unitSeconds(m.groupValues[3])
            val seconds = (upper * perUnit).toInt()
            if (seconds <= 0) return@forEach
            val last = merged.lastOrNull()
            val gap = last?.let { text.substring(it.end, m.range.first) }
            if (last != null && previousPerUnit == 3600 && perUnit == 60 && gap != null && JOINER.matches(gap)) {
                merged[merged.lastIndex] = StepTimer(
                    seconds = last.seconds + seconds,
                    text = text.substring(last.start, m.range.last + 1),
                    start = last.start,
                    end = m.range.last + 1,
                )
            } else {
                merged += StepTimer(seconds, m.value, m.range.first, m.range.last + 1)
            }
            previousPerUnit = perUnit
        }
        return merged
    }

    private fun unitSeconds(unit: String): Int = when (unit.lowercase().first()) {
        'h' -> 3600
        'm' -> 60
        else -> 1
    }

    /** "5 min", "1 h 30 min", "30 sec" - short enough for a chip. */
    fun formatShort(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0 && h == 0) add("$s sec")
        }.joinToString(" ").ifEmpty { "0 sec" }
    }

    /** Countdown clock text: "5:00", "1:30:00". */
    fun formatClock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
