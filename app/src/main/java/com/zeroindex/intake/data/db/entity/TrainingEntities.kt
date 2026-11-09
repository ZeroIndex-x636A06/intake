package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.ProgressionType
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "exercise",
    indices = [Index("name"), Index("primaryMuscle"), Index("deletedAt")],
)
data class ExerciseEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val primaryMuscle: String,
    /** Comma-joined. */
    val secondaryMuscles: String = "",
    val equipment: String = "",
    val isBodyweight: Boolean = false,
    val defaultRestSec: Int = 120,
    /** The person's own how-to text for a custom exercise (shown under "How to"). */
    val notes: String? = null,
    val videoUri: String? = null,
    /**
     * Name of the person's own photo for a custom exercise, a file in the app's `exercise-photos`
     * folder (never a path outside it). Null for the built-in photos, which are bundled assets.
     */
    val photoPath: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/** SPEC §10: a program is data with progression rules attached. */
@Serializable
@Entity(tableName = "program", indices = [Index("isActive"), Index("deletedAt")])
data class ProgramEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val description: String = "",
    val isActive: Boolean = false,
    val currentWeek: Int = 1,
    val currentDayIndex: Int = 0,
    /**
     * True for a saved single-session routine ("Arm day", "Hotel gym"): a program with exactly one day
     * that is never active, never in the program list and can be started any time. See docs/WORKOUTS.md.
     */
    @ColumnInfo(defaultValue = "0") val isStandalone: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "program_day",
    foreignKeys = [
        ForeignKey(ProgramEntity::class, ["id"], ["programId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("programId"), Index("deletedAt")],
)
data class ProgramDayEntity(
    @PrimaryKey override val id: String = newId(),
    val programId: String,
    val dayIndex: Int,
    val name: String,
    /** ISO weekday 1 (Mon) to 7 (Sun) this day is scheduled on, or null for "next in the rotation". */
    val weekday: Int? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * A rule the domain layer can evaluate (SPEC §10). [params] is a JSON blob so a
 * new rule type needs no migration; it is parsed in `domain/`, and a malformed
 * blob surfaces as a validation error in the builder rather than a crash.
 */
@Serializable
@Entity(tableName = "progression_rule", indices = [Index("type"), Index("deletedAt")])
data class ProgressionRuleEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val type: ProgressionType,
    val params: String = "{}",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "program_exercise",
    foreignKeys = [
        ForeignKey(ProgramDayEntity::class, ["id"], ["programDayId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(ProgressionRuleEntity::class, ["id"], ["progressionRuleId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [
        Index("programDayId"), Index("exerciseId"), Index("progressionRuleId"), Index("deletedAt"),
    ],
)
data class ProgramExerciseEntity(
    @PrimaryKey override val id: String = newId(),
    val programDayId: String,
    val exerciseId: String,
    val displayOrder: Int,
    val targetSets: Int,
    val targetRepsMin: Int,
    val targetRepsMax: Int,
    val targetRpe: Double? = null,
    val progressionRuleId: String? = null,
    val restSec: Int = 120,
    /**
     * First prescription when there is no history for this lift (kg; for a bodyweight lift,
     * the added load). Set by a program script's `weight:` line; null means "pick a weight".
     */
    val startWeightKg: Double? = null,
    /** A note for this exercise in this routine ("slow negatives", "seat 4"). */
    val notes: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/** [programDayId] is nullable so a freestyle session works (SPEC §4). */
@Serializable
@Entity(
    tableName = "workout_session",
    foreignKeys = [
        ForeignKey(ProgramDayEntity::class, ["id"], ["programDayId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("programDayId"), Index("startedAt"), Index("deletedAt")],
)
data class WorkoutSessionEntity(
    @PrimaryKey override val id: String = newId(),
    val programDayId: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val notes: String? = null,
    /** Captured at session start for bodyweight lift maths (SPEC §4). */
    val bodyweightKg: Double? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(WorkoutSessionEntity::class, ["id"], ["sessionId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(ExerciseEntity::class, ["id"], ["exerciseId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("sessionId"), Index("exerciseId"), Index("completedAt"), Index("deletedAt")],
)
data class SetLogEntity(
    @PrimaryKey override val id: String = newId(),
    val sessionId: String,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val isFailure: Boolean = false,
    val restTakenSec: Int? = null,
    val completedAt: Long,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
