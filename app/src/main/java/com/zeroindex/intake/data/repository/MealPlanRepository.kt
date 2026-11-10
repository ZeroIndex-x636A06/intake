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
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.data.db.entity.PlanWeekEntity
import com.zeroindex.intake.data.db.entity.PlanWeekRecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.db.entity.ShoppingListItemEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.db.entity.EatOutOrigin
import com.zeroindex.intake.data.plan.AllergenCheck
import com.zeroindex.intake.data.plan.PlanExportRows
import com.zeroindex.intake.data.plan.PlanExporter
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.script.Diagnostic
import com.zeroindex.intake.domain.script.IntakeScript
import com.zeroindex.intake.domain.script.MealPlanScript
import com.zeroindex.intake.domain.script.ScriptDocument
import com.zeroindex.intake.domain.script.ScriptInput
import com.zeroindex.intake.data.plan.PlanImportBatch
import com.zeroindex.intake.data.plan.PlanImportBuilder
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.plan.PlanWatchTerms
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.model.PlanLoopMode
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.plan.PlanPosition
import com.zeroindex.intake.domain.recipe.IngredientGroupsCodec
import com.zeroindex.intake.domain.recipe.IngredientLineParser
import com.zeroindex.intake.domain.plan.PlanSchedule
import java.time.LocalDate
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
    data class Ready(
        val batch: PlanImportBatch,
        val weeks: Int,
        val recipes: Int,
        val eatOut: Int,
        /** Script warnings (empty for a JSON file). Shown with the allergen hits before the user confirms. */
        val warnings: List<Diagnostic> = emptyList(),
    ) : PlanImportPreview
    data class Invalid(val errors: List<String>) : PlanImportPreview
    /** A script with errors: nothing is imported. [diagnostics] holds errors and warnings. */
    data class ScriptErrors(val diagnostics: List<Diagnostic>) : PlanImportPreview
}

sealed interface MealLogResult {
    data class Logged(val log: FoodLogEntity, val macros: RecipeMacroResult?, val status: AllergenStatus) : MealLogResult
    data class Blocked(val terms: List<String>) : MealLogResult
}

/** SPEC §9: meal plans of any length, their shopping lists, import, and logging from them. */
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
    // ---- plans and the schedule ---------------------------------------------------------------

    private fun today(): Long = clock.today().toEpochDay()

    fun observePlans(): Flow<List<MealPlanEntity>> = planDao.observePlans()

    fun observeActivePlan(): Flow<MealPlanEntity?> = planDao.observeActivePlan()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> withActivePlan(block: (MealPlanEntity) -> Flow<T>, none: T): Flow<T> =
        planDao.observeActivePlan().flatMapLatest { plan -> if (plan == null) flowOf(none) else block(plan) }

    /** The live weeks of the active plan, in order. */
    fun observeWeeks(): Flow<List<PlanWeekEntity>> = withActivePlan({ planDao.observeWeeks(it.id) }, emptyList())

    /** How many weeks the active plan has (the highest week number); 0 with no plan. */
    fun observePlanLength(): Flow<Int> = observeWeeks().map { rows -> rows.maxOfOrNull { it.weekNumber } ?: 0 }

    /** Where today falls in the active plan, or null with no plan. Re-read when the plan changes. */
    fun observePosition(): Flow<PlanPosition?> =
        combine(planDao.observeActivePlan(), observeWeeks()) { plan, weeks ->
            plan?.let { positionOf(it, weeks.maxOfOrNull { w -> w.weekNumber } ?: 1) }
        }

    private fun positionOf(plan: MealPlanEntity, length: Int): PlanPosition =
        PlanSchedule.position(plan.startDate, today(), length, plan.loopMode, plan.loopCount)

    fun observeCurrentWeekNumber(): Flow<Int> = observePosition().map { it?.week ?: 1 }

    /** Weeks that have a main dish. */
    fun observePlannedWeekNumbers(): Flow<Set<Int>> =
        observeWeeks().map { rows -> rows.filter { it.mainDishName.isNotBlank() }.map { it.weekNumber }.toSet() }

    fun observeMeta(): Flow<PlanMeta> = planDao.observeActivePlan().map { PlanMeta.decode(it?.metaJson) }

    /**
     * Jumps to [weekNumber] by moving the plan's start date so that today is the first day of
     * that week (no override is stored, so the schedule stays the single source of truth).
     * SPEC §9: check state resets when the week changes, so both the week being left and the
     * one being entered start clean.
     */
    suspend fun setCurrentWeek(weekNumber: Int) {
        val active = planDao.activePlan() ?: return
        val weeks = planDao.weeksOf(active.id)
        val length = weeks.maxOfOrNull { it.weekNumber } ?: return
        val target = weekNumber.coerceIn(1, length)
        val previous = positionOf(active, length).week
        planDao.upsertPlan(active.copy(startDate = PlanSchedule.startForWeek(today(), target), updatedAt = clock.nowMillis()))
        settings.setCurrentPlanWeek(target)
        if (previous != target) {
            listOf(previous, target).forEach { n -> weeks.firstOrNull { it.weekNumber == n }?.let { plan.resetChecks(it.id) } }
        }
    }

    /**
     * The week can also change by itself as the calendar moves. This keeps the "last seen week"
     * marker in step and, when it differs, resets the shopping checks of both weeks (SPEC §9).
     */
    suspend fun syncWeekMarker(position: PlanPosition?) {
        if (position == null) return
        val seen = settings.settings.first().currentPlanWeek
        if (seen == position.week) return
        settings.setCurrentPlanWeek(position.week)
        val active = planDao.activePlan() ?: return
        val weeks = planDao.weeksOf(active.id)
        listOf(seen, position.week).forEach { n -> weeks.firstOrNull { it.weekNumber == n }?.let { plan.resetChecks(it.id) } }
    }

    /** A name no live plan uses yet: "Cut", then "Cut 2", "Cut 3". */
    private suspend fun uniqueName(wanted: String, exceptId: String? = null): String {
        val base = wanted.trim().ifBlank { "My plan" }
        val taken = planDao.plans().filter { it.id != exceptId }.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }

    /**
     * Creates a plan with [weeks] empty weeks (1..104) and makes it the active one. [startDay] is an
     * epoch day; null means today.
     */
    suspend fun createPlan(
        name: String,
        weeks: Int,
        loopMode: PlanLoopMode = PlanLoopMode.NONE,
        loopCount: Int = 0,
        startDay: Long? = null,
    ): MealPlanEntity {
        val now = clock.nowMillis()
        val count = weeks.coerceIn(1, PlanSchedule.MAX_WEEKS)
        val created = MealPlanEntity(
            name = uniqueName(name),
            startDate = startDay ?: today(),
            loopMode = loopMode,
            loopCount = if (loopMode == PlanLoopMode.REPEAT_N) loopCount.coerceAtLeast(2) else 0,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            planDao.deactivateOthers(created.id, now)
            planDao.upsertPlan(created)
            planDao.upsertWeeks((1..count).map { n -> emptyWeek(created.id, n, now) })
        }
        return created
    }

    private fun emptyWeek(planId: String, n: Int, now: Long) = PlanWeekEntity(
        planId = planId, weekNumber = n, mainDishName = "", proteinType = "", createdAt = now, updatedAt = now,
    )

    suspend fun updatePlan(
        planId: String,
        name: String,
        notes: String,
        startDay: Long,
        loopMode: PlanLoopMode,
        loopCount: Int,
    ) {
        val existing = planDao.plan(planId) ?: return
        planDao.upsertPlan(
            existing.copy(
                name = uniqueName(name, exceptId = planId),
                notes = notes.trim(),
                startDate = startDay,
                loopMode = loopMode,
                loopCount = if (loopMode == PlanLoopMode.REPEAT_N) loopCount.coerceAtLeast(2) else 0,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    /** Exactly one plan is active: this one. */
    suspend fun setActive(planId: String) {
        val target = planDao.plan(planId) ?: return
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.deactivateOthers(planId, now)
            planDao.upsertPlan(target.copy(isActive = true, updatedAt = now))
        }
    }

    /** Archives a plan (soft delete). If it was the active one, the next plan takes over. */
    suspend fun archivePlan(planId: String) {
        val target = planDao.plan(planId) ?: return
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.softDeletePlan(planId, now)
            if (target.isActive) {
                planDao.plans().firstOrNull { it.id != planId }
                    ?.let { planDao.upsertPlan(it.copy(isActive = true, updatedAt = now)) }
            }
        }
    }

    /** Appends an empty week; returns its number, or null at the 104-week limit or with no active plan. */
    suspend fun addWeek(): Int? {
        val active = planDao.activePlan() ?: return null
        val rows = planDao.weeksOf(active.id)
        val next = (rows.maxOfOrNull { it.weekNumber } ?: 0) + 1
        if (next > PlanSchedule.MAX_WEEKS) return null
        val now = clock.nowMillis()
        planDao.upsertWeek(emptyWeek(active.id, next, now))
        return next
    }

    /** Inserts a copy of [weekNumber] right after it; later weeks move down one. Returns the new week's number. */
    suspend fun duplicateWeek(weekNumber: Int): Int? {
        val active = planDao.activePlan() ?: return null
        val rows = planDao.weeksOf(active.id)
        val source = rows.firstOrNull { it.weekNumber == weekNumber } ?: return null
        if ((rows.maxOfOrNull { it.weekNumber } ?: 0) >= PlanSchedule.MAX_WEEKS) return null
        val now = clock.nowMillis()
        val copy = source.copy(id = newId(), weekNumber = weekNumber + 1, createdAt = now, updatedAt = now)
        db.withTransaction {
            planDao.upsertWeeks(rows.filter { it.weekNumber > weekNumber }.map { it.copy(weekNumber = it.weekNumber + 1, updatedAt = now) })
            planDao.upsertWeek(copy)
            // The copy uses the same recipes (editing a recipe changes both weeks) and its own, unchecked list.
            planDao.weekRecipes(source.id).forEach {
                planDao.upsertWeekRecipe(it.copy(id = newId(), planWeekId = copy.id, createdAt = now, updatedAt = now))
            }
            shoppingDao.forWeek(source.id).forEach {
                shoppingDao.upsert(it.copy(id = newId(), planWeekId = copy.id, isChecked = false, createdAt = now, updatedAt = now))
            }
        }
        return copy.weekNumber
    }

    /** Removes a week (soft) and closes the gap. The last remaining week cannot be removed. */
    suspend fun removeWeek(weekNumber: Int): Boolean {
        val active = planDao.activePlan() ?: return false
        val rows = planDao.weeksOf(active.id)
        val target = rows.firstOrNull { it.weekNumber == weekNumber } ?: return false
        if (rows.size <= 1) return false
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.weekRecipes(target.id).forEach { planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now)) }
            softDeleteShopping(target.id, now)
            planDao.softDeleteWeek(target.id, now)
            planDao.upsertWeeks(rows.filter { it.weekNumber > weekNumber }.map { it.copy(weekNumber = it.weekNumber - 1, updatedAt = now) })
        }
        return true
    }

    /** Swaps a week with its neighbour: [delta] is -1 (earlier) or +1 (later). */
    suspend fun moveWeek(weekNumber: Int, delta: Int): Boolean {
        val active = planDao.activePlan() ?: return false
        val rows = planDao.weeksOf(active.id)
        val a = rows.firstOrNull { it.weekNumber == weekNumber } ?: return false
        val b = rows.firstOrNull { it.weekNumber == weekNumber + delta } ?: return false
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.upsertWeeks(listOf(a.copy(weekNumber = b.weekNumber, updatedAt = now), b.copy(weekNumber = a.weekNumber, updatedAt = now)))
        }
        return true
    }

    /** Saves what the user typed for a week: dish name, protein, a note and one-line variation ideas. */
    suspend fun saveWeekDetails(weekNumber: Int, dish: String, protein: String, note: String, variations: List<String>) {
        val week = ensureWeek(weekNumber) ?: return
        val extras = PlanWeekExtras.decode(week.notes).copy(
            note = note.trim(),
            variations = variations.map { it.trim() }.filter { it.isNotEmpty() },
        )
        planDao.upsertWeek(
            week.copy(
                mainDishName = dish.trim(), proteinType = protein.trim(), notes = extras.encode(),
                updatedAt = clock.nowMillis(), deletedAt = null,
            ),
        )
    }

    /** The active plan's row for [weekNumber], created empty if the plan has none yet. */
    private suspend fun ensureWeek(weekNumber: Int): PlanWeekEntity? {
        val active = planDao.activePlan() ?: return null
        planDao.week(active.id, weekNumber)?.let { return it }
        if (weekNumber !in 1..PlanSchedule.MAX_WEEKS) return null
        val created = emptyWeek(active.id, weekNumber, clock.nowMillis())
        planDao.upsertWeek(created)
        return created
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWeekContent(weekNumber: Int): Flow<WeekContent?> =
        planDao.observeActivePlan().flatMapLatest { active ->
            if (active == null) flowOf(null) else planDao.observeWeek(active.id, weekNumber).flatMapLatest { week ->
                if (week == null) {
                    flowOf(null)
                } else {
                    combine(planDao.observeWeekRecipes(week.id), recipeDao.observeAll()) { links, recipes -> links to recipes }
                        .map { (links, recipes) -> buildContent(week, links, recipes) }
                }
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

    /**
     * Every saved recipe with its per-serving macros and its allergen status as of now (the Swap sheet's "Plan & recipes").
     * Nothing leaves the phone.
     */
    suspend fun recipeContents(): List<RecipeContent> {
        val scanner = scanner()
        return recipeDao.all().map { contentOf(it, scanner) }
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
        val scan = check.scan(recipe.name, text, tags)
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
        val aliases = allergens.aliases().map { AllergenAlias(it.allergen, it.aliasTerm, it.level) }
        val soft = HiddenCarriers.forAllergens(avoided) + PlanWatchTerms.extract(
            planChecklist ?: PlanMeta.decode(planDao.activePlan()?.metaJson).hiddenDairyChecklist,
        )
        return object : AllergenCheck {
            // Plan text is written by a person with no label to read, so it gets the soft scan and may
            // be read for negation ("no cheese"). A file's allergen tags go in as panel text, never negated.
            override fun scan(name: String, ingredientsText: String?) = scan(name, ingredientsText, null)

            override fun scan(name: String, ingredientsText: String?, panelText: String?) =
                AllergenMatcher.scan(
                    aliases, avoided, name = name, ingredients = ingredientsText,
                    checkLabelTerms = soft, checkLabelInIngredients = true,
                    freeText = true, panelText = panelText,
                )
        }
    }

    // ---- assigning from saved recipes -------------------------------------------------------

    fun observeRecipes(): Flow<List<RecipeEntity>> = recipeDao.observeAll()

    /**
     * Makes [recipe] the MAIN dish of [weekNumber] in the active plan, creating the week if needed.
     * Replaces any previous MAIN link (soft). Returns false when there is no active plan.
     */
    suspend fun assignRecipe(weekNumber: Int, recipe: RecipeEntity, proteinType: String): Boolean {
        val now = clock.nowMillis()
        val week = ensureWeek(weekNumber) ?: return false
        val wid = week.id
        db.withTransaction {
            planDao.upsertWeek(
                week.copy(mainDishName = recipe.name, proteinType = proteinType.trim().ifBlank { week.proteinType }, updatedAt = now, deletedAt = null),
            )
            planDao.weekRecipes(wid).filter { it.role == PlanRecipeRole.MAIN && it.recipeId != recipe.id }.forEach {
                planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now))
            }
            planDao.upsertWeekRecipe(
                PlanWeekRecipeEntity(
                    id = seedId("plan-link", "$wid|${recipe.id}|MAIN"),
                    planWeekId = wid, recipeId = recipe.id, role = PlanRecipeRole.MAIN,
                    createdAt = now, updatedAt = now,
                ),
            )
            // The old recipe's generated list goes with it; the new one is built fresh.
            softDeleteShopping(wid, now)
            generateShopping(wid, listOf(recipe), force = true)
        }
        return true
    }

    /**
     * "Add to this week" from a recipe: puts it in the current week of the active plan, making a
     * small plan first when there is none. Returns "Week N of Plan", or null if it could not.
     */
    suspend fun addToCurrentWeek(recipe: RecipeEntity): String? {
        var active = planDao.activePlan()
        if (active == null) active = createPlan("My plan", weeks = 4)
        val length = planDao.weeksOf(active.id).maxOfOrNull { it.weekNumber } ?: 1
        val week = positionOf(active, length).week
        if (!assignRecipe(week, recipe, "")) return null
        return "Week $week of ${active.name}"
    }

    suspend fun updateProtein(weekNumber: Int, proteinType: String) {
        val week = ensureWeek(weekNumber) ?: return
        planDao.upsertWeek(week.copy(proteinType = proteinType.trim(), updatedAt = clock.nowMillis()))
    }

    /**
     * Empties the week's dish: the slot stays (a plan keeps its length), but its recipe links and
     * shopping rows are removed (soft) and the dish, protein and notes are cleared.
     */
    suspend fun clearWeek(weekNumber: Int) {
        val active = planDao.activePlan() ?: return
        val week = planDao.week(active.id, weekNumber) ?: return
        val now = clock.nowMillis()
        db.withTransaction {
            planDao.weekRecipes(week.id).forEach { planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now)) }
            softDeleteShopping(week.id, now)
            planDao.upsertWeek(week.copy(mainDishName = "", proteinType = "", notes = "", updatedAt = now))
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
                foodDao.findById(row.foodId)?.let { ShoppingIngredient(it.name, row.quantityGrams, row.groupLabel) }
            }
            // Group labels are stored per line of the raw text, so they are read before blanks are dropped.
            val rawLines = r.ingredientsText.lines()
            val rawGroups = IngredientGroupsCodec.decode(r.ingredientGroups, rawLines.size)
            val lines = rawLines.indices.filter { rawLines[it].isNotBlank() }.map { i ->
                val line = rawLines[i].trim()
                // Plan-imported text lines carry a "250 g " prefix the import did not use in the item's name.
                when (r.origin) {
                    RecipeOrigin.PLAN -> ShoppingIngredient(line.replace(Regex("^\\d+(\\.\\d+)? g "), ""), null, rawGroups[i])
                    // Typed or imported lines are "1 1/2 cups milk": shop for the item, with grams when the unit is a weight.
                    else -> IngredientLineParser.parse(line).let { ShoppingIngredient(it.name, it.grams, rawGroups[i]) }
                }
            }
            rows + lines
        }
        val have = if (force) emptySet() else shoppingDao.idsForWeek(weekId).toSet()
        val fresh = ShoppingListBuilder.generate(ingredients).mapNotNull { g ->
            val id = seedId("shopping", "$weekId:${g.key}")
            if (id in have) null
            else ShoppingListItemEntity(
                id = id, planWeekId = weekId, name = g.name, quantity = g.quantity, category = g.category,
                isPantryStaple = g.isPantryStaple, note = g.note, createdAt = now, updatedAt = now,
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
        if (ScriptInput.isJson(text)) previewJson(text) else previewScript(text)

    private suspend fun previewScript(text: String): PlanImportPreview {
        val result = IntakeScript.parse(text)
        if (result.errors.isNotEmpty()) return PlanImportPreview.ScriptErrors(result.diagnostics)
        return when (val doc = result.document) {
            is ScriptDocument.MealPlan -> {
                val file = doc.doc.file
                val batch = buildBatch(file)
                PlanImportPreview.Ready(batch, file.weeks.size, batch.recipes.size, batch.eatOutOptions.size, result.warnings)
            }
            is ScriptDocument.Program ->
                PlanImportPreview.Invalid(listOf("This is a workout program. Import it from the Train tab instead."))
            null -> PlanImportPreview.Invalid(listOf("This file could not be read."))
        }
    }

    /**
     * Rows for [file]: a new plan, or, when a live plan has the same name, that plan updated in
     * place (its week rows are reused, weeks missing from the file are removed softly). The file's
     * `starts:` and `repeat:` are used when present; otherwise a new plan starts today and does not
     * repeat, and an existing plan keeps its schedule.
     */
    private suspend fun buildBatch(file: PlanFile): PlanImportBatch {
        val now = clock.nowMillis()
        val name = file.name.trim().ifBlank { "Imported plan" }
        val existing = planDao.planByName(name)
        val planId = existing?.id ?: newId()
        val oldWeeks = existing?.let { planDao.weeksIncludingRemoved(it.id) }.orEmpty()
        val built = PlanImportBuilder.build(
            file, now, scanner(file.hiddenDairyChecklist), planId,
            existingWeekIds = oldWeeks.associate { it.weekNumber to it.id },
        )
        val repeat = PlanSchedule.parseRepeat(file.repeat)
        val start = file.startsOn.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()).toEpochDay() }.getOrNull() }
        val meta = PlanMeta.of(file)
        val plan = (existing ?: MealPlanEntity(name = name, startDate = today(), createdAt = now, updatedAt = now, id = planId)).copy(
            name = existing?.name ?: name,
            startDate = start ?: existing?.startDate ?: today(),
            loopMode = repeat?.first ?: existing?.loopMode ?: PlanLoopMode.NONE,
            loopCount = repeat?.second ?: existing?.loopCount ?: 0,
            isActive = true,
            metaJson = if (meta.isEmpty) "" else meta.encode(),
            updatedAt = now,
            deletedAt = null,
        )
        val inFile = file.weeks.map { it.weekNumber }.toSet()
        val gone = oldWeeks.filter { it.deletedAt == null && it.weekNumber !in inFile }.map { it.id }
        val keptLinkIds = built.links.map { it.id }.toSet()
        val staleLinks = built.weeks.flatMap { w -> planDao.weekRecipesIncludingRemoved(w.id) }
            .filter { it.deletedAt == null && it.id !in keptLinkIds }.map { it.id }
        return built.copy(plan = plan, isUpdate = existing != null, removedWeekIds = gone, staleLinkIds = staleLinks)
    }

    /** A plan as a PlanFile (default: the active plan), ready for [MealPlanScript.printFile]. */
    suspend fun exportPlanFile(planId: String? = null): PlanFile {
        val plan = (if (planId != null) planDao.plan(planId) else planDao.activePlan()) ?: return PlanFile(
            format = PlanFileParser.FORMAT, version = PlanFileParser.SUPPORTED_VERSION,
        )
        val weeks = planDao.weeksOf(plan.id)
        val weekIds = weeks.map { it.id }.toSet()
        val rows = PlanExportRows(
            plan = plan,
            weeks = weeks,
            links = planDao.allWeekRecipes().filter { it.planWeekId in weekIds },
            recipes = recipeDao.all(),
            ingredients = recipeDao.allIngredients(),
            foods = foodDao.all(),
            shopping = shoppingDao.all().filter { it.planWeekId in weekIds },
            eatOut = planDao.allEatOutOptions()
                .filter { it.origin == EatOutOrigin.PLAN && (it.planWeekId == null || it.planWeekId in weekIds) },
        )
        return PlanExporter.toPlanFile(rows)
    }

    suspend fun exportPlanScript(planId: String? = null): String = MealPlanScript.printFile(exportPlanFile(planId))

    private suspend fun previewJson(text: String): PlanImportPreview =
        when (val parsed = PlanFileParser.parse(text)) {
            is PlanFileParse.Invalid -> PlanImportPreview.Invalid(parsed.errors)
            is PlanFileParse.Ok -> {
                val batch = buildBatch(parsed.file)
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
            batch.plan?.let { p ->
                // Exactly one plan is active: the one just imported.
                planDao.deactivateOthers(p.id, p.updatedAt)
                planDao.upsertPlan(p)
            }
            val now = batch.plan?.updatedAt ?: clock.nowMillis()
            batch.removedWeekIds.forEach { id ->
                planDao.weekRecipesIncludingRemoved(id).filter { it.deletedAt == null }
                    .forEach { planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now)) }
                softDeleteShopping(id, now)
                planDao.softDeleteWeek(id, now)
            }
            batch.staleLinkIds.forEach { id ->
                planDao.weekRecipeById(id)?.let { planDao.upsertWeekRecipe(it.copy(deletedAt = now, updatedAt = now)) }
            }
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

    /** Soft delete: a saved eat-out option is hidden, never removed from the database. */
    suspend fun deleteEatOutOption(id: String) = planDao.softDeleteEatOutOption(id, clock.nowMillis())

    suspend fun weekByNumber(n: Int): PlanWeekEntity? = planDao.activePlan()?.let { planDao.week(it.id, n) }
}
