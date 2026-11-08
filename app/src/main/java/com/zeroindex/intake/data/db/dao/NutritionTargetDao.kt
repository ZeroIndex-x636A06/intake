package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.NutritionTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionTargetDao {
    @Upsert suspend fun upsert(target: NutritionTargetEntity)

    /**
     * The target in force at [atMillis]. SPEC §4 keeps targets as a dated
     * series, so "the target" is always relative to a moment.
     */
    @Query(
        """
        SELECT * FROM nutrition_target
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
        ORDER BY effectiveFrom DESC LIMIT 1
        """,
    )
    fun observeEffectiveAt(atMillis: Long): Flow<NutritionTargetEntity?>

    @Query(
        """
        SELECT * FROM nutrition_target
        WHERE deletedAt IS NULL AND effectiveFrom <= :atMillis
        ORDER BY effectiveFrom DESC LIMIT 1
        """,
    )
    suspend fun effectiveAt(atMillis: Long): NutritionTargetEntity?

    @Query("SELECT * FROM nutrition_target WHERE deletedAt IS NULL ORDER BY effectiveFrom DESC")
    fun observeHistory(): Flow<List<NutritionTargetEntity>>

    @Query("SELECT * FROM nutrition_target WHERE deletedAt IS NULL ORDER BY effectiveFrom")
    suspend fun all(): List<NutritionTargetEntity>

    @Query("UPDATE nutrition_target SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
