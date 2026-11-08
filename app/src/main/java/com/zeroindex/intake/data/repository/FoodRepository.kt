package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.FoodDao
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** SPEC §2: the only thing that touches [FoodDao]. */
@Singleton
class FoodRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val clock: Clock,
) {
    fun observeRecents(limit: Int = 30): Flow<List<FoodEntity>> = foodDao.observeMostLogged(limit)
    fun observeFavorites(): Flow<List<FoodEntity>> = foodDao.observeFavorites()
    fun search(query: String): Flow<List<FoodEntity>> = foodDao.searchByName(query)

    suspend fun findById(id: String): FoodEntity? = foodDao.findById(id)
    suspend fun findByBarcode(barcode: String): FoodEntity? = foodDao.findByBarcode(barcode)
    suspend fun all(): List<FoodEntity> = foodDao.all()

    suspend fun toggleFavorite(id: String, favorite: Boolean) =
        foodDao.setFavorite(id, favorite, clock.nowMillis())

    suspend fun softDelete(id: String) = foodDao.softDelete(id, clock.nowMillis())

    suspend fun save(food: FoodEntity): FoodEntity {
        val now = clock.nowMillis()
        val stamped = food.copy(
            createdAt = if (food.createdAt == 0L) now else food.createdAt,
            updatedAt = now,
        )
        foodDao.upsert(stamped)
        return stamped
    }

    suspend fun saveAll(foods: List<FoodEntity>) {
        val now = clock.nowMillis()
        foodDao.upsertAll(
            foods.map {
                it.copy(createdAt = if (it.createdAt == 0L) now else it.createdAt, updatedAt = now)
            },
        )
    }

    /**
     * Creates a food from raw per-100g numbers. Used by manual entry, and by the
     * remote paths which cache every result locally so the database grows into a
     * personal one that works offline (SPEC §5.1).
     */
    suspend fun create(
        name: String,
        brand: String? = null,
        barcode: String? = null,
        servingSizeGrams: Double,
        servingLabel: String,
        kcalPer100g: Double,
        proteinPer100g: Double,
        carbsPer100g: Double,
        fatPer100g: Double,
        fiberPer100g: Double = 0.0,
        sugarPer100g: Double = 0.0,
        sodiumPer100g: Double = 0.0,
        saturatedFatPer100g: Double = 0.0,
        source: FoodSource,
        allergenTags: String = "",
        isVerified: Boolean = false,
    ): FoodEntity {
        val now = clock.nowMillis()
        val food = FoodEntity(
            id = newId(),
            name = name,
            brand = brand?.takeIf { it.isNotBlank() },
            barcode = barcode?.takeIf { it.isNotBlank() },
            servingSizeGrams = servingSizeGrams,
            servingLabel = servingLabel,
            kcalPer100g = kcalPer100g,
            proteinPer100g = proteinPer100g,
            carbsPer100g = carbsPer100g,
            fatPer100g = fatPer100g,
            fiberPer100g = fiberPer100g,
            sugarPer100g = sugarPer100g,
            sodiumPer100g = sodiumPer100g,
            saturatedFatPer100g = saturatedFatPer100g,
            source = source,
            allergenTags = allergenTags,
            isVerified = isVerified,
            createdAt = now,
            updatedAt = now,
        )
        foodDao.upsert(food)
        return food
    }
}
