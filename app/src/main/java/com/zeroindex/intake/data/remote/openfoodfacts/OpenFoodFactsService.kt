package com.zeroindex.intake.data.remote.openfoodfacts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts is crowdsourced, so a number field can arrive as a number, as a
 * quoted number ("12.5") or as an empty string. This reads all three and turns
 * anything else into null instead of failing the whole product.
 */
object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        if (element is JsonNull) return null
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.content.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

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
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("serving_quantity") val servingQuantity: Double? = null,
    @SerialName("ingredients_text") val ingredientsText: String? = null,
    /** Structured tags, e.g. ["en:milk"]. SPEC §7.3 trusts these first. */
    @SerialName("allergens_tags") val allergensTags: List<String> = emptyList(),
    @SerialName("traces_tags") val tracesTags: List<String> = emptyList(),
    val nutriments: OffNutriments? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
)

/** Open Food Facts reports per-100g values under the `_100g` suffix. */
@Serializable
data class OffNutriments(
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("energy-kcal_100g") val energyKcal100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("proteins_100g") val proteins100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("carbohydrates_100g") val carbohydrates100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fat_100g") val fat100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("fiber_100g") val fiber100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("sugars_100g") val sugars100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("sodium_100g") val sodium100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("salt_100g") val salt100g: Double? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    @SerialName("saturated-fat_100g") val saturatedFat100g: Double? = null,
)

interface OpenFoodFactsService {

    /**
     * SPEC §5.2: barcode lookup. No key and no account, so this works out of the box.
     * An unknown barcode is HTTP 404 with `status: 0` on some products and HTTP 200
     * with `status: 0` on others; [com.zeroindex.intake.data.repository.BarcodeRepository]
     * treats both as "not found".
     */
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): OffProductResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val FIELDS =
            "product_name,brands,serving_size,serving_quantity,ingredients_text," +
                "allergens_tags,traces_tags,nutriments,image_front_small_url,image_front_url"
    }
}

/** The User-Agent Open Food Facts asks every app to send. Never contains an email. */
object OffUserAgent {
    fun forVersion(versionName: String) = "Intake/$versionName (personal Android app)"
}
