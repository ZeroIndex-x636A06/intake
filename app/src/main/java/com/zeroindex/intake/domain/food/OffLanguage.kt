package com.zeroindex.intake.domain.food

import java.lang.Character.UnicodeScript

/** The writing system a product name is mostly in. [NONE] means no letters at all (digits, symbols). */
enum class NameScript { LATIN, CYRILLIC, GREEK, ARABIC, HEBREW, CJK, THAI, DEVANAGARI, OTHER, NONE }

/**
 * Which Open Food Facts products to show for the user's language. Pure, so it is tested against captured responses.
 *
 * Open Food Facts is crowdsourced: a Russian milk can arrive with `lang: "en"` and a Cyrillic `product_name_en`,
 * so the language tags cannot be trusted on their own. The script of the name itself is the reliable signal.
 *
 * - [pickName] takes `product_name_<device language>`, then `product_name_en`, then `product_name`, preferring the
 *   first one written in a script the user reads, and falling back to the first non-blank one.
 * - [hasNameIn] is the language rule: a product is listed only when it has a name in one of the person's food
 *   languages (the "Food search language" setting, then English). A Dutch or French product with no English name is
 *   left out for an English reader, even though its letters are Latin.
 * - [isReadable] / [isShown] also hide a result whose chosen name is in another script (a Cyrillic name tagged
 *   English). "Show products in other languages" lists both kinds.
 *
 * Nothing here translates anything and nothing here touches allergens: a hidden result is simply not listed, and
 * allergen checks on a food that is logged never depend on which name was shown.
 */
object OffLanguage {

    /**
     * @param readable the name is in a script the reader uses.
     * @param inLanguage the name is one the product has in one of the person's food languages (see [hasNameIn]).
     */
    class Picked(val name: String, val readable: Boolean, val inLanguage: Boolean = true)

    /** What the "Food search language" setting offers besides "Phone language" (stored as ""). */
    val CHOICES: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German",
        "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch",
    )

    private fun code(language: String?): String = language.orEmpty().trim().lowercase().substringBefore('-').substringBefore('_')

    /**
     * The person's food languages, first choice first: the "Food search language" setting ([choice], blank for the
     * phone's language), then English as the fallback. Anything that is not a language code falls back to English.
     */
    fun foodLanguages(choice: String?, phoneLanguage: String?): List<String> {
        val first = code(choice).ifEmpty { code(phoneLanguage) }.takeIf { it.length in 2..3 && it.all(Char::isLetter) } ?: "en"
        return listOf(first, "en").distinct()
    }

    /**
     * True when the product has a name in one of [languages]: a non-blank `product_name_<code>` for one of them
     * ([names]), or its main language ([mainLanguage], Open Food Facts `lang` / `lc`) is one of them, in which case
     * the plain `product_name` is that name. A product with only a plain name and another main language has none:
     * "Gemarineerde kipfilet" (`lang: nl`) is not shown to an English reader.
     */
    fun hasNameIn(names: Map<String, String>, productName: String?, mainLanguage: String?, languages: List<String>): Boolean {
        val wanted = languages.map(::code).filter { it.isNotEmpty() }
        if (wanted.any { !names[it].isNullOrBlank() }) return true
        return code(mainLanguage) in wanted && !productName.isNullOrBlank()
    }

    /**
     * The name to show for a search hit and whether it may be listed. Names in the person's languages come first, in
     * the order of [languages]; when the product has none, the first name it does have is returned with
     * `inLanguage = false`, which only "Show products in other languages" lists.
     */
    fun pickName(names: Map<String, String>, productName: String?, mainLanguage: String?, languages: List<String>): Picked? {
        val wanted = languages.map(::code).filter { it.isNotEmpty() }.ifEmpty { listOf("en") }
        fun readable(name: String) = scriptOf(name).let { s -> s == NameScript.NONE || wanted.any { expectedScript(it) == s } }
        fun clean(text: String?) = text?.trim()?.takeIf(String::isNotEmpty)
        val own = wanted.mapNotNull { clean(names[it]) } +
            listOfNotNull(clean(productName).takeIf { code(mainLanguage) in wanted })
        own.firstOrNull(::readable)?.let { return Picked(it, readable = true, inLanguage = true) }
        own.firstOrNull()?.let { return Picked(it, readable = false, inLanguage = true) }
        val other = (listOfNotNull(clean(productName)) + names.values.mapNotNull(::clean)).firstOrNull() ?: return null
        return Picked(other, readable = readable(other), inLanguage = false)
    }

    fun scriptOf(text: String): NameScript {
        val counts = HashMap<NameScript, Int>()
        text.forEach { ch ->
            if (!Character.isLetter(ch)) return@forEach
            val script = when (UnicodeScript.of(ch.code)) {
                UnicodeScript.LATIN -> NameScript.LATIN
                UnicodeScript.CYRILLIC -> NameScript.CYRILLIC
                UnicodeScript.GREEK -> NameScript.GREEK
                UnicodeScript.ARABIC -> NameScript.ARABIC
                UnicodeScript.HEBREW -> NameScript.HEBREW
                UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HANGUL -> NameScript.CJK
                UnicodeScript.THAI -> NameScript.THAI
                UnicodeScript.DEVANAGARI -> NameScript.DEVANAGARI
                else -> NameScript.OTHER
            }
            counts[script] = (counts[script] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: NameScript.NONE
    }

    /** The script a reader of [language] (an ISO 639-1 code such as "ru") expects; Latin when in doubt. */
    fun expectedScript(language: String): NameScript = when (language.lowercase().substringBefore('-').substringBefore('_')) {
        "ru", "uk", "bg", "sr", "be", "mk", "kk", "mn" -> NameScript.CYRILLIC
        "el" -> NameScript.GREEK
        "ar", "fa", "ur", "ps" -> NameScript.ARABIC
        "he", "iw", "yi" -> NameScript.HEBREW
        "ja", "zh", "ko" -> NameScript.CJK
        "th" -> NameScript.THAI
        "hi", "mr", "ne" -> NameScript.DEVANAGARI
        else -> NameScript.LATIN
    }

    /** True when [name] is in a script the reader of [language] uses (or has no letters to judge by). */
    fun isReadable(name: String, language: String): Boolean {
        val script = scriptOf(name)
        return script == NameScript.NONE || script == expectedScript(language)
    }

    /**
     * [names] maps a language code to `product_name_<code>`; [productName] is the plain `product_name`.
     * Null when no name exists at all (such a product cannot be logged anyway).
     */
    fun pickName(names: Map<String, String>, productName: String?, language: String): Picked? {
        val lang = language.lowercase().substringBefore('-').substringBefore('_')
        val candidates = listOf(names[lang], names["en"], productName)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        if (candidates.isEmpty()) return null
        candidates.firstOrNull { isReadable(it, lang) }?.let { return Picked(it, readable = true) }
        return Picked(candidates.first(), readable = false)
    }

    /** Listed when it has a readable name in one of the person's languages, or when the setting shows everything. */
    fun isShown(picked: Picked, showOtherLanguages: Boolean): Boolean =
        showOtherLanguages || (picked.readable && picked.inLanguage)

    /** The languages to send as `langs`: the user's own, then English, with no repeats. */
    fun searchLanguages(language: String): List<String> {
        val lang = language.lowercase().substringBefore('-').substringBefore('_').ifBlank { "en" }
        return listOf(lang, "en").distinct()
    }

    /** The `fields` to ask for, so the app gets the local name, the English one, the thumbnail and the rest. */
    fun searchFields(language: String, base: String): String = searchFields(searchLanguages(language), base)

    /** The same for an explicit list of food languages: each one's name, and the product's main language. */
    fun searchFields(languages: List<String>, base: String): String =
        (base.split(',') + listOf("lang", "lc", "image_front_small_url", "image_front_thumb_url") +
            languages.map { "product_name_$it" }).distinct().joinToString(",")
}
