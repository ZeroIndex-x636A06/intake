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
import com.zeroindex.intake.domain.nfc.TemplateLink
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
}

sealed interface TemplateLogResult {
    data class Logged(val template: MealTemplateEntity, val slot: MealSlot, val logs: List<FoodLogEntity>) : TemplateLogResult
    /** The allergen profile now matches a food that was fine when the template was saved. */
    data class Blocked(val template: MealTemplateEntity, val foods: List<String>) : TemplateLogResult
    data object NotFound : TemplateLogResult
}

/**
 * Meal templates: a saved meal that logs in one go. Amendment §2 puts them on
 * NFC tags stuck to meal-prep containers.
 */
@Singleton
class MealTemplateRepository @Inject constructor(
    private val templateDao: MealTemplateDao,
    private val foodDao: FoodDao,
    private val foodLogDao: FoodLogDao,
    private val foodLog: FoodLogRepository,
    private val allergens: AllergenRepository,
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
