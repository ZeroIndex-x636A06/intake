package com.zeroindex.intake.domain.drinks

import com.zeroindex.intake.domain.hydration.DrinkType

/**
 * The nine drink categories the Drinks page browses (Nutrition > Drinks). A drink is an ordinary food row with
 * `drinkCategory` set to one of these names; everything else about it (per-100 ml nutrition, allergens, search)
 * is the food database's.
 *
 * [fallbackType] is the hydration drink type used when a drink has no more specific one (Water splits into
 * Sparkling by name, Alcohol into Beer, Wine or Spirits, see [DrinkKinds]).
 */
enum class DrinkCategory(val label: String, val fallbackType: DrinkType) {
    WATER("Water", DrinkType.WATER),
    COFFEE("Coffee", DrinkType.COFFEE),
    TEA("Tea", DrinkType.TEA),
    SODA("Soda", DrinkType.SODA),
    JUICE("Juice", DrinkType.JUICE),
    SPORTS_ENERGY("Sports & energy", DrinkType.SPORTS_DRINK),
    MILK("Milk & alternatives", DrinkType.MILK),
    ALCOHOL("Alcohol", DrinkType.BEER),
    OTHER("Other", DrinkType.CUSTOM),
    ;

    companion object {
        /** Reads the stored name; anything unknown is [OTHER] rather than a crash, so a newer backup still opens. */
        fun fromStored(name: String?): DrinkCategory? =
            name?.takeIf { it.isNotBlank() }?.let { n -> entries.firstOrNull { it.name == n } ?: OTHER }
    }
}

/** Which hydration drink type (and so which factor, caffeine and strength defaults) a drink food maps to. */
object DrinkKinds {
    fun typeOf(category: DrinkCategory, name: String, abvPercent: Double?): DrinkType {
        val n = name.lowercase()
        return when (category) {
            DrinkCategory.WATER ->
                if (listOf("sparkling", "seltzer", "mineral", "club soda", "carbonated").any { it in n }) DrinkType.SPARKLING else DrinkType.WATER
            DrinkCategory.ALCOHOL -> when {
                listOf("beer", "lager", "stout", "ale", "cider").any { it in n } -> DrinkType.BEER
                listOf("wine", "champagne", "prosecco", "sake").any { it in n } -> DrinkType.WINE
                (abvPercent ?: 0.0) >= 15.0 -> DrinkType.SPIRITS
                (abvPercent ?: 0.0) >= 8.0 -> DrinkType.WINE
                abvPercent != null && abvPercent > 0 -> DrinkType.BEER
                else -> DrinkType.SPIRITS
            }
            else -> category.fallbackType
        }
    }
}

/**
 * Deterministic beverage rules (no AI, no network) for foods that arrive from USDA or Open Food Facts, so they can
 * be browsed under Drinks. [categorize] returns null for something that is not a drink to log by volume: a dry
 * mix, a milk chocolate bar, cheese made from milk.
 *
 * Three ideas keep it honest:
 *  - powders, mixes, concentrates and dairy or sweet foods that merely mention a drink word are never drinks;
 *  - an ambiguous word (water, milk, tea, coffee, juice) counts only near the start of a name, because USDA names
 *    put the thing first ("Tea, black, brewed") and put preparations last ("Beans, canned, in water");
 *  - when the source itself says it is a beverage (USDA group "Beverages", an Open Food Facts `en:beverages` tag) the
 *    words count anywhere, and an unmatched beverage is [DrinkCategory.OTHER].
 */
object DrinkCategorizer {

    /**
     * Words that mean "not something to drink by the glass", whatever else the name or the source says: dry forms,
     * sweets and dairy foods, and the liquids people cook with rather than drink (soups, broths, oils, sauces).
     */
    private val notDrink = setOf(
        "powder", "powdered", "mix", "mixes", "dry", "dried", "concentrate", "concentrated", "crystals", "tablets",
        "bar", "bars", "cookie", "cookies", "cake", "cakes", "candy", "pudding", "gum", "cheese", "yogurt", "yoghurt",
        "cottage", "ice", "cream", "creamer", "whitener", "sauce", "soup", "bread", "cereal", "granola", "pie", "dessert", "baking", "leavening",
        "soups", "sauces", "broth", "broths", "stock", "stocks", "bouillon", "consomme", "gravy", "dressing", "vinegar",
        "oil", "oils", "condensed", "evaporated", "biscuit", "biscuits", "cracker", "crackers", "chestnut", "chestnuts",
        "melon", "beans", "grounds", "leaves", "jelly", "jam",
    )

    /** Drinks whose usual name holds one of the [notDrink] words: read as the drink before the exclusions are checked. */
    private val drinkPhrases = listOf("cream soda" to "soda", "ice tea" to "tea", "ice coffee" to "coffee", "ice water" to "water", "ice cold" to " ")

    private val rootBeerLike = listOf("root beer", "ginger beer", "ginger ale", "cream soda", "birch beer", "non-alcoholic", "alcohol-free", "alcohol free")

    private val alcohol = setOf(
        "beer", "beers", "lager", "stout", "porter", "wine", "wines", "vodka", "whiskey", "whisky", "gin", "rum", "tequila",
        "brandy", "liqueur", "champagne", "prosecco", "sake", "daiquiri", "margarita", "alcoholic", "bourbon", "cognac",
    )

    private val sportsEnergyAnywhere = setOf("gatorade", "powerade", "lucozade", "electrolyte", "isotonic", "bodyarmor", "redbull")
    private val soda = setOf("soda", "sodas", "cola", "colas", "pepsi", "coke", "sprite", "fanta", "lemonade", "limeade", "tonic", "carbonated")
    private val waterPhrases = listOf("club soda", "seltzer", "sparkling water", "mineral water", "spring water", "drinking water")
    private val coffee = setOf("coffee", "espresso", "latte", "cappuccino", "americano", "macchiato", "mocha", "cortado")
    private val tea = setOf("tea", "teas", "chai", "matcha")
    private val juice = setOf("juice", "juices", "nectar")
    private val milk = setOf("milk", "milks", "buttermilk", "kefir", "soymilk")

    /** Open Food Facts category tags that name a kind of drink, most specific first. */
    private val offTagRules: List<Pair<DrinkCategory, List<String>>> = listOf(
        DrinkCategory.ALCOHOL to listOf("en:alcoholic-beverages", "en:beers", "en:wines", "en:spirits", "en:liquors", "en:ciders", "en:cocktails"),
        DrinkCategory.SPORTS_ENERGY to listOf("en:energy-drinks", "en:sports-drinks", "en:isotonic-drinks"),
        DrinkCategory.WATER to listOf("en:waters", "en:mineral-waters", "en:spring-waters", "en:sparkling-waters"),
        DrinkCategory.SODA to listOf("en:sodas", "en:carbonated-drinks", "en:colas", "en:lemonades", "en:sweetened-beverages-carbonated"),
        DrinkCategory.JUICE to listOf("en:fruit-juices", "en:juices-and-nectars", "en:vegetable-juices", "en:fruit-based-beverages", "en:nectars"),
        DrinkCategory.TEA to listOf("en:teas", "en:iced-teas", "en:tea-based-beverages", "en:herbal-teas", "en:tea-bags"),
        DrinkCategory.COFFEE to listOf("en:coffee-beverages", "en:coffee-drinks", "en:iced-coffees", "en:instant-coffee-drinks"),
        DrinkCategory.MILK to listOf("en:milks", "en:plant-based-milks", "en:dairy-drinks", "en:plant-based-beverages", "en:oat-milks", "en:almond-milks", "en:soy-milks"),
    )

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * @param name the food's name.
     * @param category a source category text (USDA food group such as "Beverages"); may be blank.
     * @param offTags Open Food Facts `categories_tags` such as `en:sodas`; may be empty.
     */
    fun categorize(name: String, category: String = "", offTags: List<String> = emptyList()): DrinkCategory? {
        val lowerName = name.lowercase()
        val all = tokens(name)
        if (all.isEmpty()) return null
        val forExclusion = tokens(drinkPhrases.fold(lowerName) { text, (phrase, plain) -> text.replace(phrase, plain) })
        if (forExclusion.any { it in notDrink } || "milk chocolate" in lowerName) return null

        val tags = offTags.map { it.lowercase().trim() }
        val sourceSaysBeverage = tags.any { it == "en:beverages" || it == "en:beverages-and-beverages-preparations" } ||
            category.contains("beverage", ignoreCase = true)

        // A source tag is the strongest statement, ahead of any wording in the name.
        if (tags.isNotEmpty()) {
            offTagRules.forEach { (cat, list) -> if (tags.any { it in list }) return cat }
        }

        // Leading words only, so "Beans, canned, in water" is not a water. "Beverages" and "alcoholic" are not the thing.
        val head = all.filterNot { it == "beverages" || it == "beverage" }.take(3)
        val head4 = all.filterNot { it == "beverages" || it == "beverage" }.take(4)
        fun near(words: Set<String>, h: List<String> = head) = h.any { it in words }
        fun anywhere(words: Set<String>) = all.any { it in words }

        if (rootBeerLike.any { it in lowerName }) return DrinkCategory.SODA
        if (waterPhrases.any { it in lowerName }) return DrinkCategory.WATER
        if (anywhere(alcohol) && (head4.any { it in alcohol } || sourceSaysBeverage || "alcoholic" in all)) return DrinkCategory.ALCOHOL
        if ("energy" in all && ("drink" in all || "shot" in all || "drinks" in all)) return DrinkCategory.SPORTS_ENERGY
        if ("sports" in all && ("drink" in all || "drinks" in all)) return DrinkCategory.SPORTS_ENERGY
        if (anywhere(sportsEnergyAnywhere) || ("red" in all && "bull" in all)) return DrinkCategory.SPORTS_ENERGY
        if (anywhere(soda) || ("soft" in all && "drink" in all) || ("dr" in all && "pepper" in all)) return DrinkCategory.SODA
        if (near(juice, head4) || (sourceSaysBeverage && anywhere(juice))) return DrinkCategory.JUICE
        if (near(coffee) || (sourceSaysBeverage && anywhere(coffee))) return DrinkCategory.COFFEE
        if (near(tea) || (sourceSaysBeverage && anywhere(tea))) return DrinkCategory.TEA
        if (near(milk) || (sourceSaysBeverage && anywhere(milk)) || ("oat" in head && "drink" in all)) return DrinkCategory.MILK
        if ("water" in head || "waters" in head) return if ("coconut" in all) DrinkCategory.OTHER else DrinkCategory.WATER
        if (sourceSaysBeverage || "smoothie" in all || "shake" in all || "kombucha" in all || "drink" in head4 || "ready" in all && "drink" in all) {
            return DrinkCategory.OTHER
        }
        return null
    }

}

/** Which of the two searches a result list is for: Log food lists foods only, Drinks lists drinks only. */
enum class SearchScope {
    FOODS,
    DRINKS,
    ;

    /** True when a row that [isDrink] (or is not) belongs in this search. */
    fun keeps(isDrink: Boolean): Boolean = (this == DRINKS) == isDrink
}

/**
 * The one "is it a drink" rule, used by both searches (Log food and Drinks) and by the copy to the phone.
 *
 *  - A food already on the phone is a drink exactly when its stored `drinkCategory` is set: that is what the Drinks
 *    page lists and what the drink flow logs, and the person can change it.
 *  - A result from a source (the bundled USDA database, Open Food Facts, the USDA API) is judged by
 *    [DrinkCategorizer]: the source's own category first (USDA "Beverages", Open Food Facts `en:beverages` and the
 *    more specific drink tags), and the name rules when the source gives none. Soups, broths, oils and sauces are
 *    never drinks; milk and plant milks are.
 */
object DrinkRule {

    /** The drink category of a source result, or null when it is a food. */
    fun categoryOf(name: String, sourceCategory: String = "", offTags: List<String> = emptyList()): DrinkCategory? =
        DrinkCategorizer.categorize(name, sourceCategory, offTags)

    fun isDrink(name: String, sourceCategory: String = "", offTags: List<String> = emptyList()): Boolean =
        categoryOf(name, sourceCategory, offTags) != null

    /** For a food row (on the phone, or already mapped from a source): the stored category decides. */
    fun isStoredDrink(drinkCategory: String?): Boolean = DrinkCategory.fromStored(drinkCategory) != null
}
