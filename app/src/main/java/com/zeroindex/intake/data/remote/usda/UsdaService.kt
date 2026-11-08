package com.zeroindex.intake.data.remote.usda

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * USDA FoodData Central search. Only the fields actually used are declared; the
 * Json instance ignores the rest.
 */
@Serializable
data class UsdaSearchResponse(
    val foods: List<UsdaFood> = emptyList(),
    val totalHits: Int = 0,
)

@Serializable
data class UsdaFood(
    val fdcId: Long = 0,
    val description: String = "",
    val brandName: String? = null,
    val brandOwner: String? = null,
    val dataType: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val ingredients: String? = null,
    val foodNutrients: List<UsdaNutrient> = emptyList(),
)

@Serializable
data class UsdaNutrient(
    @SerialName("nutrientId") val nutrientId: Int = 0,
    @SerialName("nutrientName") val nutrientName: String? = null,
    @SerialName("unitName") val unitName: String? = null,
    val value: Double? = null,
)

/** Nutrient ids from the FDC reference set. */
internal object UsdaNutrientIds {
    const val ENERGY_KCAL = 1008
    const val PROTEIN = 1003
    const val CARBS = 1005
    const val FAT = 1004
    const val FIBER = 1079
    const val SUGAR = 2000
    const val SODIUM = 1093
    const val SATURATED_FAT = 1258
}

interface UsdaService {

    /**
     * SPEC §5.1. The key is user-supplied and passed per call rather than held
     * in an interceptor, so a key added in settings takes effect immediately
     * and no key is ever baked into the client.
     */
    @GET("fdc/v1/foods/search")
    suspend fun search(
        @Header("X-Api-Key") apiKey: String,
        @Query("query") query: String,
        @Query("pageSize") pageSize: Int = 25,
        @Query("dataType") dataType: String = "Foundation,SR Legacy,Branded",
    ): UsdaSearchResponse

    companion object {
        const val BASE_URL = "https://api.nal.usda.gov/"
    }
}
