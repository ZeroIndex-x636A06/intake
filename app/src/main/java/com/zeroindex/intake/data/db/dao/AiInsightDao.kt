package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.ModelBenchmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiInsightDao {
    @Upsert suspend fun upsert(insight: AiInsightEntity)
    @Upsert suspend fun upsertAll(insights: List<AiInsightEntity>)

    @Query(
        "SELECT * FROM ai_insight WHERE dayStartMillis = :dayStart AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1",
    )
    fun observeForDay(dayStart: Long): Flow<AiInsightEntity?>

    @Query(
        "SELECT * FROM ai_insight WHERE dayStartMillis = :dayStart AND deletedAt IS NULL ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun forDay(dayStart: Long): AiInsightEntity?

    @Query("SELECT * FROM ai_insight WHERE deletedAt IS NULL")
    suspend fun all(): List<AiInsightEntity>

    @Query("UPDATE ai_insight SET dismissedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun dismiss(id: String, now: Long)

    @Upsert suspend fun upsertBenchmarks(results: List<ModelBenchmarkEntity>)

    @Query("SELECT * FROM model_benchmark WHERE deletedAt IS NULL ORDER BY task, accuracy DESC")
    fun observeBenchmarks(): Flow<List<ModelBenchmarkEntity>>

    @Query("SELECT * FROM model_benchmark WHERE deletedAt IS NULL")
    suspend fun allBenchmarks(): List<ModelBenchmarkEntity>
}
