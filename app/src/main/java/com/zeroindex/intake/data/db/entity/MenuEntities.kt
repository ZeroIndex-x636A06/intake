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

/** Outcome of running the local allergen engine over a menu item or recipe. */
enum class AllergenStatus { CLEAR, MAY_CONTAIN, CONTAINS, UNKNOWN }

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
