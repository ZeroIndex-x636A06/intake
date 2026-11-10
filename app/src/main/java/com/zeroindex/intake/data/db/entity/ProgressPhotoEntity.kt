package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.photos.PhotoAngle
import kotlinx.serialization.Serializable

/**
 * The metadata of one progress photo (You > Progress photos). The image itself is a file in the app's private
 * storage named [fileName]; the bytes are never synced, uploaded or put in the JSON export. A row synced from
 * another device points at a file this phone does not have, and the screen shows a placeholder for it.
 * Nothing is hard-deleted: [deletedAt] hides the photo; the file is kept.
 */
@Serializable
@Entity(tableName = "progress_photo", indices = [Index("takenAt"), Index("deletedAt")])
data class ProgressPhotoEntity(
    @PrimaryKey override val id: String = newId(),
    /** UTC epoch millis of the date the photo is for. */
    val takenAt: Long,
    val angle: PhotoAngle,
    val fileName: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
