package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Amendment §3: water is a first-class metric, not a food entry with zero
 * calories. One row per drink. Volumes are millilitres; the display unit is a
 * setting like every other quantity.
 */
@Entity(
    tableName = "hydration_log",
    indices = [Index("loggedAt"), Index("deletedAt")],
)
data class HydrationLogEntity(
    @PrimaryKey override val id: String = newId(),
    val loggedAt: Long,
    val volumeMl: Double,
    /** The preset tapped, if any - kept so "undo last glass" is unambiguous. */
    val presetId: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/** A one-tap button on the Today hydration card ("500 ml bottle"). */
@Entity(tableName = "hydration_preset", indices = [Index("deletedAt")])
data class HydrationPresetEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val volumeMl: Double,
    val displayOrder: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
