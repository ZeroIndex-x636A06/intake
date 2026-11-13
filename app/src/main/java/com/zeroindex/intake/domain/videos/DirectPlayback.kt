package com.zeroindex.intake.domain.videos

/** Which player a video opens in. */
enum class PlayerKind {
    /** YouTube's own embedded player (the default, with its ads). */
    YOUTUBE,

    /** A downloaded file, played offline by the in-app player. */
    OFFLINE_FILE,

    /** A stream fetched with yt-dlp, played by the in-app player (opt-in). */
    DIRECT_STREAM,
}

/** The pure rules for choosing a player and for the words about storage. */
object DirectPlayback {
    /**
     * A downloaded file always wins (it is the person's own file and works offline). Otherwise the opt-in picks the in-app
     * player; with it off, YouTube's own player is used.
     */
    fun choose(optedIn: Boolean, hasDownloadedFile: Boolean): PlayerKind = when {
        hasDownloadedFile -> PlayerKind.OFFLINE_FILE
        optedIn -> PlayerKind.DIRECT_STREAM
        else -> PlayerKind.YOUTUBE
    }

    /** "12.4 MB", "1.2 GB", "0 B": base 1024, one decimal from MB up. */
    fun size(bytes: Long): String {
        val b = bytes.coerceAtLeast(0)
        return when {
            b < 1024L -> "$b B"
            b < 1024L * 1024 -> "${b / 1024} KB"
            b < 1024L * 1024 * 1024 -> "%.1f MB".format(java.util.Locale.US, b / (1024.0 * 1024))
            else -> "%.1f GB".format(java.util.Locale.US, b / (1024.0 * 1024 * 1024))
        }
    }

    /** "Downloading 42%", or "Downloading" before the first report. */
    fun progressText(percent: Float): String = if (percent < 0f) "Downloading" else "Downloading ${percent.toInt().coerceIn(0, 100)}%"
}

/**
 * The Shorts the person can swipe through from the card they opened: the ids of the Shorts in the list on screen, kept in
 * memory only. When the opened video is not in it, the player has just that video.
 */
object ShortsQueue {
    fun playlist(shortIds: List<String>, startId: String): List<String> =
        if (startId in shortIds) shortIds.distinct() else listOf(startId)

    fun indexOf(playlist: List<String>, startId: String): Int = playlist.indexOf(startId).coerceAtLeast(0)
}
