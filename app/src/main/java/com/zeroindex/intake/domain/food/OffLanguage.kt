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
 * - [isReadable] / [isShown] hide a result whose chosen name is in another script (a Cyrillic name for an English
 *   reader) unless "Show products in other languages" is on. A Latin-script name in another language (Spanish,
 *   French) is kept: it can still be read and is what the barcode says.
 *
 * Nothing here translates anything and nothing here touches allergens: a hidden result is simply not listed, and
 * allergen checks on a food that is logged never depend on which name was shown.
 */
object OffLanguage {

    class Picked(val name: String, val readable: Boolean)

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

    fun isShown(picked: Picked, showOtherLanguages: Boolean): Boolean = showOtherLanguages || picked.readable

    /** The languages to send as `langs`: the user's own, then English, with no repeats. */
    fun searchLanguages(language: String): List<String> {
        val lang = language.lowercase().substringBefore('-').substringBefore('_').ifBlank { "en" }
        return listOf(lang, "en").distinct()
    }

    /** The `fields` to ask for, so the app gets the local name, the English one, the thumbnail and the rest. */
    fun searchFields(language: String, base: String): String =
        (base.split(',') + listOf("lang", "image_front_small_url", "image_front_thumb_url") +
            searchLanguages(language).map { "product_name_$it" }).distinct().joinToString(",")
}
