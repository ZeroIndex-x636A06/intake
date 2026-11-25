package com.zeroindex.intake.domain.videos

/** Why direct playback (yt-dlp) did not give a stream or captions, in the words the person should read. */
enum class DirectFailure {
    /** No connection: the phone is offline, or the host could not be reached. */
    NO_NETWORK,

    /** It took longer than [DirectFailure.STREAM_TIMEOUT_MS]. */
    TIMEOUT,

    /** The connection worked but yt-dlp could not read the video (YouTube changed, the video is blocked or gone). */
    EXTRACTION;

    /** The note shown with the player when the video falls back to YouTube's own player. */
    fun playbackNote(): String = when (this) {
        NO_NETWORK -> "No connection, so the video could not be fetched directly. YouTube's player needs a connection too."
        TIMEOUT -> "Fetching the video directly took too long, so YouTube's player is used."
        EXTRACTION -> "Direct playback did not work (YouTube may have changed), so YouTube's player is used."
    }

    fun captionsMessage(): String = when (this) {
        NO_NETWORK -> "No connection, so the captions could not be fetched. Connect and try again, or paste the transcript below."
        TIMEOUT -> "Fetching the captions took too long. Try again, or paste the transcript below."
        EXTRACTION -> "Could not get the captions. YouTube may have changed. Try again later, or paste the transcript below."
    }

    fun downloadMessage(): String = when (this) {
        NO_NETWORK -> "No connection. Connect and try again."
        else -> "The download failed. Check your connection and try again."
    }

    companion object {
        /** How long the page waits for yt-dlp to produce a stream before it plays with YouTube's player instead. */
        const val STREAM_TIMEOUT_MS = 20_000L

        private val NETWORK_WORDS = listOf(
            "unknownhost", "unable to resolve host", "name resolution", "temporary failure in name", "nodename nor servname",
            "network is unreachable", "no route to host", "connection refused", "connection reset", "connection aborted",
            "failed to connect", "unable to download webpage", "urlopen error", "timed out", "no address associated",
            "sockettimeout", "connecttimeout", "ssl: ", "transporterror",
        )

        /**
         * Sorts an error from the downloader. [hasNetwork] false is conclusive. Otherwise the error text (and its causes) is
         * checked for the usual connection failures; anything else is an extraction failure. The text is only read, never kept.
         */
        fun classify(hasNetwork: Boolean, vararg messages: String?): DirectFailure {
            if (!hasNetwork) return NO_NETWORK
            val text = messages.filterNotNull().joinToString(" ").lowercase()
            return if (NETWORK_WORDS.any { it in text }) NO_NETWORK else EXTRACTION
        }

        /** The class names and messages of [e] and its causes, for [classify]. */
        fun messagesOf(e: Throwable): Array<String?> {
            val out = mutableListOf<String?>()
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth < 6) {
                out += t.javaClass.simpleName
                out += t.message
                t = t.cause
                depth++
            }
            return out.toTypedArray()
        }
    }
}
