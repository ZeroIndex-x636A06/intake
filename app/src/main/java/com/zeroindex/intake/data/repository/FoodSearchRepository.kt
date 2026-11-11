package com.zeroindex.intake.data.repository

import android.util.Log
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.remote.usda.UsdaFood
import com.zeroindex.intake.data.remote.usda.UsdaNutrientIds
import com.zeroindex.intake.data.remote.usda.UsdaService
import com.zeroindex.intake.domain.drinks.DrinkRule
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.nutrition.NutrientKey
import com.zeroindex.intake.domain.nutrition.Unlisted
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * SPEC §5.1: search the local table first, then USDA, and cache every remote
 * result locally so the database grows into a personal one that works offline.
 */
@Singleton
class FoodSearchRepository @Inject constructor(
    private val usdaService: UsdaService,
    private val apiKeyStore: ApiKeyStore,
    private val foodRepository: FoodRepository,
) {
    suspend fun searchRemote(query: String): RemoteResult<List<FoodEntity>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return RemoteResult.Success(emptyList())
        val key = apiKeyStore.usdaKey() ?: return RemoteResult.MissingKey

        return try {
            val response = usdaService.search(apiKey = key, query = trimmed)
            val cached = response.foods.mapNotNull { it.toFoodEntityOrNull() }
            if (cached.isEmpty()) return RemoteResult.NotFound
            // Cached immediately: the next search for the same thing is offline.
            foodRepository.saveAll(cached)
            RemoteResult.Success(cached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "USDA search failed", e)
            RemoteResult.Failed("No connection to the food database. Add it by hand instead.")
        } catch (e: Exception) {
            Log.w(TAG, "USDA search failed", e)
            RemoteResult.Failed(
                "The food database returned something unexpected. Add it by hand instead.",
            )
        }
    }

    private fun UsdaFood.toFoodEntityOrNull(): FoodEntity? {
        val name = description.trim()
        if (name.isBlank()) return null
        fun nutrient(id: Int): Double = foodNutrients.firstOrNull { it.nutrientId == id }?.value ?: 0.0
        fun missing(id: Int): Boolean = foodNutrients.none { it.nutrientId == id && it.value != null }

        val kcal = nutrient(UsdaNutrientIds.ENERGY_KCAL)
        // A record with no energy value is not usable for logging.
        if (kcal <= 0.0) return null

        val servingGrams = servingSize
            ?.takeIf { it > 0 && servingSizeUnit?.lowercase() in setOf("g", "gram", "grams") }
            ?: 100.0

        return FoodEntity(
            name = name,
            brand = (brandName ?: brandOwner)?.trim()?.takeIf { it.isNotEmpty() },
            servingSizeGrams = servingGrams,
            servingLabel = householdServingFullText?.trim()?.takeIf { it.isNotEmpty() }
                ?: "${servingGrams.toInt()} g",
            // USDA reports per 100 g, which is how they are stored (SPEC §4).
            kcalPer100g = kcal,
            proteinPer100g = nutrient(UsdaNutrientIds.PROTEIN),
            carbsPer100g = nutrient(UsdaNutrientIds.CARBS),
            fatPer100g = nutrient(UsdaNutrientIds.FAT),
            fiberPer100g = nutrient(UsdaNutrientIds.FIBER),
            sugarPer100g = nutrient(UsdaNutrientIds.SUGAR),
            sodiumPer100g = nutrient(UsdaNutrientIds.SODIUM),
            saturatedFatPer100g = nutrient(UsdaNutrientIds.SATURATED_FAT),
            source = FoodSource.SEARCH,
            unlistedNutrients = Unlisted.encode(
                buildSet {
                    if (missing(UsdaNutrientIds.FIBER)) add(NutrientKey.FIBER)
                    if (missing(UsdaNutrientIds.SUGAR)) add(NutrientKey.SUGAR)
                    if (missing(UsdaNutrientIds.SODIUM)) add(NutrientKey.SODIUM)
                    if (missing(UsdaNutrientIds.SATURATED_FAT)) add(NutrientKey.SATURATED_FAT)
                },
            ),
            drinkCategory = DrinkRule.categoryOf(name, foodCategory.orEmpty())?.name,
            // Ingredient text is what the allergen engine scans later.
            allergenTags = ingredients?.trim().orEmpty(),
            isVerified = dataType == "Foundation" || dataType == "SR Legacy",
            createdAt = 0,
            updatedAt = 0,
        )
    }

    private companion object { const val TAG = "FoodSearchRepository" }
}
