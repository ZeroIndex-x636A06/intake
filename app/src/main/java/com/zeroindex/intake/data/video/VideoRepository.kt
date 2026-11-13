package com.zeroindex.intake.data.video

import android.content.Context
import com.zeroindex.intake.data.db.dao.ProgramDao
import com.zeroindex.intake.data.db.dao.VideoDao
import com.zeroindex.intake.data.db.entity.SavedVideoEntity
import com.zeroindex.intake.data.db.entity.VideoCacheEntity
import com.zeroindex.intake.data.db.entity.VideoSearchEntity
import com.zeroindex.intake.data.remote.youtube.VideoError
import com.zeroindex.intake.data.remote.youtube.YoutubeApi
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.videos.VideoCachePolicy
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.VideoPaging
import com.zeroindex.intake.domain.videos.VideoQuota
import com.zeroindex.intake.domain.videos.YoutubeIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What this phone has spent of the day's YouTube quota. It only counts this app's calls, so the screen says "about". The
 * day rolls over at midnight Pacific time, which is when YouTube resets the quota.
 */
@Singleton
class VideoQuotaStore @Inject constructor(@ApplicationContext context: Context, private val clock: Clock) {
    private val prefs = context.getSharedPreferences("video_quota", Context.MODE_PRIVATE)

    private fun today(): String = java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().toString()

    fun usedUnits(): Int = if (prefs.getString("day", null) == today()) prefs.getInt("units", 0) else 0

    fun add(units: Int) {
        val used = usedUnits() + units
        prefs.edit().putString("day", today()).putInt("units", used).apply()
    }

    fun hint(): String = VideoQuota.hint(usedUnits())
}

/** One page of search results. [fromCache] is true when no quota was spent. [hasMore] is true while YouTube has a next page. */
data class VideoResults(val videos: List<VideoMeta>, val fromCache: Boolean, val fetchedAt: Long, val hasMore: Boolean = false)

/**
 * Videos: search with a cache (so a repeat costs no quota), saved videos, a pasted link, and the 30-day purge of API data.
 * Everything here is on demand from a screen the person opened; nothing runs in the background.
 */
@Singleton
class VideoRepository @Inject constructor(
    private val dao: VideoDao,
    private val programDao: ProgramDao,
    private val api: YoutubeApi,
    private val quota: VideoQuotaStore,
    private val clock: Clock,
) {
    fun hasKey(): Boolean = api.hasKey()
    fun quotaHint(): String = quota.hint()
    fun quotaUsedUnits(): Int = quota.usedUnits()

    /** "About N searches left today" for the overflow menu. */
    fun searchesLeft(): Int = VideoQuota.searchesLeft(quota.usedUnits())

    /** The first page of a search. See [searchPage]. */
    suspend fun search(query: String, filters: String = ""): VideoResults = searchPage(query, filters, 1)

    /**
     * Page [page] (1 based) of a search: `search.list` then `videos.list`, or the cache when that page is under six hours
     * old. [filters] only names the search for the cache; the words sent are [query]. Page 1 is cached under the plain search
     * key and later pages under their own, and each page stores the `nextPageToken` that leads on (in a `video_search` row
     * whose ids column holds the token, blank at the end), so this needs no schema change. A page is asked for only after the
     * page before it, and only in answer to the person scrolling.
     */
    suspend fun searchPage(query: String, filters: String, page: Int): VideoResults {
        val now = clock.nowMillis()
        val base = VideoCachePolicy.searchKey(query, filters)
        val pageKey = VideoPaging.pageKey(base, page)
        val tokenRow = dao.search(VideoPaging.tokenKey(base, page))
        dao.search(pageKey)?.let { hit ->
            if (tokenRow != null && VideoCachePolicy.isFresh(hit.fetchedAt, now, VideoCachePolicy.SEARCH_TTL_MS)) {
                val ids = hit.videoIds.split(',').filter { it.isNotBlank() }
                val cached = dao.cached(ids).associateBy { it.videoId }
                if (ids.all { it in cached }) {
                    return VideoResults(ids.map { cached.getValue(it).toMeta() }, true, hit.fetchedAt, hasMore = tokenRow.videoIds.isNotBlank())
                }
            }
        }
        if (!api.hasKey()) throw VideoError.NoKey
        val token: String? = if (page <= 1) {
            null
        } else {
            // No token for the page before means there is no such page.
            dao.search(VideoPaging.tokenKey(base, page - 1))?.videoIds?.takeIf { it.isNotBlank() }
                ?: return VideoResults(emptyList(), true, now, hasMore = false)
        }
        val found = api.searchPage(query, token).also { quota.add(VideoQuota.SEARCH_COST) }
        val videos = api.videos(found.ids).also { quota.add(VideoQuota.LIST_COST) }
        dao.upsertCache(videos.map { it.toCache(now) })
        dao.upsertSearch(VideoSearchEntity(pageKey, videos.joinToString(",") { it.id }, now))
        dao.upsertSearch(VideoSearchEntity(VideoPaging.tokenKey(base, page), found.nextPageToken.orEmpty(), now))
        return VideoResults(videos, false, now, hasMore = found.nextPageToken != null)
    }

    /** One video's details: the cache if it is under a day old, else `videos.list` with a key, else oEmbed (title and channel only). */
    suspend fun video(id: String): VideoMeta? {
        val now = clock.nowMillis()
        dao.cachedOne(id)?.let { if (VideoCachePolicy.isFresh(it.fetchedAt, now, VideoCachePolicy.VIDEO_TTL_MS)) return it.toMeta() }
        val fresh: VideoMeta? = if (api.hasKey()) {
            api.videos(listOf(id)).also { quota.add(VideoQuota.LIST_COST) }.firstOrNull()
        } else {
            api.oembed(id)
        }
        if (fresh != null) {
            // An oEmbed answer has no duration or description: it is not cached as if it were a full record.
            if (api.hasKey()) dao.upsertCache(listOf(fresh.toCache(now)))
            return fresh
        }
        return dao.cachedOne(id)?.toMeta() ?: savedVideoMeta(id)
    }

    /** "Paste a YouTube link": the id of the link and its details, or null when it is not a YouTube video link. */
    suspend fun fromLink(link: String): VideoMeta? {
        val id = YoutubeIds.parse(link) ?: return null
        return video(id) ?: VideoMeta(id = id, title = "YouTube video")
    }

    // ---- saved

    fun observeSaved(): Flow<List<SavedVideoEntity>> = dao.observeSaved()

    fun observeSavedMeta(): Flow<List<VideoMeta>> = dao.observeSaved().map { rows -> rows.map { it.toMeta() } }

    suspend fun isSaved(id: String): Boolean = dao.savedByVideoId(id)?.let { it.deletedAt == null } ?: false

    suspend fun save(meta: VideoMeta) {
        val now = clock.nowMillis()
        val existing = dao.savedByVideoId(meta.id)
        dao.upsertSaved(
            SavedVideoEntity(
                id = existing?.id ?: com.zeroindex.intake.data.db.entity.newId(),
                videoId = meta.id,
                title = meta.title.ifBlank { null },
                channelTitle = meta.channelTitle.ifBlank { null },
                categoryId = meta.categoryId.ifBlank { null },
                durationSec = meta.durationSec.takeIf { it > 0 },
                thumbnailUrl = meta.thumbnailUrl,
                metaFetchedAt = now,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )
    }

    /** Soft delete: the row stays, hidden. */
    suspend fun unsave(id: String) = dao.softDeleteSaved(id, clock.nowMillis())

    private suspend fun savedVideoMeta(id: String): VideoMeta? = dao.savedByVideoId(id)?.takeIf { it.deletedAt == null }?.toMeta()

    // ---- the workout made from a video

    /** The workout (standalone program) made from [videoId], if any, newest first. */
    suspend fun workoutIdFor(videoId: String): String? =
        programDao.workouts().firstOrNull { it.videoId == videoId }?.id

    // ---- the 30-day rule

    /**
     * Deletes cached API data older than 30 days and clears the stored snapshot of saved videos that old (the saves stay and
     * refill when the video is next opened). Cheap and local: called when the app starts and when Videos opens.
     */
    suspend fun purgeExpired() {
        val cutoff = VideoCachePolicy.purgeCutoff(clock.nowMillis())
        dao.purgeCache(cutoff)
        dao.purgeSearches(cutoff)
        dao.scrubSnapshots(cutoff)
    }

    /** "Clear video cache" for the person who wants it gone now. */
    suspend fun clearCache() {
        dao.clearCache()
        dao.clearSearches()
    }

    private fun VideoMeta.toCache(now: Long) = VideoCacheEntity(
        videoId = id, title = title, description = description, channelTitle = channelTitle, categoryId = categoryId,
        durationSec = durationSec, thumbnailUrl = thumbnailUrl, publishedAt = publishedAt, viewCount = viewCount, fetchedAt = now,
    )

    private fun VideoCacheEntity.toMeta() = VideoMeta(
        id = videoId, title = title, description = description, channelTitle = channelTitle, categoryId = categoryId,
        durationSec = durationSec, thumbnailUrl = thumbnailUrl, publishedAt = publishedAt, viewCount = viewCount,
    )

    private fun SavedVideoEntity.toMeta() = VideoMeta(
        id = videoId, title = title ?: "Saved video", channelTitle = channelTitle.orEmpty(), categoryId = categoryId.orEmpty(),
        durationSec = durationSec ?: 0, thumbnailUrl = thumbnailUrl,
    )
}
