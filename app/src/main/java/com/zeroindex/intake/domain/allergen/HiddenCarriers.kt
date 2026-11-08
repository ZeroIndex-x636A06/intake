package com.zeroindex.intake.domain.allergen

/**
 * A food that commonly hides an allergen without naming it. A hit is the softest tier, "Check the
 * label" (docs/ALLERGENS.md): it never blocks and never asks for a tap. [phrase] is matched as whole
 * words, case-insensitively, with spaces and hyphens interchangeable; the token "..." allows up to
 * two other words in that spot ("taco ... seasoning" matches "Taco Bell mild seasoning").
 */
data class CheckLabelTerm(
    val allergen: String,
    val phrase: String,
    /** Short human reason, shown verbatim next to the chip. */
    val reason: String,
) {
    /** Whole-word regex: letters and digits on either side disqualify a match. */
    val regex: Regex by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val gap = """(?:[\p{L}\p{N}'&-]+\s+){0,2}"""
        val sep = """[\s\-]+"""
        val body = phrase.trim().lowercase().split(Regex("\\s+\\.\\.\\.\\s+")).joinToString(sep + gap) { segment ->
            segment.split(Regex("[\\s\\-]+")).joinToString(sep) { Regex.escape(it) }
        }
        Regex("(?<![\\p{L}\\p{N}])$body(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    /** First whole-word hit in [text], or null. */
    fun findIn(text: String): MatchResult? = regex.find(text)
}

/**
 * Foods that commonly hide an allergen, for every built-in allergen. The softest tier, "Check the
 * label": a hit never blocks and never asks for a tap. In code rather than in the alias table, so a
 * list change reaches existing installs on the next update with no re-seed, and so it can never
 * weaken an alias match. The dairy list comes from the user's own hidden-dairy checklist.
 *
 * Decisions (also in docs/ALLERGENS.md):
 * - bare "roll(s)" is NOT a term, so egg rolls, spring rolls and "rolled oats" stay quiet; rolls count
 *   only when qualified as bread (hoagie, dinner, ...).
 * - bare "seasoning" is NOT a term ("Italian seasoning" is a dried herb blend); only packets, mixes, and
 *   taco, ranch, fajita and chili seasoning.
 * - "chocolate" is a term on its own (milk chocolate is already a hard match through "milk");
 *   "caramelized" does not match "caramel" (whole words).
 * - "natural flavor" is a dairy term only, because the user's checklist names it. US and EU rules make
 *   the other major allergens name themselves on the label even inside a flavour.
 */
object HiddenCarriers {
    const val DAIRY = AllergenCatalog.DAIRY

    private fun carriers(allergen: String, reason: String, vararg phrases: String) =
        phrases.map { CheckLabelTerm(allergen, it, reason) }

    private fun dairy(reason: String, vararg phrases: String) = carriers(DAIRY, reason, *phrases)

    val DAIRY_TERMS: List<CheckLabelTerm> =
        dairy(
            "Flour tortillas often contain milk, whey or butter",
            "tortilla", "tortillas",
        ) + dairy(
            "Bread rolls and buns often contain milk or butter",
            "bun", "buns",
            "hoagie roll", "hoagie rolls", "hamburger roll", "hamburger rolls",
            "hot dog roll", "hot dog rolls", "burger roll", "burger rolls",
            "dinner roll", "dinner rolls", "sandwich roll", "sandwich rolls",
            "sub roll", "sub rolls", "bread roll", "bread rolls", "kaiser roll",
            "kaiser rolls", "hawaiian roll", "hawaiian rolls", "crescent roll",
            "crescent rolls", "cinnamon roll", "cinnamon rolls",
        ) + dairy(
            "Brioche and croissants are made with butter and milk",
            "brioche", "croissant", "croissants",
        ) + dairy(
            "Breadcrumbs often contain milk or whey",
            "breadcrumb", "breadcrumbs", "bread crumbs", "panko",
        ) + dairy(
            "Crackers often contain milk, butter or whey",
            "cracker", "crackers", "saltine", "saltines",
        ) + dairy(
            "Corn chips and seasoned chips can contain cheese, whey or milk powder",
            "fritos", "frito", "corn chips",
        ) + dairy(
            "Seasoning packets often contain milk powder, whey or cheese",
            "taco ... seasoning", "ranch ... seasoning", "fajita seasoning", "chili seasoning",
            "seasoning packet", "seasoning packets", "seasoning mix", "seasoning mixes",
            "seasoning envelope", "taco mix", "ranch mix", "ranch packet",
        ) + dairy(
            "\"Natural flavoring\" on seasoned meat can include dairy",
            "natural flavoring", "natural flavorings", "natural flavor", "natural flavors",
            "natural flavour", "natural flavours",
        ) + dairy(
            "Margarine usually contains whey or milk solids",
            "margarine",
        ) + dairy(
            "Creamers, even \"non-dairy\" ones, can contain sodium caseinate",
            "creamer", "creamers",
        ) + dairy(
            "Instant mashed potatoes often contain milk or butter",
            "instant mashed potatoes", "instant mashed potato", "instant potatoes",
        ) + dairy(
            "Boxed rice sides often contain milk, butter or cheese",
            "boxed rice", "rice side", "rice sides", "rice pilaf mix",
        ) + dairy(
            "Caramel and nougat are made with milk or cream",
            "caramel", "nougat",
        ) + dairy(
            "Chocolate often contains milk or milk fat",
            "chocolate",
        ) + dairy(
            "Protein bars often have milk chocolate, caramel or whey coatings",
            "protein bar", "protein bars",
        ) + dairy(
            "Puddings, mousses and frostings are usually made with milk or butter",
            "pudding", "puddings", "mousse", "frosting", "icing", "ganache",
        )

    private val EGG_TERMS = carriers(
        AllergenCatalog.EGG, "Baked goods, pasta and noodles often contain egg",
        "baked goods", "pasta", "noodle", "noodles", "marshmallow", "marshmallows", "nougat",
    ) + carriers(
        AllergenCatalog.EGG, "Breaded and battered foods are often dipped in egg",
        "breaded", "battered", "crumbed", "fritter", "fritters",
    )

    private val FISH_TERMS = carriers(
        AllergenCatalog.FISH, "Asian sauces and curry pastes often contain fish sauce or anchovy",
        "stir fry sauce", "oyster sauce", "thai curry paste", "curry paste", "pad thai", "worcestershire",
    )

    private val SHELLFISH_TERMS = carriers(
        AllergenCatalog.SHELLFISH, "Curry pastes, XO sauce and fish sauces can contain shrimp",
        "fish sauce", "stir fry sauce", "thai curry paste", "curry paste", "xo sauce", "surimi", "sushi",
    )

    private val MOLLUSC_TERMS = carriers(
        AllergenCatalog.MOLLUSCS, "Stir-fry sauces and curry pastes can contain oyster or clam",
        "stir fry sauce", "fish sauce", "thai curry paste", "curry paste",
    )

    private val TREE_NUT_TERMS = carriers(
        AllergenCatalog.TREE_NUTS, "Coconut is not a tree nut on most labels, but some people react to it",
        "coconut", "coconut milk", "coconut oil", "coconut flour",
    ) + carriers(
        AllergenCatalog.TREE_NUTS, "Baked goods, cereals, chocolate and ice cream often contain nuts or share a line with them",
        "baked goods", "cookie", "cookies", "cake", "cakes", "cereal", "chocolate", "ice cream",
    )

    private val PEANUT_TERMS = carriers(
        AllergenCatalog.PEANUTS, "Plant proteins can come from peanuts",
        "hydrolyzed plant protein", "hydrolyzed vegetable protein", "textured vegetable protein",
    ) + carriers(
        AllergenCatalog.PEANUTS, "Baked goods, candy, chocolate and Asian sauces often contain peanuts or share a line with them",
        "baked goods", "cookie", "cookies", "brownie", "brownies", "granola", "energy bar", "snack bar",
        "ice cream", "candy", "chocolate", "cereal", "asian sauce", "stir fry",
    )

    private val WHEAT_TERMS = carriers(
        AllergenCatalog.WHEAT, "Corn tortillas and soba noodles are sometimes blended with wheat",
        "tortilla", "tortillas", "soba",
    ) + carriers(
        AllergenCatalog.WHEAT, "Soups, sauces and seasoning mixes are often thickened with wheat flour",
        "modified food starch", "seasoning mix", "soup", "soups", "sauce mix", "imitation crab",
        "veggie burger", "veggie burgers", "licorice", "oats", "oatmeal",
    )

    private val GLUTEN_TERMS = carriers(
        AllergenCatalog.GLUTEN, "Starches, soups, broths and sauce mixes can hide gluten",
        "modified food starch", "seasoning mix", "soup", "soups", "sauce mix", "imitation crab",
        "veggie burger", "veggie burgers", "licorice", "soy sauce", "broth", "bouillon",
    )

    private val SOY_TERMS = carriers(
        AllergenCatalog.SOY, "Vegetable oil blends, shortening and spreads are often made from soybean oil",
        "vegetable oil", "vegetable shortening", "margarine", "mayonnaise",
    ) + carriers(
        AllergenCatalog.SOY, "Protein bars, baked goods, broths and chocolate often contain soy protein or lecithin",
        "protein bar", "protein bars", "baked goods", "canned tuna", "broth", "bouillon", "chocolate", "bread",
    )

    private val SESAME_TERMS = carriers(
        AllergenCatalog.SESAME, "Buns, bagels, breads and crackers often carry sesame seeds",
        "bun", "buns", "burger bun", "burger buns", "bagel", "bagels", "bread", "cracker", "crackers",
        "crispbread", "rice cracker", "rice crackers", "baked goods",
    ) + carriers(
        AllergenCatalog.SESAME, "Spice blends, Asian sauces, dressings and plant burgers can contain sesame",
        "granola", "protein bar", "protein bars", "veggie burger", "veggie burgers", "asian sauce",
        "stir fry", "salad dressing", "spice blend", "seasoning blend", "sushi",
    )

    private val CELERY_TERMS = carriers(
        AllergenCatalog.CELERY, "Stocks, soups, stuffing and spice blends often contain celery or celery salt",
        "stock", "broth", "bouillon", "soup", "soups", "stuffing", "dressing", "spice blend", "seasoning blend",
        "curry powder", "old bay", "cajun seasoning", "creole seasoning", "vegetable stock", "vegetable broth",
        "gravy", "stew", "casserole", "marinade", "sausage", "deli meat",
    )

    private val MUSTARD_TERMS = carriers(
        AllergenCatalog.MUSTARD, "Dressings, sauces, pickles and processed meats often contain mustard",
        "salad dressing", "mayonnaise", "ketchup", "hot sauce", "spice blend", "seasoning blend",
        "seasoning mix", "curry powder", "sauce", "soup", "deli meat", "pickled vegetables",
        "potato salad", "coleslaw",
    )

    private val LUPIN_TERMS = carriers(
        AllergenCatalog.LUPIN, "Some gluten-free flours, pastas and baked goods are made with lupin",
        "gluten free pasta", "gluten free bread", "gluten free flour", "gluten free baking",
        "gluten free pastry", "pizza base", "crepe", "crepes", "waffle", "waffles", "wafer", "wafers",
        "veggie burger", "vegan burger", "meat free sausage", "pastry", "pastries", "pasta", "bread",
    )

    private val SULPHITE_TERMS = carriers(
        AllergenCatalog.SULPHITES, "Vinegars, jams, juices, pickles and dried fruit are often preserved with sulphites",
        "vinegar", "beer", "jam", "jelly", "marmalade", "canned vegetables", "coconut flakes",
        "shredded coconut", "guacamole", "dried mushroom", "dried mushrooms", "instant mashed potatoes",
        "juice concentrate", "fruit juice", "pickle", "pickles", "relish", "molasses", "soy sauce",
        "gravy mix", "shrimp", "hot sauce",
    )

    /** Every carrier term, every allergen. */
    val ALL: List<CheckLabelTerm> =
        DAIRY_TERMS + EGG_TERMS + FISH_TERMS + SHELLFISH_TERMS + MOLLUSC_TERMS + TREE_NUT_TERMS + PEANUT_TERMS +
            WHEAT_TERMS + GLUTEN_TERMS + SOY_TERMS + SESAME_TERMS + CELERY_TERMS + MUSTARD_TERMS +
            LUPIN_TERMS + SULPHITE_TERMS

    /** The terms for one allergen, matched on its lower-case key. */
    fun forAllergen(key: String): List<CheckLabelTerm> = ALL.filter { it.allergen == key.trim().lowercase() }

    /** The terms for the allergens in [keys]. */
    fun forAllergens(keys: Set<String>): List<CheckLabelTerm> = ALL.filter { it.allergen in keys }

    /** First term in [terms] found as whole words in [text]; pure, for list rows. */
    fun firstHit(terms: List<CheckLabelTerm>, text: String?): CheckLabelTerm? =
        if (text.isNullOrBlank()) null else terms.firstOrNull { it.findIn(text) != null }

    /** The reference screen's list, grouped by the reason shown with each. */
    fun termsByReason(terms: List<CheckLabelTerm> = DAIRY_TERMS): Map<String, List<String>> =
        terms.groupBy({ it.reason }, { it.phrase })
}
