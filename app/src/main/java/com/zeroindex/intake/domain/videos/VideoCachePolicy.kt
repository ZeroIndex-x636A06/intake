package com.zeroindex.intake.domain.videos

/**
 * How long video data from the YouTube API may be kept. The YouTube API Services terms let an app store API data for at most
 * 30 days, so everything cached is removed after [MAX_AGE_MS]. Search results are reused for [SEARCH_TTL_MS] to save the
 * person's quota; a video's own details are reused for [VIDEO_TTL_MS].
 */
object VideoCachePolicy {
    const val HOUR_MS = 3_600_000L
    const val DAY_MS = 24 * HOUR_MS

    /** Search results and the Recommended page: six hours. */
    const val SEARCH_TTL_MS = 6 * HOUR_MS

    /** Details of one video (title, duration, category): a day. */
    const val VIDEO_TTL_MS = DAY_MS

    /** The hard limit from the API terms. Nothing cached is kept longer. */
    const val MAX_AGE_MS = 30 * DAY_MS

    fun isFresh(fetchedAt: Long, now: Long, ttlMs: Long): Boolean = now - fetchedAt in 0 until ttlMs

    /** Rows fetched before this moment are deleted. */
    fun purgeCutoff(now: Long): Long = now - MAX_AGE_MS

    fun isExpired(fetchedAt: Long, now: Long): Boolean = fetchedAt < purgeCutoff(now)

    /** The cache key of a search: the same words and filters give the same key whatever the case or spacing. */
    fun searchKey(query: String, filters: String = ""): String =
        query.trim().lowercase().replace(Regex("\\s+"), " ") + "|" + filters.trim().lowercase()
}

/**
 * A local estimate of the day's YouTube quota. The free quota is 10,000 units a day; `search.list` costs 100 and
 * `videos.list` costs 1, so roughly 100 searches. The counter only knows what this phone spent (the key may be used
 * elsewhere), so the text says "about".
 */
object VideoQuota {
    const val DAILY_UNITS = 10_000
    const val SEARCH_COST = 100
    const val LIST_COST = 1

    fun searchesLeft(usedUnits: Int): Int = ((DAILY_UNITS - usedUnits).coerceAtLeast(0)) / SEARCH_COST

    fun hint(usedUnits: Int): String {
        val left = searchesLeft(usedUnits)
        return when {
            usedUnits <= 0 -> "A free key allows about 100 searches a day."
            left <= 0 -> "About all of today's free searches are used on this phone."
            else -> "About $left searches left today (a free key allows about 100)."
        }
    }
}
