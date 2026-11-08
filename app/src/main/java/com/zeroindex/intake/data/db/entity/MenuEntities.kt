package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class MenuSourceKind { URL, PDF, TEXT }

enum class MenuRunStatus { NEVER_RUN, OK, FAILED, RUNNING }

/**
 * Amendment §1, menu ingestion: where a restaurant's menu comes from.
 * [location] is a URL for [MenuSourceKind.URL], a persisted content:// URI for
 * [MenuSourceKind.PDF], and unused for [MenuSourceKind.TEXT], whose menu is
 * kept in [pastedText].
 */
@Serializable
@Entity(tableName = "menu_source", indices = [Index("deletedAt")])
data class MenuSourceEntity(
    @PrimaryKey override val id: String = newId(),
    val restaurantName: String,
    val kind: MenuSourceKind,
    val location: String = "",
    val pastedText: String? = null,
    val enabled: Boolean = true,
    val lastRunAt: Long? = null,
    val lastStatus: MenuRunStatus = MenuRunStatus.NEVER_RUN,
    val lastError: String? = null,
    val itemCount: Int = 0,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * Outcome of running the local allergen engine over a menu item or recipe.
 * Stored by name in a TEXT column, so adding [CHECK_LABEL] needs no migration.
 * Order of severity: CONTAINS > MAY_CONTAIN > CHECK_LABEL > CLEAR (UNKNOWN is
 * "not checked" and loses to any real verdict); see [rank] and [worseOf].
 */
enum class AllergenStatus { CLEAR, CHECK_LABEL, MAY_CONTAIN, CONTAINS, UNKNOWN }

/** CONTAINS 4 > MAY_CONTAIN 3 > CHECK_LABEL 2 > CLEAR 1 > UNKNOWN 0. */
val AllergenStatus?.rank: Int
    get() = when (this) {
        AllergenStatus.CONTAINS -> 4
        AllergenStatus.MAY_CONTAIN -> 3
        AllergenStatus.CHECK_LABEL -> 2
        AllergenStatus.CLEAR -> 1
        AllergenStatus.UNKNOWN, null -> 0
    }

/** The more serious of two verdicts; a soft verdict can never replace a harder one. */
fun worseOf(a: AllergenStatus?, b: AllergenStatus?): AllergenStatus? = if (b.rank > a.rank) b else a

/**
 * One parsed menu item. Macros are the model's estimate unless the source
 * published them ([macrosPublished]). [allergenStatus] is always the *local*
 * engine's verdict, never the model's: allergy safety is not delegated.
 */
@Serializable
@Entity(
    tableName = "menu_item",
    foreignKeys = [
        ForeignKey(MenuSourceEntity::class, ["id"], ["sourceId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("sourceId"), Index("restaurantName"), Index("deletedAt")],
)
data class MenuItemEntity(
    @PrimaryKey override val id: String = newId(),
    val sourceId: String? = null,
    val restaurantName: String,
    val name: String,
    val description: String = "",
    val ingredientsText: String = "",
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val macrosPublished: Boolean = false,
    val allergenStatus: AllergenStatus = AllergenStatus.UNKNOWN,
    /** The matched terms, comma separated, so the reason is always shown. */
    val allergenMatches: String = "",
    val priceCents: Int? = null,
    val importedAt: Long,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
