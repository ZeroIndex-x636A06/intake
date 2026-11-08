package com.zeroindex.intake.data.repository

import android.util.Log
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.remote.openfoodfacts.OffProduct
import com.zeroindex.intake.data.remote.openfoodfacts.OpenFoodFactsService
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.FoodSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** A scanned product plus the allergen verdict for it. */
data class ScannedProduct(
    val food: FoodEntity,
    val scan: AllergenScanResult,
    val fromCache: Boolean,
)

/**
 * SPEC §5.2: on a scan, check the local table first - a barcode seen before
 * resolves instantly and offline - then Open Food Facts. Structured allergen
 * tags come back with the product and are the primary signal (SPEC §7.3).
 */
@Singleton
class BarcodeRepository @Inject constructor(
    private val service: OpenFoodFactsService,
    private val foodRepository: FoodRepository,
    private val allergenRepository: AllergenRepository,
) {
    suspend fun lookup(barcode: String): RemoteResult<ScannedProduct> {
        val code = barcode.trim()
        if (code.isBlank()) return RemoteResult.NotFound

        foodRepository.findByBarcode(code)?.let { cached ->
            return RemoteResult.Success(
                ScannedProduct(cached, allergenRepository.scan(cached), fromCache = true),
            )
        }

        return try {
            val response = service.product(code)
            val product = response.product
            if (response.status != 1 || product == null) return RemoteResult.NotFound

            val food = product.toFoodEntity(code) ?: return RemoteResult.NotFound
            val saved = foodRepository.save(food)
            val scan = allergenRepository.scan(
                name = saved.name,
                brand = saved.brand,
                ingredients = product.ingredientsText,
                structuredContains = product.allergensTags,
                structuredTraces = product.tracesTags,
            )
            RemoteResult.Success(ScannedProduct(saved, scan, fromCache = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Open Food Facts lookup failed", e)
            RemoteResult.Failed("No connection. You can still add this barcode by hand.")
        } catch (e: Exception) {
            Log.w(TAG, "Open Food Facts lookup failed", e)
            RemoteResult.Failed("That product record could not be read. Add it by hand instead.")
        }
    }

    /**
     * SPEC §5.2: if the product is not found, the barcode is kept against a
     * hand-made food so the next scan of the same item resolves instantly.
     */
    suspend fun createManualForBarcode(
        barcode: String,
        name: String,
        brand: String?,
        servingGrams: Double,
        servingLabel: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
    ): FoodEntity {
        val per100 = 100.0 / servingGrams
        return foodRepository.create(
            name = name,
            brand = brand,
            barcode = barcode,
            servingSizeGrams = servingGrams,
            servingLabel = servingLabel.ifBlank { "1 serving" },
            kcalPer100g = kcal * per100,
            proteinPer100g = protein * per100,
            carbsPer100g = carbs * per100,
            fatPer100g = fat * per100,
            source = FoodSource.BARCODE,
        )
    }

    private fun OffProduct.toFoodEntity(barcode: String): FoodEntity? {
        val n = nutriments ?: return null
        val kcal = n.energyKcal100g ?: return null
        if (kcal <= 0.0) return null
        val name = productName?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val servingGrams = servingQuantity?.takeIf { it > 0 } ?: 100.0
        // Open Food Facts reports sodium in grams; salt is the fallback.
        val sodiumMg = (n.sodium100g ?: n.salt100g?.let { it * 0.393 })?.times(1000.0) ?: 0.0

        return FoodEntity(
            name = name,
            brand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            barcode = barcode,
            servingSizeGrams = servingGrams,
            servingLabel = servingSize?.trim()?.takeIf { it.isNotEmpty() }
                ?: "${servingGrams.toInt()} g",
            kcalPer100g = kcal,
            proteinPer100g = n.proteins100g ?: 0.0,
            carbsPer100g = n.carbohydrates100g ?: 0.0,
            fatPer100g = n.fat100g ?: 0.0,
            fiberPer100g = n.fiber100g ?: 0.0,
            sugarPer100g = n.sugars100g ?: 0.0,
            sodiumPer100g = sodiumMg,
            saturatedFatPer100g = n.saturatedFat100g ?: 0.0,
            source = FoodSource.BARCODE,
            // Keep both the structured tags and the panel text: the engine
            // rescans this whenever the food is logged again.
            allergenTags = buildString {
                val tags = (allergensTags + tracesTags).joinToString(",")
                if (tags.isNotEmpty()) append(tags)
                ingredientsText?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    if (isNotEmpty()) append(" | ")
                    append(it)
                }
            },
            createdAt = 0,
            updatedAt = 0,
        )
    }

    private companion object { const val TAG = "BarcodeRepository" }
}
