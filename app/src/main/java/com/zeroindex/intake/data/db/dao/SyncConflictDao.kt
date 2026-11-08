package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConflictDao {
    @Upsert suspend fun upsert(conflict: SyncConflictEntity)

    @Query("SELECT * FROM sync_conflict WHERE resolvedAt IS NULL AND deletedAt IS NULL ORDER BY detectedAt DESC")
    fun observeOpen(): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflict WHERE id = :id")
    suspend fun byId(id: String): SyncConflictEntity?
}
