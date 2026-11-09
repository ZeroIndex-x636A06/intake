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
