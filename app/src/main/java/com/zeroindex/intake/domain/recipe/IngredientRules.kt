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

    private val CAN_411 = mapOf("can" to 411.0, "jar" to 411.0, "" to 411.0)
    private val CAN_425 = mapOf("can" to 425.0, "" to 425.0)

    val ALL: List<Rule> = listOf(
        rule("""\b(zucchini|zucchinis|courgettes?)\b""", "zucchini includes skin raw", mapOf("small" to 118.0, "medium" to 196.0, "large" to 323.0, "" to 196.0)),
        rule("""\b(cherry|grape)\s+tomato""", "tomatoes grape raw", mapOf("" to 17.0)),
        rule("""\btomato\s+paste\b""", "tomato products canned paste", mapOf("can" to 170.0, "" to 170.0), 262.0),
        rule("""\btomato\s+(sauce|puree|purée)\b""", "tomato products canned sauce", mapOf("can" to 425.0, "jar" to 680.0, "" to 425.0)),
        // A can of tomatoes: crushed and stewed have their own foods; every other canned form (diced, whole peeled) is the plain
        // canned food. A standard 14.5 oz can is 411 g; a size on the line ("28 oz") overrides it before this is read.
        rule("""\bcrushed\s+tomato|\bcrushed\b.*\btomato""", "tomatoes crushed canned", CAN_411),
        rule("""\bstewed\s+tomato""", "tomatoes red ripe canned stewed", CAN_411),
        rule(
            """\b(can|canned|jar)\b.*\btomato|\btomato(es)?\b.*\b(canned|peeled)\b|\bwhole\s+peeled\b.*\btomato""",
            "tomatoes canned red ripe diced", CAN_411,
        ),
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
        // Stock and broth: the kind on the line decides. A line that only says "stock" gets the everyday chicken broth,
        // never beef, which USDA lists first.
        rule("""\b(vegetable|veggie)\s+(stock|broth|bouillon|base)\b""", "soup vegetable broth ready serve"),
        rule("""\b(beef|bone)\s+(stock|broth|bouillon)\b""", "soup stock beef home-prepared"),
        rule("""\b(fish|seafood)\s+(stock|broth)\b""", "soup stock fish home-prepared"),
        rule("""\bchicken\s+(stock|broth|bouillon)\b""", "soup chicken broth ready-to-serve"),
        rule("""\b(stock|broth|bouillon)\b""", "soup chicken broth ready-to-serve"),
        // Milk and dairy: cow's milk, whole unless the line says otherwise; never sheep, goat or buffalo unless named.
        rule("""\bcoconut\s+(milk|cream)\b""", "nuts coconut milk canned", mapOf("can" to 400.0, "" to 400.0)),
        rule("""\balmond\s+milk\b""", "beverages almond milk unsweetened", gramsPerCup = 245.0),
        rule("""\bbuttermilk\b""", "milk buttermilk fluid whole", gramsPerCup = 245.0),
        rule("""\bcondensed\s+milk\b""", "milk canned condensed sweetened", mapOf("can" to 397.0, "" to 397.0)),
        rule("""\bevaporated\s+milk\b""", "milk canned evaporated", mapOf("can" to 354.0, "" to 354.0)),
        rule("""\b(dry|powdered|dried)\s+milk\b|\bmilk\s+powder\b""", "milk dry nonfat"),
        rule("""\b(skim|nonfat|non-fat|fat[- ]free)\s+milk\b|\bmilk,?\s+(skim|nonfat)\b""", "milk nonfat fluid skim", gramsPerCup = 245.0),
        rule("""\b(1%|low[- ]?fat)\s+milk\b""", "milk lowfat milkfat fluid", gramsPerCup = 245.0),
        rule("""\b2%\s+milk\b|\breduced[- ]fat\s+milk\b|\bmilk,?\s+2%""", "milk reduced milkfat fluid", gramsPerCup = 245.0),
        rule("""\bsheep(?:'s)?\s+milk\b""", "milk sheep fluid", gramsPerCup = 245.0),
        rule("""\bgoat(?:'s)?\s+milk\b""", "milk goat fluid", gramsPerCup = 244.0),
        rule("""\bmilk\b(?!\s+chocolate)""", "milk whole 3.25%", gramsPerCup = 244.0),
        rule("""\b(heavy|whipping)\s+cream\b""", "cream fluid heavy whipping", gramsPerCup = 238.0),
        rule("""\bhalf[- ]and[- ]half\b""", "cream fluid half and half", gramsPerCup = 242.0),
        rule("""\bsour\s+cream\b""", "cream sour cultured", gramsPerCup = 230.0),
        rule("""\bcream\s+cheese\b""", "cheese cream", mapOf("package" to 227.0, "" to 227.0), 232.0),
        rule("""(?<!ice )\bcream\b(?!\s+(of|cheese))""", "cream fluid light whipping", gramsPerCup = 239.0),
        rule("""\bgreek\s+yogh?urt\b""", "yogurt greek plain lowfat", gramsPerCup = 245.0),
        rule("""\byogh?urt\b""", "yogurt plain whole milk", gramsPerCup = 245.0),
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
        rule("""\bsweet\s*potato(es)?\b|\byams?\b""", "sweet potato raw unprepared", mapOf("small" to 100.0, "medium" to 130.0, "large" to 180.0, "" to 130.0)),
        rule("""\bpotato(es)?\b""", "potatoes flesh skin raw", mapOf("small" to 170.0, "medium" to 213.0, "large" to 369.0, "" to 213.0)),
        rule("""\bpeanut\s+butter\b""", "peanut butter smooth"),
        rule("""\bunsalted\s+butter\b|\bbutter,?\s+unsalted\b|\bsweet\s+cream\s+butter\b""", "butter without salt"),
        rule("""(?<!almond )(?<!cocoa )(?<!apple )(?<!shea )\bbutter\b(?!\s*(milk|beans?|lettuce|squash|nut))""", "butter salted", mapOf("stick" to 113.0)),
        rule("""\bwhole[- ]?(wheat|grain)\s+flour\b""", "wheat flour whole-grain", gramsPerCup = 120.0),
        rule("""^(?!.*\b(almond|coconut|rice|oat|chickpea|corn|buckwheat|tapioca|self)\s*-?\s*(rising\s+)?flour).*\bflour\b""", "wheat flour white all-purpose enriched", gramsPerCup = 125.0),
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
        // Meat and fish: ground meats name their fat grade (USDA lists the leanest first); salmon is the farmed fillet.
        rule("""\bground\s+(beef|chuck|sirloin|round)\b|\bbeef,?\s+ground\b|\bminced\s+beef\b""", "beef ground 85% lean raw"),
        rule("""\bground\s+turkey\b""", "turkey ground raw"),
        rule("""\bground\s+pork\b|\bpork\s+mince\b""", "pork ground raw"),
        rule("""\bsalmon\b""", "fish salmon atlantic farmed raw"),
        rule("""\btuna\b""", "fish tuna light canned water drained", mapOf("can" to 142.0, "" to 142.0)),
        rule("""\btofu\b""", "tofu raw regular prepared calcium", mapOf("block" to 340.0, "package" to 340.0)),
        // Grains: rice and pasta are the dry food unless the line says cooked.
        rule("""\bcauliflower\s+rice\b""", "cauliflower raw"),
        rule("""\bcooked\b.*\bbrown\s+rice\b|\bbrown\s+rice\b.*\bcooked\b""", "rice brown cooked grain"),
        rule("""\bbrown\s+rice\b""", "rice brown raw grain"),
        rule("""\bcooked\b.*\brice\b|\brice\b.*\bcooked\b""", "rice white regular cooked enriched"),
        rule("""\brice\b(?!\s+(vinegar|flour|noodles?|wine|cakes?|paper|krispies|milk|bran|syrup))""", "rice white regular raw enriched"),
        rule("""\b(rolled\s+|old[- ]fashioned\s+|quick\s+)?oats?\b(?!\s+(milk|flour|bran))|\boatmeal\b""", "cereals oats regular dry"),
        rule("""\bquinoa\b""", "quinoa uncooked"),
        // Pulses: canned when the line says can, cooked when it says cooked, otherwise dry; a can is 15 oz (425 g).
        rule("""\b(can|canned|jar)\b.*\bblack\s+beans?\b|\bblack\s+beans?\b.*\b(canned|drained)\b""", "beans black turtle canned", CAN_425),
        rule("""\bcooked\b.*\bblack\s+beans?\b|\bblack\s+beans?\b.*\bcooked\b""", "beans black cooked boiled"),
        rule("""\bblack\s+beans?\b""", "beans black mature seeds raw"),
        rule("""\b(can|canned|jar)\b.*\bkidney\s+beans?\b|\bkidney\s+beans?\b.*\b(canned|drained)\b""", "beans kidney canned solids liquids", CAN_425),
        rule("""\bcooked\b.*\bkidney\s+beans?\b|\bkidney\s+beans?\b.*\bcooked\b""", "beans kidney cooked boiled"),
        rule("""\bkidney\s+beans?\b""", "beans kidney red raw"),
        rule("""\b(can|canned|jar)\b.*\b(chickpeas?|garbanzo)|\b(chickpeas?|garbanzo\s+beans?)\b.*\b(canned|drained)\b""", "chickpeas canned solids liquids", CAN_425),
        rule("""\bcooked\b.*\b(chickpeas?|garbanzo)|\b(chickpeas?|garbanzo\s+beans?)\b.*\bcooked\b""", "chickpeas cooked boiled"),
        rule("""\b(chickpeas?|garbanzo\s+beans?)\b""", "chickpeas raw"),
        rule("""\bcooked\b.*\blentils?\b|\blentils?\b.*\bcooked\b""", "lentils cooked boiled"),
        rule("""\blentils?\b""", "lentils raw"),
        rule("""\b(can|canned)\b.*\bcorn\b|\bcorn\b.*\bcanned\b""", "corn sweet yellow canned whole", mapOf("can" to 340.0, "" to 340.0)),
        // Everyday produce and bread with their usual weights.
        rule("""\bbananas?\b""", "bananas raw", mapOf("small" to 101.0, "medium" to 118.0, "large" to 136.0, "" to 118.0)),
        rule("""\bapples?\b(?!\s+(cider|juice|sauce|butter|vinegar))""", "apples raw with skin", mapOf("small" to 149.0, "medium" to 182.0, "large" to 223.0, "" to 182.0)),
        rule("""\bcelery\b(?!\s+(seed|salt))""", "celery raw", mapOf("stalk" to 40.0, "small" to 30.0, "medium" to 40.0, "large" to 64.0, "" to 40.0), 101.0),
        rule("""\bwhole[- ]?(wheat|grain)\s+bread\b""", "bread whole-wheat commercially prepared"),
        rule("""\bbread\b(?!\s*crumbs?)""", "bread white commercially prepared"),
    )

    fun find(description: String): Rule? = ALL.firstOrNull { it.pattern.containsMatchIn(description) }

    /**
     * The rule for a parsed line. A container word ("can", "jar") the reader took off the front is put back in front of the
     * name, so "a can of tomatoes" is read as the canned food.
     */
    fun find(a: IngredientAmount): Rule? {
        val text = a.description.ifEmpty { a.name }
        val container = a.unit?.takeIf { IngredientAmounts.isContainerUnit(it) }
        return find(if (container != null) "$container $text" else text)
    }
}
