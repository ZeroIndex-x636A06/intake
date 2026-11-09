package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.food.ProductImagePolicy.Decision
import org.junit.Test

class ProductImagePolicyTest {

    private val url = "https://images.openfoodfacts.org/images/products/500/031/600/0256/front_en.21.200.jpg"

    @Test
    fun nothingLoadsWhenOpenFoodFactsSearchIsOff() {
        assertThat(ProductImagePolicy.decide(url, offEnabled = false, online = true)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide(url, offEnabled = false, online = false)).isEqualTo(Decision.SKIP)
    }

    @Test
    fun offlineOnlyTheCacheIsUsed() {
        assertThat(ProductImagePolicy.decide(url, offEnabled = true, online = false)).isEqualTo(Decision.CACHE_ONLY)
        assertThat(ProductImagePolicy.decide(url, offEnabled = true, online = true)).isEqualTo(Decision.FETCH_OK)
    }

    @Test
    fun onlyHttpsOpenFoodFactsAddressesAreAllowed() {
        assertThat(ProductImagePolicy.decide("http://images.openfoodfacts.org/a.jpg", true, true)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide("https://example.com/a.jpg", true, true)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide("https://images.openfoodfacts.org@evil.com/a.jpg", true, true)).isEqualTo(Decision.SKIP)
        assertThat(ProductImagePolicy.decide(null, true, true)).isEqualTo(Decision.SKIP)
    }

    @Test
    fun sampleSizeKeepsTheBitmapJustLargeEnough() {
        assertThat(ProductImagePolicy.sampleSize(200, 150, 132)).isEqualTo(1)
        assertThat(ProductImagePolicy.sampleSize(400, 400, 100)).isEqualTo(4)
        assertThat(ProductImagePolicy.sampleSize(4000, 3000, 100)).isEqualTo(16)
        assertThat(ProductImagePolicy.sampleSize(0, 0, 100)).isEqualTo(1)
    }

    @Test
    fun cacheNamesAreStableAndNeverAPath() {
        val a = ProductImagePolicy.cacheName(url)
        assertThat(a).isEqualTo(ProductImagePolicy.cacheName(url))
        assertThat(a).matches("[0-9a-f]{64}")
    }

    @Test
    fun foodsWithoutAPhotoGetAnIconForTheirKind() {
        assertThat(FoodCategory.of("Whole milk")).isEqualTo(FoodCategory.DAIRY)
        assertThat(FoodCategory.of("Rice, white, cooked")).isEqualTo(FoodCategory.GRAIN)
        assertThat(FoodCategory.of("Ice cream, vanilla")).isEqualTo(FoodCategory.SWEET)
        assertThat(FoodCategory.of("Apple juice")).isEqualTo(FoodCategory.DRINK)
        assertThat(FoodCategory.of("Salmon, Atlantic")).isEqualTo(FoodCategory.FISH)
        assertThat(FoodCategory.of("Mystery stew")).isEqualTo(FoodCategory.OTHER)
        assertThat(FoodCategory.of("Mystery stew", "Beef Products")).isEqualTo(FoodCategory.MEAT)
    }
}
