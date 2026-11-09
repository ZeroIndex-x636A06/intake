package com.zeroindex.intake.ui.addfood

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.data.repository.ScannedProduct
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.FoodSource
import org.junit.Test

class BarcodeRoutingTest {

    private val food = FoodEntity(
        name = "Nutella", servingSizeGrams = 15.0, servingLabel = "1 tbsp", kcalPer100g = 539.0,
        proteinPer100g = 6.3, carbsPer100g = 57.5, fatPer100g = 30.9, source = FoodSource.OPEN_FOOD_FACTS,
        createdAt = 1, updatedAt = 1,
    )

    @Test
    fun aFoundProductIsShown() {
        val state = routeBarcodeResult(
            "3017620422003",
            RemoteResult.Success(ScannedProduct(food, AllergenScanResult.CLEAN, fromCache = true)),
        )
        assertThat(state).isEqualTo(BarcodeState.Found(food, AllergenScanResult.CLEAN, fromCache = true))
    }

    @Test
    fun anUnknownBarcodeOffersAddThisProductWithTheBarcodeKept() {
        assertThat(routeBarcodeResult("0894700010137", RemoteResult.NotFound))
            .isEqualTo(BarcodeState.Unknown("0894700010137", null))
        assertThat(routeBarcodeResult("0894700010137", RemoteResult.MissingKey))
            .isEqualTo(BarcodeState.Unknown("0894700010137", null))
    }

    @Test
    fun aFailedLookupOffersRetryNotAFalseNotFound() {
        assertThat(routeBarcodeResult("0894700010137", RemoteResult.Failed("No connection.")))
            .isEqualTo(BarcodeState.LookupFailed("0894700010137", "No connection."))
    }
}
