package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Upsert suspend fun upsert(exercise: ExerciseEntity)
    @Upsert suspend fun upsertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT * FROM exercise WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercise WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY name")
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("UPDATE exercise SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
