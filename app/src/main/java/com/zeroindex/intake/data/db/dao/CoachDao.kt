package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CoachDao {
    @Upsert suspend fun upsert(row: CoachSuggestionEntity)
    @Upsert suspend fun upsertAll(rows: List<CoachSuggestionEntity>)

    @Query("SELECT * FROM coach_suggestion WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): CoachSuggestionEntity?

    @Query("SELECT * FROM coach_suggestion WHERE deletedAt IS NULL ORDER BY checkInDay DESC, priority, suggestionKey")
    fun observeAll(): Flow<List<CoachSuggestionEntity>>

    @Query("SELECT * FROM coach_suggestion WHERE deletedAt IS NULL AND checkInDay >= :fromDay")
    suspend fun since(fromDay: Long): List<CoachSuggestionEntity>

    @Query("SELECT COUNT(*) FROM coach_suggestion WHERE deletedAt IS NULL AND status = 'PENDING' AND checkInDay >= :fromDay")
    fun observePendingCount(fromDay: Long): Flow<Int>

    @Query("SELECT * FROM coach_suggestion WHERE deletedAt IS NULL AND checkInDay = :day")
    suspend fun forCheckIn(day: Long): List<CoachSuggestionEntity>

    @Query("UPDATE coach_suggestion SET status = :status, decidedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun decide(id: String, status: String, now: Long)
}
