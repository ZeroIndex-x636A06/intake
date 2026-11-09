package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.hydration.DrinkType
import kotlinx.serialization.Serializable

/**
 * Amendment §3: water is a first-class metric, not a food entry with zero
 * calories. One row per drink. Volumes are millilitres; the display unit is a
 * setting like every other quantity.
 */
@Serializable
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
    /**
     * Hydration v2 (schema v3). Rows from v2, and v2 backup or sync rows with no such keys, decode to
     * plain water: factor 1, no caffeine, no alcohol - exactly what they meant before.
     */
    @ColumnInfo(defaultValue = "WATER") val drinkType: DrinkType = DrinkType.WATER,
    /** What one ml of this drink counts for, frozen when logged so editing a factor later never rewrites history. */
    @ColumnInfo(defaultValue = "1.0") val hydrationFactor: Double = 1.0,
    @ColumnInfo(defaultValue = "0.0") val caffeineMg: Double = 0.0,
    /** Millilitres of pure alcohol in the drink. */
    @ColumnInfo(defaultValue = "0.0") val alcoholMl: Double = 0.0,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited {
    /** What this drink counts toward the day's water. Negative for spirits. */
    val effectiveMl: Double get() = volumeMl * hydrationFactor
}

/** A one-tap button on the Today hydration card ("500 ml bottle"). */
@Serializable
@Entity(tableName = "hydration_preset", indices = [Index("deletedAt")])
data class HydrationPresetEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val volumeMl: Double,
    val displayOrder: Int,
    @ColumnInfo(defaultValue = "WATER") val drinkType: DrinkType = DrinkType.WATER,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
