package com.zeroindex.intake.domain.allergen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The rules are about concepts (role, share, count, defining dish), so most cases are not the owner's two examples. */
class SubstitutionsTest {

    private fun scan(avoided: Set<String>, name: String?, text: String?) = AllergenMatcher.scan(
        aliases = AllergenCatalog.aliases().filter { it.allergen in avoided },
        avoidedAllergens = avoided,
        name = name,
        ingredients = text,
        checkLabelInIngredients = true,
    )

    private fun assess(title: String, lines: List<String>, vararg avoid: String): Adaptation {
        val avoided = avoid.toSet()
        return Substitutions.assess(
            title = title,
            ingredients = lines,
            avoided = avoided,
            lineScans = lines.map { scan(avoided, null, it) },
            overall = scan(avoided, title, lines.joinToString("\n")),
        )
    }

    private fun adaptable(a: Adaptation) = a as Adaptation.Adaptable
    private fun refused(a: Adaptation) = a as Adaptation.NotAdaptable

    private val pastaBase = listOf(
        "12 oz penne pasta", "2 cups cherry tomatoes", "3 cloves garlic, minced", "2 tablespoons olive oil",
        "1 lb chicken breast", "1 teaspoon salt", "1 cup spinach", "1/2 cup chopped basil",
    )

    @Test fun `no blocking allergen means already fine`() {
        assertThat(assess("Tomato pasta", pastaBase, "dairy")).isEqualTo(Adaptation.AlreadyFine)
    }

    @Test fun `a pasta with a parmesan topping is adaptable`() {
        val a = adaptable(assess("Chicken tomato pasta", pastaBase + "1/4 cup grated parmesan cheese", "dairy"))
        assertThat(a.swaps).hasSize(1)
        assertThat(a.swaps.single().summary).isEqualTo("Swap parmesan cheese → vegan parmesan")
        assertThat(a.adaptedIngredients).contains("1/4 cup grated vegan parmesan")
        assertThat(a.confidence).isEqualTo(SwapConfidence.HIGH)
    }

    @Test fun `a cheddar topping on chili is adaptable and keeps the rest of the line`() {
        val a = adaptable(
            assess(
                "Beef chili",
                listOf("1 lb ground beef", "2 cans (14 oz) kidney beans", "1 can (28 oz) crushed tomatoes", "1 onion", "2 tbsp chili powder", "1/2 cup shredded cheddar cheese"),
                "dairy",
            ),
        )
        assertThat(a.adaptedIngredients.last()).isEqualTo("1/2 cup shredded vegan cheddar")
    }

    @Test fun `mac and cheese is refused because cheese is the dish`() {
        val a = refused(
            assess(
                "Mac and Cheese",
                listOf("1 lb macaroni", "2 cups shredded cheddar", "2 cups milk", "2 tbsp butter"),
                "dairy",
            ),
        )
        assertThat(a.reason).contains("dairy dish")
    }

    @Test fun `a cheesecake is refused`() {
        val a = refused(
            assess(
                "New York cheesecake",
                listOf("2 cups graham crackers", "24 oz cream cheese", "1 cup sugar", "1 cup sour cream"),
                "dairy",
            ),
        )
        assertThat(a.reasons.joinToString()).contains("dairy dish")
    }

    @Test fun `tres leches is refused by its title even when the lines are few`() {
        val a = refused(assess("Tres Leches Cake", listOf("1 cup flour", "1 can (12 oz) evaporated milk", "1 cup sugar"), "dairy"))
        assertThat(a.reasons.joinToString()).contains("dairy dish")
    }

    @Test fun `the same concept refuses a custard, an alfredo and a flan not just those names`() {
        for (t in listOf("Vanilla custard", "Chicken alfredo", "Caramel flan", "Banana pudding")) {
            val a = assess(t, listOf("2 cups milk", "1 cup sugar", "1 tsp vanilla"), "dairy")
            assertThat(a).isInstanceOf(Adaptation.NotAdaptable::class.java)
        }
    }

    @Test fun `four dairy lines is too many`() {
        val a = refused(
            assess(
                "Weeknight chicken bake",
                listOf("1 lb chicken", "2 tbsp butter", "1/4 cup milk", "2 tbsp sour cream", "1/4 cup shredded cheddar", "2 cups broccoli", "1 cup rice", "1 onion", "2 cups stock"),
                "dairy",
            ),
        )
        assertThat(a.reasons.joinToString()).contains("4 ingredient lines")
    }

    @Test fun `three dairy lines in a large dish still adapt`() {
        val a = adaptable(
            assess(
                "Weeknight chicken bake",
                listOf("2 lb chicken", "1 tbsp butter", "1/4 cup milk", "1/4 cup shredded cheddar", "4 cups broccoli", "2 cups rice", "1 onion", "2 cups stock"),
                "dairy",
            ),
        )
        assertThat(a.swaps).hasSize(3)
    }

    @Test fun `a cream sauce at a modest share is borderline, adaptable with a warning`() {
        val a = adaptable(
            assess(
                "Chicken in a cream sauce",
                listOf("1 lb chicken thighs", "1 cup heavy cream", "1 onion", "1 cup chicken stock", "2 tbsp olive oil"),
                "dairy",
            ),
        )
        assertThat(a.confidence).isEqualTo(SwapConfidence.LOW)
        assertThat(a.caveats.joinToString()).contains("not taste or feel the same")
        assertThat(a.swaps.single().to).isEqualTo("coconut cream")
    }

    @Test fun `a cream sauce that is most of the dish is refused`() {
        val a = refused(assess("Creamy soup", listOf("3 cups heavy cream", "1 onion", "1 cup stock"), "dairy"))
        assertThat(a.reasons.joinToString()).containsMatch("(base|main ingredient)")
    }

    @Test fun `a cake with eggs as structure is refused`() {
        val a = refused(
            assess(
                "Vanilla layer cake",
                listOf("4 large eggs", "2 cups sugar", "1/2 cup vegetable oil", "1 tsp vanilla", "2 tsp baking powder", "1 cup water"),
                "egg",
            ),
        )
        assertThat(a.reasons.joinToString()).containsMatch("(structure|baked)")
    }

    @Test fun `an untitled bake with three eggs is still refused by baking chemistry`() {
        val a = refused(
            assess(
                "Lemon squares",
                listOf("3 eggs", "1 cup sugar", "1 cup flour", "2 tsp baking powder", "1 cup oil", "1 lemon"),
                "egg",
            ),
        )
        assertThat(a.reasons.joinToString()).contains("Eggs are the structure")
    }

    @Test fun `one egg as a binder in meatballs is adaptable to a flax egg`() {
        val a = adaptable(
            assess(
                "Turkey meatballs",
                listOf("1 lb ground turkey", "1 egg", "1/2 cup oats", "1 tsp salt", "2 cloves garlic", "1 cup marinara sauce"),
                "egg",
            ),
        )
        assertThat(a.swaps.single().to).isEqualTo("flax egg")
        assertThat(a.swaps.single().confidence).isEqualTo(SwapConfidence.HIGH)
        assertThat(a.adaptedIngredients).contains("1 flax egg")
    }

    @Test fun `a cookie with one egg adapts but a quiche never does`() {
        assertThat(
            assess("Oat cookies", listOf("2 cups oats", "1/2 cup sugar", "1 egg", "1 tsp baking soda", "1/2 cup raisins", "1 cup flour"), "egg"),
        ).isInstanceOf(Adaptation.Adaptable::class.java)
        assertThat(
            assess("Spinach quiche", listOf("4 eggs", "1 cup spinach", "1 pie shell"), "egg"),
        ).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `mayonnaise in a slaw swaps to vegan mayonnaise`() {
        val a = adaptable(assess("Crunchy slaw", listOf("1 head cabbage", "3 carrots", "1/4 cup mayonnaise", "1 tbsp vinegar"), "egg"))
        assertThat(a.swaps.single().to).isEqualTo("vegan mayonnaise")
    }

    @Test fun `a stir-fry with soy sauce for a soy allergy goes to coconut aminos`() {
        val a = adaptable(
            assess(
                "Chicken and broccoli stir-fry",
                listOf("1 lb chicken breast", "4 cups broccoli florets", "2 cloves garlic", "1 tbsp ginger", "3 tbsp soy sauce", "1 tbsp cornstarch", "2 cups cooked rice"),
                "soy",
            ),
        )
        assertThat(a.swaps.single().to).isEqualTo("coconut aminos")
        assertThat(a.adaptedIngredients).contains("3 tbsp coconut aminos")
    }

    @Test fun `a substitute that carries another avoided allergen is skipped`() {
        val lines = listOf("1 lb chicken", "1 cup rice", "1/2 cup milk", "2 cups broccoli", "1 onion")
        assertThat(adaptable(assess("Chicken and rice", lines, "dairy")).swaps.single().to).isEqualTo("oat milk")
        val s = assess("Chicken and rice", lines, "dairy", "gluten")
        assertThat(adaptable(s).swaps.single().to).isEqualTo("soy milk")
        val t = assess("Chicken and rice", lines, "dairy", "gluten", "soy")
        assertThat(adaptable(t).swaps.single().to).isEqualTo("unsweetened coconut milk beverage")
    }

    @Test fun `breadcrumbs and a pinch of flour for a gluten allergy are adaptable`() {
        val a = adaptable(
            assess(
                "Crispy baked chicken",
                listOf("2 lb chicken breast", "1/2 cup panko breadcrumbs", "1 egg", "1 tbsp all-purpose flour", "1 tsp paprika", "1 tbsp olive oil"),
                "gluten",
            ),
        )
        assertThat(a.adaptedIngredients).contains("1/2 cup gluten-free breadcrumbs")
        assertThat(a.swaps.map { it.to }).contains("cornstarch (about half the amount)")
    }

    @Test fun `wheat flour as the structure of a dough is refused`() {
        val a = refused(assess("Homemade pizza night", listOf("3 cups bread flour", "1 packet yeast", "1 cup water", "1 tsp salt", "1/2 cup tomato sauce"), "gluten"))
        assertThat(a.reasons.joinToString()).containsMatch("(dough|wheat flour)")
    }

    @Test fun `pasta alone is only may contain so it does not block, and breadcrumbs beside it are swapped on their own`() {
        assertThat(assess("Tomato pasta", pastaBase, "wheat")).isEqualTo(Adaptation.AlreadyFine)
        val a = adaptable(assess("Pasta bake", pastaBase + "1/2 cup panko breadcrumbs", "wheat"))
        assertThat(a.swaps.single().to).isEqualTo("gluten-free breadcrumbs")
        assertThat(a.adaptedIngredients).contains("12 oz penne pasta")
    }

    @Test fun `a nut topping adapts and a nut crust does not`() {
        assertThat(
            assess("Roasted carrot salad", listOf("2 lb carrots", "2 tbsp olive oil", "1/4 cup chopped walnuts", "2 cups arugula", "1 tbsp lemon juice"), "tree nuts"),
        ).isInstanceOf(Adaptation.Adaptable::class.java)
        assertThat(
            assess("Chicken with a crust", listOf("1 lb chicken", "2 cups chopped pecans", "1 onion"), "tree nuts"),
        ).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `peanut oil for frying adapts, satay does not`() {
        assertThat(
            assess("Crispy tofu bites", listOf("28 oz firm tofu", "2 tbsp peanut oil", "1 tsp salt", "2 cups broccoli"), "peanuts"),
        ).isInstanceOf(Adaptation.Adaptable::class.java)
        assertThat(
            assess("Chicken satay", listOf("1 lb chicken", "1/4 cup peanut butter", "2 tbsp coconut milk"), "peanuts"),
        ).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `sesame as a garnish adapts, hummus does not`() {
        assertThat(
            assess("Green beans", listOf("1 lb green beans", "1 tbsp olive oil", "1 tsp sesame seeds", "1 tsp salt"), "sesame"),
        ).isInstanceOf(Adaptation.Adaptable::class.java)
        assertThat(
            assess("Classic hummus", listOf("2 cups chickpeas", "1/2 cup tahini", "1 lemon"), "sesame"),
        ).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `an allergen line with no swap class is refused with the ingredient named`() {
        val a = refused(assess("Chicken dinner", listOf("1 lb chicken", "1/2 cup whey protein powder", "2 cups rice", "1 onion"), "dairy"))
        assertThat(a.reasons.joinToString()).contains("whey protein powder")
    }

    @Test fun `an optional garnish with no swap can simply be left out`() {
        val a = adaptable(
            assess("Chicken dinner", listOf("1 lb chicken", "2 cups rice", "1 onion", "1 tbsp whey protein powder, optional"), "dairy"),
        )
        assertThat(a.swaps.single().summary).startsWith("Leave out")
        assertThat(a.adaptedIngredients).hasSize(3)
    }

    @Test fun `a block the lines do not explain is never adapted`() {
        val lines = listOf("1 lb chicken", "2 cups rice")
        val a = Substitutions.assess(
            title = "Chicken and rice",
            ingredients = lines,
            avoided = setOf("dairy"),
            lineScans = lines.map { AllergenScanResult.CLEAN },
            overall = scan(setOf("dairy"), "Butter chicken", null),
        )
        assertThat(a).isInstanceOf(Adaptation.NotAdaptable::class.java)
    }

    @Test fun `plant milk and nut butter words are not mistaken for dairy lines`() {
        val a = adaptable(
            assess("Chicken and rice", listOf("1 lb chicken", "1/2 cup almond milk", "2 tbsp peanut butter", "1/2 cup grated parmesan", "2 cups rice", "1 onion"), "dairy"),
        )
        assertThat(a.swaps.map { it.original }).containsExactly("1/2 cup grated parmesan")
    }

    // "Healthy Ground Beef Burrito Bowls": cheese and sour cream are toppings, so the dairy is a swappable part.
    private val burritoBowl = listOf(
        "1 lb lean ground beef", "1 tablespoon olive oil", "1 packet taco seasoning", "2 cups cooked brown rice",
        "1 can (15 oz) black beans, drained", "1 cup frozen corn", "1 cup salsa", "2 romaine hearts, chopped",
        "1 avocado, diced", "1/2 cup shredded cheddar cheese", "1/4 cup sour cream", "1/4 cup chopped cilantro", "1 lime, juiced",
    )

    @Test fun `a burrito bowl with cheese and sour cream as toppings is adaptable`() {
        val a = adaptable(assess("Healthy Ground Beef Burrito Bowls", burritoBowl, "dairy"))
        assertThat(a.swaps.map { it.original }).containsExactly("1/2 cup shredded cheddar cheese", "1/4 cup sour cream")
        assertThat(a.adaptedIngredients).hasSize(burritoBowl.size)
        assertThat(a.adaptedIngredients).doesNotContain("1/2 cup shredded cheddar cheese")
    }

    @Test fun `a burrito bowl is adaptable with the toppings left out too when they are optional`() {
        val lines = burritoBowl.dropLast(3) + listOf("1/2 cup shredded cheddar cheese, optional", "1/4 cup sour cream, for serving")
        val a = adaptable(assess("Burrito Bowls", lines, "dairy"))
        assertThat(a.swaps).isNotEmpty()
    }
}
