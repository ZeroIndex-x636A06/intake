package com.zeroindex.intake.data.plan

import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeIngredientEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileEatOut
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanFileRecipe
import com.zeroindex.intake.domain.plan.PlanFileShoppingItem
import com.zeroindex.intake.domain.plan.PlanFileWeek
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanSchedule
import com.zeroindex.intake.domain.plan.PlanWeekExtras
import com.zeroindex.intake.domain.plan.ShoppingIngredient
import com.zeroindex.intake.domain.plan.ShoppingListBuilder

/** The live rows of the plan, as the exporter needs them. */
data class PlanExportRows(
    /** The plan being exported: its name, schedule and plan-wide content. */
    val plan: MealPlanEntity,
    /** Live weeks of that plan only. */
    val weeks: List<PlanWeekEntity>,
    val links: List<PlanWeekRecipeEntity>,
    val recipes: List<RecipeEntity>,
    val ingredients: List<RecipeIngredientEntity>,
    val foods: List<FoodEntity>,
    val shopping: List<ShoppingListItemEntity>,
    val eatOut: List<EatOutOptionEntity>,
)

/**
 * The inverse of [PlanImportBuilder]: rows back into a [PlanFile]. Pure. Shopping rows the importer
 * would regenerate from the recipes (same name and quantity) are left out, so the exported file
 * does not turn a generated list into a hand-written one; pantry staples go back to the plan-wide
 * pantry list. Check marks are state, not content, and are not exported.
 */
object PlanExporter {

    fun toPlanFile(rows: PlanExportRows): PlanFile {
        val meta = PlanMeta.decode(rows.plan.metaJson)
        val planWeeks = rows.weeks.filter { it.weekNumber >= 1 }.sortedBy { it.weekNumber }
        val recipesById = rows.recipes.associateBy { it.id }
        val foodsById = rows.foods.associateBy { it.id }
        val ingredientsByRecipe = rows.ingredients.groupBy { it.recipeId }
        val pantryKeys = meta.pantry.map { ShoppingListBuilder.normalise(it) }.toSet()

        val shoppingOut = ArrayList<PlanFileShoppingItem>()
        val weeks = planWeeks.map { w ->
            val extras = PlanWeekExtras.decode(w.notes)
            val linked = rows.links.filter { it.planWeekId == w.id }
            val recipes = linked
                .sortedBy { if (it.role == PlanRecipeRole.MAIN) 0 else 1 }
                .mapNotNull { link -> recipesById[link.recipeId]?.let { recipeOf(it, link.role, ingredientsByRecipe[it.id].orEmpty(), foodsById) } }

            // What the importer would generate for this week from its recipes.
            val generated = ShoppingListBuilder.generate(
                recipes.flatMap { r -> r.ingredients.map { ShoppingIngredient(it.name, it.grams) } },
            ).associateBy { it.key }
            rows.shopping.filter { it.planWeekId == w.id }.forEach { s ->
                val key = ShoppingListBuilder.normalise(s.name)
                val g = generated[key]
                val sameAsGenerated = g != null && g.name == s.name && g.quantity == s.quantity
                val isPlanPantry = s.isPantryStaple && key in pantryKeys
                if (!sameAsGenerated && !isPlanPantry) {
                    shoppingOut += PlanFileShoppingItem(
                        name = s.name, quantity = s.quantity, category = s.category.name,
                        isPantryStaple = s.isPantryStaple, weekNumber = w.weekNumber,
                    )
                }
            }
            PlanFileWeek(
                weekNumber = w.weekNumber,
                mainDishName = w.mainDishName,
                proteinType = w.proteinType,
                notes = extras.note,
                serving = extras.serving,
                servingTitle = extras.servingTitle,
                variations = extras.variations,
                recipes = recipes,
            )
        }

        val weekNumberById = rows.weeks.associate { it.id to it.weekNumber }
        val eatOut = rows.eatOut.sortedWith(compareBy({ it.venueName.lowercase() }, { it.whatToOrder })).map { o ->
            val has = o.estimatedKcal > 0
            PlanFileEatOut(
                venueName = o.venueName,
                whatToOrder = o.whatToOrder,
                cautionNote = o.cautionNote,
                estimatedKcal = o.estimatedKcal.takeIf { has },
                estimatedProtein = o.estimatedProtein.takeIf { has },
                estimatedCarbs = o.estimatedCarbs.takeIf { has },
                estimatedFat = o.estimatedFat.takeIf { has },
                weekNumber = o.planWeekId?.let { weekNumberById[it] }?.takeIf { it >= 1 },
            )
        }

        return PlanFile(
            format = PlanFileParser.FORMAT,
            version = PlanFileParser.SUPPORTED_VERSION,
            name = rows.plan.name,
            startsOn = PlanSchedule.date(rows.plan.startDate).toString(),
            repeat = PlanSchedule.repeatText(rows.plan.loopMode, rows.plan.loopCount),
            intro = meta.intro,
            standingBreakfasts = meta.standingBreakfasts,
            standingSnacks = meta.standingSnacks,
            pantry = meta.pantry,
            hiddenDairyChecklist = meta.hiddenDairyChecklist,
            eatOutNotes = meta.eatOutNotes,
            weeks = weeks,
            shoppingItems = shoppingOut,
            eatOutOptions = eatOut,
        )
    }

    private val TEXT_LINE = Regex("""^(\d+(?:\.\d+)?) g (.+)$""")

    private fun recipeOf(
        r: RecipeEntity,
        role: PlanRecipeRole,
        rows: List<RecipeIngredientEntity>,
        foods: Map<String, FoodEntity>,
    ): PlanFileRecipe {
        val withData = rows.sortedBy { it.displayOrder }.mapNotNull { row ->
            val f = foods[row.foodId] ?: return@mapNotNull null
            PlanFileIngredient(
                name = f.name,
                grams = row.quantityGrams,
                kcalPer100g = f.kcalPer100g,
                proteinPer100g = f.proteinPer100g,
                carbsPer100g = f.carbsPer100g,
                fatPer100g = f.fatPer100g,
                allergenTags = f.allergenTags,
            )
        }
        val textOnly = r.ingredientsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val m = if (r.origin == RecipeOrigin.PLAN) TEXT_LINE.find(line) else null
            if (m != null) PlanFileIngredient(name = m.groupValues[2], grams = m.groupValues[1].toDouble())
            else PlanFileIngredient(name = line)
        }
        return PlanFileRecipe(
            name = r.name,
            role = role.name,
            servings = r.servings,
            totalTimeMinutes = r.totalTimeMinutes,
            steps = CookSteps.split(r.instructions),
            ingredients = withData + textOnly,
        )
    }
}
