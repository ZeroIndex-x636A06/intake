package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.AllergenSeverity
import kotlinx.serialization.Serializable

/** A single row (SPEC §4). [SINGLETON_ID] keeps it that way. */
@Serializable
@Entity(tableName = "allergen_profile")
data class AllergenProfileEntity(
    @PrimaryKey override val id: String = SINGLETON_ID,
    /** Comma-joined allergen names the user avoids. */
    val avoidedAllergens: String = "",
    val severityLevel: AllergenSeverity = AllergenSeverity.AVOID,
    val notes: String = "",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited {
    companion object {
        const val SINGLETON_ID = "allergen-profile"
    }
}

/**
 * The alias table the allergen engine matches against (SPEC §7.1). Seeded at
 * first run with the hidden-dairy list from SPEC §4, which is the whole reason
 * the engine catches things a label scan misses.
 */
@Serializable
@Entity(
    tableName = "allergen_alias",
    indices = [
        Index("allergen"),
        Index(value = ["allergen", "aliasTerm"], unique = true),
        Index("deletedAt"),
    ],
)
data class AllergenAliasEntity(
    @PrimaryKey override val id: String = newId(),
    val allergen: String,
    /** Lower-cased at write time; matching is case-insensitive and whole-word. */
    val aliasTerm: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * SPEC §6.9: when the user edits an AI estimate, the delta is recorded so the
 * camera screen can say "AI estimates have run about 15% low for you" once
 * there are enough corrections to mean anything.
 */
@Serializable
@Entity(tableName = "ai_correction", indices = [Index("correctedAt"), Index("deletedAt")])
data class AiCorrectionEntity(
    @PrimaryKey override val id: String = newId(),
    val correctedAt: Long,
    val estimatedKcal: Double,
    val correctedKcal: Double,
    val estimatedGrams: Double?,
    val correctedGrams: Double?,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
