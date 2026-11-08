package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One local day of Health Connect data, cached so reports, the daily insight
 * and the target adjustment work offline and do not re-query on every frame.
 *
 * [activeKcalRaw] is what the wearable reported for tracked sessions;
 * [bmrKcalInWindows] is the user's BMR over those same minutes; the difference,
 * [activeKcalNet], is the only part ever added to a target (amendment §2) -
 * otherwise the resting burn would be counted twice, once in TDEE and again
 * in the workout.
 */
@Entity(
    tableName = "health_daily_summary",
    indices = [Index(value = ["dayStartMillis"], unique = true), Index("deletedAt")],
)
data class HealthDailySummaryEntity(
    @PrimaryKey override val id: String = newId(),
    val dayStartMillis: Long,
    val activeKcalRaw: Double? = null,
    val bmrKcalInWindows: Double? = null,
    val activeKcalNet: Double? = null,
    /** The device's whole-day burn estimate, BMR included - for reports, never added to a target. */
    val totalKcalBurned: Double? = null,
    val steps: Long? = null,
    val sleepMinutes: Long? = null,
    val sleepStartMillis: Long? = null,
    val restingHeartRate: Double? = null,
    val avgHeartRate: Double? = null,
    val syncedAt: Long,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

enum class BackupStatus { RUNNING, OK, FAILED }

/** One attempt at a nightly headless backup (amendment §2). */
@Entity(tableName = "backup_run", indices = [Index("startedAt"), Index("deletedAt")])
data class BackupRunEntity(
    @PrimaryKey override val id: String = newId(),
    val startedAt: Long,
    val finishedAt: Long? = null,
    val destination: String,
    val bytes: Long? = null,
    val status: BackupStatus,
    val error: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
