package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.PerformanceMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceDao {
    @Upsert suspend fun upsert(metric: PerformanceMetricEntity)
    @Upsert suspend fun upsertAll(metrics: List<PerformanceMetricEntity>)

    @Query("SELECT * FROM performance_metric WHERE deletedAt IS NULL ORDER BY measuredAt")
    fun observeAll(): Flow<List<PerformanceMetricEntity>>

    @Query("SELECT * FROM performance_metric WHERE deletedAt IS NULL ORDER BY measuredAt")
    suspend fun all(): List<PerformanceMetricEntity>

    @Query(
        """
        SELECT * FROM performance_metric
        WHERE deletedAt IS NULL AND measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt
        """,
    )
    suspend fun inRange(startInclusive: Long, endExclusive: Long): List<PerformanceMetricEntity>

    @Query("UPDATE performance_metric SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
