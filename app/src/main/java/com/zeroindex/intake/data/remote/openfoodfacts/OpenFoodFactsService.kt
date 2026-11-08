package com.zeroindex.intake.data.remote.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: Double? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    /** Structured tags, e.g. ["en:milk"]. SPEC §7.3 trusts these first. */
    @SerialName("allergens_tags") val allergensTags: List<String> = emptyList(),
    @SerialName("traces_tags") val tracesTags: List<String> = emptyList(),
    val nutriments: OffNutriments? = null,
)

/** Open Food Facts reports per-100g values under the `_100g` suffix. */
@Serializable
data class OffNutriments(
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @SerialName("fat_100g") val fat100g: Double? = null,
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @SerialName("sugars_100g") val sugars100g: Double? = null,
    @SerialName("sodium_100g") val sodium100g: Double? = null,
    @SerialName("salt_100g") val salt100g: Double? = null,
    @SerialName("saturated-fat_100g") val saturatedFat100g: Double? = null,
)

interface OpenFoodFactsService {

    /** SPEC §5.2: barcode lookup. No key needed, so this path works out of the box. */
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): OffProductResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val FIELDS =
            "product_name,brands,serving_size,serving_quantity,ingredients_text," +
                "allergens_tags,traces_tags,nutriments"
    }
}
