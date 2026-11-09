package com.zeroindex.intake.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One dish read straight from a page's schema.org data, with no model involved. */
data class StructuredMenuItem(
    val name: String,
    val description: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val priceCents: Double?,
)

/**
 * The deterministic first step of a menu import (SPEC-v3 "AI policy": rules before models). Many restaurant
 * sites publish their menu as schema.org `MenuItem` entries in JSON-LD; when a page does, the dishes are read
 * exactly as published and no model is called. A page with no such data returns an empty list, and only then
 * does the import fall back to the AI for the unstructured text.
 *
 * Pure: it takes the page's HTML and never touches a network.
 */
object MenuStructured {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val script = Regex("""<script\b([^>]*)>(.*?)</script\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val number = Regex("""(\d+(?:[.,]\d+)?)""")

    fun parse(html: String): List<StructuredMenuItem> {
        val found = LinkedHashMap<String, StructuredMenuItem>()
        script.findAll(html)
            .filter { it.groupValues[1].contains("ld+json", ignoreCase = true) }
            .forEach { block ->
                val root = parseJson(block.groupValues[2]) ?: return@forEach
                menuItems(root, 0).forEach { o ->
                    val item = fromJsonLd(o) ?: return@forEach
                    found.putIfAbsent(item.name.lowercase(), item)
                }
            }
        return found.values.toList()
    }

    private fun parseJson(raw: String): JsonElement? {
        val text = raw.trim().removePrefix("<!--").removeSuffix("-->").trim().dropWhile { it.code == 0xFEFF }
        if (text.isEmpty()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: runCatching { json.parseToJsonElement(text.replace(Regex("""[\r\n\t]+"""), " ")) }.getOrNull()
    }

    private fun isMenuItem(o: JsonObject): Boolean {
        fun matches(s: String?) = s != null && (s.equals("MenuItem", ignoreCase = true) || s.endsWith("/MenuItem"))
        return when (val t = o["@type"]) {
            is JsonArray -> t.any { matches((it as? JsonPrimitive)?.contentOrNull) }
            is JsonPrimitive -> matches(t.contentOrNull)
            else -> false
        }
    }

    /** Every MenuItem in the document, however deep the Menu, MenuSection and graph wrappers nest them. */
    private fun menuItems(e: JsonElement, depth: Int): List<JsonObject> {
        if (depth > 12) return emptyList()
        return when (e) {
            is JsonArray -> e.flatMap { menuItems(it, depth + 1) }
            is JsonObject -> buildList {
                if (isMenuItem(e)) add(e)
                listOf("@graph", "hasMenu", "hasMenuSection", "hasMenuItem", "mainEntity", "itemListElement", "item", "menu").forEach { k ->
                    e[k]?.let { addAll(menuItems(it, depth + 1)) }
                }
            }
            else -> emptyList()
        }
    }

    private fun fromJsonLd(o: JsonObject): StructuredMenuItem? {
        val name = text(o["name"])?.takeIf { it.isNotBlank() } ?: return null
        val nutrition = o["nutrition"] as? JsonObject
        return StructuredMenuItem(
            name = name,
            description = text(o["description"]).orEmpty(),
            kcal = nutrition?.let { kcal(text(it["calories"])) },
            protein = nutrition?.let { grams(text(it["proteinContent"])) },
            carbs = nutrition?.let { grams(text(it["carbohydrateContent"])) },
            fat = nutrition?.let { grams(text(it["fatContent"])) },
            priceCents = price(o["offers"]),
        )
    }

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.trim()
        is JsonArray -> e.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(e["name"]) ?: text(e["@value"])
        else -> null
    }

    private fun num(s: String?): Double? = s?.let { number.find(it)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() }

    private fun grams(s: String?): Double? = num(s)

    private fun kcal(s: String?): Double? {
        val v = num(s) ?: return null
        return if (s != null && Regex("""\bkj\b""", RegexOption.IGNORE_CASE).containsMatchIn(s)) v / 4.184 else v
    }

    /** `offers.price` in dollars (or any currency's major unit), returned in hundredths. */
    private fun price(e: JsonElement?): Double? = when (e) {
        is JsonObject -> num(text(e["price"]) ?: (e["priceSpecification"] as? JsonObject)?.let { text(it["price"]) })?.times(100)
        is JsonArray -> e.firstNotNullOfOrNull { price(it) }
        else -> null
    }
}
