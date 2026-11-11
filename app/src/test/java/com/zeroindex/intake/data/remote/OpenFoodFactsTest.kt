package com.zeroindex.intake.data.remote

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionForm
import com.zeroindex.intake.data.remote.openfoodfacts.OffContributionService
import com.zeroindex.intake.data.remote.openfoodfacts.OffMapping
import com.zeroindex.intake.data.remote.openfoodfacts.OffProductResponse
import com.zeroindex.intake.data.remote.openfoodfacts.OffSearchResponse
import com.zeroindex.intake.data.remote.openfoodfacts.OffUserAgent
import com.zeroindex.intake.data.remote.openfoodfacts.OffWriteResponse
import com.zeroindex.intake.data.repository.ContributionOutcome
import com.zeroindex.intake.data.repository.OffContributionRepository
import com.zeroindex.intake.data.repository.OffSearchRepository
import com.zeroindex.intake.domain.drinks.SearchScope
import com.zeroindex.intake.domain.model.FoodSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Captured Open Food Facts responses (src/test/resources/off) and the contribution form. */
class OpenFoodFactsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String) =
        checkNotNull(javaClass.classLoader!!.getResource("off/$name")) { name }.readText()

    @Test
    fun searchResponseParsesHitsWithListBrands() {
        val response = json.decodeFromString<OffSearchResponse>(fixture("search-greek-yogurt.json"))
        assertThat(response.hits).hasSize(6)
        val hit = response.hits.first()
        assertThat(hit.code).isEqualTo("0894700010137")
        assertThat(hit.firstBrand).isEqualTo("Chobani")
        assertThat(hit.nutriments?.energyKcal100g).isNotNull()
    }

    @Test
    fun searchHitsMapToFoodsPer100gWithSodiumInMilligrams() {
        val nutella = json.decodeFromString<OffSearchResponse>(fixture("search-nutella.json")).hits
            .mapNotNull { OffMapping.fromHit(it) }
        assertThat(nutella).hasSize(3)
        val food = nutella.first()
        assertThat(food.name).isEqualTo("Nutella & go! hazelnut spread + breadsticks")
        assertThat(food.brand).isEqualTo("Nutella")
        assertThat(food.barcode).isEqualTo("0009800800049")
        assertThat(food.kcalPer100g).isWithin(0.01).of(519.23)
        assertThat(food.sodiumPer100g).isWithin(0.5).of(234.48) // 0.23448 g
        assertThat(food.source).isEqualTo(FoodSource.OPEN_FOOD_FACTS)
        assertThat(food.allergenTags).isEqualTo("en:gluten")
        assertThat(food.servingSizeGrams).isEqualTo(100.0)
    }

    @Test
    fun aSearchHitKeepsItsTraceTagsMainLanguageAndDrinkCategory() {
        // The shape search.openfoodfacts.org answers with (fields: lang, lc, allergens_tags, traces_tags, categories_tags).
        val body = """{"hits":[
            {"code":"8718452361205","lang":"nl","lc":"nl","product_name":"Kip cordon blue","product_name_nl":"Kip cordon blue",
             "allergens_tags":["en:gluten","en:milk"],"traces_tags":["en:peanuts"],
             "categories_tags":["en:meats"],"nutriments":{"energy-kcal_100g":210}},
            {"code":"5449000000996","lc":"en","product_name":"Original Taste",
             "categories_tags":["en:beverages","en:carbonated-drinks","en:sodas"],"nutriments":{"energy-kcal_100g":42}}
        ]}"""
        val hits = json.decodeFromString<OffSearchResponse>(body).hits
        assertThat(hits.map { it.mainLanguage }).containsExactly("nl", "en").inOrder()
        val kip = OffMapping.fromHit(hits[0], listOf("en"))!!
        // The trace tag is stored beside the allergen tags: never dropped, never downgraded.
        assertThat(kip.food.allergenTags).isEqualTo("en:gluten,en:milk,en:peanuts")
        assertThat(kip.food.drinkCategory).isNull()
        assertThat(kip.isShown(showOtherLanguages = false)).isFalse()
        val cola = OffMapping.fromHit(hits[1], listOf("en"))!!
        assertThat(cola.food.drinkCategory).isEqualTo("SODA")
        assertThat(cola.isShown(showOtherLanguages = false)).isTrue()
    }

    @Test
    fun theDrinksSearchAddsOnlyTheBeveragesFilterToTheWordsTyped() {
        assertThat(OffSearchRepository.queryFor("mcdonald's chicken", SearchScope.FOODS)).isEqualTo("mcdonald's chicken")
        assertThat(OffSearchRepository.queryFor("coke zero", SearchScope.DRINKS)).isEqualTo("coke zero categories_tags:\"en:beverages\"")
        // Characters that mean something in the query syntax are taken out of the typed words; hyphens and apostrophes stay.
        assertThat(OffSearchRepository.queryFor("coca-cola \"zero\" (can): 330", SearchScope.DRINKS))
            .isEqualTo("coca-cola zero can 330 categories_tags:\"en:beverages\"")
    }

    @Test
    fun productResponseParsesAndMaps() {
        val response = json.decodeFromString<OffProductResponse>(fixture("product-nutella.json"))
        assertThat(response.status).isEqualTo(1)
        val food = OffMapping.fromProduct(response.product!!, "3017620422003")!!
        assertThat(food.name).isNotEmpty()
        assertThat(food.kcalPer100g).isWithin(0.01).of(539.0)
        assertThat(food.allergenTags).contains("en:milk")
        assertThat(FoodIngredients.text(food.allergenTags)).contains("sugar")
    }

    @Test
    fun unknownProductIsStatusZeroWithNoProduct() {
        val response = json.decodeFromString<OffProductResponse>(fixture("product-not-found.json"))
        assertThat(response.status).isEqualTo(0)
        assertThat(response.product).isNull()
    }

    @Test
    fun numbersThatArriveAsStringsOrBlanksDoNotBreakAProduct() {
        val body = """{"status":1,"product":{"product_name":"X","serving_quantity":"30","nutriments":
            {"energy-kcal_100g":"250","proteins_100g":"","fat_100g":null,"carbohydrates_100g":12.5}}}"""
        val product = json.decodeFromString<OffProductResponse>(body).product!!
        assertThat(product.servingQuantity).isEqualTo(30.0)
        assertThat(product.nutriments?.energyKcal100g).isEqualTo(250.0)
        assertThat(product.nutriments?.proteins100g).isNull()
        assertThat(OffMapping.fromProduct(product, "123")?.carbsPer100g).isEqualTo(12.5)
    }

    @Test
    fun productsWithoutEnergyOrNameAreSkipped() {
        val noEnergy = json.decodeFromString<OffProductResponse>("""{"status":1,"product":{"product_name":"X","nutriments":{}}}""")
        assertThat(OffMapping.fromProduct(noEnergy.product!!, "1")).isNull()
    }

    @Test
    fun userAgentHasTheRequestedShapeAndNoEmail() {
        val ua = OffUserAgent.forVersion("0.1.0")
        assertThat(ua).isEqualTo("Intake/0.1.0 (personal Android app)")
        assertThat(ua).doesNotContain("@")
    }

    // ---- contribution -----------------------------------------------------------------

    private val food = FoodEntity(
        id = "f1", name = "Peanut Crunch Bar", brand = "Homemade Co", barcode = "0894700010137",
        servingSizeGrams = 40.0, servingLabel = "1 bar", kcalPer100g = 480.0, proteinPer100g = 15.5,
        carbsPer100g = 50.0, fatPer100g = 24.0, sodiumPer100g = 300.0, source = FoodSource.MANUAL,
        allergenTags = "peanuts, oats, honey", createdAt = 1, updatedAt = 1,
    )

    @Test
    fun formCarriesTheDocumentedFieldsPer100g() {
        val body = OffContributionForm.build(food, "160 g", FoodIngredients.text(food.allergenTags))
            .toFormBody("alice", "s3cret", "0.1.0")
        assertThat(body).containsAtLeast(
            "code", "0894700010137",
            "product_name", "Peanut Crunch Bar",
            "brands", "Homemade Co",
            "quantity", "160 g",
            "nutrition_data_per", "100g",
            "nutriment_energy-kcal", "480",
            "nutriment_proteins", "15.5",
            "nutriment_sodium", "0.3",
            "nutriment_sodium_unit", "g",
            "ingredients_text", "peanuts, oats, honey",
            "user_id", "alice",
        )
        // Zero or unentered optional nutrients are not sent as if they were measured.
        assertThat(body).doesNotContainKey("nutriment_fiber")
        assertThat(body).doesNotContainKey("nutriment_sugars")
    }

    @Test
    fun theConfirmationPreviewNeverShowsThePassword() {
        val preview = OffContributionForm.build(food, null, null)
        assertThat(preview.previewLines.joinToString("\n")).doesNotContain("password")
        assertThat(preview.previewLines.first()).isEqualTo("Barcode: 0894700010137")
    }

    @Test
    fun onlyUserMadePackagedFoodsWithBarcodesAreEligible() {
        assertThat(OffContributionForm.isEligible(food)).isTrue()
        assertThat(OffContributionForm.isEligible(food.copy(barcode = null))).isFalse()
        assertThat(OffContributionForm.isEligible(food.copy(barcode = "123"))).isFalse()
        assertThat(OffContributionForm.isEligible(food.copy(source = FoodSource.OPEN_FOOD_FACTS))).isFalse()
        assertThat(OffContributionForm.isEligible(food.copy(source = FoodSource.DATABASE))).isFalse()
        assertThat(OffContributionForm.isEligible(food.copy(source = FoodSource.BARCODE))).isTrue()
        assertThat(OffContributionForm.isEligible(food.copy(deletedAt = 5))).isFalse()
    }

    /** Sends the real Retrofit service through a stub transport and records the request. */
    private class Captured {
        lateinit var url: String
        lateinit var method: String
        lateinit var contentType: String
        lateinit var body: String
        var headers: List<String> = emptyList()
    }

    @Test
    fun thePasswordTravelsOnlyInThePostBodyNeverInTheUrl() = runBlocking {
        val captured = Captured()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request: Request = chain.request()
            captured.url = request.url.toString()
            captured.method = request.method
            captured.contentType = request.body?.contentType().toString()
            captured.body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            captured.headers = request.headers.names().toList().map { it + "=" + request.header(it) }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"status_verbose":"fields saved","status":1}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val service = Retrofit.Builder()
            .baseUrl(OffContributionService.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OffContributionService::class.java)

        val form = OffContributionForm.build(food, null, null).toFormBody("alice", "p@ss word&1", "0.1.0")
        val response = service.submit(form)

        assertThat(response.accepted).isTrue()
        assertThat(captured.method).isEqualTo("POST")
        assertThat(captured.url).isEqualTo("https://world.openfoodfacts.org/cgi/product_jqm2.pl")
        assertThat(captured.url).doesNotContain("password")
        assertThat(captured.url).doesNotContain("alice")
        assertThat(captured.url).doesNotContain("?")
        assertThat(captured.headers.joinToString()).doesNotContain("p@ss")
        assertThat(captured.contentType).startsWith("application/x-www-form-urlencoded")
        assertThat(captured.body).contains("password=p%40ss+word%261")
        assertThat(captured.body).contains("user_id=alice")
    }

    @Test
    fun responsesAreInterpretedAndRejectionsKeepTheServerMessage() {
        assertThat(OffContributionRepository.interpret(OffWriteResponse(status = 1.0, statusVerbose = "fields saved")))
            .isEqualTo(ContributionOutcome.Sent)
        val rejected = OffContributionRepository.interpret(OffWriteResponse(status = 0.0, statusVerbose = "no permission"))
        assertThat(rejected).isEqualTo(ContributionOutcome.Rejected("no permission"))
        assertThat(OffContributionRepository.interpret(OffWriteResponse())).isInstanceOf(ContributionOutcome.Rejected::class.java)
        val parsed = json.decodeFromString<OffWriteResponse>("""{"status_verbose":"fields saved","status":1}""")
        assertThat(parsed.accepted).isTrue()
    }

    @Test
    fun ingredientTextAndTagsSplitApart() {
        val stored = FoodIngredients.join(listOf("en:milk", "en:nuts"), "sugar, palm oil")
        assertThat(stored).isEqualTo("en:milk,en:nuts | sugar, palm oil")
        assertThat(FoodIngredients.tags(stored)).isEqualTo("en:milk,en:nuts")
        assertThat(FoodIngredients.text(stored)).isEqualTo("sugar, palm oil")
        assertThat(FoodIngredients.text("en:milk,en:nuts")).isEmpty()
        assertThat(FoodIngredients.text("water, salt")).isEqualTo("water, salt")
        assertThat(FoodIngredients.tags("water, salt")).isEmpty()
    }
}
