package com.zeroindex.intake.data.repository

import androidx.room.withTransaction
import com.zeroindex.intake.data.db.IntakeDatabase
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.dao.ShoppingListDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.plan.AllergenCheck
import com.zeroindex.intake.data.plan.PlanImportBatch
import com.zeroindex.intake.data.plan.PlanImportBuilder
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.plan.PlanWatchTerms
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.model.StoreCategory
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.plan.CookSteps
import com.zeroindex.intake.domain.plan.IngredientNutrition
import com.zeroindex.intake.domain.plan.PlanFileParse
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanWeekExtras
import com.zeroindex.intake.domain.plan.RecipeMacroResult
import com.zeroindex.intake.domain.plan.RecipeMacros
import com.zeroindex.intake.domain.plan.ShoppingIngredient
import com.zeroindex.intake.domain.plan.ShoppingListBuilder
import com.zeroindex.intake.domain.plan.compute
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A recipe with what the plan screen needs: steps, ingredients, macros and a live allergen verdict. */
data class RecipeContent(
    val recipe: RecipeEntity,
    val steps: List<String>,
    /** Ingredient names for display, with grams where known. */
    val ingredientLines: List<String>,
    val macros: RecipeMacroResult,
    /** Worse of the stored verdict and a fresh scan with today's profile. */
    val allergenStatus: AllergenStatus,
    val allergenTerms: List<String>,
)

data class WeekContent(
    val week: PlanWeekEntity,
    val extras: PlanWeekExtras,
    val main: RecipeContent?,
    val variations: List<RecipeContent>,
)

sealed interface PlanImportPreview {
    data class Ready(val batch: PlanImportBatch, val weeks: Int, val recipes: Int, val eatOut: Int) : PlanImportPreview
    data class Invalid(val errors: List<String>) : PlanImportPreview
}

sealed interface MealLogResult {
    data class Logged(val log: FoodLogEntity, val macros: RecipeMacroResult?, val status: AllergenStatus) : MealLogResult
    data class Blocked(val terms: List<String>) : MealLogResult
}

/** SPEC §9: the 12-week plan, its shopping list, import, and logging from it. */
@Singleton
class MealPlanRepository @Inject constructor(
    private val db: IntakeDatabase,
    private val planDao: PlanDao,
    private val recipeDao: RecipeDao,
    private val foodDao: FoodDao,
    private val shoppingDao: ShoppingListDao,
    private val plan: PlanRepository,
    private val allergens: AllergenRepository,
    private val foodLog: FoodLogRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observeCurrentWeekNumber(): Flow<Int> = settings.settings.map { it.currentPlanWeek.coerceIn(1, 12) }

    /** Weeks 1-12 that have a plan; the plan-wide row (week 0) is not one of them. */
    fun observePlannedWeekNumbers(): Flow<Set<Int>> =
        planDao.observeWeeks().map { rows -> rows.map { it.weekNumber }.filter { it in 1..12 }.toSet() }

    fun observeMeta(): Flow<PlanMeta> =
        planDao.observeWeek(PlanMeta.WEEK_NUMBER).map { PlanMeta.decode(it?.notes) }

    /**
     * Switches the current week. SPEC §9: check state resets when the week
     * changes, so both the week being left and the one being entered start clean.
     */
    suspend fun setCurrentWeek(weekNumber: Int) {
        val target = weekNumber.coerceIn(1, 12)
        val previous = settings.settings.first().currentPlanWeek
        if (previous == target) return
        settings.setCurrentPlanWeek(target)
        listOf(previous, target).forEach { n -> planDao.week(n)?.let { plan.resetChecks(it.id) } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeekContent(weekNumber: Int): Flow<WeekContent?> =
        planDao.observeWeek(weekNumber).flatMapLatest { week ->
            if (week == null) {
                flowOf(null)
            } else {
                combine(planDao.observeWeekRecipes(week.id), recipeDao.observeAll()) { links, recipes -> links to recipes }
                    .map { (links, recipes) -> buildContent(week, links, recipes) }
            }
        }

    private suspend fun buildContent(
        week: PlanWeekEntity,
        links: List<PlanWeekRecipeEntity>,
        recipes: List<RecipeEntity>,
    ): WeekContent {
        val byId = recipes.associateBy { it.id }
        val scanner = scanner()
        suspend fun content(link: PlanWeekRecipeEntity): RecipeContent? =
            byId[link.recipeId]?.let { contentOf(it, scanner) }
        val main = links.firstOrNull { it.role == PlanRecipeRole.MAIN }?.let { content(it) }
        val variations = links.filter { it.role == PlanRecipeRole.VARIATION }.mapNotNull { content(it) }
        return WeekContent(week, PlanWeekExtras.decode(week.notes), main, variations)
    }

    private suspend fun contentOf(recipe: RecipeEntity, check: AllergenCheck): RecipeContent {
        val rows = recipeDao.ingredientsOf(recipe.id)
        val foods = rows.mapNotNull { row -> foodDao.findById(row.foodId)?.let { it to row.quantityGrams } }
        val known = foods.map { (f, g) ->
            IngredientNutrition(f.name, g, f.kcalPer100g, f.proteinPer100g, f.carbsPer100g, f.fatPer100g)
        }
        val textLines = recipe.ingredientsText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val macros = when {
            known.isEmpty() && recipe.kcalPerServing != null -> {
                // Saved with published per-serving numbers (Recipe Hunter) and no matched ingredients.
                val per = Macros(
                    recipe.kcalPerServing, recipe.proteinPerServing ?: 0.0,
                    recipe.carbsPerServing ?: 0.0, recipe.fatPerServing ?: 0.0,
                )
                RecipeMacroResult(per * recipe.servings.toDouble(), per, emptyList(), 1)
            }
            // Ingredient rows exist alongside text-only lines (or only text lines): text lines are the missing ones.
            else -> RecipeMacros.compute(known, textLines, recipe.servings)
        }
        val lines = foods.map { (f, g) -> "${f.name} · ${g.toLong()} g" } + textLines
        val text = (foods.map { it.first.name } + textLines).joinToString("\n")
        val tags = foods.joinToString(", ") { it.first.allergenTags }.takeIf { it.isNotBlank() }
        val scan = check.scan(recipe.name, listOfNotNull(text, tags).joinToString("\n"))
        val live = PlanImportBuilder.statusOf(scan)
        val status = if (PlanImportBuilder.rank(live) >= PlanImportBuilder.rank(recipe.allergenStatus)) live
        else recipe.allergenStatus ?: live
        return RecipeContent(
            recipe = recipe,
            steps = CookSteps.split(recipe.instructions),
            ingredientLines = lines,
            macros = macros,
            allergenStatus = status,
            allergenTerms = scan.matches.map { it.matchedTerm }.distinct(),
        )
    }

    fun observeCheckLabelTerms() = allergens.observeCheckLabelTerms()

    /** A snapshot of the user's profile and alias table as an [AllergenCheck]. Local only. */
    suspend fun scanner(planChecklist: List<String>? = null): AllergenCheck {
        val avoided = allergens.avoidedAllergens()
        val aliases = allergens.aliases().map { AllergenAlias(it.allergen, it.aliasTerm) }
        val soft = HiddenCarriers.DAIRY_TERMS + PlanWatchTerms.extract(
            planChecklist ?: PlanMeta.decode(planDao.week(PlanMeta.WEEK_NUMBER)?.notes).hiddenDairyChecklist,
        )
        return AllergenCheck { name, ingredients ->
            // Plan ingredient lines are free text with no label to read, so they get the soft scan too.
            AllergenMatcher.scan(
                aliases, avoided, name = name, ingredients = ingredients,
                checkLabelTerms = soft, checkLabelInIngredients = true,
            )
        }
    }

    // ---- assigning from saved recipes -------------------------------------------------------

    fun observeRecipes(): Flow<List<RecipeEntity>> = recipeDao.observeAll()

    /** Creates or updates the week to use [recipe] as its MAIN dish. Replaces any previous MAIN link (soft). */
    suspend fun assignRecipe(weekNumber: Int, recipe: RecipeEntity, proteinType: String) {
        val now = clock.nowMillis()
        val existing = planDao.week(weekNumber)
        val wid = existing?.id ?: PlanImportBuilder.weekId(weekNumber)
        db.withTransaction {
            planDao.upsertWeek(
                (existing ?: PlanWeekEntity(id = wid, weekNumber = weekNumber, mainDishName = "", proteinType = "", createdAt = now, updatedAt = now))
                    .copy(mainDishName = recipe.name, proteinType = proteinType.trim(), updatedAt = now, deletedAt = null),
            )
            planDao.weekRecipes(wid).filter { it.role == PlanRecipeRole.MAIN && it.recipeId != recipe.id }.forEach {
                planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now))
            }
            planDao.upsertWeekRecipe(
                PlanWeekRecipeEntity(
                    id = seedId("plan-link", "$weekNumber|${recipe.id}|MAIN"),
                    planWeekId = wid, recipeId = recipe.id, role = PlanRecipeRole.MAIN,
                    createdAt = now, updatedAt = now,
                ),
            )
            // The old recipe's generated list goes with it; the new one is built fresh.
            softDeleteShopping(wid, now)
            generateShopping(wid, listOf(recipe), force = true)
        }
    }

    suspend fun updateProtein(weekNumber: Int, proteinType: String) {
        val week = planDao.week(weekNumber) ?: return
        planDao.upsertWeek(week.copy(proteinType = proteinType.trim(), updatedAt = clock.nowMillis()))
    }

    /** Removes the week's plan. Soft delete only: the week, its links and its shopping rows keep their data. */
    suspend fun clearWeek(weekNumber: Int) {
        val week = planDao.week(weekNumber) ?: return
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.weekRecipes(week.id).forEach { planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now)) }
            softDeleteShopping(week.id, now)
            planDao.softDeleteWeek(week.id, now)
        }
    }

    private suspend fun softDeleteShopping(weekId: String, now: Long) {
        shoppingDao.forWeek(weekId).forEach { shoppingDao.softDelete(it.id, now) }
    }

    // ---- shopping list ----------------------------------------------------------------------

    fun observeShopping(weekId: String): Flow<List<ShoppingListItemEntity>> = plan.observeShoppingList(weekId)

    /**
     * Makes sure the week's generated items exist. Rows that already exist,
     * including ones the user removed, are left alone: checks survive and a
     * removal is not undone by looking at the list again.
     */
    suspend fun ensureShoppingList(weekId: String) {
        val links = planDao.weekRecipes(weekId)
        val recipes = links.mapNotNull { recipeDao.findById(it.recipeId) }
        generateShopping(weekId, recipes, force = false)
    }

    private suspend fun generateShopping(weekId: String, recipes: List<RecipeEntity>, force: Boolean) {
        val now = clock.nowMillis()
        val ingredients = recipes.flatMap { r ->
            val rows = recipeDao.ingredientsOf(r.id).mapNotNull { row ->
                foodDao.findById(row.foodId)?.let { ShoppingIngredient(it.name, row.quantityGrams) }
            }
            val lines = r.ingredientsText.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
                // Plan-imported text lines carry a "250 g " prefix the import did not use in the item's name.
                val name = if (r.origin == RecipeOrigin.PLAN) line.replace(Regex("^\\d+(\\.\\d+)? g "), "") else line
                ShoppingIngredient(name, null)
            }
            rows + lines
        }
        val have = if (force) emptySet() else shoppingDao.idsForWeek(weekId).toSet()
        val fresh = ShoppingListBuilder.generate(ingredients).mapNotNull { g ->
            val id = seedId("shopping", "$weekId:${g.key}")
            if (id in have) null
            else ShoppingListItemEntity(
                id = id, planWeekId = weekId, name = g.name, quantity = g.quantity, category = g.category,
                isPantryStaple = g.isPantryStaple, createdAt = now, updatedAt = now,
            )
        }
        if (fresh.isNotEmpty()) shoppingDao.upsertAll(fresh)
    }

    suspend fun setChecked(itemId: String, checked: Boolean) = plan.setChecked(itemId, checked)

    suspend fun addManualItem(weekId: String, name: String, quantity: String, category: StoreCategory) {
        if (name.isBlank()) return
        val now = clock.nowMillis()
        shoppingDao.upsert(
            ShoppingListItemEntity(
                id = newId(), planWeekId = weekId, name = name.trim(), quantity = quantity.trim(),
                category = category, createdAt = now, updatedAt = now,
            ),
        )
    }

    suspend fun removeItem(itemId: String) = plan.softDeleteShoppingItem(itemId)

    // ---- import -----------------------------------------------------------------------------

    /** Parses and validates the file and runs the local allergen engine over it. Writes nothing. */
    suspend fun previewImport(text: String): PlanImportPreview =
        when (val parsed = PlanFileParser.parse(text)) {
            is PlanFileParse.Invalid -> PlanImportPreview.Invalid(parsed.errors)
            is PlanFileParse.Ok -> {
                val batch = PlanImportBuilder.build(parsed.file, clock.nowMillis(), scanner(parsed.file.hiddenDairyChecklist))
                PlanImportPreview.Ready(batch, parsed.file.weeks.size, batch.recipes.size, batch.eatOutOptions.size)
            }
        }

    /**
     * Writes the whole batch in one transaction. Ids are deterministic, so a
     * second import of the same file updates the same rows. Foods that already
     * exist keep their log counts and favourite flag.
     */
    suspend fun applyImport(batch: PlanImportBatch) {
        db.withTransaction {
            val newFoods = batch.foods.filter { foodDao.findById(it.id) == null }
            if (newFoods.isNotEmpty()) foodDao.upsertAll(newFoods)
            recipeDao.upsertRecipes(batch.recipes)
            recipeDao.upsertIngredients(batch.ingredients)
            planDao.upsertWeeks(batch.weeks)
            planDao.upsertWeekRecipes(batch.links)
            shoppingDao.upsertAll(batch.shoppingItems)
            planDao.upsertEatOutOptions(batch.eatOutOptions)
        }
    }

    // ---- logging ----------------------------------------------------------------------------

    /**
     * SPEC §9: one FoodLog for the recipe's per-serving macros. [override] is what the user
     * confirmed when the macros were incomplete or typed in by hand. A recipe that now matches
     * the allergen profile is not logged.
     */
    suspend fun logMeal(content: RecipeContent, slot: MealSlot, override: Macros? = null): MealLogResult {
        val live = scanner().scan(content.recipe.name, content.ingredientLines.joinToString("\n"))
        val status = PlanImportBuilder.statusOf(live)
        if (status == AllergenStatus.CONTAINS) {
            return MealLogResult.Blocked(live.contains.map { it.matchedTerm }.distinct())
        }
        val macros = override ?: content.macros.perServing
        val log = foodLog.logPrepared(
            name = content.recipe.name, macros = macros, slot = slot, servingLabel = "1 serving",
            at = clock.nowMillis(),
        )
        return MealLogResult.Logged(log, if (override == null) content.macros else null, status)
    }

    suspend fun logEatOut(option: EatOutOptionEntity, slot: MealSlot, override: Macros? = null): FoodLogEntity =
        foodLog.logPrepared(
            name = "${option.venueName}: ${option.whatToOrder}".take(120),
            macros = override ?: Macros(
                option.estimatedKcal.toDouble(), option.estimatedProtein.toDouble(),
                option.estimatedCarbs.toDouble(), option.estimatedFat.toDouble(),
            ),
            slot = slot,
            at = clock.nowMillis(),
        )

    suspend fun undoLog(log: FoodLogEntity) = foodLog.softDelete(log.id)

    fun observeEatOutOptions(): Flow<List<EatOutOptionEntity>> = plan.observeAllEatOutOptions()

    suspend fun weekByNumber(n: Int): PlanWeekEntity? = planDao.week(n)
}
