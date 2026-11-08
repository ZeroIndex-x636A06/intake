package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HydrationDao {
    @Upsert suspend fun upsertLog(log: HydrationLogEntity)
    @Upsert suspend fun upsertLogs(logs: List<HydrationLogEntity>)
    @Upsert suspend fun upsertPreset(preset: HydrationPresetEntity)
    @Upsert suspend fun upsertPresets(presets: List<HydrationPresetEntity>)

    @Query(
        """
        SELECT * FROM hydration_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        ORDER BY loggedAt DESC
        """,
    )
    fun observeLogsInRange(startInclusive: Long, endExclusive: Long): Flow<List<HydrationLogEntity>>

    @Query(
        """
        SELECT * FROM hydration_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        ORDER BY loggedAt
        """,
    )
    suspend fun logsInRange(startInclusive: Long, endExclusive: Long): List<HydrationLogEntity>

    @Query("SELECT * FROM hydration_log WHERE deletedAt IS NULL")
    suspend fun allLogs(): List<HydrationLogEntity>

    @Query("UPDATE hydration_log SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteLog(id: String, now: Long)

    @Query("SELECT * FROM hydration_preset WHERE deletedAt IS NULL ORDER BY displayOrder")
    fun observePresets(): Flow<List<HydrationPresetEntity>>

    @Query("SELECT * FROM hydration_preset WHERE deletedAt IS NULL ORDER BY displayOrder")
    suspend fun presets(): List<HydrationPresetEntity>

    /** Counts soft-deleted rows too, so deleting every preset never re-seeds. */
    @Query("SELECT COUNT(*) FROM hydration_preset")
    suspend fun presetRowCount(): Int

    @Query("UPDATE hydration_preset SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeletePreset(id: String, now: Long)

    /** Every row touched since [since], deleted rows included, for Health Connect sync. */
    @Query("SELECT * FROM hydration_log WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<HydrationLogEntity>

    /** Emits whenever the table changes; the sync debounces on it. */
    @Query("SELECT MAX(updatedAt) FROM hydration_log")
    fun observeLastChange(): Flow<Long?>
}
