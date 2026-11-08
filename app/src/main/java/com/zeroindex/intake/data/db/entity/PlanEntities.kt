package com.zeroindex.intake.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.model.StoreCategory

/** One of the twelve weeks in the plan (SPEC §9). */
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

/**
 * "Can't eat what I cooked" (SPEC §9). [planWeekId] is null when the option
 * applies to every week. Read in a parking lot with no signal, so it must work
 * entirely from local data.
 */
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
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

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
