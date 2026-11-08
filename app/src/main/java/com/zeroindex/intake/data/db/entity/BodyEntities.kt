package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.TdeeConfidence

/**
 * A weigh-in, optionally with tape measurements and a photo.
 * Lengths in centimetres, weight in kilograms (SPEC §3.5).
 */
@Entity(tableName = "body_metric", indices = [Index("measuredAt"), Index("deletedAt")])
data class BodyMetricEntity(
    @PrimaryKey override val id: String = newId(),
    val measuredAt: Long,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    /** Amendment §3: read from Health Connect when a scale reports it. */
    val skeletalMuscleKg: Double? = null,
    /** MANUAL, or HEALTH_CONNECT for rows imported from a smart scale. */
    @ColumnInfo(defaultValue = "MANUAL") val source: String = "MANUAL",
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipCm: Double? = null,
    val notes: String? = null,
    /** App-private storage, excluded from the media scanner (SPEC §11). */
    val photoUri: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * SPEC §4: targets are a dated series rather than a single row, so history
 * stays accurate when goals change. The row in effect for a given day is the
 * newest one whose [effectiveFrom] is not in the future.
 */
@Entity(tableName = "nutrition_target", indices = [Index("effectiveFrom"), Index("deletedAt")])
data class NutritionTargetEntity(
    @PrimaryKey override val id: String = newId(),
    val effectiveFrom: Long,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val isAutoCalculated: Boolean,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * Written nightly by WorkManager from the user's own intake and weight trend
 * (SPEC §8). Never a formula estimate once there is real data.
 */
@Entity(tableName = "tdee_estimate", indices = [Index("calculatedFor"), Index("deletedAt")])
data class TdeeEstimateEntity(
    @PrimaryKey override val id: String = newId(),
    /** Start-of-local-day UTC millis for the day this estimate describes. */
    val calculatedFor: Long,
    val estimatedTdee: Double,
    val confidence: TdeeConfidence,
    val rollingIntakeAvg: Double,
    /** Kilograms per day; negative while losing. */
    val weightTrendSlope: Double,
    val daysOfData: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
