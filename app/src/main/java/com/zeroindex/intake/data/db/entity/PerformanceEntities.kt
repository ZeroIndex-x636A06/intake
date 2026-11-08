package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Amendment §2: explosive athletic output tracked next to lifting volume.
 *
 * All lengths are centimetres (SPEC §3.5). Any two of the three determine the
 * third - total reach = standing reach + vertical jump - so each is nullable
 * and [com.zeroindex.intake.domain.performance.JumpMetrics] fills in the
 * missing one. Storing what was actually measured, rather than a derived
 * value, keeps a later correction to one field from silently rewriting the
 * others.
 */
@Serializable
@Entity(
    tableName = "performance_metric",
    indices = [Index("measuredAt"), Index("deletedAt")],
)
data class PerformanceMetricEntity(
    @PrimaryKey override val id: String = newId(),
    val measuredAt: Long,
    val verticalJumpCm: Double? = null,
    val standingReachCm: Double? = null,
    val totalReachCm: Double? = null,
    val notes: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
