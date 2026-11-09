package com.zeroindex.intake.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import kotlinx.serialization.Serializable

/**
 * A food the app knows about, from any of the five add paths (SPEC §5).
 *
 * Every macro column is **per 100 g** (SPEC §4). The `Per100g` suffixes are
 * deliberate: serving maths happens at display time, and a column named plain
 * `kcal` is the kind of thing that quietly becomes per-serving somewhere.
 */
@Serializable
@Entity(
    tableName = "food",
    indices = [Index("barcode"), Index("name"), Index("deletedAt"), Index("timesLogged")],
)
data class FoodEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSizeGrams: Double,
    val servingLabel: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    /** Milligrams, as USDA reports it; Open Food Facts grams are converted on import. */
    val sodiumPer100g: Double = 0.0,
    val saturatedFatPer100g: Double = 0.0,
    val source: FoodSource,
    /** Comma-joined allergen terms plus any ingredient text (SPEC §4). */
    val allergenTags: String = "",
    val isVerified: Boolean = false,
    val isFavorite: Boolean = false,
    val timesLogged: Int = 0,
    /** UTC millis of the last time the user sent this product to Open Food Facts, or null. */
    val offContributedAt: Long? = null,
    /**
     * An https thumbnail from Open Food Facts, kept so My foods can show the product. Only the address is
     * stored: the picture is fetched again when it is needed and is never part of a backup.
     */
    val imageUrl: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/**
 * One entry on one day.
 *
 * The macro columns here are the **absolute amounts for this entry**, copied at
 * log time (SPEC §4), so editing a food later never rewrites history. [foodId]
 * is nullable because quick add (SPEC §5.4) deliberately creates no food record.
 */
@Serializable
@Entity(
    tableName = "food_log",
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("foodId"), Index("loggedAt"), Index("mealSlot"), Index("deletedAt")],
)
data class FoodLogEntity(
    @PrimaryKey override val id: String = newId(),
    val foodId: String? = null,
    val loggedAt: Long,
    val mealSlot: MealSlot,
    /** Null for a quick add, which has raw macros and no portion. */
    val quantityGrams: Double? = null,
    val foodNameAtLog: String,
    val servingLabelAtLog: String? = null,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    /** Milligrams. */
    val sodium: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val sourcePhotoUri: String? = null,
    val aiConfidence: AiConfidence? = null,
    val wasEdited: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/** USER: typed in. PLAN: from a meal plan file. HUNTER: Recipe Hunter. LINK: imported from a web page the user pasted. */
enum class RecipeOrigin { USER, PLAN, HUNTER, LINK }

@Serializable
@Entity(tableName = "recipe", indices = [Index("sourceWeekNumber"), Index("deletedAt")])
data class RecipeEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    val servings: Int,
    val instructions: String,
    val totalTimeMinutes: Int,
    /** Links back to the 12-week plan (SPEC §9), null for a standalone recipe. */
    val sourceWeekNumber: Int? = null,
    @ColumnInfo(defaultValue = "USER") val origin: RecipeOrigin = RecipeOrigin.USER,
    /** Where a Recipe Hunter result came from, so the user can check it. */
    val sourceUrl: String? = null,
    /**
     * Ingredient lines as found, for recipes whose ingredients are not (yet)
     * matched to foods in the catalog - Recipe Hunter results start this way.
     */
    @ColumnInfo(defaultValue = "") val ingredientsText: String = "",
    val kcalPerServing: Double? = null,
    val proteinPerServing: Double? = null,
    val carbsPerServing: Double? = null,
    val fatPerServing: Double? = null,
    /** The local allergen engine's verdict when the recipe was saved. */
    val allergenStatus: AllergenStatus? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "recipe_ingredient",
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(FoodEntity::class, ["id"], ["foodId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("recipeId"), Index("foodId"), Index("deletedAt")],
)
data class RecipeIngredientEntity(
    @PrimaryKey override val id: String = newId(),
    val recipeId: String,
    val foodId: String,
    val quantityGrams: Double,
    val displayOrder: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

/** A saved combination logged in one tap (SPEC §4). */
@Serializable
@Entity(tableName = "meal_template", indices = [Index("deletedAt")])
data class MealTemplateEntity(
    @PrimaryKey override val id: String = newId(),
    val name: String,
    /**
     * Amendment §2: the NFC tag this template was last written to, by its
     * hardware UID, so the list can show which containers are tagged.
     */
    val nfcTagUid: String? = null,
    val nfcWrittenAt: Long? = null,
    val lastLoggedAt: Long? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited

@Serializable
@Entity(
    tableName = "meal_template_item",
    foreignKeys = [
        ForeignKey(MealTemplateEntity::class, ["id"], ["mealTemplateId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(FoodEntity::class, ["id"], ["foodId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index("mealTemplateId"), Index("foodId"), Index("deletedAt")],
)
data class MealTemplateItemEntity(
    @PrimaryKey override val id: String = newId(),
    val mealTemplateId: String,
    val foodId: String,
    val quantityGrams: Double,
    val displayOrder: Int,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val deletedAt: Long? = null,
) : Audited
