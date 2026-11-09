package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricDao {
    @Upsert suspend fun upsert(metric: BodyMetricEntity)

    /** True when the table holds any row at all, deleted or not. Used once, to spot an install with data. */
    @Query("SELECT EXISTS(SELECT 1 FROM body_metric)")
    suspend fun hasAnyRow(): Boolean
    @Upsert suspend fun upsertAll(metrics: List<BodyMetricEntity>)

    @Query("SELECT * FROM body_metric WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): BodyMetricEntity?

    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<BodyMetricEntity>>

    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL ORDER BY measuredAt")
    suspend fun all(): List<BodyMetricEntity>

    @Query(
        """
        SELECT * FROM body_metric
        WHERE deletedAt IS NULL AND measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt
        """,
    )
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<BodyMetricEntity>>

    /** Ascending, for the trend smoothing in SPEC §8. */
    @Query(
        """
        SELECT * FROM body_metric
        WHERE deletedAt IS NULL AND measuredAt >= :startInclusive AND measuredAt < :endExclusive
        ORDER BY measuredAt
        """,
    )
    suspend fun inRange(startInclusive: Long, endExclusive: Long): List<BodyMetricEntity>

    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(): Flow<BodyMetricEntity?>

    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): BodyMetricEntity?

    @Query(
        "SELECT * FROM body_metric WHERE deletedAt IS NULL AND photoUri IS NOT NULL ORDER BY measuredAt DESC",
    )
    fun observeWithPhotos(): Flow<List<BodyMetricEntity>>

    @Query("UPDATE body_metric SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** Every row touched since [since], deleted rows included, for Health Connect sync. */
    @Query("SELECT * FROM body_metric WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<BodyMetricEntity>

    /** Emits whenever the table changes; the sync debounces on it. */
    @Query("SELECT MAX(updatedAt) FROM body_metric")
    fun observeLastChange(): Flow<Long?>

    /** A row near [at], to avoid importing a weigh-in twice. */
    @Query("SELECT * FROM body_metric WHERE deletedAt IS NULL AND measuredAt BETWEEN :at - :window AND :at + :window LIMIT 1")
    suspend fun near(at: Long, window: Long): BodyMetricEntity?
}
