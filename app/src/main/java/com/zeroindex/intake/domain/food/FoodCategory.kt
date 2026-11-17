package com.zeroindex.intake.domain.food

/**
 * A coarse picture for a food that has no photo (USDA and offline foods, or Open Food Facts with images off).
 * Matched on whole words in the name and category text, so "ice cream" is a sweet and "rice" is not. When nothing
 * matches the answer is [OTHER], which draws a plain plate: never a wrong picture.
 */
enum class FoodCategory { DAIRY, EGG, MEAT, FISH, FRUIT, VEGETABLE, GRAIN, SWEET, DRINK, NUT, SUPPLEMENT, OTHER;

    companion object {
        private val words: List<Pair<FoodCategory, Set<String>>> = listOf(
            SWEET to setOf("candy", "chocolate", "cookie", "cookies", "cake", "dessert", "sweet", "sweets", "biscuit", "biscuits", "sugar", "honey", "jam", "ice", "sorbet", "brownie", "donut", "pastry", "spread"),
            DRINK to setOf("juice", "soda", "cola", "coffee", "tea", "beer", "wine", "water", "drink", "beverage", "smoothie", "lemonade", "shake"),
            DAIRY to setOf("milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "kefir", "skyr", "cheddar", "mozzarella", "dairy"),
            EGG to setOf("egg", "eggs", "omelette", "omelet"),
            FISH to setOf("fish", "salmon", "tuna", "cod", "shrimp", "prawn", "sardine", "sardines", "trout", "seafood", "haddock", "mackerel"),
            MEAT to setOf("beef", "pork", "chicken", "turkey", "lamb", "ham", "bacon", "sausage", "steak", "meat", "mince", "burger"),
            NUT to setOf("almond", "almonds", "peanut", "peanuts", "walnut", "walnuts", "cashew", "cashews", "nut", "nuts", "seeds", "pistachio", "hazelnut"),
            FRUIT to setOf("apple", "banana", "orange", "berries", "berry", "strawberry", "blueberry", "grape", "grapes", "mango", "pear", "peach", "lemon", "melon", "fruit", "cherry", "pineapple"),
            VEGETABLE to setOf("carrot", "broccoli", "spinach", "lettuce", "tomato", "tomatoes", "onion", "potato", "potatoes", "pepper", "cucumber", "salad", "vegetable", "vegetables", "beans", "peas", "cabbage", "mushroom", "mushrooms"),
            GRAIN to setOf("bread", "rice", "pasta", "oats", "oat", "cereal", "flour", "noodle", "noodles", "wheat", "bagel", "tortilla", "granola", "muesli", "quinoa", "couscous", "toast"),
        )

        /**
         * Phrases that decide before the words do (QA Q1-25): "sweet potato" and "peppers, sweet" are vegetables, not
         * cookies, and "peanut butter" is a nut, not a dairy spread.
         */
        private val phrases: List<Pair<Regex, FoodCategory>> = listOf(
            Regex("""\b(?:sweet potato(?:es)?|sweet corn|sweet peppers?|peppers?,? sweet|yams?)\b""") to VEGETABLE,
            // Whey and protein powders (QA Q1-25): a gym-style icon, not a plate. "Protein shake" is a powder-style product too.
            Regex("""\b(?:whey|casein|protein (?:powders?|isolates?|concentrates?|shakes?|supplements?)|(?:pea|soy|plant|rice|egg white) protein (?:powders?|isolates?))\b""") to SUPPLEMENT,
            Regex("""\b(?:peanut|almond|cashew|hazelnut|nut|sunflower seed) butters?\b""") to NUT,
        )

        fun of(name: String, category: String = ""): FoodCategory {
            val text = "$name $category".lowercase()
            phrases.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.let { return it.second }
            val tokens = text.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
            return words.firstOrNull { (_, set) -> tokens.any { it in set } }?.first ?: OTHER
        }
    }
}
