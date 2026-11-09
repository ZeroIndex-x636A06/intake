package com.zeroindex.intake.domain.food

/**
 * A coarse picture for a food that has no photo (USDA and offline foods, or Open Food Facts with images off).
 * Matched on whole words in the name and category text, so "ice cream" is a sweet and "rice" is not. When nothing
 * matches the answer is [OTHER], which draws a plain plate: never a wrong picture.
 */
enum class FoodCategory { DAIRY, EGG, MEAT, FISH, FRUIT, VEGETABLE, GRAIN, SWEET, DRINK, NUT, OTHER;

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

        fun of(name: String, category: String = ""): FoodCategory {
            val tokens = "$name $category".lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()
            return words.firstOrNull { (_, set) -> tokens.any { it in set } }?.first ?: OTHER
        }
    }
}
