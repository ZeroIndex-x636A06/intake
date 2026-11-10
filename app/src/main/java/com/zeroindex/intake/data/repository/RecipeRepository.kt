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
import com.zeroindex.intake.domain.recipe.IngredientGroupsCodec
import com.zeroindex.intake.domain.recipe.LineAllergenTag
import com.zeroindex.intake.domain.recipe.LineAllergens
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
    /** The Notes section. Always replaces what was stored: the editor shows it, so blank means cleared. */
    val notes: String? = null,
    /** Prep and cook time apart. Null keeps what a recipe had, unless its total changed. */
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** The photo's address from the imported page. Null keeps what a recipe had. */
    val imageUrl: String? = null,
)

/** One ingredient line as the recipe screen shows it. */
data class IngredientView(val key: String, val text: String, val group: String?, val tag: LineAllergenTag?)

/** A recipe ready to show: its ingredients in order with their group and allergen tag. */
data class RecipeView(
    val recipe: RecipeEntity,
    val ingredients: List<IngredientView>,
    val steps: List<String>,
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
                    group = row.groupLabel,
                )
            }
        }
        // Group labels are stored by line, so they are read against the raw lines before blanks are dropped.
        val rawLines = recipe.ingredientsText.lines()
        val rawGroups = IngredientGroupsCodec.decode(recipe.ingredientGroups, rawLines.size)
        val fromText = rawLines.indices.filter { rawLines[it].isNotBlank() }
            .map { i -> RecipeEditing.draftFromLine("t${n++}", rawLines[i].trim(), rawGroups[i]).copy(grams = null) }
        return EditableRecipe(recipe, fromFoods + fromText, CookSteps.split(recipe.instructions))
    }

    /** A recipe for the detail screen: every ingredient (picked foods and text lines), its group, and its own allergen tag. */
    suspend fun view(id: String): RecipeView? {
        val e = load(id) ?: return null
        val scan = allergens.lineScanner()
        val lines = e.ingredients.filter { it.name.isNotBlank() }.map { d ->
            val tag = LineAllergens.tagOf(scan(listOf(d.line, d.allergenTags).filter(String::isNotBlank).joinToString(" ")))
            IngredientView(d.key, d.line, d.group, tag)
        }
        return RecipeView(e.recipe, lines, e.steps)
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
        val textGroups = ArrayList<String?>()
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
                    groupLabel = d.group?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = now, updatedAt = now,
                )
            } else {
                textLines += d.line
                textGroups += d.group
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
            ingredientGroups = IngredientGroupsCodec.encode(textGroups),
            notes = req.notes?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = req.imageUrl ?: existing?.imageUrl,
            // Prep and cook belong to the total they were read with: a changed total makes them stale.
            prepMinutes = req.prepMinutes ?: existing?.prepMinutes?.takeIf { existing.totalTimeMinutes == req.totalMinutes },
            cookMinutes = req.cookMinutes ?: existing?.cookMinutes?.takeIf { existing.totalTimeMinutes == req.totalMinutes },
            fiberPerServing = req.fallbackNutrition?.fiberG ?: existing?.fiberPerServing,
            sugarPerServing = req.fallbackNutrition?.sugarG ?: existing?.sugarPerServing,
            sodiumMgPerServing = req.fallbackNutrition?.sodiumMg ?: existing?.sodiumMgPerServing,
            saturatedFatPerServing = req.fallbackNutrition?.saturatedFatG ?: existing?.saturatedFatPerServing,
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
