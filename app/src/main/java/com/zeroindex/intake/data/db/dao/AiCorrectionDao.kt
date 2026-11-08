package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.AiCorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiCorrectionDao {
    @Upsert suspend fun upsert(correction: AiCorrectionEntity)

    @Query("SELECT * FROM ai_correction WHERE deletedAt IS NULL ORDER BY correctedAt DESC")
    fun observeAll(): Flow<List<AiCorrectionEntity>>

    @Query("SELECT * FROM ai_correction WHERE deletedAt IS NULL ORDER BY correctedAt")
    suspend fun all(): List<AiCorrectionEntity>

    @Query("SELECT COUNT(*) FROM ai_correction WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
