package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressionRuleDao {
    @Upsert suspend fun upsert(rule: ProgressionRuleEntity)
    @Upsert suspend fun upsertAll(rules: List<ProgressionRuleEntity>)

    @Query("SELECT * FROM progression_rule WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ProgressionRuleEntity?

    @Query("SELECT * FROM progression_rule WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<ProgressionRuleEntity>>

    @Query("SELECT * FROM progression_rule WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<ProgressionRuleEntity>

    @Query("UPDATE progression_rule SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
