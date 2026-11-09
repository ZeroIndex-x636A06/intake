package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zeroindex.intake.data.db.entity.AiUsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageDao {
    @Insert suspend fun insert(row: AiUsageEntity)

    /** Everything logged since [since], oldest first. */
    @Query("SELECT * FROM ai_usage WHERE deletedAt IS NULL AND at >= :since ORDER BY at")
    fun observeSince(since: Long): Flow<List<AiUsageEntity>>

    @Query("SELECT * FROM ai_usage WHERE deletedAt IS NULL AND at >= :since ORDER BY at")
    suspend fun since(since: Long): List<AiUsageEntity>

    /** What counts against the monthly budget: spend from [since] on. */
    @Query("SELECT COALESCE(SUM(costUsd), 0.0) FROM ai_usage WHERE deletedAt IS NULL AND at >= :since")
    suspend fun spendSince(since: Long): Double

    @Query("SELECT COALESCE(SUM(costUsd), 0.0) FROM ai_usage WHERE deletedAt IS NULL AND at >= :since")
    fun observeSpendSince(since: Long): Flow<Double>
}
