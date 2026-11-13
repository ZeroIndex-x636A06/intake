package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.remote.openfoodfacts.OffImages
import com.zeroindex.intake.data.remote.openfoodfacts.OffMapping
import com.zeroindex.intake.data.remote.openfoodfacts.OffSearchResponse
import kotlinx.serialization.json.Json
import org.junit.Test

/** The language rule, against responses captured from search.openfoodfacts.org (src/test/resources/off). */
class OffLanguageTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    private fun fixture(name: String) =
        checkNotNull(javaClass.classLoader!!.getResource("off/$name")) { name }.readText()

    private fun results(file: String, language: String) =
        json.decodeFromString<OffSearchResponse>(fixture(file)).hits.mapNotNull { OffMapping.fromHit(it, language) }

    @Test
    fun scriptsAreTold_apartByTheirLetters() {
        assertThat(OffLanguage.scriptOf("Moo Milk 2%")).isEqualTo(NameScript.LATIN)
        assertThat(OffLanguage.scriptOf("Молоко 2,5")).isEqualTo(NameScript.CYRILLIC)
        assertThat(OffLanguage.scriptOf("ผลิตภัณฑ์นมพาสเจอไรซ์ - 946 ml")).isEqualTo(NameScript.THAI)
        assertThat(OffLanguage.scriptOf("牛乳")).isEqualTo(NameScript.CJK)
        assertThat(OffLanguage.scriptOf("1,5 %")).isEqualTo(NameScript.NONE)
    }

    @Test
    fun anEnglishReaderDoesNotSeeRussianProducts_evenWhenTheyAreTaggedEnglish() {
        // Every hit in this capture has lang "en" and a Cyrillic product_name_en: the tags lie, the script does not.
        val all = results("search-russian.json", "en")
        assertThat(all).hasSize(6)
        assertThat(all.map { it.readable }).containsExactly(false, false, false, false, false, false)
    }

    @Test
    fun aRussianReaderSeesThem() {
        assertThat(results("search-russian.json", "ru").all { it.readable }).isTrue()
    }

    @Test
    fun englishIsPreferredOverThePlainNameWhenThePlainNameIsInAnotherScript() {
        val items = results("search-milk-langs-en.json", "en")
        val thai = items.first { it.food.barcode == "0025073009910" }
        assertThat(thai.food.name).isEqualTo("Homogenized Milk")
        assertThat(thai.readable).isTrue()
        val vietnamese = items.first { it.food.barcode == "8938503131957" }
        assertThat(vietnamese.food.name).isEqualTo("Fresh Milk Natural Plain")
    }

    @Test
    fun aLatinNameInAnotherLanguageIsKept() {
        // 7591691180292 in the capture: only product_name, in Spanish (it has no energy value, so it cannot be
        // logged and is not listed; the rule itself is what is checked here).
        val picked = OffLanguage.pickName(emptyMap(), "Leche Deslactosada", "en")!!
        assertThat(picked.name).isEqualTo("Leche Deslactosada")
        assertThat(picked.readable).isTrue()
    }

    @Test
    fun theDeviceLanguageNameComesFirst() {
        val items = results("search-lait-langs-fr.json", "fr")
        assertThat(items).hasSize(5) // one capture has no energy value
        assertThat(items.first().food.name).isEqualTo("J'aime le lait d'ici")
        assertThat(items.all { it.readable }).isTrue()
    }

    @Test
    fun theChoiceOrderIsLocalThenEnglishThenPlain() {
        val picked = OffLanguage.pickName(mapOf("fr" to "Lait", "en" to "Milk"), "Mleko", "fr")!!
        assertThat(picked.name).isEqualTo("Lait")
        assertThat(OffLanguage.pickName(mapOf("en" to "Milk"), "Mleko", "fr")!!.name).isEqualTo("Milk")
        assertThat(OffLanguage.pickName(emptyMap(), "Mleko", "fr")!!.name).isEqualTo("Mleko")
        assertThat(OffLanguage.pickName(emptyMap(), "  ", "fr")).isNull()
        // A Cyrillic English field loses to a readable plain name.
        assertThat(OffLanguage.pickName(mapOf("en" to "Молоко"), "Milk", "en")!!.name).isEqualTo("Milk")
    }

    @Test
    fun theSettingShowsWhatWouldBeHidden() {
        val hidden = OffLanguage.pickName(mapOf("en" to "Молоко"), null, "en")!!
        assertThat(OffLanguage.isShown(hidden, showOtherLanguages = false)).isFalse()
        assertThat(OffLanguage.isShown(hidden, showOtherLanguages = true)).isTrue()
    }

    // ---- The language rule: a product needs a name in one of the person's food languages ----

    private class Product(val name: String, val lang: String, val names: Map<String, String> = emptyMap())

    /** What an English reader got for "McDonald's chicken": Dutch and French products with no English name. */
    private val reported = listOf(
        Product("Gemarineerde kipfilet", "nl", mapOf("nl" to "Gemarineerde kipfilet")),
        Product("Filetblokjes kippenvlees", "nl", mapOf("nl" to "Filetblokjes kippenvlees")),
        Product("Frites moyennes - McDonald's", "fr", mapOf("fr" to "Frites moyennes - McDonald's")),
        Product("La boîte de 20 Chicken McNuggets", "fr"),
    )

    private fun shown(p: Product, languages: List<String>, showOther: Boolean = false): Boolean =
        OffLanguage.isShown(OffLanguage.pickName(p.names, p.name, p.lang, languages)!!, showOther)

    @Test
    fun productsWithNoNameInTheReadersLanguageAreHidden() {
        val english = OffLanguage.foodLanguages(choice = "", phoneLanguage = "en")
        assertThat(english).containsExactly("en")
        reported.forEach { p ->
            assertThat(OffLanguage.hasNameIn(p.names, p.name, p.lang, english)).isFalse()
            assertThat(shown(p, english)).isFalse()
        }
    }

    @Test
    fun theSameProductsAreShownToAReaderOfTheirLanguage_andWhenTheSwitchIsOn() {
        val dutch = OffLanguage.foodLanguages(choice = "nl", phoneLanguage = "en")
        assertThat(dutch).containsExactly("nl", "en").inOrder()
        assertThat(reported.map { shown(it, dutch) }).containsExactly(true, true, false, false).inOrder()
        val french = OffLanguage.foodLanguages(choice = "", phoneLanguage = "fr-FR")
        assertThat(reported.map { shown(it, french) }).containsExactly(false, false, true, true).inOrder()
        // "Show products in other languages" lists everything, under the name the product does have.
        reported.forEach { assertThat(shown(it, listOf("en"), showOther = true)).isTrue() }
        assertThat(OffLanguage.pickName(emptyMap(), "La boîte de 20 Chicken McNuggets", "fr", listOf("en"))!!.name)
            .isEqualTo("La boîte de 20 Chicken McNuggets")
    }

    @Test
    fun aNameInAChosenLanguageIsEnough_whateverTheMainLanguage() {
        val english = listOf("en")
        // A French product that also has an English name is shown, under the English name.
        val picked = OffLanguage.pickName(mapOf("fr" to "Frites moyennes", "en" to "Medium Fries"), "Frites moyennes", "fr", english)!!
        assertThat(picked.name).isEqualTo("Medium Fries")
        assertThat(OffLanguage.isShown(picked, showOtherLanguages = false)).isTrue()
        // An English product with only the plain name is shown: its main language is the name's language.
        val plain = OffLanguage.pickName(emptyMap(), "Mc Chicken - Mcdonalds", "en", english)!!
        assertThat(OffLanguage.isShown(plain, showOtherLanguages = false)).isTrue()
        // A blank per-language name does not count, and neither does a missing main language.
        assertThat(OffLanguage.hasNameIn(mapOf("en" to "  "), "Kip cordon bleu", "nl", english)).isFalse()
        assertThat(OffLanguage.hasNameIn(emptyMap(), "Kip cordon bleu", null, english)).isFalse()
        // The script rule still applies on top: a Cyrillic name tagged English is not listed.
        val cyrillic = OffLanguage.pickName(mapOf("en" to "Молоко"), "Молоко", "en", english)!!
        assertThat(OffLanguage.isShown(cyrillic, showOtherLanguages = false)).isFalse()
    }

    @Test
    fun theFoodLanguageChoiceFallsBackToThePhoneAndThenEnglish() {
        assertThat(OffLanguage.foodLanguages("", "de")).containsExactly("de", "en").inOrder()
        assertThat(OffLanguage.foodLanguages("es", "de")).containsExactly("es", "en").inOrder()
        assertThat(OffLanguage.foodLanguages(null, null)).containsExactly("en")
        assertThat(OffLanguage.foodLanguages("not a code!", "")).containsExactly("en")
        assertThat(OffLanguage.CHOICES.map { it.first }).containsExactly("en", "es", "fr", "de", "it", "pt", "nl").inOrder()
        val fields = OffLanguage.searchFields(listOf("nl", "en"), "code,product_name").split(',')
        assertThat(fields).containsAtLeast("lang", "lc", "product_name_nl", "product_name_en")
    }

    @Test
    fun capturedHitsAreJudgedByTheirMainLanguage() {
        // Captured with langs=fr: every product is French. A French reader sees them; an English one does not,
        // unless the product also carries an English name.
        val hits = json.decodeFromString<OffSearchResponse>(fixture("search-lait-langs-fr.json")).hits
        val forEnglish = hits.mapNotNull { OffMapping.fromHit(it, listOf("en")) }
        val forFrench = hits.mapNotNull { OffMapping.fromHit(it, listOf("fr", "en")) }
        assertThat(forFrench.all { it.isShown(showOtherLanguages = false) }).isTrue()
        forEnglish.zip(hits.filter { h -> forEnglish.any { it.food.barcode == h.code } }).forEach { (item, hit) ->
            val hasEnglish = !hit.names["en"].isNullOrBlank() || hit.mainLanguage == "en"
            assertThat(item.isShown(showOtherLanguages = false)).isEqualTo(hasEnglish)
            assertThat(item.isShown(showOtherLanguages = true)).isTrue()
        }
    }

    @Test
    fun theRequestAsksForTheUsersLanguageThenEnglish() {
        assertThat(OffLanguage.searchLanguages("fr")).containsExactly("fr", "en").inOrder()
        assertThat(OffLanguage.searchLanguages("en")).containsExactly("en")
        assertThat(OffLanguage.searchLanguages("")).containsExactly("en")
        val fields = OffLanguage.searchFields("de", "code,product_name").split(',')
        assertThat(fields).containsAtLeast("code", "product_name", "lang", "product_name_de", "product_name_en", "image_front_small_url")
    }

    @Test
    fun thumbnailsComeFromTheCapturedFields() {
        val moo = results("search-milk-langs-en.json", "en").first()
        assertThat(moo.food.imageUrl).isEqualTo("https://images.openfoodfacts.org/images/products/500/031/600/0256/front_en.21.200.jpg")
    }

    @Test
    fun onlyHttpsPicturesFromOpenFoodFactsAreKept() {
        assertThat(OffImages.safeUrl("https://images.openfoodfacts.org/x.jpg")).isNotNull()
        assertThat(OffImages.safeUrl("http://images.openfoodfacts.org/x.jpg")).isNull()
        assertThat(OffImages.safeUrl("https://evil.example.com/x.jpg")).isNull()
        assertThat(OffImages.safeUrl("https://images.openfoodfacts.org.evil.com/x.jpg")).isNull()
        assertThat(OffImages.safeUrl(null)).isNull()
    }
}
