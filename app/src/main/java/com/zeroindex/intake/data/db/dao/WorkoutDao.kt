package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.db.projection.MuscleSetCount
import com.zeroindex.intake.data.db.projection.SessionStat
import com.zeroindex.intake.data.db.projection.SetPoint
import com.zeroindex.intake.data.db.projection.SetWithExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Upsert suspend fun upsertSession(session: WorkoutSessionEntity)
    @Upsert suspend fun upsertSessions(sessions: List<WorkoutSessionEntity>)
    @Upsert suspend fun upsertSet(set: SetLogEntity)
    @Upsert suspend fun upsertSets(sets: List<SetLogEntity>)

    /** True when the table holds any row at all, deleted or not. Used once, to spot an install with data. */
    @Query("SELECT EXISTS(SELECT 1 FROM workout_session)")
    suspend fun hasAnySession(): Boolean

    @Query("SELECT * FROM workout_session WHERE id = :id AND deletedAt IS NULL")
    suspend fun findSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_session WHERE deletedAt IS NULL ORDER BY startedAt")
    suspend fun allSessions(): List<WorkoutSessionEntity>

    /** An unfinished session is the one to resume. */
    @Query(
        "SELECT * FROM workout_session WHERE deletedAt IS NULL AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1",
    )
    fun observeInProgress(): Flow<WorkoutSessionEntity?>

    @Query(
        "SELECT * FROM workout_session WHERE deletedAt IS NULL AND finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun inProgress(): WorkoutSessionEntity?

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY completedAt, setIndex")
    fun observeSets(sessionId: String): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY completedAt, setIndex")
    suspend fun setsOf(sessionId: String): List<SetLogEntity>

    @Query("SELECT * FROM set_log WHERE deletedAt IS NULL ORDER BY completedAt")
    suspend fun allSets(): List<SetLogEntity>

    /**
     * The working sets from the most recent finished session that trained
     * [exerciseId]. SPEC §10 feeds exactly this into the progression rules and
     * shows it inline on the active workout screen.
     */
    @Query(
        """
        SELECT * FROM set_log
        WHERE deletedAt IS NULL AND isWarmup = 0 AND exerciseId = :exerciseId
          AND sessionId = (
            SELECT s.id FROM workout_session s
            JOIN set_log l ON l.sessionId = s.id
            WHERE s.deletedAt IS NULL AND l.deletedAt IS NULL
              AND l.exerciseId = :exerciseId AND l.isWarmup = 0
              AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
            ORDER BY s.startedAt DESC LIMIT 1
          )
        ORDER BY setIndex
        """,
    )
    suspend fun lastWorkingSetsFor(
        exerciseId: String,
        excludeSessionId: String? = null,
    ): List<SetLogEntity>

    @Query(
        """
        SELECT completedAt AS completedAt, weightKg AS weightKg, reps AS reps
        FROM set_log
        WHERE deletedAt IS NULL AND isWarmup = 0 AND exerciseId = :exerciseId
        ORDER BY completedAt
        """,
    )
    fun observeSetPoints(exerciseId: String): Flow<List<SetPoint>>

    /** Every non-deleted set for an exercise, newest first. */
    @Query("SELECT * FROM set_log WHERE exerciseId = :exerciseId AND deletedAt IS NULL ORDER BY completedAt DESC")
    fun observeSetsForExercise(exerciseId: String): Flow<List<SetLogEntity>>

    /** SPEC §10: weekly working sets per muscle group, to flag anything under 10. */
    @Query(
        """
        SELECT e.primaryMuscle AS primaryMuscle, COUNT(*) AS setCount
        FROM set_log l
        JOIN exercise e ON e.id = l.exerciseId
        WHERE l.deletedAt IS NULL AND e.deletedAt IS NULL AND l.isWarmup = 0
          AND l.completedAt >= :startInclusive AND l.completedAt < :endExclusive
        GROUP BY e.primaryMuscle
        ORDER BY setCount DESC
        """,
    )
    fun observeWeeklySetsPerMuscle(
        startInclusive: Long,
        endExclusive: Long,
    ): Flow<List<MuscleSetCount>>

    /** Every working set in a window, for weekly tonnage (amendment §2 overlay). */
    @Query(
        """
        SELECT completedAt AS completedAt, weightKg AS weightKg, reps AS reps
        FROM set_log
        WHERE deletedAt IS NULL AND isWarmup = 0
          AND completedAt >= :startInclusive AND completedAt < :endExclusive
        ORDER BY completedAt
        """,
    )
    fun observeWorkingSetsInRange(startInclusive: Long, endExclusive: Long): Flow<List<SetPoint>>

    @Query("UPDATE workout_session SET finishedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun finishSession(id: String, now: Long)

    @Query("UPDATE workout_session SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSession(id: String, now: Long)

    @Query("UPDATE set_log SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSet(id: String, now: Long)

    /** Every row touched since [since], deleted rows included, for Health Connect sync. */
    @Query("SELECT * FROM workout_session WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<WorkoutSessionEntity>

    /** Emits whenever the table changes; the sync debounces on it. */
    @Query("SELECT MAX(updatedAt) FROM workout_session")
    fun observeLastChange(): Flow<Long?>

    /** Every logged set in a window with its exercise's muscles, for the weekly volume (warm-ups included; the maths skips them). */
    @Query(
        """
        SELECT l.exerciseId AS exerciseId, e.name AS exerciseName, e.primaryMuscle AS primaryMuscle,
               e.secondaryMuscles AS secondaryMuscles, l.weightKg AS weightKg, l.reps AS reps,
               l.completedAt AS completedAt, l.isWarmup AS isWarmup
        FROM set_log l JOIN exercise e ON e.id = l.exerciseId
        WHERE l.deletedAt IS NULL AND l.completedAt >= :startInclusive AND l.completedAt < :endExclusive
        ORDER BY l.completedAt
        """,
    )
    fun observeSetsWithExercise(startInclusive: Long, endExclusive: Long): Flow<List<SetWithExercise>>

    @Query(
        """
        SELECT l.exerciseId AS exerciseId, e.name AS exerciseName, e.primaryMuscle AS primaryMuscle,
               e.secondaryMuscles AS secondaryMuscles, l.weightKg AS weightKg, l.reps AS reps,
               l.completedAt AS completedAt, l.isWarmup AS isWarmup
        FROM set_log l JOIN exercise e ON e.id = l.exerciseId
        WHERE l.deletedAt IS NULL AND l.sessionId = :sessionId
        ORDER BY l.completedAt, l.setIndex
        """,
    )
    suspend fun setsWithExerciseOf(sessionId: String): List<SetWithExercise>

    /** Working sets of earlier sessions for some exercises: what a record in [sessionId] is measured against. */
    @Query(
        """
        SELECT * FROM set_log
        WHERE deletedAt IS NULL AND isWarmup = 0 AND sessionId != :sessionId AND completedAt < :before
          AND exerciseId IN (:exerciseIds)
        """,
    )
    suspend fun historyBefore(sessionId: String, before: Long, exerciseIds: List<String>): List<SetLogEntity>

    /** Finished sessions, newest first, with volume and set counts. */
    @Query(
        """
        SELECT s.id AS sessionId, s.startedAt AS startedAt, s.finishedAt AS finishedAt, d.name AS dayName,
               COALESCE(SUM(CASE WHEN l.isWarmup = 0 THEN l.weightKg * l.reps END), 0.0) AS volumeKg,
               COUNT(CASE WHEN l.isWarmup = 0 THEN 1 END) AS workingSets
        FROM workout_session s
        LEFT JOIN program_day d ON d.id = s.programDayId
        LEFT JOIN set_log l ON l.sessionId = s.id AND l.deletedAt IS NULL
        WHERE s.deletedAt IS NULL AND s.finishedAt IS NOT NULL
        GROUP BY s.id
        ORDER BY s.startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeSessionStats(limit: Int): Flow<List<SessionStat>>

    @Query(
        """
        SELECT * FROM set_log
        WHERE deletedAt IS NULL AND isWarmup = 0
          AND completedAt >= :startInclusive AND completedAt < :endExclusive
        ORDER BY completedAt
        """,
    )
    suspend fun workingSetsInRange(startInclusive: Long, endExclusive: Long): List<SetLogEntity>
}
