package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * What the YouTube Data API said about one video. A cache and nothing else: it is **not** part of a backup, export or sync, and
 * every row is deleted 30 days after it was fetched (the YouTube API Services terms), which is why this table has no
 * `deletedAt` and is the one place the app hard-deletes. See `docs/VIDEOS.md`.
 */
@Entity(tableName = "video_cache", indices = [Index("fetchedAt")])
data class VideoCacheEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val description: String,
    val channelTitle: String,
    val categoryId: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val publishedAt: String,
    val viewCount: Long?,
    val fetchedAt: Long,
)

/** The result of one search (the words and the filters), as video ids in order, so a repeat costs no quota for 6 hours. */
@Entity(tableName = "video_search", indices = [Index("fetchedAt")])
data class VideoSearchEntity(
    @PrimaryKey val searchKey: String,
    /** Comma-joined video ids, in the order the API returned them. */
    val videoIds: String,
    val fetchedAt: Long,
)

/**
 * A video the person saved ("watch later"). The row is theirs (backed up, synced, soft-deleted); the snapshot columns are
 * API data, so they are cleared 30 days after [metaFetchedAt] and refilled the next time the video is opened with a key
 * or a pasted link. The video itself is never stored.
 */
@Serializable
@Entity(tableName = "saved_video", indices = [Index(value = ["videoId"]), Index("deletedAt")])
data class SavedVideoEntity(
    @PrimaryKey override val id: String = newId(),
    val videoId: String,
    val title: String? = null,
    val channelTitle: String? = null,
    val categoryId: String? = null,
    val durationSec: Int? = null,
    val thumbnailUrl: String? = null,
    val metaFetchedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
