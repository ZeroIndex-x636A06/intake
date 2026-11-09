package com.zeroindex.intake.data.repository

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeIngredientEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.plan.PlanImportBuilder
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.recipe.IngredientDraft
import com.zeroindex.intake.domain.recipe.PageNutrition
import com.zeroindex.intake.domain.recipe.RecipeEditing
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What the editor and the link preview hand over to be saved. */
data class RecipeSaveRequest(
    /** Null creates a recipe; otherwise this recipe is updated in place. */
    val id: String?,
    val name: String,
    val servings: Int,
    val totalMinutes: Int,
    val steps: List<String>,
    val ingredients: List<IngredientDraft>,
    val origin: RecipeOrigin = RecipeOrigin.USER,
    val sourceUrl: String? = null,
    /**
     * Per-serving numbers to store when the ingredients cannot produce a complete total: the page's
     * published nutrition for a link import, or the values a recipe already had.
     */
    val fallbackNutrition: PageNutrition? = null,
)

/** A recipe loaded for editing. */
data class EditableRecipe(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientDraft>,
    val steps: List<String>,
)

/** My recipes: create, edit, delete (soft), and the allergen scan of an ingredient list. */
@Singleton
class RecipeRepository @Inject constructor(
    private val db: IntakeDatabase,
    private val recipeDao: RecipeDao,
    private val foodDao: FoodDao,
    private val allergens: AllergenRepository,
    private val clock: Clock,
) {
    /**
     * The local allergen engine over a recipe's name and ingredients, with the normal ingredient rules:
     * a recipe's ingredients are things that go in the dish, so "no cheese" is not read as avoiding it
     * (that softening is only for a meal plan's own prose).
     */
    suspend fun scan(name: String, ingredients: List<IngredientDraft>): AllergenScanResult =
        allergens.scan(
            name = name,
            ingredients = ingredients.joinToString("\n") { listOf(it.line, it.allergenTags).filter(String::isNotBlank).joinToString(" ") },
            checkLabelInIngredients = true,
        )

    suspend fun load(id: String): EditableRecipe? {
        val recipe = recipeDao.findById(id) ?: return null
        val rows = recipeDao.ingredientsOf(id)
        var n = 0
        val fromFoods = rows.mapNotNull { row ->
            foodDao.findById(row.foodId)?.let { f ->
                IngredientDraft(
                    key = "f${n++}", name = f.name, foodId = f.id, grams = row.quantityGrams,
                    kcalPer100g = f.kcalPer100g, proteinPer100g = f.proteinPer100g,
                    carbsPer100g = f.carbsPer100g, fatPer100g = f.fatPer100g, allergenTags = f.allergenTags,
                )
            }
        }
        val fromText = recipe.ingredientsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { RecipeEditing.draftFromLine("t${n++}", it).copy(grams = null) }
        return EditableRecipe(recipe, fromFoods + fromText, CookSteps.split(recipe.instructions))
    }

    suspend fun save(req: RecipeSaveRequest): RecipeEntity {
        val now = clock.nowMillis()
        val named = req.ingredients.filter { it.name.isNotBlank() }
        val servings = req.servings.coerceAtLeast(1)
        val macros = RecipeEditing.macros(named, servings)
        val scan = scan(req.name, named)
        val existing = req.id?.let { recipeDao.findById(it) }

        // Free-text ingredients with weight and macros become small foods so their numbers survive.
        val foods = ArrayList<FoodEntity>()
        val rows = ArrayList<RecipeIngredientEntity>()
        val rid = existing?.id ?: com.zeroindex.intake.data.db.entity.newId()
        val textLines = ArrayList<String>()
        named.forEachIndexed { index, d ->
            if (d.hasNutrition) {
                val foodId = d.foodId ?: seedId("recipe-food", "${d.name.trim().lowercase()}|${d.kcalPer100g}|${d.proteinPer100g}|${d.carbsPer100g}|${d.fatPer100g}").also {
                    foods += FoodEntity(
                        id = it, name = d.name.trim(), servingSizeGrams = 100.0, servingLabel = "100 g",
                        kcalPer100g = d.kcalPer100g!!, proteinPer100g = d.proteinPer100g!!,
                        carbsPer100g = d.carbsPer100g!!, fatPer100g = d.fatPer100g!!,
                        source = FoodSource.RECIPE, allergenTags = d.allergenTags.trim(), createdAt = now, updatedAt = now,
                    )
                }
                rows += RecipeIngredientEntity(
                    recipeId = rid, foodId = foodId, quantityGrams = d.grams!!, displayOrder = index,
                    createdAt = now, updatedAt = now,
                )
            } else {
                textLines += d.line
            }
        }

        val usePage = !macros.isComplete && req.fallbackNutrition?.kcal != null
        val perServing = when {
            macros.isComplete -> macros.perServing
            else -> null
        }
        val entity = (existing ?: RecipeEntity(id = rid, name = req.name, servings = servings, instructions = "", totalTimeMinutes = 0, createdAt = now, updatedAt = now)).copy(
            name = req.name.trim(),
            servings = servings,
            instructions = CookSteps.join(req.steps),
            totalTimeMinutes = req.totalMinutes.coerceAtLeast(0),
            origin = if (existing != null) existing.origin else req.origin,
            sourceUrl = req.sourceUrl ?: existing?.sourceUrl,
            ingredientsText = textLines.joinToString("\n"),
            kcalPerServing = perServing?.kcal ?: if (usePage) req.fallbackNutrition?.kcal else null,
            proteinPerServing = perServing?.protein ?: if (usePage) req.fallbackNutrition?.proteinG else null,
            carbsPerServing = perServing?.carbs ?: if (usePage) req.fallbackNutrition?.carbsG else null,
            fatPerServing = perServing?.fat ?: if (usePage) req.fallbackNutrition?.fatG else null,
            allergenStatus = PlanImportBuilder.statusOf(scan),
            updatedAt = now,
            deletedAt = null,
        )
        db.withTransaction {
            if (foods.isNotEmpty()) foodDao.upsertAll(foods.filter { foodDao.findById(it.id) == null })
            if (existing != null) {
                recipeDao.ingredientsOf(existing.id).forEach { recipeDao.softDeleteIngredient(it.id, now) }
            }
            recipeDao.upsertRecipe(entity)
            if (rows.isNotEmpty()) recipeDao.upsertIngredients(rows)
        }
        return entity
    }

    suspend fun softDelete(id: String) = recipeDao.softDeleteRecipe(id, clock.nowMillis())

    companion object {
        /** Worst of two verdicts, for callers that combine a stored and a fresh one. */
        fun worse(a: AllergenStatus?, b: AllergenStatus?): AllergenStatus? =
            if (PlanImportBuilder.rank(a) >= PlanImportBuilder.rank(b)) a else b
    }
}
