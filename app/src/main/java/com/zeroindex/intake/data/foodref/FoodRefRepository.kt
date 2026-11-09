package com.zeroindex.intake.data.foodref

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.foodref.FoodRefSearch.toRefFood
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.domain.model.FoodSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline search over the bundled USDA database, and the copy into the user's own
 * foods that happens when a result is chosen.
 */
@Singleton
class FoodRefRepository @Inject constructor(
    private val dao: RefFoodDao,
    private val foodRepository: FoodRepository,
) {
    suspend fun search(query: String, limit: Int = FoodRefSearch.RESULT_LIMIT): List<RefFood> {
        val tokens = FoodRefSearch.queryTokens(query)
        if (tokens.isEmpty()) return emptyList()
        val padded = tokens + List(FoodRefSearch.MAX_TOKENS - tokens.size) { "" }
        val candidates = dao.candidates(padded[0], padded[1], padded[2], padded[3], FoodRefSearch.CANDIDATE_LIMIT)
        return FoodRefSearch.rank(query, candidates, limit).map { it.toRefFood() }
    }

    suspend fun count(): Int = dao.count()

    /**
     * Copies a bundled food into the user's foods as [FoodSource.DATABASE], so logs
     * stay stable if the bundled dataset ever changes. Choosing the same food twice
     * returns the first copy (and any edits the user made to it) instead of adding
     * a duplicate.
     */
    suspend fun copyToMyFoods(ref: RefFood): FoodEntity {
        val id = foodId(ref.id)
        foodRepository.findById(id)?.let { return it }
        return foodRepository.save(toFoodEntity(ref))
    }

    companion object {
        fun foodId(refId: Int) = "usda-$refId"

        /**
         * The copy's serving is the first portion USDA lists. Generic foods have no
         * ingredient text, so the allergen engine scans the name (SPEC §7): "Cheese,
         * cheddar" is caught. The engine runs at log time, as for every food.
         */
        fun toFoodEntity(ref: RefFood): FoodEntity {
            val portion = ref.defaultPortion
            return FoodEntity(
                id = foodId(ref.id),
                name = ref.name,
                servingSizeGrams = portion.grams,
                servingLabel = if (ref.portions.isEmpty()) "100 g" else "${portion.label} (${portion.grams.toInt()} g)",
                kcalPer100g = ref.kcal,
                proteinPer100g = ref.protein,
                carbsPer100g = ref.carbs,
                fatPer100g = ref.fat,
                fiberPer100g = ref.fiber,
                sugarPer100g = ref.sugar,
                sodiumPer100g = ref.sodium,
                saturatedFatPer100g = ref.saturatedFat,
                source = FoodSource.DATABASE,
                isVerified = true,
                createdAt = 0,
                updatedAt = 0,
            )
        }
    }
}
