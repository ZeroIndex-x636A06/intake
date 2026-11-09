package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.AllergenDao
import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.entity.AllergenAliasEntity
import com.zeroindex.intake.data.db.entity.AllergenDefinitionEntity
import com.zeroindex.intake.data.db.entity.AllergenProfileEntity
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.CheckLabelTerm
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.model.KeywordLevel
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanWatchTerms
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** One row of the "My allergens" list. */
data class AllergenListItem(
    val key: String,
    val displayName: String,
    val summary: String,
    val isCustom: Boolean,
    val avoided: Boolean,
    val keywordCount: Int,
)

/** The allergens the user can pick from, and the profile they are saved in. */
data class AllergenOverview(
    val items: List<AllergenListItem>,
    val severity: AllergenSeverity,
)

/** The alias list and the user's profile, plus the scan that uses both. */
@Singleton
class AllergenRepository @Inject constructor(
    private val allergenDao: AllergenDao,
    private val foodDao: FoodDao,
    private val planDao: PlanDao,
    private val clock: Clock,
) {
    fun observeProfile(): Flow<AllergenProfileEntity?> = allergenDao.observeProfile()

    /** Keywords the engine uses (switched-off ones excluded). */
    fun observeAliases(): Flow<List<AllergenAliasEntity>> = allergenDao.observeAliases()

    /** Grouped for the reference screen (SPEC §7.6). */
    fun observeAliasesByAllergen(): Flow<Map<String, List<String>>> =
        allergenDao.observeAliases().map { rows ->
            rows.groupBy { it.allergen }.mapValues { (_, v) -> v.map { it.aliasTerm }.sorted() }
        }

    fun observeAvoidedAllergens(): Flow<Set<String>> =
        allergenDao.observeProfile().map { it.avoidedSet() }

    /**
     * The soft "Check the label" terms in force for the user's avoided allergens: the built-in carriers,
     * their own check-the-label keywords, and quoted phrases from the plan's watch-for list.
     */
    fun observeCheckLabelTerms(): Flow<List<CheckLabelTerm>> =
        combine(
            observeAvoidedAllergens(),
            planDao.observeActivePlan(),
            allergenDao.observeAliases(),
        ) { avoided, plan, aliases ->
            val own = aliases.filter { it.level == KeywordLevel.CHECK_LABEL }.map {
                CheckLabelTerm(it.allergen, it.aliasTerm, "Says ${it.aliasTerm} - check the label")
            }
            val all = HiddenCarriers.ALL + own +
                PlanWatchTerms.extract(PlanMeta.decode(plan?.metaJson).hiddenDairyChecklist)
            all.filter { it.allergen in avoided }
        }

    suspend fun profile(): AllergenProfileEntity? = allergenDao.profile()

    /** The allergens the user avoids, lower-cased. */
    suspend fun avoidedAllergens(): Set<String> = allergenDao.profile().avoidedSet()

    /** The keywords the engine uses, as engine types. */
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

    // ---- My allergens ----------------------------------------------------------------------

    fun observeOverview(): Flow<AllergenOverview> =
        combine(allergenDao.observeDefinitions(), allergenDao.observeAllAliases(), allergenDao.observeProfile()) { defs, aliases, profile ->
            val avoided = profile.avoidedSet()
            val counts = aliases.groupingBy { it.allergen }.eachCount()
            AllergenOverview(
                items = defs.map { d ->
                    AllergenListItem(d.name, d.displayName, d.description, d.isCustom, d.name in avoided, counts[d.name] ?: 0)
                },
                severity = profile?.severityLevel ?: AllergenSeverity.AVOID,
            )
        }

    fun observeDefinition(key: String): Flow<AllergenDefinitionEntity?> =
        allergenDao.observeDefinitions().map { list -> list.firstOrNull { it.name == key } }

    /** Every live keyword of one allergen, switched-off ones included. */
    fun observeKeywords(key: String): Flow<List<AllergenAliasEntity>> =
        allergenDao.observeAllAliases().map { rows -> rows.filter { it.allergen == key } }

    suspend fun setAvoided(key: String, avoid: Boolean) {
        val profile = allergenDao.profile()
        val set = profile.avoidedSet().toMutableSet()
        if (avoid) set += key else set -= key
        setProfile(set, profile?.severityLevel ?: AllergenSeverity.AVOID, profile?.notes.orEmpty())
    }

    suspend fun setSeverity(severity: AllergenSeverity) {
        val profile = allergenDao.profile()
        setProfile(profile.avoidedSet(), severity, profile?.notes.orEmpty())
    }

    /**
     * Creates a custom allergen (for example "Nightshades") and starts avoiding it. Returns its key, or
     * null when the name is blank. A name that already exists is reused, undeleting it if needed.
     */
    suspend fun createCustomAllergen(displayName: String): String? {
        val shown = displayName.trim().replace(Regex("\\s+"), " ").replace(',', ' ').trim()
        if (shown.isEmpty()) return null
        val key = shown.lowercase()
        val now = clock.nowMillis()
        val existing = allergenDao.definitionIncludingDeleted(key)
        if (existing == null) {
            allergenDao.upsertDefinition(
                AllergenDefinitionEntity(
                    id = seedId("allergen-custom", key), name = key, displayName = shown,
                    description = "Added by you.", isCustom = true, sortOrder = 1000,
                    createdAt = now, updatedAt = now,
                ),
            )
        } else if (existing.deletedAt != null) {
            allergenDao.upsertDefinition(existing.copy(deletedAt = null, updatedAt = now))
        }
        setAvoided(key, true)
        return key
    }

    /** Removes a custom allergen (soft delete, with its custom keywords). Built-in allergens cannot be removed. */
    suspend fun removeCustomAllergen(key: String) {
        val def = allergenDao.definitionIncludingDeleted(key)?.takeIf { it.isCustom } ?: return
        val now = clock.nowMillis()
        allergenDao.softDeleteDefinition(def.id, now)
        allergenDao.softDeleteCustomAliasesOf(key, now)
        setAvoided(key, false)
    }

    /** Adds (or revives, or re-levels) a keyword the user wrote. Works on built-in and custom allergens. */
    suspend fun addKeyword(allergen: String, term: String, level: KeywordLevel) {
        val key = allergen.trim().lowercase()
        val clean = term.trim().lowercase().replace(Regex("\\s+"), " ")
        if (key.isEmpty() || clean.isEmpty()) return
        val now = clock.nowMillis()
        val existing = allergenDao.aliasIncludingDeleted(key, clean)
        if (existing == null) {
            allergenDao.upsertAlias(
                AllergenAliasEntity(
                    id = seedId("allergen-keyword", "$key|$clean"), allergen = key, aliasTerm = clean,
                    level = level, isCustom = true, isEnabled = true, createdAt = now, updatedAt = now,
                ),
            )
        } else {
            // Never weaken a built-in keyword by re-adding it; a custom one takes the new level.
            val newLevel = if (existing.isCustom) level else strongest(existing.level, level)
            allergenDao.upsertAlias(
                existing.copy(level = newLevel, isEnabled = true, deletedAt = null, updatedAt = now),
            )
        }
    }

    /** Compatibility with the old Settings editor. */
    suspend fun addAlias(allergen: String, term: String) = addKeyword(allergen, term, KeywordLevel.CONTAINS)

    suspend fun setKeywordLevel(id: String, level: KeywordLevel) {
        val row = allergenDao.aliasById(id)?.takeIf { it.isCustom } ?: return
        allergenDao.upsertAlias(row.copy(level = level, updatedAt = clock.nowMillis()))
    }

    /** The soft flag: turns a keyword off (or back on) without deleting anything. */
    suspend fun setKeywordEnabled(id: String, enabled: Boolean) {
        val row = allergenDao.aliasById(id) ?: return
        allergenDao.upsertAlias(row.copy(isEnabled = enabled, updatedAt = clock.nowMillis()))
    }

    /** Removes a keyword the user added (soft delete). Built-in keywords can only be switched off. */
    suspend fun removeKeyword(id: String) {
        val row = allergenDao.aliasById(id)?.takeIf { it.isCustom } ?: return
        allergenDao.softDeleteAlias(row.id, clock.nowMillis())
    }

    suspend fun removeAlias(id: String) = removeKeyword(id)

    private fun strongest(a: KeywordLevel, b: KeywordLevel) = if (a.kind.rank >= b.kind.rank) a else b

    // ---- Scanning --------------------------------------------------------------------------

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
            AllergenAlias(allergen = it.allergen, aliasTerm = it.aliasTerm, level = it.level)
        }
        return AllergenMatcher.scan(
            aliases = aliases,
            avoidedAllergens = avoided,
            name = name,
            brand = brand,
            ingredients = ingredients,
            structuredContains = structuredContains,
            structuredTraces = structuredTraces,
            checkLabelTerms = HiddenCarriers.forAllergens(avoided) +
                PlanWatchTerms.extract(PlanMeta.decode(planDao.activePlan()?.metaJson).hiddenDairyChecklist),
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
