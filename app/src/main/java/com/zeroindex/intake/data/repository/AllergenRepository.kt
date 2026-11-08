package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.AllergenDao
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanWatchTerms
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** The alias list and the user's profile, plus the scan that uses both. */
@Singleton
class AllergenRepository @Inject constructor(
    private val allergenDao: AllergenDao,
    private val foodDao: FoodDao,
    private val planDao: PlanDao,
    private val clock: Clock,
) {
    fun observeProfile(): Flow<AllergenProfileEntity?> = allergenDao.observeProfile()

    fun observeAliases(): Flow<List<AllergenAliasEntity>> = allergenDao.observeAliases()

    /** Grouped for the reference screen (SPEC §7.6). */
    fun observeAliasesByAllergen(): Flow<Map<String, List<String>>> =
        allergenDao.observeAliases().map { rows ->
            rows.groupBy { it.allergen }.mapValues { (_, v) -> v.map { it.aliasTerm }.sorted() }
        }

    fun observeAvoidedAllergens(): Flow<Set<String>> =
        allergenDao.observeProfile().map { it.avoidedSet() }

    /**
     * The soft "Check the label" terms in force for the user's avoided allergens:
     * the built-in carriers plus quoted phrases from the plan's watch-for list.
     */
    fun observeCheckLabelTerms(): Flow<List<CheckLabelTerm>> =
        combine(observeAvoidedAllergens(), planDao.observeWeek(PlanMeta.WEEK_NUMBER)) { avoided, week ->
            val all = HiddenCarriers.DAIRY_TERMS +
                PlanWatchTerms.extract(PlanMeta.decode(week?.notes).hiddenDairyChecklist)
            all.filter { it.allergen in avoided }
        }

    suspend fun profile(): AllergenProfileEntity? = allergenDao.profile()

    /** The allergens the user avoids, lower-cased. */
    suspend fun avoidedAllergens(): Set<String> = allergenDao.profile().avoidedSet()

    suspend fun aliases(): List<AllergenAliasEntity> = allergenDao.aliases()

    suspend fun setProfile(avoided: Set<String>, severity: AllergenSeverity, notes: String) {
        val now = clock.nowMillis()
        val existing = allergenDao.profile()
        allergenDao.upsertProfile(
            AllergenProfileEntity(
                id = AllergenProfileEntity.SINGLETON_ID,
                avoidedAllergens = avoided.joinToString(","),
                severityLevel = severity,
                notes = notes,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun addAlias(allergen: String, term: String) {
        val now = clock.nowMillis()
        allergenDao.upsertAliases(
            listOf(
                AllergenAliasEntity(
                    id = newId(),
                    allergen = allergen.trim().lowercase(),
                    aliasTerm = term.trim().lowercase(),
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
        )
    }

    suspend fun removeAlias(id: String) = allergenDao.softDeleteAlias(id, clock.nowMillis())

    /** SPEC §7: runs on every food entering the system, from any path. */
    suspend fun scan(
        name: String?,
        brand: String? = null,
        ingredients: String? = null,
        structuredContains: List<String> = emptyList(),
        structuredTraces: List<String> = emptyList(),
        /** True when [ingredients] is free-text recipe lines rather than a label panel. */
        checkLabelInIngredients: Boolean = false,
    ): AllergenScanResult {
        val avoided = allergenDao.profile().avoidedSet()
        if (avoided.isEmpty()) return AllergenScanResult.CLEAN
        val aliases = allergenDao.aliases().map {
            AllergenAlias(allergen = it.allergen, aliasTerm = it.aliasTerm)
        }
        return AllergenMatcher.scan(
            aliases = aliases,
            avoidedAllergens = avoided,
            name = name,
            brand = brand,
            ingredients = ingredients,
            structuredContains = structuredContains,
            structuredTraces = structuredTraces,
            checkLabelTerms = HiddenCarriers.DAIRY_TERMS +
                PlanWatchTerms.extract(PlanMeta.decode(planDao.week(PlanMeta.WEEK_NUMBER)?.notes).hiddenDairyChecklist),
            checkLabelInIngredients = checkLabelInIngredients,
        )
    }

    suspend fun scan(food: FoodEntity): AllergenScanResult = scan(
        name = food.name,
        brand = food.brand,
        ingredients = food.allergenTags.takeIf { it.isNotBlank() },
    )

    /**
     * SPEC §7.5: any food logged three or more times with no allergen flag gets
     * a green check in search results. "No flag" is read off the stored
     * allergen text, so a food that was flagged and logged anyway never
     * becomes safe.
     */
    fun observeSafeFoodIds(): Flow<Set<String>> =
        combine(
            foodDao.observeMostLogged(limit = 500),
            observeAvoidedAllergens(),
        ) { foods, avoided ->
            foods.filter { food ->
                food.timesLogged >= SAFE_FOOD_THRESHOLD &&
                    (avoided.isEmpty() || food.allergenTags.isBlank())
            }.map { it.id }.toSet()
        }

    private fun AllergenProfileEntity?.avoidedSet(): Set<String> =
        this?.avoidedAllergens
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    private companion object {
        const val SAFE_FOOD_THRESHOLD = 3
    }
}
