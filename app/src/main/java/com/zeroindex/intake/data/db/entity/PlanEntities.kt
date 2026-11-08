package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.model.StoreCategory
import kotlinx.serialization.Serializable

/** One of the twelve weeks in the plan (SPEC §9). */
@Serializable
@Entity(tableName = "plan_week", indices = [Index("weekNumber"), Index("deletedAt")])
data class PlanWeekEntity(
    @PrimaryKey override val id: String = newId(),
    val weekNumber: Int,
    val mainDishName: String,
    val proteinType: String,
    val notes: String = "",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "plan_week_recipe",
    foreignKeys = [
        ForeignKey(PlanWeekEntity::class, ["id"], ["planWeekId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("planWeekId"), Index("recipeId"), Index("deletedAt")],
)
data class PlanWeekRecipeEntity(
    @PrimaryKey override val id: String = newId(),
    val planWeekId: String,
    val recipeId: String,
    val role: PlanRecipeRole,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

enum class EatOutOrigin { PLAN, PLAN_B }

/**
 * "Can't eat what I cooked" (SPEC §9). [planWeekId] is null when the option
 * applies to every week. Read in a parking lot with no signal, so it must work
 * entirely from local data.
 */
@Serializable
@Entity(
    tableName = "eat_out_option",
    foreignKeys = [
        ForeignKey(PlanWeekEntity::class, ["id"], ["planWeekId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("planWeekId"), Index("deletedAt")],
)
data class EatOutOptionEntity(
    @PrimaryKey override val id: String = newId(),
    val planWeekId: String? = null,
    val venueName: String,
    /** The exact wording to say at the counter. */
    val whatToOrder: String,
    val cautionNote: String = "",
    val estimatedKcal: Int,
    val estimatedProtein: Int,
    @ColumnInfo(defaultValue = "0") val estimatedCarbs: Int = 0,
    @ColumnInfo(defaultValue = "0") val estimatedFat: Int = 0,
    /** PLAN for the curated list, PLAN_B for a saved finder result. */
    @ColumnInfo(defaultValue = "PLAN") val origin: EatOutOrigin = EatOutOrigin.PLAN,
    val address: String? = null,
    /** The local allergen engine's verdict on [whatToOrder]. */
    val allergenStatus: AllergenStatus? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "shopping_list_item",
    foreignKeys = [
        ForeignKey(PlanWeekEntity::class, ["id"], ["planWeekId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("planWeekId"), Index("category"), Index("isPantryStaple"), Index("deletedAt")],
)
data class ShoppingListItemEntity(
    @PrimaryKey override val id: String = newId(),
    val planWeekId: String,
    val name: String,
    val quantity: String = "",
    val category: StoreCategory = StoreCategory.OTHER,
    val isChecked: Boolean = false,
    val isPantryStaple: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
