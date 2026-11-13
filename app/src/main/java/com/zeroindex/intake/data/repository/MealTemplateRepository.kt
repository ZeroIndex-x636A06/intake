package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.MealTemplateDao
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.MealTemplateEntity
import com.zeroindex.intake.data.db.entity.MealTemplateItemEntity
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.domain.nutrition.CoreMacro
import com.zeroindex.intake.domain.nutrition.Unlisted
import com.zeroindex.intake.domain.template.MealTemplateRules
import com.zeroindex.intake.domain.template.RecipeFood
import com.zeroindex.intake.domain.template.TemplateLine
import com.zeroindex.intake.domain.template.TemplateTotals
import com.zeroindex.intake.domain.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TemplateSummary(
    val template: MealTemplateEntity,
    val items: List<Pair<FoodEntity, Double>>,
) {
    val kcal: Double get() = items.sumOf { (f, g) -> f.kcalPer100g * g / 100 }
    val protein: Double get() = items.sumOf { (f, g) -> f.proteinPer100g * g / 100 }

    /** The same totals with protein that nobody listed kept as unknown, not counted as 0 g. */
    val totals: TemplateTotals
        get() = MealTemplateRules.totals(items.map { (f, g) -> templateLineOf(f, g) })
}

/** A food or recipe in a template, at an amount in grams (a recipe serving is 100 g, see [RecipeFood]). */
data class TemplateItemInput(val foodId: String, val grams: Double)

fun templateLineOf(food: FoodEntity, grams: Double) = TemplateLine(
    name = food.name,
    grams = grams,
    kcalPer100g = food.kcalPer100g,
    proteinPer100g = food.proteinPer100g,
    proteinListed = CoreMacro.PROTEIN !in Unlisted.decodeMacros(food.unlistedNutrients),
)

sealed interface TemplateLogResult {
    data class Logged(val template: MealTemplateEntity, val slot: MealSlot, val logs: List<FoodLogEntity>) : TemplateLogResult
    /** The allergen profile now matches a food that was fine when the template was saved. */
    data class Blocked(val template: MealTemplateEntity, val foods: List<String>) : TemplateLogResult
    data object NotFound : TemplateLogResult
}

/**
 * Meal templates: a saved meal that logs in one go. They belong to no meal plan: made on the Meal templates screen
 * from foods and recipes, or from a meal logged on Today. Amendment §2 adds an optional NFC tag on a template, which
 * logs it with one tap.
 */
@Singleton
class MealTemplateRepository @Inject constructor(
    private val templateDao: MealTemplateDao,
    private val foodDao: FoodDao,
    private val foodLogDao: FoodLogDao,
    private val foodLog: FoodLogRepository,
    private val allergens: AllergenRepository,
    private val recipes: RecipeRepository,
    private val clock: Clock,
) {
    fun observeSummaries(): Flow<List<TemplateSummary>> = templateDao.observeAll().map { templates ->
        templates.map { t -> summary(t) }
    }

    private suspend fun summary(t: MealTemplateEntity) = TemplateSummary(
        template = t,
        items = templateDao.itemsOf(t.id).mapNotNull { item -> foodDao.findById(item.foodId)?.let { it to item.quantityGrams } },
    )

    /**
     * Saves what is logged in one slot on one day as a template. Entries with
     * no food behind them - quick adds, photo estimates, prepared meals - get
     * a food made from their logged macros, so the template reproduces exactly
     * what was eaten.
     */
    suspend fun saveSlotAsTemplate(date: LocalDate, slot: MealSlot, name: String): MealTemplateEntity {
        val range = clock.rangeFor(date)
        val entries = foodLogDao.inRange(range.startInclusive, range.endExclusive).filter { it.mealSlot == slot }
        require(entries.isNotEmpty()) { "That meal has nothing logged" }
        val now = clock.nowMillis()
        val template = MealTemplateEntity(name = name.trim(), createdAt = now, updatedAt = now)
        templateDao.upsertTemplate(template)

        val items = entries.mapIndexed { index, entry ->
            val (foodId, grams) = entry.foodId?.let { it to (entry.quantityGrams ?: 100.0) } ?: run {
                val grams = entry.quantityGrams ?: 100.0
                val per100 = 100.0 / grams
                val food = FoodEntity(
                    name = entry.foodNameAtLog,
                    servingSizeGrams = grams,
                    servingLabel = entry.servingLabelAtLog ?: "1 serving",
                    kcalPer100g = entry.kcal * per100,
                    proteinPer100g = entry.protein * per100,
                    carbsPer100g = entry.carbs * per100,
                    fatPer100g = entry.fat * per100,
                    source = FoodSource.MANUAL,
                    // Protein, carbs or fat the entry never had stay unknown in the template's food too.
                    unlistedNutrients = entry.unlistedNutrients,
                    createdAt = now,
                    updatedAt = now,
                )
                foodDao.upsert(food)
                food.id to grams
            }
            MealTemplateItemEntity(
                mealTemplateId = template.id,
                foodId = foodId,
                quantityGrams = grams,
                displayOrder = index,
                createdAt = now,
                updatedAt = now,
            )
        }
        templateDao.upsertItems(items)
        return template
    }

    /** A template with its items, for the editor. Null when it is gone. */
    suspend fun find(templateId: String): TemplateSummary? =
        templateDao.all().firstOrNull { it.id == templateId }?.let { summary(it) }

    /** Makes a template from foods and recipes picked on the Meal templates screen. Needs no meal plan. */
    suspend fun create(name: String, items: List<TemplateItemInput>): MealTemplateEntity {
        val clean = checkedName(name, items)
        val now = clock.nowMillis()
        val template = MealTemplateEntity(name = clean, createdAt = now, updatedAt = now)
        templateDao.upsertTemplate(template)
        templateDao.upsertItems(rowsFor(template.id, items, now))
        return template
    }

    /**
     * Renames a template and replaces its items. The old item rows are soft-deleted. The NFC tag, if one was written,
     * keeps working: the tag holds only the template's id.
     */
    suspend fun update(templateId: String, name: String, items: List<TemplateItemInput>): MealTemplateEntity? {
        val clean = checkedName(name, items)
        val template = templateDao.all().firstOrNull { it.id == templateId } ?: return null
        val now = clock.nowMillis()
        templateDao.itemsOf(templateId).forEach { templateDao.softDeleteItem(it.id, now) }
        val updated = template.copy(name = clean, updatedAt = now)
        templateDao.upsertTemplate(updated)
        templateDao.upsertItems(rowsFor(templateId, items, now))
        return updated
    }

    private fun checkedName(name: String, items: List<TemplateItemInput>): String {
        val clean = MealTemplateRules.cleanName(name)
        require(clean.isNotEmpty()) { "A template needs a name" }
        require(items.isNotEmpty() && items.size <= MealTemplateRules.MAX_LINES) { "A template needs between 1 and ${MealTemplateRules.MAX_LINES} items" }
        require(items.all { MealTemplateRules.portionOk(it.grams) }) { "Every amount must be more than 0" }
        return clean
    }

    private fun rowsFor(templateId: String, items: List<TemplateItemInput>, now: Long) = items.mapIndexed { index, item ->
        MealTemplateItemEntity(
            mealTemplateId = templateId,
            foodId = item.foodId,
            quantityGrams = item.grams,
            displayOrder = index,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * A saved recipe as a food a template can hold: one serving is 100 g, so its per-serving figures are the per-100 g
     * figures. Figures the recipe never gave stay unlisted, and the ingredient lines are kept as the food's allergen
     * text so the local scan at log time still sees them. Null when the recipe has no calorie figure. The same recipe
     * with the same figures reuses one food, so adding it to several templates does not pile up copies.
     */
    suspend fun foodForRecipe(recipe: RecipeEntity): FoodEntity? {
        val spec = RecipeFood.spec(
            recipe.kcalPerServing, recipe.proteinPerServing, recipe.carbsPerServing, recipe.fatPerServing,
            recipe.fiberPerServing, recipe.sugarPerServing, recipe.sodiumMgPerServing, recipe.saturatedFatPerServing,
        ) ?: return null
        val id = seedId(
            "recipe-food",
            listOf(recipe.id, spec.kcalPer100g, spec.proteinPer100g, spec.carbsPer100g, spec.fatPer100g, spec.unlisted).joinToString(":"),
        )
        foodDao.findById(id)?.let { return it }
        val lines = runCatching {
            recipes.load(recipe.id)?.ingredients.orEmpty().joinToString("\n") { listOf(it.line, it.allergenTags).filter(String::isNotBlank).joinToString(" ") }
        }.getOrNull().orEmpty().ifBlank { recipe.ingredientsText }
        val now = clock.nowMillis()
        val food = FoodEntity(
            id = id,
            name = recipe.name,
            servingSizeGrams = RecipeFood.SERVING_GRAMS,
            servingLabel = RecipeFood.SERVING_LABEL,
            kcalPer100g = spec.kcalPer100g,
            proteinPer100g = spec.proteinPer100g,
            carbsPer100g = spec.carbsPer100g,
            fatPer100g = spec.fatPer100g,
            fiberPer100g = spec.fiberPer100g,
            sugarPer100g = spec.sugarPer100g,
            sodiumPer100g = spec.sodiumMgPer100g,
            saturatedFatPer100g = spec.saturatedFatPer100g,
            source = FoodSource.RECIPE,
            allergenTags = lines,
            unlistedNutrients = spec.unlisted,
            createdAt = now,
            updatedAt = now,
        )
        foodDao.upsert(food)
        return food
    }

    /**
     * Logs a template. With no [slot], it goes to the slot for the time of
     * day - what a tag tap means by "the current slot".
     *
     * Every food is re-checked against the allergen profile as it is now. A
     * template saved before an allergen was added must not log silently.
     */
    suspend fun log(templateId: String, slot: MealSlot? = null, at: Long = clock.nowMillis(), allergenOverride: Boolean = false): TemplateLogResult {
        val template = templateDao.all().firstOrNull { it.id == templateId } ?: return TemplateLogResult.NotFound
        val items = summary(template).items
        if (items.isEmpty()) return TemplateLogResult.NotFound

        val blocked = items.filter { (food, _) -> allergens.scan(food).blocks }.map { it.first.name }
        if (blocked.isNotEmpty() && !allergenOverride) return TemplateLogResult.Blocked(template, blocked)

        val target = slot ?: TemplateLink.slotFor(Instant.ofEpochMilli(at).atZone(clock.zone()).toLocalTime())
        val logs = items.map { (food, grams) -> foodLog.logFood(food, grams, target, at) }
        templateDao.upsertTemplate(template.copy(lastLoggedAt = at, updatedAt = clock.nowMillis()))
        return TemplateLogResult.Logged(template, target, logs)
    }

    /** Undo for the "logged from tag" snackbar. */
    suspend fun undo(logs: List<FoodLogEntity>) = logs.forEach { foodLog.softDelete(it.id) }

    suspend fun markWritten(templateId: String, tagUid: String) {
        val template = templateDao.all().firstOrNull { it.id == templateId } ?: return
        val now = clock.nowMillis()
        templateDao.upsertTemplate(template.copy(nfcTagUid = tagUid, nfcWrittenAt = now, updatedAt = now))
    }

    suspend fun delete(templateId: String) = templateDao.softDelete(templateId, clock.nowMillis())
}
