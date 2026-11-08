package com.zeroindex.intake.data.remote.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Food Facts search-a-licious. No key, no account: the only thing sent is the
 * query text (and the app's User-Agent). Shown in Add food as "Packaged foods".
 */
interface OffSearchService {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page_size") pageSize: Int = PAGE_SIZE,
        @Query("fields") fields: String = FIELDS,
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://search.openfoodfacts.org/"
        const val PAGE_SIZE = 20
        const val FIELDS =
            "code,product_name,brands,quantity,serving_size,serving_quantity,allergens_tags,nutriments"
    }
}

@Serializable
data class OffSearchResponse(
    val hits: List<OffSearchHit> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class OffSearchHit(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    /** A list here, a comma-joined string on the v2 product endpoint. */
    val brands: JsonElement? = null,
    val quantity: String? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("serving_quantity") val servingQuantity: Double? = null,
    @SerialName("allergens_tags") val allergensTags: List<String>? = null,
    val nutriments: OffNutriments? = null,
) {
    /** The first brand, whichever shape [brands] arrived in. */
    val firstBrand: String?
        get() = when (val b = brands) {
            is JsonArray -> b.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> b.content.split(',').firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
            else -> null
        }
}
