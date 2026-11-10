package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.SavedVideoEntity
import com.zeroindex.intake.data.db.entity.VideoCacheEntity
import com.zeroindex.intake.data.db.entity.VideoSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    // ---- cache (never backed up; deleted after 30 days)
    @Upsert suspend fun upsertCache(rows: List<VideoCacheEntity>)
    @Upsert suspend fun upsertSearch(row: VideoSearchEntity)

    @Query("SELECT * FROM video_cache WHERE videoId IN (:ids)")
    suspend fun cached(ids: List<String>): List<VideoCacheEntity>

    @Query("SELECT * FROM video_cache WHERE videoId = :id")
    suspend fun cachedOne(id: String): VideoCacheEntity?

    @Query("SELECT * FROM video_search WHERE searchKey = :key")
    suspend fun search(key: String): VideoSearchEntity?

    /** Removes API data older than the cut-off. Returns the rows removed (cache and searches). */
    @Query("DELETE FROM video_cache WHERE fetchedAt < :cutoff")
    suspend fun purgeCache(cutoff: Long): Int

    @Query("DELETE FROM video_search WHERE fetchedAt < :cutoff")
    suspend fun purgeSearches(cutoff: Long): Int

    @Query("DELETE FROM video_cache")
    suspend fun clearCache(): Int

    @Query("DELETE FROM video_search")
    suspend fun clearSearches(): Int

    // ---- saved videos
    @Upsert suspend fun upsertSaved(row: SavedVideoEntity)

    @Query("SELECT * FROM saved_video WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeSaved(): Flow<List<SavedVideoEntity>>

    @Query("SELECT * FROM saved_video WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun saved(): List<SavedVideoEntity>

    @Query("SELECT * FROM saved_video WHERE videoId = :videoId ORDER BY deletedAt IS NULL DESC, updatedAt DESC LIMIT 1")
    suspend fun savedByVideoId(videoId: String): SavedVideoEntity?

    @Query("UPDATE saved_video SET deletedAt = :now, updatedAt = :now WHERE videoId = :videoId AND deletedAt IS NULL")
    suspend fun softDeleteSaved(videoId: String, now: Long)

    /** Clears the stored API snapshot of saved videos whose snapshot is older than the cut-off. The save itself stays. */
    @Query(
        "UPDATE saved_video SET title = NULL, channelTitle = NULL, categoryId = NULL, durationSec = NULL, thumbnailUrl = NULL, " +
            "metaFetchedAt = NULL WHERE metaFetchedAt IS NOT NULL AND metaFetchedAt < :cutoff",
    )
    suspend fun scrubSnapshots(cutoff: Long): Int
}
