package com.zeroindex.intake.domain.videos

/** Reading YouTube links and durations. Pure. */
object YoutubeIds {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** The 11-character video id in a pasted link (watch, youtu.be, shorts, embed, live, mobile) or a bare id. */
    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (ID.matches(text)) return text
        val withScheme = if (text.startsWith("http://") || text.startsWith("https://")) text else "https://$text"
        val afterScheme = withScheme.substringAfter("://")
        val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
            .lowercase().removePrefix("www.").removePrefix("m.")
        val rest = afterScheme.substringAfter('/', "")
        val path = rest.substringBefore('?').substringBefore('#')
        val query = rest.substringAfter('?', "").substringBefore('#')
        val candidate: String? = when (host) {
            "youtu.be" -> path.substringBefore('/')
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when {
                path == "watch" -> query.split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                path.startsWith("shorts/") || path.startsWith("embed/") || path.startsWith("live/") || path.startsWith("v/") ->
                    path.substringAfter('/').substringBefore('/')
                else -> null
            }
            else -> null
        }
        return candidate?.takeIf { ID.matches(it) }
    }

    /**
     * The video's still picture, straight from its id. YouTube serves it for every video with no key and no API call,
     * so a pasted link, or a saved video whose details were never fetched, still has a picture.
     */
    fun thumbnailUrl(id: String): String = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    /** `https://www.youtube.com/watch?v=<id>`: for "Open in YouTube" and the share action. */
    fun watchUrl(id: String): String = "https://www.youtube.com/watch?v=$id"

    /** ISO 8601 duration as the API returns it ("PT1H2M3S", "PT45S", "P0D" for a live stream) in seconds. */
    fun durationSeconds(iso: String?): Int {
        val m = Regex("^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$").matchEntire(iso.orEmpty().trim()) ?: return 0
        fun g(i: Int) = m.groupValues[i].toIntOrNull() ?: 0
        return g(1) * 86_400 + g(2) * 3_600 + g(3) * 60 + g(4)
    }

    /** "28:30" or "1:02:03". */
    fun clock(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
