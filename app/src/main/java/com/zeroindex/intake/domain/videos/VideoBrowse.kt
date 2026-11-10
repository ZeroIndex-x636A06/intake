package com.zeroindex.intake.domain.videos

/** The style chips under the Videos search box. [word] is added to the words sent to YouTube. */
enum class VideoStyle(val label: String, val word: String) {
    PUSH("Push", "push day workout"),
    PULL("Pull", "pull day workout"),
    LEGS("Legs", "leg workout"),
    PILATES("Pilates", "pilates"),
    YOGA("Yoga", "yoga"),
    MOBILITY("Mobility", "mobility"),
    HIIT("HIIT", "hiit workout"),
    STRETCH("Stretch", "stretching routine"),
}

/** The Length chips. Filtering by length happens on this phone, so it costs no quota. */
enum class VideoLength(val label: String, val minSec: Int, val maxSec: Int) {
    UNDER_15("Up to 15 min", 0, 15 * 60),
    FROM_15("15 to 30 min", 15 * 60, 30 * 60),
    FROM_30("30 to 45 min", 30 * 60, 45 * 60),
    OVER_45("45 min +", 45 * 60, Int.MAX_VALUE),
    ;

    fun accepts(durationSec: Int): Boolean = durationSec >= minSec && durationSec < maxSec
}

/** One row of results with its verdict. The verdict is recomputed from the metadata, never stored. */
data class ScoredVideo(val video: VideoMeta, val verdict: VideoVerdict)

/** What a search shows after the filters, and how many rows the "Workouts only" filter hid. */
data class FilteredVideos(val shown: List<ScoredVideo>, val hiddenAsNotWorkouts: Int, val total: Int) {
    /** "12 of 30 results hidden as not workouts.", or null when nothing was hidden. */
    fun hiddenLine(): String? =
        if (hiddenAsNotWorkouts > 0) "$hiddenAsNotWorkouts of $total results hidden as not workouts." else null
}

/** The pure rules of the Videos home: the words sent, and the filters applied afterwards. */
object VideoBrowse {
    /** The text sent to `search.list`: the person's words plus the style chip's word. Only this leaves the phone. */
    fun searchQuery(typed: String, style: VideoStyle?): String {
        val words = typed.trim().replace(Regex("\\s+"), " ")
        val extra = style?.word.orEmpty()
        return when {
            words.isEmpty() -> extra
            extra.isEmpty() -> words
            // Do not repeat a word the person already typed ("pilates pilates").
            words.lowercase().contains(extra.lowercase()) -> words
            else -> "$words $extra"
        }
    }

    /** The cache key part for the chips that change what is asked of YouTube (only the style does). */
    fun cacheFilters(style: VideoStyle?): String = style?.name.orEmpty()

    fun score(videos: List<VideoMeta>, catalogNames: Collection<String>): List<ScoredVideo> =
        videos.map { ScoredVideo(it, WorkoutVideoClassifier.classify(it, catalogNames)) }

    /**
     * Applies "Workouts only" (when [onlyWorkouts]) and the length chip. [showEverything] is the "Show everything" tap for
     * the current search: it lifts the workout filter for the rows drawn, and changes no score.
     */
    fun apply(scored: List<ScoredVideo>, onlyWorkouts: Boolean, showEverything: Boolean, length: VideoLength?): FilteredVideos {
        val byLength = scored.filter { length == null || it.video.durationSec <= 0 || length.accepts(it.video.durationSec) }
        val filterOn = onlyWorkouts && !showEverything
        val kept = if (filterOn) byLength.filter { it.verdict.label != VideoLabel.NOT_WORKOUT } else byLength
        return FilteredVideos(kept, if (filterOn) byLength.size - kept.size else 0, byLength.size)
    }

    /** How many chapters the description has (shown as a "Chapters" badge on the card). */
    fun chapterCount(video: VideoMeta): Int = ChapterParser.chapters(video.description).size
}
