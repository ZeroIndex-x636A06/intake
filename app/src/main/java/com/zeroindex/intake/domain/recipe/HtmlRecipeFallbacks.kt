package com.zeroindex.intake.domain.recipe

/**
 * Reads a recipe straight from a page's visible HTML when the page has no JSON-LD Recipe and no schema.org
 * microdata. The last non-AI steps of [RecipePageParser], tried in this order:
 *
 * 1. WP Recipe Maker's card classes (`wprm-recipe-ingredient`, `wprm-recipe-instruction-text`, ...);
 * 2. Tasty Recipes' card classes (`tasty-recipes-ingredients`, `tasty-recipes-instructions`, ...);
 * 3. a generic heuristic: an "Ingredients" heading followed by a list, and an "Instructions" (or Directions,
 *    Method) heading after it.
 *
 * No DOM library, no network, no AI. Deterministic and total: markup it cannot read gives null. The result is
 * only ingredient lines, steps and what numbers the page prints; allergens are scanned from the lines elsewhere.
 */
internal object HtmlRecipeFallbacks {
    private val NOISE = Regex(
        """<(script|style|noscript|svg|template|nav|footer|aside|form|iframe)\b.*?</\1\s*>|<!--.*?-->""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val HEADING = Regex("""<(h[1-6])\b[^>]*>(.*?)</\1\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val LI = Regex("""<li\b[^>]*>(.*?)</li\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val PARA = Regex("""<p\b[^>]*>(.*?)</p\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE = Regex("""<title\b[^>]*>(.*?)</title\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val INGREDIENTS_HEAD = Regex("""^\s*(?:the\s+)?ingredients?(?:\s+list)?\s*:?\s*$""", RegexOption.IGNORE_CASE)
    private val STEPS_HEAD = Regex(
        """^\s*(?:instructions?|directions?|method|preparation|steps?|how to make(?: it)?)\s*:?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val END_HEAD = Regex(
        """^\s*(?:notes?|recipe notes?|nutrition(?:al)?(?: information| facts)?|tips?|equipment|video|comments?|reviews?|""" +
            """you (?:may|might) also like|related.*|more recipes.*)\s*:?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun wprm(html: String, pageUrl: String?): RecipeDraft? {
        val body = NOISE.replace(html, " ")
        val groups = RecipeHtmlExtras.wprmGroups(body)
        val ingredients = groups.flatMap { it.ingredients }.ifEmpty {
            RecipeHtmlExtras.withClass(body, "wprm-recipe-ingredient").mapNotNull { RecipeHtmlExtras.wprmLine(it.inner) }
        }
        if (ingredients.isEmpty()) return null
        val steps = RecipeHtmlExtras.withClass(body, "wprm-recipe-instruction-text").map { HtmlText.plain(it.inner) }
            .ifEmpty { RecipeHtmlExtras.withClass(body, "wprm-recipe-instruction").map { HtmlText.plain(it.inner) } }
            .filter { it.isNotEmpty() }
        val nutrition = wprmNutrition(body)
        fun text(token: String) = RecipeHtmlExtras.first(body, token)?.let { HtmlText.plain(it.inner) }
        return draft(
            name = text("wprm-recipe-name") ?: pageTitle(html),
            servings = text("wprm-recipe-servings")?.let { servingsOf(it) },
            total = text("wprm-recipe-total_time-container")?.let { minutesIn(it) }
                ?: text("wprm-recipe-total-time-container")?.let { minutesIn(it) },
            prep = text("wprm-recipe-prep_time-container")?.let { minutesIn(it) },
            cook = text("wprm-recipe-cook_time-container")?.let { minutesIn(it) },
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition,
            pageUrl = pageUrl,
            source = RecipeSource.WPRM_HTML,
        )
    }

    private fun wprmNutrition(body: String): PageNutrition? {
        val values = HashMap<String, String>()
        for (e in RecipeHtmlExtras.withClass(body, "wprm-nutrition-label-text-nutrition-container")) {
            fun part(token: String) = RecipeHtmlExtras.first(e.inner, token)?.let { HtmlText.plain(it.inner) }.orEmpty()
            val label = part("wprm-nutrition-label-text-nutrition-label").trim().trimEnd(':').trim().lowercase()
            val value = part("wprm-nutrition-label-text-nutrition-value")
            val unit = part("wprm-nutrition-label-text-nutrition-unit")
            if (label.isNotEmpty() && value.isNotEmpty()) values.putIfAbsent(label, "$value $unit".trim())
        }
        return nutritionFromLabels(values)
    }

    fun tasty(html: String, pageUrl: String?): RecipeDraft? {
        val body = NOISE.replace(html, " ")
        val ingredients = RecipeHtmlExtras.tastyGroups(body).flatMap { it.ingredients }
        if (ingredients.isEmpty()) return null
        val container = RecipeHtmlExtras.first(body, "tasty-recipes-instructions")
        val scope = container?.let { RecipeHtmlExtras.first(it.inner, "tasty-recipes-instructions-body")?.inner ?: it.inner }.orEmpty()
        val steps = LI.findAll(scope).map { HtmlText.plain(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
        fun text(token: String) = RecipeHtmlExtras.first(body, token)?.let { HtmlText.plain(it.inner) }
        val nutritionBlock = RecipeHtmlExtras.first(body, "tasty-recipes-nutrition")
            ?: RecipeHtmlExtras.first(body, "tasty-recipes-nutrition-info")
        return draft(
            name = text("tasty-recipes-title") ?: pageTitle(html),
            servings = text("tasty-recipes-yield")?.let { servingsOf(it) },
            total = text("tasty-recipes-total-time")?.let { minutesIn(it) },
            prep = text("tasty-recipes-prep-time")?.let { minutesIn(it) },
            cook = text("tasty-recipes-cook-time")?.let { minutesIn(it) },
            ingredients = ingredients,
            steps = steps,
            nutrition = nutritionBlock?.let { labelledNutrition(HtmlText.plain(it.inner, keepLines = true)) },
            pageUrl = pageUrl,
            source = RecipeSource.TASTY_HTML,
        )
    }

    /** "Calories: 310", "Protein: 6 g", one per line or inline, as Tasty Recipes prints its nutrition list. */
    private fun labelledNutrition(text: String): PageNutrition? {
        val values = HashMap<String, String>()
        val pair = Regex("""(calories|protein|carbohydrates?|carbs|total fat|saturated fat|fat|fiber|fibre|sugar|sodium)\s*:?\s*([\d.,]+\s*(?:kcal|cal|mg|g)?)""", RegexOption.IGNORE_CASE)
        for (m in pair.findAll(text)) values.putIfAbsent(m.groupValues[1].lowercase(), m.groupValues[2].trim())
        return nutritionFromLabels(values)
    }

    private fun nutritionFromLabels(v: Map<String, String>): PageNutrition? = RecipePageParser.nutritionOf(
        calories = v["calories"],
        protein = v["protein"],
        carbs = v["carbohydrates"] ?: v["carbohydrate"] ?: v["carbs"],
        fat = v["fat"] ?: v["total fat"],
        fiber = v["fiber"] ?: v["fibre"],
        sugar = v["sugar"] ?: v["sugars"],
        sodium = v["sodium"],
        saturatedFat = v["saturated fat"],
    )

    /**
     * An "Ingredients" heading followed by a list. Every list item up to the next heading that opens the method (or a
     * notes or nutrition block) is an ingredient, so "For the sauce" sub-headings in between do not end the list.
     * Needs at least two items, so a stray "Ingredients" menu entry is not mistaken for a recipe.
     */
    fun generic(html: String, pageUrl: String?): RecipeDraft? {
        val body = NOISE.replace(html, " ")
        val headings = HEADING.findAll(body).map { m ->
            Triple(m, HtmlText.plain(m.groupValues[2]), m.groupValues[1].lowercase())
        }.toList()
        val start = headings.indexOfFirst { INGREDIENTS_HEAD.matches(it.second) }
        if (start < 0) return null
        val level = headings[start].third
        // A heading that opens the method, a notes or nutrition block, or a bigger heading ends the ingredient list;
        // a smaller one ("For the sauce") is a sub-heading inside it.
        fun stops(t: Triple<MatchResult, String, String>) =
            STEPS_HEAD.matches(t.second) || END_HEAD.matches(t.second) || t.third < level
        val segmentEnd = headings.drop(start + 1).firstOrNull { stops(it) }?.first?.range?.first
            ?: minOf(body.length, headings[start].first.range.last + 8_000)
        val segment = body.substring(headings[start].first.range.last + 1, segmentEnd)
        val ingredients = LI.findAll(segment).map { IngredientCleaner.clean(HtmlText.plain(it.groupValues[1])) }
            .filter { it.isNotEmpty() && it.length <= 200 }.toList()
        if (ingredients.size < 2) return null

        var steps = emptyList<String>()
        val stepsHead = headings.drop(start + 1).firstOrNull { STEPS_HEAD.matches(it.second) }
        if (stepsHead != null) {
            val from = stepsHead.first.range.last + 1
            val to = headings.firstOrNull { it.first.range.first > from && (END_HEAD.matches(it.second) || it.third <= stepsHead.third) }
                ?.first?.range?.first ?: minOf(body.length, from + 12_000)
            val seg = body.substring(from, to)
            steps = LI.findAll(seg).map { HtmlText.plain(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
                .ifEmpty { PARA.findAll(seg).map { HtmlText.plain(it.groupValues[1]) }.filter { it.length > 15 }.toList() }
        }
        val text = HtmlText.plain(body, keepLines = true)
        val servings = Regex("""(?:serves|servings?|yields?|makes)\s*:?\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..999 }
        val name = headings.firstOrNull { it.third == "h1" }?.second?.takeIf { it.isNotBlank() } ?: pageTitle(html)
        return draft(name, servings, null, null, null, ingredients, steps, null, pageUrl, RecipeSource.HEURISTIC)
    }

    private fun draft(
        name: String?,
        servings: Int?,
        total: Int?,
        prep: Int?,
        cook: Int?,
        ingredients: List<String>,
        steps: List<String>,
        nutrition: PageNutrition?,
        pageUrl: String?,
        source: RecipeSource,
    ): RecipeDraft? {
        if (ingredients.isEmpty()) return null
        return RecipeDraft(
            name = name?.takeIf { it.isNotBlank() } ?: "Recipe",
            servings = servings,
            totalMinutes = total ?: listOfNotNull(prep, cook).takeIf { it.isNotEmpty() }?.sum(),
            ingredients = ingredients,
            steps = steps,
            nutrition = nutrition,
            imageUrl = null,
            url = pageUrl,
            source = source,
            prepMinutes = prep,
            cookMinutes = cook,
        )
    }

    private fun pageTitle(html: String): String? {
        val t = TITLE.find(html)?.let { HtmlText.plain(it.groupValues[1]) }?.takeIf { it.isNotBlank() } ?: return null
        // "Chicken Soup | Some Site" and "Chicken Soup - Some Site": the dish is the first part.
        return t.split(Regex("""\s+[|–—-]\s+""")).first().trim().takeIf { it.isNotEmpty() }
    }

    private fun servingsOf(raw: String): Int? =
        Regex("""(\d{1,3})(?:\s*(?:-|–|to)\s*(\d{1,3}))?""").find(raw)
            ?.let { (it.groupValues[2].ifEmpty { it.groupValues[1] }).toIntOrNull() }?.takeIf { it in 1..999 }

    /** "1 hour 15 minutes", "45 mins", "1 hr": whole minutes, or null. */
    private fun minutesIn(raw: String): Int? {
        val h = Regex("""(\d+)\s*(?:h\b|hr|hrs|hour|hours)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val m = Regex("""(\d+)\s*(?:m\b|min|mins|minute|minutes)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (h * 60 + m).takeIf { it > 0 }
    }
}
