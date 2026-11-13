package com.zeroindex.intake.domain.recipe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Per-serving numbers a recipe page published, as the page gave them. Any may be missing. */
data class PageNutrition(
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val sugarG: Double? = null,
    /** Milligrams, the unit the app stores sodium in. A page's "1.2 g" is converted. */
    val sodiumMg: Double? = null,
    val saturatedFatG: Double? = null,
) {
    val isEmpty: Boolean
        get() = kcal == null && proteinG == null && carbsG == null && fatG == null &&
            fiberG == null && sugarG == null && sodiumMg == null && saturatedFatG == null
}

enum class RecipeSource { JSON_LD, MICRODATA }

/** A recipe read from a page. Nothing here has been checked for allergens or saved. */
data class RecipeDraft(
    val name: String,
    val servings: Int?,
    val totalMinutes: Int?,
    /** One ingredient per entry, as written on the page and tidied by [IngredientCleaner]. */
    val ingredients: List<String>,
    val steps: List<String>,
    val nutrition: PageNutrition?,
    val imageUrl: String?,
    val url: String?,
    val source: RecipeSource,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    /** The recipe card's Notes section (WP Recipe Maker, Tasty Recipes); JSON-LD has no field for it. */
    val notes: String? = null,
    /**
     * The group header of each ingredient ("For the Sauce"), same order as [ingredients], or empty when
     * the page has no named groups. Read from the page's HTML, since JSON-LD flattens the groups.
     */
    val ingredientGroups: List<String?> = emptyList(),
)

sealed interface PageParse {
    data class Found(val recipe: RecipeDraft) : PageParse

    /**
     * No readable recipe data. [pageText] is the visible text of the page (capped), kept only so
     * the optional "Try with AI" button has something to send when the user taps it.
     */
    data class NoRecipe(val pageText: String) : PageParse
}

/**
 * Reads a recipe out of a web page's HTML without a DOM library: JSON-LD first, then schema.org
 * microdata. Deterministic, total (malformed input gives [PageParse.NoRecipe], never an
 * exception) and offline.
 */
object RecipePageParser {
    const val MAX_PAGE_TEXT = 24_000

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(html: String, pageUrl: String? = null): PageParse = try {
        val fromLd = jsonLdRecipe(html, pageUrl)
        val found = fromLd ?: Microdata(html, pageUrl).recipe()
        if (found != null && (found.ingredients.isNotEmpty() || found.steps.isNotEmpty())) {
            PageParse.Found(enrich(found, html, pageUrl))
        } else {
            PageParse.NoRecipe(pageText(html))
        }
    } catch (e: Exception) {
        PageParse.NoRecipe("")
    } catch (e: StackOverflowError) {
        PageParse.NoRecipe("")
    }

    /**
     * Tidies the ingredient lines and adds what only the page's HTML has: the Notes section and the
     * ingredient group headers. Never throws; if the HTML pass fails the structured data stands alone.
     */
    private fun enrich(found: RecipeDraft, html: String, pageUrl: String?): RecipeDraft {
        val ingredients = found.ingredients.map(IngredientCleaner::clean).filter { it.isNotEmpty() }
        val extras = runCatching { RecipeHtmlExtras.read(html) }.getOrDefault(HtmlExtras())
        val labels = RecipeHtmlExtras.labelsFor(ingredients, extras.groups)
        return found.copy(
            ingredients = ingredients,
            ingredientGroups = if (labels.any { it != null }) labels else emptyList(),
            notes = found.notes ?: extras.notes,
            // The structured data's own photo first, then og:image, twitter:image and the first large picture.
            imageUrl = RecipeImages.absolute(found.imageUrl, pageUrl)
                ?: runCatching { RecipeImages.fromHtml(html, pageUrl) }.getOrNull(),
        )
    }

    // ---- page text for the AI fallback --------------------------------------------------------

    fun pageText(html: String): String {
        val noScripts = html
            .replace(Regex("""<(script|style|noscript|svg|nav|footer|header|form|iframe)\b.*?</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), " ")
        return HtmlText.plain(noScripts, keepLines = true).take(MAX_PAGE_TEXT)
    }

    // ---- JSON-LD -----------------------------------------------------------------------------

    private val SCRIPT = Regex("""<script\b([^>]*)>(.*?)</script\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun jsonLdBlocks(html: String): List<String> = SCRIPT.findAll(html)
        .filter { it.groupValues[1].contains("ld+json", ignoreCase = true) }
        .map { it.groupValues[2] }
        .toList()

    private fun jsonLdRecipe(html: String, pageUrl: String?): RecipeDraft? {
        for (block in jsonLdBlocks(html)) {
            val root = parseJson(block) ?: continue
            val index = HashMap<String, JsonObject>().also { indexById(root, it, 0) }
            for (candidate in recipeObjects(root, depth = 0)) {
                val draft = fromJsonLd(candidate, pageUrl, index)
                if (draft != null && (draft.ingredients.isNotEmpty() || draft.steps.isNotEmpty())) return draft
            }
        }
        return null
    }

    private val CDATA_OPEN = Regex("""^\s*(?:(?://|/\*)\s*)?<!\[CDATA\[\s*(?:\*/)?""")
    private val CDATA_CLOSE = Regex("""\s*(?:(?://|/\*)\s*)?\]\]>\s*(?:\*/)?\s*$""")

    private fun parseJson(raw: String): JsonElement? {
        var text = raw.trim()
        text = text.removePrefix("<!--").removeSuffix("-->").trim()
        text = text.dropWhile { it.code == 0xFEFF }.trim()
        // CDATA wrappers, with or without a space after the comment marker: "//<![CDATA[", "// <![CDATA[", "/* <![CDATA[ */".
        text = CDATA_OPEN.replace(text, "")
        text = CDATA_CLOSE.replace(text, "").trim()
        if (text.isEmpty()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
            // Raw line breaks and tabs inside strings are common and invalid; as spaces they are harmless.
            ?: runCatching { json.parseToJsonElement(text.replace(Regex("""[\r\n\t]+"""), " ")) }.getOrNull()
    }

    private fun isRecipeType(o: JsonObject): Boolean {
        val t = o["@type"] ?: return false
        return when (t) {
            is JsonArray -> t.any { (it as? JsonPrimitive)?.contentOrNull.equals("Recipe", ignoreCase = true) || (it as? JsonPrimitive)?.contentOrNull?.endsWith("/Recipe") == true }
            is JsonPrimitive -> t.contentOrNull.let { it.equals("Recipe", ignoreCase = true) || it?.endsWith("/Recipe") == true }
            else -> false
        }
    }

    /** Every Recipe object in the document: top level, arrays, @graph and nested mainEntity. */
    private fun recipeObjects(e: JsonElement, depth: Int): List<JsonObject> {
        if (depth > 6) return emptyList()
        return when (e) {
            is JsonArray -> e.flatMap { recipeObjects(it, depth + 1) }
            is JsonObject -> buildList {
                if (isRecipeType(e)) add(e)
                listOf("@graph", "mainEntity", "mainEntityOfPage", "itemListElement", "item").forEach { k ->
                    e[k]?.let { addAll(recipeObjects(it, depth + 1)) }
                }
            }
            else -> emptyList()
        }
    }

    private fun fromJsonLd(o: JsonObject, pageUrl: String?, index: Map<String, JsonObject>): RecipeDraft? {
        val name = text(o["name"]) ?: text(o["headline"]) ?: return null
        val ingredients = strings(o["recipeIngredient"]).ifEmpty { strings(o["ingredients"]) }
        val steps = instructions(o["recipeInstructions"], 0)
        val prep = IsoDuration.minutes(text(o["prepTime"]))
        val cook = IsoDuration.minutes(text(o["cookTime"]))
        val total = IsoDuration.minutes(text(o["totalTime"]))
            ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum()
        return RecipeDraft(
            name = name,
            servings = servings(o["recipeYield"]),
            totalMinutes = total,
            prepMinutes = prep,
            cookMinutes = cook,
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition(o["nutrition"], index),
            imageUrl = imageUrl(o["image"], index, pageUrl) ?: imageUrl(o["thumbnailUrl"], index, pageUrl),
            url = text(o["url"]) ?: pageUrl,
            source = RecipeSource.JSON_LD,
        )
    }

    /** A string value with markup removed; numbers become their text. */
    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.let { HtmlText.plain(it) }?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(e["text"]) ?: text(e["name"]) ?: text(e["@value"])
        else -> null
    }

    private fun strings(e: JsonElement?): List<String> = when (e) {
        is JsonPrimitive -> e.contentOrNull?.let { c ->
            // A single string holding several lines is several ingredients.
            c.split('\n').map { HtmlText.plain(it) }.filter { it.isNotEmpty() }
        }.orEmpty()
        is JsonArray -> e.flatMap { strings(it) }
        is JsonObject -> listOfNotNull(text(e))
        else -> emptyList()
    }

    /** String or array; HowToStep, HowToSection (with nested itemListElement) and plain text steps. */
    private fun instructions(e: JsonElement?, depth: Int, section: String? = null): List<String> {
        if (depth > 6) return emptyList()
        return when (e) {
            null -> emptyList()
            is JsonPrimitive -> splitSteps(e.contentOrNull.orEmpty())
            is JsonArray -> e.flatMap { instructions(it, depth + 1, section) }
            is JsonObject -> {
                val type = (e["@type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val list = e["itemListElement"]
                if (list != null || type.contains("Section", ignoreCase = true)) {
                    val title = text(e["name"])
                    instructions(list, depth + 1, title ?: section)
                } else {
                    val step = text(e["text"]) ?: text(e["name"])
                    listOfNotNull(step?.let { s -> if (section != null && !s.startsWith(section, true)) "$section: $s" else s })
                }
            }
            else -> emptyList()
        }
    }

    private fun splitSteps(raw: String): List<String> {
        val plain = HtmlText.plain(raw, keepLines = true)
        if (plain.isEmpty()) return emptyList()
        val lines = plain.lines().map { it.replace(Regex("""^\s*(?:step\s*)?\d{1,2}\s*[.):]\s+""", RegexOption.IGNORE_CASE), "").trim() }
            .filter { it.isNotEmpty() }
        return lines
    }

    /** "4", "4 servings", ["4","4 servings"], 4 -> 4. Takes the first number inside; a range uses its upper value. */
    private fun servings(e: JsonElement?): Int? {
        val raw = when (e) {
            is JsonPrimitive -> e.contentOrNull
            is JsonArray -> e.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.any(Char::isDigit) } }
            else -> null
        } ?: return null
        val m = Regex("""(\d{1,3})(?:\s*(?:-|–|to)\s*(\d{1,3}))?""").find(raw) ?: return null
        val n = (m.groupValues[2].ifEmpty { m.groupValues[1] }).toIntOrNull() ?: return null
        return n.takeIf { it in 1..999 }
    }

    /**
     * Recipe.image in every form schema.org allows: a string, an array of strings or objects, an ImageObject
     * (its url or contentUrl), or a reference by "@id" to an ImageObject elsewhere in the @graph. Relative
     * addresses are made absolute against the page.
     */
    private fun imageUrl(e: JsonElement?, index: Map<String, JsonObject>, pageUrl: String?, depth: Int = 0): String? {
        if (depth > 5) return null
        return when (e) {
            is JsonPrimitive -> {
                val raw = e.contentOrNull?.trim().orEmpty()
                RecipeImages.absolute(raw, pageUrl)?.takeIf { raw.isNotEmpty() && index[raw] == null }
                    ?: index[raw]?.let { imageUrl(it, index, pageUrl, depth + 1) }
            }
            is JsonArray -> e.firstNotNullOfOrNull { imageUrl(it, index, pageUrl, depth + 1) }
            is JsonObject -> {
                val ref = (e["@id"] as? JsonPrimitive)?.contentOrNull
                imageUrl(e["url"], index, pageUrl, depth + 1)
                    ?: imageUrl(e["contentUrl"], index, pageUrl, depth + 1)
                    ?: if (e.size == 1 && ref != null) index[ref]?.let { imageUrl(it, index, pageUrl, depth + 1) } else null
            }
            else -> null
        }
    }

    /** Every object that carries an "@id", so a Recipe's `"nutrition": {"@id": "#nutrition"}` can be followed inside an @graph. */
    private fun indexById(e: JsonElement, out: MutableMap<String, JsonObject>, depth: Int) {
        if (depth > 8) return
        when (e) {
            is JsonArray -> e.forEach { indexById(it, out, depth + 1) }
            is JsonObject -> {
                val id = (e["@id"] as? JsonPrimitive)?.contentOrNull
                // A bare reference ({"@id": ...} alone) is not the node itself.
                if (id != null && e.size > 1) out.putIfAbsent(id, e)
                e.values.forEach { indexById(it, out, depth + 1) }
            }
            else -> Unit
        }
    }

    /**
     * The page's NutritionInformation. It may be an object, an array of objects (the first that says anything wins),
     * or a reference by "@id" to a node elsewhere in the graph. Keys are matched without regard to case and the
     * common alternatives are read too ("protein" for "proteinContent"). A missing value stays null; it is never 0.
     */
    private fun nutrition(e: JsonElement?, index: Map<String, JsonObject>, depth: Int = 0): PageNutrition? {
        if (depth > 4) return null
        return when (e) {
            is JsonArray -> e.firstNotNullOfOrNull { nutrition(it, index, depth + 1) }
            is JsonObject -> {
                val ref = (e["@id"] as? JsonPrimitive)?.contentOrNull
                if (e.size == 1 && ref != null) return index[ref]?.let { nutrition(it, index, depth + 1) }
                val lower = e.entries.associate { it.key.lowercase() to it.value }
                fun get(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> lower[k.lowercase()]?.let { measure(it) } }
                nutritionOf(
                    calories = get("calories", "energy", "kcal", "energyContent"),
                    protein = get("proteinContent", "protein"),
                    carbs = get("carbohydrateContent", "carbohydrates", "carbs", "carbohydrate"),
                    fat = get("fatContent", "fat", "totalFat"),
                    fiber = get("fiberContent", "fibreContent", "fiber", "fibre", "dietaryFiber"),
                    sugar = get("sugarContent", "sugar", "sugars"),
                    sodium = get("sodiumContent", "sodium"),
                    saturatedFat = get("saturatedFatContent", "saturatedFat"),
                )
            }
            else -> null
        }
    }

    /** A nutrient's text: a string or number as written, or the "value" (with its unit) of a QuantitativeValue. */
    private fun measure(e: JsonElement): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.let { HtmlText.plain(it) }?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.firstNotNullOfOrNull { measure(it) }
        is JsonObject -> {
            val v = (e["value"] ?: e["@value"] ?: e["minValue"])?.let { measure(it) }
            val unit = (e["unitText"] ?: e["unitCode"])?.let { measure(it) }.orEmpty()
            v?.let { if (unit.isBlank()) it else "$it $unit" } ?: text(e)
        }
        else -> null
    }

    internal fun nutritionOf(
        calories: String?,
        protein: String?,
        carbs: String?,
        fat: String?,
        fiber: String? = null,
        sugar: String? = null,
        sodium: String? = null,
        saturatedFat: String? = null,
    ): PageNutrition? {
        val result = PageNutrition(
            kcalOf(calories), massG(protein), massG(carbs), massG(fat),
            fiberG = massG(fiber), sugarG = massG(sugar), sodiumMg = massMg(sodium), saturatedFatG = massG(saturatedFat),
        )
        return result.takeUnless { it.isEmpty }
    }

    /** A number as pages write it: "12", "12.5", "12,5" (decimal comma), "1,200" (thousands), ".5". */
    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d*\.\d+|\d+(?:,\d{1,2}(?!\d))?"""

    private val THOUSANDS = Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?""")
    private val KCAL = Regex("""($NUM)\s*(?:k?cal(?:ories?)?\b)""", RegexOption.IGNORE_CASE)
    private val KILOJOULE = Regex("""\bk?j(?:oules?)?\b""", RegexOption.IGNORE_CASE)

    private fun numberOf(raw: String): Double? {
        val t = raw.trim()
        return (if (THOUSANDS.matches(t)) t.replace(",", "") else t.replace(',', '.')).toDoubleOrNull()
    }

    /**
     * "444 calories", "444 kcal", 444, "1,840 kJ" and "1,840 kJ (440 kcal)". A kilojoule figure is converted
     * unless the same text also gives kilocalories, which then win.
     */
    private fun kcalOf(s: String?): Double? {
        if (s.isNullOrBlank()) return null
        KCAL.find(s)?.let { return numberOf(it.groupValues[1]) }
        val first = Regex(NUM).find(s)?.let { numberOf(it.value) } ?: return null
        return if (KILOJOULE.containsMatchIn(s)) first / 4.184 else first
    }

    private val MASS = Regex("""($NUM)\s*(mg|milligrams?|mcg|[µμ]g|micrograms?|kg|kilograms?|grams?|gr|g)?(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    private fun isMilli(u: String) = u.startsWith("mg") || u.startsWith("milli")
    private fun isMicro(u: String) = u.startsWith("mcg") || u.startsWith("micro") || u.startsWith("µ") || u.startsWith("μ")

    /** "2 g", "2 grams", "2000 mg", "2" -> grams. A bare number is read as grams; nothing readable is null, never 0. */
    private fun massG(s: String?): Double? = mass(s)?.let { (v, unit) ->
        when {
            isMilli(unit) -> v / 1000.0
            isMicro(unit) -> v / 1_000_000.0
            unit.startsWith("k") -> v * 1000.0
            else -> v
        }
    }

    /** "1179 mg", "1.2 g", "1179" -> milligrams. A bare number is read as milligrams, which is what pages publish. */
    private fun massMg(s: String?): Double? = mass(s)?.let { (v, unit) ->
        when {
            isMilli(unit) -> v
            isMicro(unit) -> v / 1000.0
            unit.startsWith("k") -> v * 1_000_000.0
            unit.isNotEmpty() -> v * 1000.0
            else -> v
        }
    }

    private fun mass(s: String?): Pair<Double, String>? {
        val m = MASS.find(s ?: return null) ?: return null
        val v = numberOf(m.groupValues[1]) ?: return null
        return v to m.groupValues[2].lowercase()
    }

    // ---- microdata ---------------------------------------------------------------------------

    /** schema.org/Recipe microdata, read by scanning tags in order (no DOM). */
    private class Microdata(val html: String, val pageUrl: String?) {
        private val tag = Regex("""<([a-zA-Z][a-zA-Z0-9]*)\b((?:[^>"']|"[^"]*"|'[^']*')*)>""")
        private val recipeScope = Regex("""itemtype\s*=\s*["'][^"']*schema\.org/Recipe["']""", RegexOption.IGNORE_CASE)

        private fun attr(attrs: String, name: String): String? =
            Regex("""(?<![\w-])$name\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""", RegexOption.IGNORE_CASE)
                .find(attrs)?.let { it.groups[1]?.value ?: it.groups[2]?.value ?: it.groups[3]?.value }

        fun recipe(): RecipeDraft? {
            val start = tag.findAll(html).firstOrNull { recipeScope.containsMatchIn(it.groupValues[2]) } ?: return null
            val scopeEnd = closeOf(start.groupValues[1], start.range.last + 1)
            val body = html.substring(start.range.last + 1, scopeEnd)

            var name: String? = null
            var servings: Int? = null
            var total: Int? = null
            var prep: Int? = null
            var cook: Int? = null
            var image: String? = null
            var url: String? = null
            val ingredients = ArrayList<String>()
            val steps = ArrayList<String>()
            var nutrition: PageNutrition? = null

            var skipUntil = -1
            for (m in tag.findAll(body)) {
                if (m.range.first < skipUntil) continue
                val attrs = m.groupValues[2]
                val prop = attr(attrs, "itemprop")?.trim()?.lowercase() ?: continue
                val tagName = m.groupValues[1].lowercase()
                val isScope = Regex("""(?<![\w-])itemscope(?![\w-])""", RegexOption.IGNORE_CASE).containsMatchIn(attrs)
                val innerEnd = if (tagName in VOID) m.range.last + 1 else closeOf(tagName, m.range.last + 1, body)
                val inner = if (tagName in VOID) "" else body.substring(m.range.last + 1, innerEnd.coerceAtMost(body.length))
                if (isScope) skipUntil = innerEnd // anything inside a nested item is not the recipe's own

                fun value(): String {
                    attr(attrs, "content")?.let { return HtmlText.decode(it).trim() }
                    if (tagName == "time" || tagName == "meta") attr(attrs, "datetime")?.let { return it.trim() }
                    if (tagName == "img" || tagName == "source") attr(attrs, "src")?.let { return it.trim() }
                    if (tagName == "a" || tagName == "link") attr(attrs, "href")?.let { return it.trim() }
                    return HtmlText.plain(inner)
                }

                when (prop) {
                    "name" -> if (name == null && !isScope) name = value().takeIf { it.isNotEmpty() }
                    "recipeyield" -> servings = servings ?: servingsOf(value())
                    "totaltime" -> total = total ?: IsoDuration.minutes(value())
                    "preptime" -> prep = prep ?: IsoDuration.minutes(value())
                    "cooktime" -> cook = cook ?: IsoDuration.minutes(value())
                    "image" -> if (image == null) image = value().takeIf { it.startsWith("http") }
                    "url" -> if (url == null) url = value().takeIf { it.isNotEmpty() }
                    "recipeingredient", "ingredients" -> value().takeIf { it.isNotEmpty() }?.let { ingredients += it }
                    "recipeinstructions" -> steps += stepsOf(inner, value(), tagName in VOID)
                    "nutrition" -> if (isScope) nutrition = nutritionIn(inner)
                }
            }
            val n = name ?: return null
            return RecipeDraft(
                name = n,
                servings = servings,
                prepMinutes = prep,
                cookMinutes = cook,
                totalMinutes = total ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum(),
                ingredients = ingredients,
                steps = steps,
                nutrition = nutrition,
                imageUrl = image,
                url = url ?: pageUrl,
                source = RecipeSource.MICRODATA,
            )
        }

        private fun servingsOf(raw: String): Int? =
            Regex("""(\d{1,3})(?:\s*(?:-|–|to)\s*(\d{1,3}))?""").find(raw)
                ?.let { (it.groupValues[2].ifEmpty { it.groupValues[1] }).toIntOrNull() }?.takeIf { it in 1..999 }

        private fun stepsOf(inner: String, flat: String, isVoid: Boolean): List<String> {
            if (isVoid) return listOf(flat).filter { it.isNotEmpty() }
            val items = Regex("""<(li|p)\b[^>]*>(.*?)</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .findAll(inner).map { HtmlText.plain(it.groupValues[2]) }.filter { it.isNotEmpty() }.toList()
            if (items.isNotEmpty()) return items
            return splitStepsPublic(inner)
        }

        private fun splitStepsPublic(raw: String): List<String> =
            HtmlText.plain(raw, keepLines = true).lines()
                .map { it.replace(Regex("""^\s*(?:step\s*)?\d{1,2}\s*[.):]\s+""", RegexOption.IGNORE_CASE), "").trim() }
                .filter { it.isNotEmpty() }

        private fun nutritionIn(inner: String): PageNutrition? {
            fun prop(name: String): String? {
                for (m in tag.findAll(inner)) {
                    if (attr(m.groupValues[2], "itemprop")?.trim().equals(name, ignoreCase = true)) {
                        attr(m.groupValues[2], "content")?.let { return it }
                        val end = closeOf(m.groupValues[1].lowercase(), m.range.last + 1, inner)
                        return HtmlText.plain(inner.substring(m.range.last + 1, end.coerceAtMost(inner.length)))
                    }
                }
                return null
            }
            return nutritionOf(
                prop("calories"), prop("proteinContent"), prop("carbohydrateContent"), prop("fatContent"),
                fiber = prop("fiberContent"), sugar = prop("sugarContent"), sodium = prop("sodiumContent"),
                saturatedFat = prop("saturatedFatContent"),
            )
        }

        /** Index of the end tag that closes [name], counting nested tags of the same name. */
        private fun closeOf(name: String, from: Int, text: String = html): Int {
            val re = Regex("""<(/?)${Regex.escape(name)}\b[^>]*>""", RegexOption.IGNORE_CASE)
            var depth = 1
            var pos = from
            while (pos <= text.length) {
                val m = re.find(text, pos) ?: return text.length
                if (m.groupValues[1] == "/") depth-- else if (!m.value.endsWith("/>")) depth++
                if (depth == 0) return m.range.first
                pos = m.range.last + 1
            }
            return text.length
        }

        private companion object {
            val VOID = setOf("meta", "img", "link", "source", "input", "br", "hr")
        }
    }
}
