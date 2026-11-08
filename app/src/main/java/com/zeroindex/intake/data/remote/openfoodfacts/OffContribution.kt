package com.zeroindex.intake.data.remote.openfoodfacts

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.model.FoodSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Open Food Facts' documented write API: a form POST to `/cgi/product_jqm2.pl`
 * with the account name and password as form fields. Over HTTPS the body is
 * encrypted; the URL carries nothing, so no credential can reach a URL, a proxy
 * log or a server access log. Nothing here logs a request.
 */
interface OffContributionService {

    @FormUrlEncoded
    @POST("cgi/product_jqm2.pl")
    suspend fun submit(@FieldMap fields: Map<String, String>): OffWriteResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
    }
}

/** `{"status_verbose":"fields saved","status":1}` on success. */
@Serializable
data class OffWriteResponse(
    @Serializable(with = LenientDoubleSerializer::class)
    val status: Double? = null,
    @SerialName("status_verbose") val statusVerbose: String? = null,
) {
    val accepted: Boolean get() = status == 1.0
}

/**
 * One value that will be sent. [value] is the form value; [display] is what the
 * confirmation dialog shows (the same value, with its unit).
 */
data class ContributionField(
    val key: String,
    val label: String,
    val value: String,
    val display: String = value,
)

/** What the user is asked to confirm. Holds no credentials. */
data class OffContribution(val fields: List<ContributionField>) {

    /** Exactly what the user sees, one line per value. */
    val previewLines: List<String>
        get() = fields.filterNot { it.key.endsWith("_unit") }.map { "${it.label}: ${it.display}" }

    /**
     * The form body: the product fields plus the account name and password. This is
     * the only place the password is added, and it goes in a POST body, never a URL.
     */
    fun toFormBody(userId: String, password: String, appVersion: String): Map<String, String> =
        linkedMapOf<String, String>().apply {
            put("user_id", userId)
            put("password", password)
            fields.forEach { put(it.key, it.value) }
            put("app_name", "Intake")
            put("app_version", appVersion)
            put("comment", "Added from Intake")
        }
}

/**
 * Builds the contribution for a food the user made or edited (docs/FOOD-DATA.md).
 * Per-100 g values only, in the units Open Food Facts documents. Optional
 * nutrients that are zero are left out, because on a hand-made food zero usually
 * means "not entered" and sending it would claim a measurement that was never made.
 */
object OffContributionForm {

    private const val BARCODE_MIN = 8
    private const val BARCODE_MAX = 14

    fun isValidBarcode(code: String?): Boolean =
        code != null && code.length in BARCODE_MIN..BARCODE_MAX && code.all { it.isDigit() }

    /** Only packaged foods with a barcode that the user made or edited. */
    fun isEligible(food: FoodEntity): Boolean =
        food.deletedAt == null &&
            isValidBarcode(food.barcode) &&
            (food.source == FoodSource.MANUAL || food.source == FoodSource.BARCODE) &&
            food.name.isNotBlank() &&
            food.kcalPer100g > 0.0

    fun build(food: FoodEntity, quantity: String?, ingredientsText: String?): OffContribution {
        val fields = mutableListOf<ContributionField>()
        fun add(key: String, label: String, value: String, display: String = value) {
            fields.add(ContributionField(key, label, value, display))
        }
        fun nutrient(key: String, label: String, grams: Double) =
            add(key, label, trimmed(grams), "${trimmed(grams)} g")

        add("code", "Barcode", food.barcode.orEmpty())
        add("product_name", "Product name", food.name.trim())
        food.brand?.trim()?.takeIf { it.isNotEmpty() }?.let { add("brands", "Brand", it) }
        quantity?.trim()?.takeIf { it.isNotEmpty() }?.let { add("quantity", "Quantity", it) }
        add("nutrition_data_per", "Nutrition values are", "100g", "per 100 g")
        add("nutriment_energy-kcal", "Energy per 100 g", trimmed(food.kcalPer100g), "${trimmed(food.kcalPer100g)} kcal")
        add("nutriment_energy-kcal_unit", "Energy unit", "kcal")
        nutrient("nutriment_proteins", "Protein per 100 g", food.proteinPer100g)
        nutrient("nutriment_carbohydrates", "Carbohydrate per 100 g", food.carbsPer100g)
        nutrient("nutriment_fat", "Fat per 100 g", food.fatPer100g)
        if (food.saturatedFatPer100g > 0) nutrient("nutriment_saturated-fat", "Saturated fat per 100 g", food.saturatedFatPer100g)
        if (food.sugarPer100g > 0) nutrient("nutriment_sugars", "Sugars per 100 g", food.sugarPer100g)
        if (food.fiberPer100g > 0) nutrient("nutriment_fiber", "Fibre per 100 g", food.fiberPer100g)
        if (food.sodiumPer100g > 0) {
            // The app stores sodium in milligrams; Open Food Facts wants grams.
            val grams = food.sodiumPer100g / 1000.0
            add("nutriment_sodium", "Sodium per 100 g", trimmed(grams), "${trimmed(grams)} g")
            add("nutriment_sodium_unit", "Sodium unit", "g")
        }
        ingredientsText?.trim()?.takeIf { it.isNotEmpty() }?.let { add("ingredients_text", "Ingredients", it) }
        return OffContribution(fields)
    }

    private fun trimmed(value: Double): String {
        val rounded = Math.round(value * 1000.0) / 1000.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
}
