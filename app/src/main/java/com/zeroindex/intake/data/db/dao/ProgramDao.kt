package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Upsert suspend fun upsertProgram(program: ProgramEntity)
    @Upsert suspend fun upsertDay(day: ProgramDayEntity)
    @Upsert suspend fun upsertDays(days: List<ProgramDayEntity>)
    @Upsert suspend fun upsertExercise(exercise: ProgramExerciseEntity)
    @Upsert suspend fun upsertExercises(exercises: List<ProgramExerciseEntity>)

    /** The program list: saved single-session workouts are not programs and live in [observeWorkouts]. */
    @Query("SELECT * FROM program WHERE deletedAt IS NULL AND isStandalone = 0 ORDER BY name")
    fun observeAll(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program WHERE deletedAt IS NULL AND isStandalone = 1 ORDER BY name")
    fun observeWorkouts(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program WHERE deletedAt IS NULL AND isStandalone = 1 ORDER BY name")
    suspend fun workouts(): List<ProgramEntity>

    @Query("SELECT * FROM program WHERE deletedAt IS NULL ORDER BY name")
    suspend fun all(): List<ProgramEntity>

    @Query("SELECT * FROM program WHERE deletedAt IS NULL AND isActive = 1 LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM program WHERE deletedAt IS NULL AND isActive = 1 LIMIT 1")
    suspend fun active(): ProgramEntity?

    @Query("SELECT * FROM program WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): ProgramEntity?

    @Query("SELECT * FROM program WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: String): Flow<ProgramEntity?>

    @Query("SELECT * FROM program_day WHERE programId = :programId AND deletedAt IS NULL ORDER BY dayIndex")
    fun observeDays(programId: String): Flow<List<ProgramDayEntity>>

    @Query("SELECT * FROM program_day WHERE programId = :programId AND deletedAt IS NULL ORDER BY dayIndex")
    suspend fun daysOf(programId: String): List<ProgramDayEntity>

    @Query("SELECT * FROM program_day WHERE deletedAt IS NULL")
    suspend fun allDays(): List<ProgramDayEntity>

    @Query("SELECT * FROM program_day WHERE id = :id AND deletedAt IS NULL")
    suspend fun findDay(id: String): ProgramDayEntity?

    @Query(
        "SELECT * FROM program_exercise WHERE programDayId = :programDayId AND deletedAt IS NULL ORDER BY displayOrder",
    )
    fun observeExercises(programDayId: String): Flow<List<ProgramExerciseEntity>>

    @Query(
        "SELECT * FROM program_exercise WHERE programDayId = :programDayId AND deletedAt IS NULL ORDER BY displayOrder",
    )
    suspend fun exercisesOf(programDayId: String): List<ProgramExerciseEntity>

    @Query("SELECT * FROM program_exercise WHERE deletedAt IS NULL")
    suspend fun allProgramExercises(): List<ProgramExerciseEntity>

    /** Only one program is active at a time. */
    @Query("UPDATE program SET isActive = 0, updatedAt = :now WHERE isActive = 1")
    suspend fun clearActive(now: Long)

    @Query("UPDATE program SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun markActive(id: String, now: Long)

    @Query("UPDATE program SET currentWeek = :week, currentDayIndex = :dayIndex, updatedAt = :now WHERE id = :id")
    suspend fun updatePosition(id: String, week: Int, dayIndex: Int, now: Long)

    @Query("UPDATE program SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteProgram(id: String, now: Long)

    @Query("UPDATE program_day SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteDay(id: String, now: Long)

    @Query("UPDATE program_exercise SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteExercise(id: String, now: Long)
}
