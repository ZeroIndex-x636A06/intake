package com.zeroindex.intake.domain.videos

/**
 * "Because you saved ...": a search built from the channels and the title keywords of the videos the person saved. All
 * of it is counted on this phone; only the resulting words are sent, when Recommended loads or the person taps
 * "More like this".
 */
object SavedSimilarity {
    /** Words that say little about a video: workout words, units and filler. */
    private val STOP = setOf(
        "the", "and", "for", "with", "your", "you", "this", "that", "from", "into", "best", "new", "how", "all", "get", "day",
        "workout", "workouts", "exercise", "exercises", "routine", "follow", "along", "full", "minute", "minutes", "min", "mins",
        "video", "videos", "home", "equipment", "beginner", "beginners", "complete", "ultimate", "fitness", "training",
        "short", "shorts", "will", "can", "free", "easy", "quick", "fast", "hard", "intense",
    )

    private val WORD = Regex("[a-z][a-z'-]{2,}")

    /** The most common non-filler words in [titles], most common first, ties in order of first sight. */
    fun keywords(titles: List<String>, max: Int = 2): List<String> {
        val counts = LinkedHashMap<String, Int>()
        titles.forEach { t ->
            WORD.findAll(t.lowercase()).map { it.value.trim('-', '\'') }.filter { it.length > 2 && it !in STOP }.distinct()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.entries.sortedByDescending { it.value }.take(max).map { it.key }
    }

    /** The channel that appears most among [channels], or null when there is none. */
    fun topChannel(channels: List<String>): String? =
        channels.map { it.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().entries.maxByOrNull { it.value }?.key

    /**
     * The search for "videos like these": the top channel and the top keywords, plus "workout" so a channel's vlogs rank
     * lower. Null when there is nothing to go on.
     */
    fun query(saved: List<VideoMeta>): String? {
        val channel = topChannel(saved.map { it.channelTitle })
        val words = keywords(saved.map { it.title })
        val parts = listOfNotNull(channel) + words
        if (parts.isEmpty()) return null
        return (parts + "workout").joinToString(" ")
    }

    /** The plain reason shown on the row. */
    fun reason(saved: List<VideoMeta>): String {
        val channel = topChannel(saved.map { it.channelTitle })
        return if (channel != null) "Because you saved videos from $channel" else "Similar to what you saved"
    }
}
