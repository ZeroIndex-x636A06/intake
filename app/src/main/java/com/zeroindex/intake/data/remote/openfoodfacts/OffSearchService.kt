package com.zeroindex.intake.data.remote.openfoodfacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
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
        /** Comma-joined language codes (the user's, then English): searched and returned in those languages. */
        @Query("langs") langs: String? = null,
    ): OffSearchResponse

    companion object {
        const val BASE_URL = "https://search.openfoodfacts.org/"
        /** More than a screenful, because the language rule and the food / drink split both leave rows out. */
        const val PAGE_SIZE = 40

        /**
         * `traces_tags` and the ingredient text are asked for so the allergen engine sees everything a hit carries.
         * (The search index returned no ingredient text when this was written; the barcode lookup does.)
         */
        const val FIELDS =
            "code,product_name,brands,quantity,serving_size,serving_quantity,allergens_tags,traces_tags," +
                "ingredients_text,nutriments,categories_tags"

        /** Added to the query text for the Drinks search: only products Open Food Facts files under beverages. */
        const val BEVERAGES_FILTER = "categories_tags:\"en:beverages\""
    }
}

@Serializable
data class OffSearchResponse(
    val hits: List<@Serializable(with = OffHitSerializer::class) OffSearchHit> = emptyList(),
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
    /** The product's main language as tagged by contributors (`lc` is the same thing under its older name). */
    val lang: String? = null,
    val lc: String? = null,
    @SerialName("traces_tags") val tracesTags: List<String>? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    /** `product_name_<code>` for each language asked for, keyed by code: `{"en": "Milk", "fr": "Lait"}`. */
    val names: Map<String, String> = emptyMap(),
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("image_front_thumb_url") val imageFrontThumbUrl: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
) {
    /** The language the plain `product_name` is in. */
    val mainLanguage: String? get() = lang?.takeIf { it.isNotBlank() } ?: lc

    /** The first brand, whichever shape [brands] arrived in. */
    val firstBrand: String?
        get() = when (val b = brands) {
            is JsonArray -> b.firstNotNullOfOrNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> b.content.split(',').firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
            else -> null
        }
}

/**
 * `product_name_<code>` keys are not a fixed set, so they are gathered into [OffSearchHit.names] before the
 * ordinary decode. Only string values are kept.
 */
object OffHitSerializer : JsonTransformingSerializer<OffSearchHit>(OffSearchHit.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val names = obj.entries
            .filter { (key, value) -> key.startsWith("product_name_") && value is JsonPrimitive && value.isString }
            .associate { (key, value) -> key.removePrefix("product_name_") to value }
        return JsonObject(obj + ("names" to JsonObject(names)))
    }
}
