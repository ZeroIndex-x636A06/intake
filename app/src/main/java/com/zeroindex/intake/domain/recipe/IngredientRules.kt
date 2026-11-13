package com.zeroindex.intake.domain.recipe

/**
 * Plain-language ingredient names mapped to the way USDA names the food, plus the standard weights that
 * household measures need when the bundled food lists no portion for them. A line no rule knows is searched by
 * its own words (see [IngredientEstimator.genericQueries]); the person can always pick a different food.
 *
 * Rules are tried in order and the first match wins, so the specific ones ("garlic powder") come before the
 * general ones ("garlic").
 */
object IngredientRules {

    /**
     * [query] is searched in the offline database. [each] gives grams for a counted amount by size or unit
     * ("medium", "clove"), with "" as the default. [gramsPerCup] overrides the density worked out from USDA portions.
     */
    data class Rule(
        val pattern: Regex,
        val query: String,
        val each: Map<String, Double> = emptyMap(),
        val gramsPerCup: Double? = null,
    )

    private fun rule(pattern: String, query: String, each: Map<String, Double> = emptyMap(), gramsPerCup: Double? = null) =
        Rule(Regex(pattern, RegexOption.IGNORE_CASE), query, each, gramsPerCup)

    val ALL: List<Rule> = listOf(
        rule("""\b(zucchini|zucchinis|courgettes?)\b""", "zucchini includes skin raw", mapOf("small" to 118.0, "medium" to 196.0, "large" to 323.0, "" to 196.0)),
        rule("""\b(cherry|grape)\s+tomato""", "tomatoes grape raw", mapOf("" to 17.0)),
        rule("""\btomato\s+paste\b""", "tomato products canned paste"),
        rule("""\btomato(es)?\b""", "tomatoes red ripe raw", mapOf("small" to 91.0, "medium" to 123.0, "large" to 182.0, "" to 123.0)),
        rule("""\bwhole[- ]?(wheat|grain)\b.*\b(pasta|spaghetti|penne|macaroni)\b|\b(pasta|spaghetti|penne|macaroni)\b.*\bwhole[- ]?(wheat|grain)\b""", "pasta whole-wheat dry"),
        rule("""\bcooked\b.*\b(pasta|spaghetti|penne|fusilli|macaroni)\b|\b(pasta|spaghetti|penne|fusilli|macaroni)\b.*\bcooked\b""", "pasta cooked enriched"),
        rule("""\b(pasta|fusilli|rotini|penne|spaghetti|linguine|fettuccine|macaroni|rigatoni|farfalle|orzo|ziti|bucatini)\b""", "pasta dry enriched"),
        rule("""\bolive\s+oil\b""", "oil olive salad cooking"),
        rule("""\b(vegetable|canola|cooking)\s+oil\b""", "oil vegetable soybean refined"),
        rule("""\bgarlic\s+powder\b""", "spices garlic powder"),
        rule("""\bgarlic\b""", "garlic raw", mapOf("clove" to 3.0, "head" to 40.0, "" to 3.0)),
        rule("""\bbell\s+pepper|\bsweet\s+pepper""", "peppers sweet red raw", mapOf("small" to 74.0, "medium" to 119.0, "large" to 164.0, "" to 119.0)),
        rule("""\b(cayenne|red\s+pepper\s+flakes|chil[il]e?\s+flakes|crushed\s+red\s+pepper)\b""", "spices pepper red cayenne"),
        rule("""\b(black|ground|cracked)\s+pepper\b|^pepper\b|\bpepper,?\s+to taste""", "spices pepper black"),
        rule("""\bfennel\s+seeds?\b""", "spices fennel seed"),
        rule("""\b(vegetable|veggie)\s+(stock|broth|bouillon)\b""", "soup vegetable broth"),
        rule("""\bchicken\s+(stock|broth)\b""", "soup chicken broth ready-to-serve"),
        rule("""\bparmesan|parmigiano""", "cheese parmesan grated"),
        rule("""\bmozzarella\b""", "cheese mozzarella whole milk"),
        rule("""\bcheddar\b""", "cheese cheddar"),
        rule("""\bdried\s+basil\b""", "spices basil dried"),
        rule("""\bbasil\b""", "basil fresh"),
        rule("""\bparsley\b""", "parsley fresh"),
        rule("""\b(cilantro|coriander\s+leaves)\b""", "coriander cilantro leaves raw"),
        rule("""\boregano\b""", "spices oregano dried"),
        rule("""\bcumin\b""", "spices cumin seed"),
        rule("""\bpaprika\b""", "spices paprika"),
        rule("""\bcinnamon\b""", "spices cinnamon ground"),
        rule("""\bonions?\b""", "onions raw", mapOf("small" to 70.0, "medium" to 110.0, "large" to 150.0, "" to 110.0)),
        rule("""\bcarrots?\b""", "carrots raw", mapOf("small" to 50.0, "medium" to 61.0, "large" to 72.0, "" to 61.0)),
        rule("""\bpotato(es)?\b""", "potatoes flesh skin raw", mapOf("small" to 170.0, "medium" to 213.0, "large" to 369.0, "" to 213.0)),
        rule("""\bbutter\b""", "butter salted"),
        rule("""\b(all[- ]purpose\s+)?flour\b""", "wheat flour white all-purpose enriched"),
        rule("""\beggs?\b(?!\s+noodles)""", "egg whole raw fresh", mapOf("small" to 38.0, "medium" to 44.0, "large" to 50.0, "" to 50.0)),
        rule("""\bbrown\s+sugar\b""", "sugars brown"),
        rule("""\bsugar\b""", "sugars granulated"),
        rule("""\bhoney\b""", "honey"),
        rule("""\bsalt\b""", "salt table"),
        rule("""\bsoy\s+sauce\b""", "soy sauce shoyu"),
        rule("""\blemon\s+juice\b""", "lemon juice raw"),
        rule("""\blime\s+juice\b""", "lime juice raw"),
        rule("""\bspinach\b""", "spinach raw"),
        rule("""\bbroccoli\b""", "broccoli raw"),
        rule("""\bmushrooms?\b""", "mushrooms white raw", mapOf("" to 18.0)),
        rule("""\bchicken\s+breasts?\b""", "chicken breast boneless skinless raw"),
        rule("""\bavocados?\b""", "avocados raw california", mapOf("" to 136.0)),
    )

    fun find(description: String): Rule? = ALL.firstOrNull { it.pattern.containsMatchIn(description) }
}
