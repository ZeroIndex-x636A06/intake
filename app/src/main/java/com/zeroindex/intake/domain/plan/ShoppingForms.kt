package com.zeroindex.intake.domain.plan

/**
 * What a person buys is not always what a recipe line weighs. A line may give cooked rice, cooked spaghetti or cooked
 * chicken, but the store sells them dry or raw; and milk, broth and oil are bought by volume. This is the table for
 * both conversions. Averages only (USDA values), so a converted total is shown with the "≈" mark.
 */
object ShoppingForms {

    /**
     * A food sold in a form other than the cooked one a recipe lists. [factor] is cooked grams per bought gram (cooked rice
     * is about 2.8 times the dry weight), [cupGrams] what a cup of the cooked food weighs.
     */
    data class CookedForm(val factor: Double, val cupGrams: Double)

    private val PASTA_WORDS = setOf(
        "pasta", "spaghetti", "penne", "macaroni", "fusilli", "rotini", "linguine", "fettuccine", "rigatoni", "farfalle", "orzo", "ziti",
        "bucatini", "noodle", "noodles", "lasagna", "vermicelli", "tagliatelle", "angel", "capellini", "ramen",
    )

    /** The conversion for [text] (a line's name and description) when it says "cooked"; null when it is not cooked or not converted. */
    fun cookedForm(text: String): CookedForm? {
        val t = text.lowercase()
        if (!Regex("""\bcooked\b""").containsMatchIn(t) || Regex("""\b(un|pre)cooked\b""").containsMatchIn(t)) return null
        val words = t.split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }.toSet()
        return when {
            "rice" in words && "brown" in words -> CookedForm(3.0, 195.0)
            "rice" in words && "cauliflower" !in words -> CookedForm(2.8, 158.0)
            words.any { it in PASTA_WORDS } -> CookedForm(2.3, 140.0)
            "quinoa" in words -> CookedForm(3.0, 185.0)
            "couscous" in words -> CookedForm(3.0, 157.0)
            "lentil" in words || "lentils" in words -> CookedForm(3.0, 198.0)
            "chickpea" in words || "chickpeas" in words || "garbanzo" in words -> CookedForm(2.3, 164.0)
            words.any { it in setOf("bean", "beans") } && "green" !in words && "canned" !in words -> CookedForm(2.6, 172.0)
            // Meat and fish lose water in the pan: about a quarter of the raw weight (a third for beef and pork).
            words.any { it in setOf("chicken", "turkey", "duck") } -> CookedForm(0.75, 140.0)
            words.any { it in setOf("beef", "pork", "lamb", "steak", "veal") } -> CookedForm(0.70, 135.0)
            words.any { it in setOf("salmon", "cod", "tilapia", "fish", "shrimp", "trout") } -> CookedForm(0.80, 140.0)
            else -> null
        }
    }

    private val NOT_LIQUID = setOf(
        "chocolate", "powder", "powdered", "dry", "dried", "cheese", "chestnut", "chestnuts", "cress", "tuna", "sardine", "sardines",
        "tomato", "tomatoes", "packed", "ice", "sour", "butter", "bread", "cake", "cookie", "cookies", "candy",
    )

    /** Grams per millilitre for foods bought by the cup or litre, or null for a food that is not a liquid. */
    fun liquidDensity(key: String): Double? {
        val words = key.lowercase().split(Regex("""[^a-z]+""")).filter { it.isNotEmpty() }.toSet()
        if (words.any { it in NOT_LIQUID }) return null
        return when {
            "buttermilk" in words || "milk" in words || "kefir" in words -> 1.03
            "cream" in words || ("half" in words && "and" in words) -> 1.0
            "broth" in words || "stock" in words || "bouillon" in words -> 1.0
            "juice" in words -> 1.04
            "oil" in words -> 0.92
            "vinegar" in words -> 1.01
            "wine" in words -> 0.99
            "beer" in words || "water" in words -> 1.0
            "soy" in words && "sauce" in words -> 1.15
            else -> null
        }
    }
}
