package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.drinks.AddIn
import com.zeroindex.intake.domain.drinks.CustomDrink
import com.zeroindex.intake.domain.drinks.CustomDrinkRecipe
import com.zeroindex.intake.domain.drinks.CustomDrinkResult
import com.zeroindex.intake.domain.drinks.DrinkBase
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkKinds
import com.zeroindex.intake.domain.drinks.PlainBrew
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.nutrition.Macros
import kotlin.math.roundToInt

/** A custom drink being put together: a base drink, how much of it, and what goes in. Nothing is saved until it is. */
data class CustomDrinkDraft(
    val base: FoodEntity,
    val baseVolumeMl: Double,
    val addIns: List<AddIn>,
)

/**
 * Turns a [CustomDrinkDraft] into a food row (per 100 ml, a millilitre counted as a gram) using the maths in
 * `domain/drinks/CustomDrink`. Both ways of using a custom drink go through it: logging it once (an unsaved row that
 * rides on the base drink's id) and saving it as a "My drink".
 */
object CustomDrinkFood {

    fun typeOf(food: FoodEntity): DrinkType =
        DrinkKinds.typeOf(DrinkCategory.fromStored(food.drinkCategory) ?: DrinkCategory.OTHER, food.name, food.abvPercent)

    private fun rawPer100(food: FoodEntity) = Macros(
        food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g,
        food.fiberPer100g, food.sugarPer100g, food.sodiumPer100g, food.saturatedFatPer100g,
    )

    /** The plain-brew check of a base (see [PlainBrew]): its numbers, what is still unlisted, and whether they were replaced. */
    fun checked(food: FoodEntity): PlainBrew.Checked = PlainBrew.check(
        DrinkCategory.fromStored(food.drinkCategory), food.name, food.allergenTags, rawPer100(food), food.unlistedNutrients,
    )

    /** True when a plain tea or coffee's own numbers were impossible for a brew and the USDA brewed values stand in. */
    fun baseReplaced(food: FoodEntity): Boolean = checked(food).replaced

    /** The base drink's own numbers, with the drink type's defaults where the row has none. */
    fun baseOf(food: FoodEntity): DrinkBase {
        val type = typeOf(food)
        val c = checked(food)
        return DrinkBase(
            name = food.name,
            per100 = c.per100,
            caffeineMgPer100ml = food.caffeineMgPer100ml ?: (type.caffeineMgPer250 / 2.5),
            abvPercent = food.abvPercent ?: type.abvPercent,
            allergenTags = food.allergenTags,
            unlisted = c.unlisted,
        )
    }

    /** What one ml of the base counts for toward water. The finished drink keeps it: the factor comes from the base. */
    fun baseFactor(food: FoodEntity): Double {
        val abv = food.abvPercent ?: typeOf(food).abvPercent
        return food.hydrationFactor ?: Drinks.defaultFactor(typeOf(food), abv)
    }

    fun result(draft: CustomDrinkDraft): CustomDrinkResult = CustomDrink.compute(baseOf(draft.base), draft.baseVolumeMl, draft.addIns)

    fun defaultName(draft: CustomDrinkDraft): String = CustomDrink.name(draft.base.name, draft.addIns)

    /**
     * The food row for the draft. With [id] left as the base drink's, it is the row a one-off log is written from (it is
     * never saved); with a new id it is the "My drink". [existing] keeps an edited drink's id, creation time and use count.
     */
    fun build(draft: CustomDrinkDraft, name: String, id: String, now: Long, existing: FoodEntity? = null): FoodEntity {
        val base = baseOf(draft.base)
        val r = CustomDrink.compute(base, draft.baseVolumeMl, draft.addIns)
        val p = r.per100ml
        val recipe = CustomDrinkRecipe(draft.base.id, draft.base.name, draft.baseVolumeMl, draft.addIns)
        return FoodEntity(
            id = id,
            name = name.trim().ifBlank { defaultName(draft) },
            servingSizeGrams = r.totalVolumeMl,
            servingLabel = "${r.totalVolumeMl.roundToInt()} ml",
            kcalPer100g = p.kcal, proteinPer100g = p.protein, carbsPer100g = p.carbs, fatPer100g = p.fat,
            fiberPer100g = p.fiber, sugarPer100g = p.sugar, sodiumPer100g = p.sodium, saturatedFatPer100g = p.saturatedFat,
            source = FoodSource.MANUAL,
            allergenTags = CustomDrink.allergenText(base, draft.addIns),
            isFavorite = existing?.isFavorite ?: false,
            timesLogged = existing?.timesLogged ?: 0,
            unlistedNutrients = CustomDrink.unlistedUnion(base, draft.addIns),
            drinkCategory = draft.base.drinkCategory ?: DrinkCategory.OTHER.name,
            caffeineMgPer100ml = r.caffeineMgPer100ml,
            abvPercent = r.abvPercent.takeIf { it > 0 },
            hydrationFactor = baseFactor(draft.base),
            customDrink = recipe.encode(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
    }

    /** The row for a one-off log: the drink's computed numbers under the base drink's id (the food-log row points at it). */
    fun oneOff(draft: CustomDrinkDraft, now: Long): FoodEntity = build(draft, defaultName(draft), draft.base.id, now)

    fun newSavedId(): String = newId()
}
