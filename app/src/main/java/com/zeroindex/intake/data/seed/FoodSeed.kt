package com.zeroindex.intake.data.seed

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SPEC §16 is blunt about the food database being the real gap: USDA covers
 * whole foods well and branded items poorly, and the fix is hand-entering
 * roughly forty regular items once. This seeds that forty so the app is useful
 * on first run instead of empty.
 *
 * All values per 100 g, which is how the Food table stores them (SPEC §4).
 */
@Singleton
class FoodSeed @Inject constructor(
    private val foodRepository: FoodRepository,
    private val clock: Clock,
) {
    /** Returns name -> food for the recipe seeder to reference. */
    suspend fun ensureStaples(): Map<String, FoodEntity> {
        val existing = foodRepository.all()
        if (existing.size >= STAPLES.size) return existing.associateBy { it.name }

        val byName = existing.associateBy { it.name }.toMutableMap()
        val now = clock.nowMillis()
        val toCreate = STAPLES.filter { it.name !in byName }.map { staple ->
            FoodEntity(
                id = newId(),
                name = staple.name,
                brand = null,
                servingSizeGrams = staple.servingGrams,
                servingLabel = staple.servingLabel,
                kcalPer100g = staple.kcal,
                proteinPer100g = staple.protein,
                carbsPer100g = staple.carbs,
                fatPer100g = staple.fat,
                fiberPer100g = staple.fiber,
                source = FoodSource.MANUAL,
                // Left blank deliberately: these are whole foods with no
                // hidden-allergen text, so the engine finds nothing and they
                // can earn a safe-food check honestly (SPEC §7.5).
                allergenTags = "",
                isVerified = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        foodRepository.saveAll(toCreate)
        toCreate.forEach { byName[it.name] = it }
        return byName
    }

    private data class Staple(
        val name: String,
        val servingGrams: Double,
        val servingLabel: String,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double = 0.0,
    )

    private companion object {
        val STAPLES = listOf(
            Staple("Chicken breast, raw", 150.0, "1 breast", 165.0, 31.0, 0.0, 3.6),
            Staple("Chicken thigh, boneless", 120.0, "1 thigh", 209.0, 26.0, 0.0, 10.9),
            Staple("Lean beef mince, 5%", 125.0, "125 g", 137.0, 21.0, 0.0, 5.0),
            Staple("Beef chuck, stewing", 150.0, "150 g", 217.0, 26.0, 0.0, 12.0),
            Staple("Pork loin", 150.0, "150 g", 143.0, 26.0, 0.0, 3.5),
            Staple("Salmon fillet", 130.0, "1 fillet", 208.0, 20.0, 0.0, 13.0),
            Staple("White fish fillet", 150.0, "1 fillet", 96.0, 21.0, 0.0, 1.0),
            Staple("Prawns, cooked", 100.0, "100 g", 99.0, 24.0, 0.2, 0.3),
            Staple("Turkey mince, 7%", 125.0, "125 g", 150.0, 22.0, 0.0, 7.0),
            Staple("Eggs", 50.0, "1 egg", 143.0, 12.6, 0.7, 9.5),
            Staple("Egg whites", 33.0, "1 white", 52.0, 11.0, 0.7, 0.2),
            Staple("Tofu, firm", 100.0, "100 g", 144.0, 17.0, 2.8, 8.7),
            Staple("Red lentils, dry", 60.0, "60 g", 352.0, 24.6, 63.0, 1.1, 10.7),
            Staple("Chickpeas, cooked", 120.0, "120 g", 164.0, 8.9, 27.4, 2.6, 7.6),
            Staple("Black beans, cooked", 120.0, "120 g", 132.0, 8.9, 23.7, 0.5, 8.7),
            Staple("White rice, dry", 75.0, "75 g", 365.0, 7.1, 80.0, 0.7, 1.3),
            Staple("Brown rice, dry", 75.0, "75 g", 370.0, 7.9, 77.2, 2.9, 3.5),
            Staple("Rolled oats", 50.0, "50 g", 389.0, 16.9, 66.3, 6.9, 10.6),
            Staple("Pasta, dry", 85.0, "85 g", 371.0, 13.0, 74.7, 1.5, 3.2),
            Staple("Potato", 200.0, "1 medium", 77.0, 2.0, 17.5, 0.1, 2.2),
            Staple("Sweet potato", 180.0, "1 medium", 86.0, 1.6, 20.1, 0.1, 3.0),
            Staple("Sourdough bread", 50.0, "1 slice", 270.0, 11.0, 50.0, 1.8, 2.4),
            Staple("Tortilla, flour", 45.0, "1 tortilla", 310.0, 8.0, 51.0, 7.0, 3.0),
            Staple("Broccoli", 100.0, "100 g", 34.0, 2.8, 6.6, 0.4, 2.6),
            Staple("Spinach", 80.0, "80 g", 23.0, 2.9, 3.6, 0.4, 2.2),
            Staple("Bell pepper", 120.0, "1 pepper", 31.0, 1.0, 6.0, 0.3, 2.1),
            Staple("Onion", 110.0, "1 onion", 40.0, 1.1, 9.3, 0.1, 1.7),
            Staple("Garlic", 5.0, "1 clove", 149.0, 6.4, 33.1, 0.5, 2.1),
            Staple("Carrot", 80.0, "1 carrot", 41.0, 0.9, 9.6, 0.2, 2.8),
            Staple("Courgette", 150.0, "1 courgette", 17.0, 1.2, 3.1, 0.3, 1.0),
            Staple("Tomatoes, tinned", 200.0, "half a tin", 32.0, 1.6, 7.0, 0.3, 1.5),
            Staple("Mushrooms", 100.0, "100 g", 22.0, 3.1, 3.3, 0.3, 1.0),
            Staple("Avocado", 100.0, "half", 160.0, 2.0, 8.5, 14.7, 6.7),
            Staple("Banana", 120.0, "1 banana", 89.0, 1.1, 22.8, 0.3, 2.6),
            Staple("Apple", 180.0, "1 apple", 52.0, 0.3, 13.8, 0.2, 2.4),
            Staple("Blueberries", 100.0, "100 g", 57.0, 0.7, 14.5, 0.3, 2.4),
            Staple("Olive oil", 14.0, "1 tbsp", 884.0, 0.0, 0.0, 100.0),
            Staple("Peanut butter", 32.0, "2 tbsp", 588.0, 25.1, 19.6, 50.4, 6.0),
            Staple("Almonds", 28.0, "28 g", 579.0, 21.2, 21.6, 49.9, 12.5),
            Staple("Coconut milk, tinned", 100.0, "100 ml", 197.0, 2.0, 2.8, 21.3),
            Staple("Soy sauce", 15.0, "1 tbsp", 53.0, 8.1, 4.9, 0.6),
            Staple("Honey", 21.0, "1 tbsp", 304.0, 0.3, 82.4, 0.0),
        )
    }
}
