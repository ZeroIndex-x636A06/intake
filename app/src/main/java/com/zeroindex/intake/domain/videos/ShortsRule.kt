package com.zeroindex.intake.domain.videos

/**
 * "Hide Shorts", by orientation. Long-form videos are horizontal and Shorts are vertical, so the rule reads the shape of
 * the video from `videos.list` `part=player` (`player.embedWidth` and `player.embedHeight`, which follow the video's aspect
 * ratio when `maxWidth` and `maxHeight` are sent). A video counts as a Short when:
 *  1. its player is taller than it is wide ([VideoMeta.isVertical]), whatever its length; or
 *  2. its title, or the start of its description, carries #shorts, #short or #ytshorts (a backup signal, at any length); or
 *  3. its player size is not known (an older cached row, a pasted link) and its length is 1 to 60 seconds.
 * Length plays no other part: a horizontal 2:30 video is not a Short, and neither is a horizontal 45-second one whose size
 * is known. See docs/VIDEOS.md, "Hide Shorts".
 */
object ShortsRule {
    /** The length fallback, used only when the player size is missing. */
    const val FALLBACK_MAX_SEC = 60

    private const val DESCRIPTION_CHARS = 600

    private val TAG = Regex("#(?:yt)?shorts?(?:video|feed|youtube)?\\b", RegexOption.IGNORE_CASE)

    fun isShort(video: VideoMeta): Boolean = reason(video) != null

    /** Why the video counts as a Short, or null when it does not. For tests and for the card. */
    fun reason(video: VideoMeta): String? {
        if (video.isVertical == true) return "vertical video"
        val text = video.title + "\n" + video.description.take(DESCRIPTION_CHARS)
        if (TAG.containsMatchIn(text)) return "tagged as a Short"
        if (video.isVertical == null && video.durationSec in 1..FALLBACK_MAX_SEC) return "${video.durationSec} seconds or less"
        return null
    }
}
