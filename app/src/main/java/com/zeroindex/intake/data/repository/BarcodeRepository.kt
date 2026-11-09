package com.zeroindex.intake.data.repository

import android.util.Log
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.remote.openfoodfacts.OffMapping
import com.zeroindex.intake.data.remote.openfoodfacts.OpenFoodFactsService
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.barcode.Barcodes
import com.zeroindex.intake.domain.model.FoodSource
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

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

        Barcodes.lookupForms(code).firstNotNullOfOrNull { foodRepository.findByBarcode(it) }?.let { cached ->
            return RemoteResult.Success(
                ScannedProduct(cached, allergenRepository.scan(cached), fromCache = true),
            )
        }

        return try {
            val response = service.product(code)
            val product = response.product
            if (response.status != 1 || product == null) return RemoteResult.NotFound

            // A product Open Food Facts knows but with no name or energy cannot be logged:
            // for the user that is the same as "not found, add it by hand".
            val food = OffMapping.fromProduct(product, code) ?: return RemoteResult.NotFound
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
        } catch (e: HttpException) {
            // Open Food Facts answers an unknown barcode with 404 on some products.
            if (e.code() == 404) {
                RemoteResult.NotFound
            } else {
                Log.w(TAG, "Open Food Facts lookup failed with HTTP ${e.code()}")
                RemoteResult.Failed("Open Food Facts is not answering right now. Try again, or add this barcode by hand.")
            }
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

    private companion object { const val TAG = "BarcodeRepository" }
}
