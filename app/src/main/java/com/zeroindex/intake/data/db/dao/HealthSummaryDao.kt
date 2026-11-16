package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.BackupRunEntity
import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthSummaryDao {
    @Upsert suspend fun upsert(summary: HealthDailySummaryEntity)
    @Upsert suspend fun upsertAll(summaries: List<HealthDailySummaryEntity>)

    @Query("SELECT * FROM health_daily_summary WHERE dayStartMillis = :dayStart AND deletedAt IS NULL")
    suspend fun forDay(dayStart: Long): HealthDailySummaryEntity?

    @Query("SELECT * FROM health_daily_summary WHERE dayStartMillis = :dayStart AND deletedAt IS NULL")
    fun observeForDay(dayStart: Long): Flow<HealthDailySummaryEntity?>

    @Query(
        """
        SELECT * FROM health_daily_summary
        WHERE deletedAt IS NULL AND dayStartMillis >= :startInclusive AND dayStartMillis < :endExclusive
        ORDER BY dayStartMillis
        """,
    )
    suspend fun inRange(startInclusive: Long, endExclusive: Long): List<HealthDailySummaryEntity>

    /** The days in a range, live, for the Progress charts. */
    @Query(
        """
        SELECT * FROM health_daily_summary
        WHERE deletedAt IS NULL AND dayStartMillis >= :startInclusive AND dayStartMillis < :endExclusive
        ORDER BY dayStartMillis
        """,
    )
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<HealthDailySummaryEntity>>

    @Query("SELECT * FROM health_daily_summary WHERE deletedAt IS NULL")
    suspend fun all(): List<HealthDailySummaryEntity>

    @Upsert suspend fun upsertBackupRun(run: BackupRunEntity)

    @Query("SELECT * FROM backup_run WHERE deletedAt IS NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeBackupRuns(limit: Int = 10): Flow<List<BackupRunEntity>>
}
