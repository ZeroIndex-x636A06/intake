package com.zeroindex.intake.domain.videos

/** What the YouTube Data API tells us about one video. Pure data, so the classifier and parsers are testable. */
data class VideoMeta(
    val id: String,
    val title: String,
    val description: String = "",
    val channelTitle: String = "",
    /** YouTube's numeric category id as text ("17" is Sports), or blank when unknown. */
    val categoryId: String = "",
    val durationSec: Int = 0,
    val thumbnailUrl: String? = null,
    val publishedAt: String = "",
    val viewCount: Long? = null,
    /** `player.embedWidth` / `embedHeight` from `part=player` (they follow the video's aspect ratio), or null when unknown. */
    val embedWidth: Int? = null,
    val embedHeight: Int? = null,
) {
    /** True when the video is taller than wide, false when not, null when the player size is unknown. */
    val isVertical: Boolean?
        get() = if (embedWidth != null && embedHeight != null && embedWidth > 0 && embedHeight > 0) embedHeight > embedWidth else null
}

/** Plain-words verdict on a video, with the number behind it. Never stored: it is recomputed from the metadata. */
enum class VideoLabel(val label: String) {
    /** Clearly a workout. */
    WORKOUT("Workout"),

    /** Borderline: shown with a "Maybe" badge. */
    MAYBE("Maybe"),

    /** Hidden by "Workouts only". */
    NOT_WORKOUT("Not a workout"),
}

data class VideoVerdict(
    val score: Int,
    val label: VideoLabel,
    /** One plain sentence: why it passed or was hidden. */
    val reason: String,
    /** Each rule that fired, e.g. "title: workout (+3)". For tests and the detail page. */
    val signals: List<String>,
)

/** One timestamped line of a description. */
data class Chapter(val startSec: Int, val title: String)

/** A line of a video that reads like an exercise, before it is matched to the library. */
data class ParsedExerciseLine(
    val rawText: String,
    /** The text with times, set and rep counts removed: "Squats". */
    val name: String,
    val sets: Int? = null,
    val reps: Int? = null,
    /** Work time in seconds, when the line says "45 sec" or the chapter is a timed block. */
    val seconds: Int? = null,
    val restSec: Int? = null,
    val startSec: Int? = null,
)
