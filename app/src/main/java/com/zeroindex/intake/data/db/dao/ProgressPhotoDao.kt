package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.ProgressPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressPhotoDao {
    @Upsert suspend fun upsert(row: ProgressPhotoEntity)

    @Query("SELECT * FROM progress_photo WHERE deletedAt IS NULL ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT MAX(takenAt) FROM progress_photo WHERE deletedAt IS NULL")
    fun observeNewestAt(): Flow<Long?>

    @Query("UPDATE progress_photo SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)
}
