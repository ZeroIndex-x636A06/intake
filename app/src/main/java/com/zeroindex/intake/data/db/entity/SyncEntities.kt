package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Amendment §3 / mockup v2: "Nothing is silently overwritten." When a sync
 * brings in a version of a row that was also edited here since the last
 * sync, last-writer-wins still decides what the row holds - so every device
 * agrees - but the losing version is kept here for the user to look at and,
 * if they want, restore.
 *
 * Local to this device: not exported, not synced.
 */
@Entity(tableName = "sync_conflict", indices = [Index("resolvedAt"), Index(value = ["tableName", "rowId"])])
data class SyncConflictEntity(
    @PrimaryKey override val id: String = newId(),
    val tableName: String,
    val rowId: String,
    /** The row as it is now, after the sync. */
    val keptJson: String,
    /** The version that lost. Restoring it writes it back with a new timestamp. */
    val otherJson: String,
    /** "this device" or "another device": where [otherJson] came from. */
    val otherSource: String,
    val detectedAt: Long,
    val resolvedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
