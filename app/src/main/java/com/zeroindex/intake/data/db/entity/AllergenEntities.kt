package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.model.KeywordLevel
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
    /** The strongest verdict a hit on this keyword gives (docs/ALLERGENS.md). */
    @ColumnInfo(defaultValue = "CONTAINS") val level: KeywordLevel = KeywordLevel.CONTAINS,
    /** True for keywords the user added; built-in ones can be switched off but never deleted. */
    @ColumnInfo(defaultValue = "0") val isCustom: Boolean = false,
    /** Soft off switch for built-in keywords. A disabled keyword stays in the table and in backups. */
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * An allergen the user can pick: one of the built-in list (seeded, [isCustom] false) or one they
 * created. [name] is the stable lower-case key stored in the profile and on every keyword row.
 */
@Serializable
@Entity(
    tableName = "allergen_definition",
    indices = [Index(value = ["name"], unique = true), Index("deletedAt")],
)
data class AllergenDefinitionEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "0") val isCustom: Boolean = false,
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
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
