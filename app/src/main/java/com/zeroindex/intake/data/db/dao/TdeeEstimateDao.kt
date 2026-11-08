package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.TdeeEstimateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TdeeEstimateDao {
    @Upsert suspend fun upsert(estimate: TdeeEstimateEntity)

    @Query("SELECT * FROM tdee_estimate WHERE deletedAt IS NULL ORDER BY calculatedFor DESC LIMIT 1")
    fun observeLatest(): Flow<TdeeEstimateEntity?>

    @Query("SELECT * FROM tdee_estimate WHERE deletedAt IS NULL ORDER BY calculatedFor DESC LIMIT 1")
    suspend fun latest(): TdeeEstimateEntity?

    @Query("SELECT * FROM tdee_estimate WHERE deletedAt IS NULL AND calculatedFor = :dayStart")
    suspend fun forDay(dayStart: Long): TdeeEstimateEntity?

    @Query("SELECT * FROM tdee_estimate WHERE deletedAt IS NULL ORDER BY calculatedFor")
    suspend fun all(): List<TdeeEstimateEntity>

    @Query(
        """
        SELECT * FROM tdee_estimate
        WHERE deletedAt IS NULL AND calculatedFor >= :startInclusive AND calculatedFor < :endExclusive
        ORDER BY calculatedFor
        """,
    )
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<TdeeEstimateEntity>>
}
