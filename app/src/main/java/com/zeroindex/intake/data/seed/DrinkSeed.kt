package com.zeroindex.intake.data.seed

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.remote.openfoodfacts.FoodIngredients
import com.zeroindex.intake.domain.drinks.DrinkCategory
import com.zeroindex.intake.domain.drinks.DrinkKinds
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.model.FoodSource

/**
 * The generic drinks every install starts with. Deterministic: each id comes from [seedId] of its name, and every row is
 * stamped [SEED_TIMESTAMP], so two phones that seed independently create the same rows and sync merges them.
 *
 * All values are per 100 ml (a millilitre counted as a gram), typical published figures for the generic drink, rounded.
 * Caffeine is per 100 ml, strength is % by volume, and the hydration factor follows `docs/HYDRATION.md` (where the
 * plant milks are set to 1.0 rather than the 1.5 the study found for dairy milk, because no one measured them).
 */
object DrinkSeed {

    class Spec(
        val name: String,
        val category: DrinkCategory,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val sugar: Double,
        val fiber: Double = 0.0,
        val sodiumMg: Double = 0.0,
        val saturatedFat: Double = 0.0,
        val caffeineMg: Double = 0.0,
        val abv: Double = 0.0,
        /** The hydration factor to store; null uses the drink type's default. */
        val factor: Double? = null,
        val servingMl: Double = 240.0,
        val servingLabel: String = "1 cup (240 ml)",
        /** Structured allergen tags (`en:milk`) and ingredient text, joined the way a packaged food's are. */
        val allergenTags: List<String> = emptyList(),
        val ingredients: String? = null,
    ) {
        val type: DrinkType get() = DrinkKinds.typeOf(category, name, abv.takeIf { it > 0 })
    }

    val SPECS: List<Spec> = listOf(
        Spec("Water", DrinkCategory.WATER, 0.0, 0.0, 0.0, 0.0, 0.0, servingMl = 250.0, servingLabel = "1 glass (250 ml)"),
        Spec("Sparkling water", DrinkCategory.WATER, 0.0, 0.0, 0.0, 0.0, 0.0, sodiumMg = 2.0, servingMl = 355.0, servingLabel = "1 can (355 ml)"),
        Spec("Coffee, black", DrinkCategory.COFFEE, 1.0, 0.1, 0.0, 0.0, 0.0, sodiumMg = 2.0, caffeineMg = 40.0),
        Spec(
            "Latte (made with milk)", DrinkCategory.COFFEE, 42.0, 2.1, 3.3, 2.2, 3.3, sodiumMg = 25.0, saturatedFat = 1.4, caffeineMg = 25.0,
            servingMl = 355.0, servingLabel = "1 medium (355 ml)", allergenTags = listOf("en:milk"), ingredients = "Espresso, steamed whole milk",
        ),
        Spec("Black tea", DrinkCategory.TEA, 1.0, 0.0, 0.3, 0.0, 0.0, sodiumMg = 3.0, caffeineMg = 20.0),
        Spec("Green tea", DrinkCategory.TEA, 1.0, 0.0, 0.0, 0.0, 0.0, sodiumMg = 1.0, caffeineMg = 12.0),
        Spec("Cola", DrinkCategory.SODA, 42.0, 0.0, 10.6, 0.0, 10.6, sodiumMg = 4.0, caffeineMg = 9.6, servingMl = 355.0, servingLabel = "1 can (355 ml)"),
        Spec("Diet cola", DrinkCategory.SODA, 0.0, 0.1, 0.0, 0.0, 0.0, sodiumMg = 12.0, caffeineMg = 12.0, servingMl = 355.0, servingLabel = "1 can (355 ml)"),
        Spec("Lemon-lime soda", DrinkCategory.SODA, 41.0, 0.0, 10.4, 0.0, 10.2, sodiumMg = 12.0, servingMl = 355.0, servingLabel = "1 can (355 ml)"),
        Spec("Orange juice", DrinkCategory.JUICE, 45.0, 0.7, 10.4, 0.2, 8.4, fiber = 0.2, sodiumMg = 1.0),
        Spec("Apple juice", DrinkCategory.JUICE, 46.0, 0.1, 11.3, 0.1, 9.6, sodiumMg = 4.0),
        Spec("Sports drink", DrinkCategory.SPORTS_ENERGY, 25.0, 0.0, 6.2, 0.0, 5.6, sodiumMg = 41.0, servingMl = 355.0, servingLabel = "1 bottle (355 ml)"),
        Spec("Energy drink", DrinkCategory.SPORTS_ENERGY, 45.0, 0.0, 11.0, 0.0, 11.0, sodiumMg = 40.0, caffeineMg = 32.0, servingMl = 250.0, servingLabel = "1 can (250 ml)"),
        Spec(
            "Milk, whole", DrinkCategory.MILK, 61.0, 3.2, 4.8, 3.3, 4.8, sodiumMg = 43.0, saturatedFat = 1.9, allergenTags = listOf("en:milk"),
            ingredients = "Whole milk",
        ),
        // Plant milks: the name alone gives the allergen engine's check-the-label note (oat, almond), exactly as before.
        Spec("Oat milk, unsweetened", DrinkCategory.MILK, 43.0, 1.0, 6.7, 1.5, 4.0, fiber = 0.8, sodiumMg = 40.0, saturatedFat = 0.2, factor = 1.0),
        Spec("Almond milk, unsweetened", DrinkCategory.MILK, 15.0, 0.6, 0.3, 1.2, 0.0, fiber = 0.2, sodiumMg = 70.0, saturatedFat = 0.1, factor = 1.0),
        Spec("Beer", DrinkCategory.ALCOHOL, 43.0, 0.5, 3.6, 0.0, 0.0, sodiumMg = 4.0, abv = 5.0, servingMl = 355.0, servingLabel = "1 bottle (355 ml)"),
        Spec(
            "Wine", DrinkCategory.ALCOHOL, 83.0, 0.1, 2.6, 0.0, 0.6, sodiumMg = 4.0, abv = 12.0, servingMl = 150.0, servingLabel = "1 glass (150 ml)",
            ingredients = "Contains sulphites",
        ),
        Spec("Spirits (40%)", DrinkCategory.ALCOHOL, 231.0, 0.0, 0.0, 0.0, 0.0, abv = 40.0, servingMl = 44.0, servingLabel = "1 shot (44 ml)"),
    )

    fun idFor(name: String): String = seedId("drink", name)

    /** The seeded drink that stands for a hydration drink type when a legacy log (a Today preset) has no food of its own. */
    fun nameForType(type: DrinkType): String? = when (type) {
        DrinkType.WATER -> "Water"
        DrinkType.SPARKLING -> "Sparkling water"
        DrinkType.TEA -> "Black tea"
        DrinkType.COFFEE -> "Coffee, black"
        DrinkType.MILK -> "Milk, whole"
        DrinkType.JUICE -> "Orange juice"
        DrinkType.SODA -> "Cola"
        DrinkType.SPORTS_DRINK -> "Sports drink"
        DrinkType.BEER -> "Beer"
        DrinkType.WINE -> "Wine"
        DrinkType.SPIRITS -> "Spirits (40%)"
        DrinkType.ORS, DrinkType.CUSTOM -> null
    }

    fun foods(): List<FoodEntity> = SPECS.map { s ->
        FoodEntity(
            id = idFor(s.name),
            name = s.name,
            servingSizeGrams = s.servingMl,
            servingLabel = s.servingLabel,
            kcalPer100g = s.kcal,
            proteinPer100g = s.protein,
            carbsPer100g = s.carbs,
            fatPer100g = s.fat,
            fiberPer100g = s.fiber,
            sugarPer100g = s.sugar,
            sodiumPer100g = s.sodiumMg,
            saturatedFatPer100g = s.saturatedFat,
            source = FoodSource.DATABASE,
            allergenTags = FoodIngredients.join(s.allergenTags, s.ingredients),
            isVerified = true,
            drinkCategory = s.category.name,
            caffeineMgPer100ml = s.caffeineMg,
            abvPercent = s.abv.takeIf { it > 0 },
            hydrationFactor = s.factor ?: Drinks.defaultFactor(s.type, s.abv),
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        )
    }
}
