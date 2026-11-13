package com.zeroindex.intake.domain.videos

/**
 * "Hide Shorts". YouTube's API has no Short flag, and Shorts can now run to 3 minutes, so a length of one minute is not
 * enough. A video counts as a Short when ANY of these holds (docs/VIDEOS.md, "Hide Shorts"):
 *  1. its length is 1 to 60 seconds;
 *  2. its title or the start of its description carries #shorts, #short or #ytshorts;
 *  3. it is at most 3 minutes long AND its title or description says it is a Short ("under 1 minute", "in 60 seconds",
 *     "30-second", "#shortsvideo", "YouTube Shorts").
 * A video with no known length (a pasted link) is never called a Short by length. A vertical-format signal is not used:
 * it would need another API part cached per video (a schema change) and the API does not state orientation reliably.
 */
object ShortsRule {
    /** A Short by length alone. */
    const val SURE_MAX_SEC = 60

    /** The longest a Short can be today. Above this, only the explicit #shorts tag counts. */
    const val LONGEST_SHORT_SEC = 180

    private const val DESCRIPTION_CHARS = 600

    private val TAG = Regex("#(?:yt)?shorts?(?:video|feed|youtube)?\\b", RegexOption.IGNORE_CASE)

    private val SAYS_SHORT = Regex("\\b(?:youtube|yt)\\s+shorts?\\b", RegexOption.IGNORE_CASE)

    /** "under 1 minute", "in 60 seconds", "less than a minute", "just 45 sec". */
    private val SHORT_SPAN = Regex(
        "\\b(?:under|less than|in|within|only|just|<)\\s*(\\d{1,3}|a|an|one|two|three)[-\\s]*(min(?:ute)?s?|sec(?:ond)?s?)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** "60 second workout", "30-sec abs". */
    private val SECOND_COUNT = Regex("\\b(\\d{1,3})[-\\s]?(?:sec(?:ond)?s?)\\b", RegexOption.IGNORE_CASE)

    fun isShort(video: VideoMeta): Boolean = reason(video) != null

    /** Why the video counts as a Short, or null when it does not. For tests and for the card. */
    fun reason(video: VideoMeta): String? {
        val sec = video.durationSec
        if (sec in 1..SURE_MAX_SEC) return "$sec seconds or less"
        val text = video.title + "\n" + video.description.take(DESCRIPTION_CHARS)
        if (TAG.containsMatchIn(text)) return "tagged as a Short"
        // Unknown length (0): only the tag above can decide.
        if (sec in 1..LONGEST_SHORT_SEC && signalsShort(text)) return "says it is a Short"
        return null
    }

    internal fun signalsShort(text: String): Boolean {
        if (SAYS_SHORT.containsMatchIn(text)) return true
        if (SHORT_SPAN.findAll(text).any { spanSeconds(it.groupValues[1], it.groupValues[2]) in 1..LONGEST_SHORT_SEC }) return true
        return SECOND_COUNT.findAll(text).any { (it.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE) in 1..LONGEST_SHORT_SEC }
    }

    private fun spanSeconds(count: String, unit: String): Int {
        val n = when (count.lowercase()) {
            "a", "an", "one" -> 1
            "two" -> 2
            "three" -> 3
            else -> count.toIntOrNull() ?: return -1
        }
        return if (unit.lowercase().startsWith("min")) n * 60 else n
    }
}
