package com.zeroindex.intake.domain.script

import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileEatOut
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanFileRecipe
import com.zeroindex.intake.domain.plan.PlanFileShoppingItem
import com.zeroindex.intake.domain.plan.PlanFileWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPlanScriptTest {

    private fun parse(text: String) = MealPlanScript.parse(text.trimIndent())

    private fun file(text: String): PlanFile {
        val r = parse(text)
        assertTrue("unexpected diagnostics: ${r.diagnostics}", r.errors.isEmpty())
        return r.document!!.file
    }

    private fun week(body: String) = file("# Meal plan: T\n## Week 1: Dish\n${body.trimIndent()}").weeks.single()

    private fun ingredients(body: String) =
        week("Ingredients:\n$body").recipes.single().ingredients

    private fun firstError(text: String): Diagnostic {
        val r = parse(text)
        assertNull(r.document)
        return r.errors.first()
    }

    /** The error with [code], wherever it sits in the list. */
    private fun error(text: String, code: DiagnosticCode): Diagnostic {
        val r = parse(text)
        assertNull(r.document)
        return r.errors.firstOrNull { it.code == code } ?: throw AssertionError("no $code in ${r.diagnostics}")
    }

    @Test fun `a full small plan maps onto PlanFile`() {
        val r = parse(
            """
            # Meal plan: Test
            avoid: dairy, soy
            About:
            First paragraph.

            Second paragraph.

            ## Every week
            Breakfasts:
            - Eggs, bacon and a tortilla
            Snacks:
            - Meat sticks
            Pantry:
            - rice, olive oil
            Watch for:
            - Seasoning packets

            ## Week 1: Taco bowls
            Protein: beef
            Servings: 5
            Time: 45 min
            Ingredients:
            - 3 lb ground beef 90/10
            - salt and pepper
            Steps:
            1. Brown the beef, about 10 min.
            2. Build five bowls.
            Serve title: The bowls
            Serve:
            Rice, taco meat and salsa.
            Variations:
            - Taco salad
            Shopping:
            - Produce: 4 avocados, tomatoes
            Notes:
            Free text.

            ## Eat out
            Salads are risky.
            ### Subway
            - Rotisserie chicken, doubled, no cheese
              caution: say "no cheese" first
              estimate: 450 kcal, 40 g protein
            """,
        )
        assertTrue(r.diagnostics.toString(), r.errors.isEmpty())
        val doc = r.document!!
        assertEquals("Test", doc.name)
        assertEquals(listOf("dairy", "soy"), doc.avoid)
        val f = doc.toPlanFile()
        assertEquals(PlanFileParser.FORMAT, f.format)
        assertEquals(emptyList<String>(), PlanFileParser.validate(f))
        assertEquals("First paragraph.\n\nSecond paragraph.", f.intro)
        assertEquals(listOf("Eggs, bacon and a tortilla"), f.standingBreakfasts)
        assertEquals(listOf("Meat sticks"), f.standingSnacks)
        assertEquals(listOf("rice", "olive oil"), f.pantry)
        assertEquals(listOf("Seasoning packets"), f.hiddenDairyChecklist)
        assertEquals("Salads are risky.", f.eatOutNotes)
        val w = f.weeks.single()
        assertEquals(1, w.weekNumber)
        assertEquals("Taco bowls", w.mainDishName)
        assertEquals("beef", w.proteinType)
        assertEquals("The bowls", w.servingTitle)
        assertEquals("Rice, taco meat and salsa.", w.serving)
        assertEquals(listOf("Taco salad"), w.variations)
        assertEquals("Free text.", w.notes)
        val rec = w.recipes.single()
        assertEquals("MAIN", rec.role)
        assertEquals("Taco bowls", rec.name)
        assertEquals(5, rec.servings)
        assertEquals(45, rec.totalTimeMinutes)
        assertEquals(listOf("Brown the beef, about 10 min.", "Build five bowls."), rec.steps)
        assertEquals(
            listOf(
                PlanFileShoppingItem("avocados", "4", "PRODUCE", false, 1),
                PlanFileShoppingItem("tomatoes", "", "PRODUCE", false, 1),
            ),
            f.shoppingItems,
        )
        assertEquals(
            listOf(
                PlanFileEatOut("Subway", "Rotisserie chicken, doubled, no cheese", "say \"no cheese\" first", 450, 40, null, null, null),
            ),
            f.eatOutOptions,
        )
        assertTrue(doc.weeksWithGeneratedShopping.isEmpty())
    }

    @Test fun `weeks without a shopping section are noted`() {
        val doc = parse("# Meal plan: T\n## Week 1: A\n## Week 2: B\nShopping:\n- Meat: beef").document!!
        assertEquals(listOf(1), doc.weeksWithGeneratedShopping)
    }

    @Test fun `ingredient quantity forms`() {
        val i = ingredients(
            """
            - 3 lb ground beef 90/10
            - 3lb chicken
            - 3 lbs pork
            - 1/2 cup flour
            - 1 1/2 tsp salt
            - 5-6 lb pork shoulder
            - 5 to 6 oz cheese-free pesto
            - 2 packets Taco seasoning
            - 4 avocados
            - 3 cloves garlic
            - 2 cups of rice
            - salt and pepper
            - 90/10 ground turkey
            """,
        )
        assertEquals("3 lb", i[0].quantityText); assertEquals("ground beef 90/10", i[0].name); assertEquals(1360.8, i[0].grams!!, 0.0)
        assertEquals("3 lb", i[1].quantityText); assertEquals("chicken", i[1].name)
        assertEquals("3 lbs", i[2].quantityText)
        assertEquals("1/2 cup", i[3].quantityText); assertNull(i[3].grams)
        assertEquals("1 1/2 tsp", i[4].quantityText)
        assertEquals("5-6 lb", i[5].quantityText); assertEquals(2721.6, i[5].grams!!, 0.0)
        assertEquals("5 to 6 oz", i[6].quantityText)
        assertEquals("2 packets", i[7].quantityText); assertEquals("Taco seasoning", i[7].name)
        assertEquals("4", i[8].quantityText); assertEquals("avocados", i[8].name)
        assertEquals("3 cloves", i[9].quantityText)
        assertEquals("rice", i[10].name)
        assertEquals("", i[11].quantityText); assertEquals("salt and pepper", i[11].name)
        assertEquals("", i[12].quantityText); assertEquals("90/10 ground turkey", i[12].name)
    }

    @Test fun `weight sub line nutrition and allergens`() {
        val i = ingredients(
            """
            - 2 cups rice
              weight: 370 g
              per 100g: 176 kcal, 20 g protein, 0 g carbs, 10 g fat
              allergens: milk, soy
            - Chicken
              nutrition: fat 10, protein 20, carbs 0, kcal 176
              weight: 1 lb
            """,
        )
        assertEquals(370.0, i[0].grams!!, 0.0)
        assertEquals(176.0, i[0].kcalPer100g!!, 0.0)
        assertEquals(20.0, i[0].proteinPer100g!!, 0.0)
        assertEquals(0.0, i[0].carbsPer100g!!, 0.0)
        assertEquals(10.0, i[0].fatPer100g!!, 0.0)
        assertEquals("milk, soy", i[0].allergenTags)
        assertTrue(i[0].hasNutrition)
        assertEquals(453.6, i[1].grams!!, 0.0)
        assertTrue(i[1].hasNutrition)
    }

    @Test fun `a weight in brackets on the line still works`() {
        val i = ingredients("- 2 cups rice (370g)")
        assertEquals("rice", i.single().name)
        assertEquals(370.0, i.single().grams!!, 0.0)
    }

    @Test fun `nutrition bracket forms in any order`() {
        fun n(v: String) = ingredients("- X\n  per 100g: $v\n  weight: 100 g").single()
        val a = n("10 fat, 20 protein, 0 carbs, 176 kcal")
        assertEquals(176.0, a.kcalPer100g!!, 0.0)
        val b = n("protein 20 g, fat 10 g, carbs 0 g, calories 176 / 100g")
        assertEquals(10.0, b.fatPer100g!!, 0.0)
        val c = n("176 kcal; 20p; 0c; 10f")
        assertEquals(20.0, c.proteinPer100g!!, 0.0)
    }

    @Test fun `steps accept numbers and bullets and continuations`() {
        val steps = week("Steps:\n1. First\n2) Second\n- Third\n  continued\n* Fourth").recipes.single().steps
        assertEquals(listOf("First", "Second", "Third continued", "Fourth"), steps)
    }

    @Test fun `time and servings forms`() {
        fun r(s: String, t: String) = week("Servings: $s\nTime: $t").recipes.single()
        assertEquals(8, r("7-8", "1 hour").servings)
        assertEquals(60, r("4 servings", "1 hour").totalTimeMinutes)
        assertEquals(90, r("4", "1 hour 30 minutes").totalTimeMinutes)
        assertEquals(30, r("4", "30").totalTimeMinutes)
        assertEquals(35, r("makes 4", "35 minutes").totalTimeMinutes)
    }

    @Test fun `shopping categories and synonyms`() {
        val items = week(
            """
            Shopping:
            - produce: 4 avocados, onion
            - MEAT: 3 lb ground beef
            - Meat & grains: chicken
            - Frozen: peas
            - Dry goods: rice
            - Whatever: widgets
            - Bare item without category
            - Staples: salt
            """,
        )
        // the helper only returns the week; read the file for the items
        val f = file(
            "# Meal plan: T\n## Week 1: D\nShopping:\n- produce: 4 avocados, onion\n- MEAT: 3 lb ground beef\n" +
                "- Meat & grains: chicken\n- Frozen: peas\n- Dry goods: rice\n- Staples: salt",
        )
        assertNotNull(items)
        val cats = f.shoppingItems.map { it.name to it.category }
        assertEquals(
            listOf(
                "avocados" to "PRODUCE", "onion" to "PRODUCE", "ground beef" to "MEAT", "chicken" to "MEAT",
                "peas" to "FROZEN", "rice" to "PANTRY", "salt" to "PANTRY",
            ),
            cats,
        )
        assertEquals("3 lb", f.shoppingItems[2].quantity)
        assertTrue(f.shoppingItems.last().isPantryStaple)
    }

    @Test fun `unknown shopping category warns and uses other`() {
        val r = parse("# Meal plan: T\n## Week 1: D\nShopping:\n- Whatever: widgets\n- no category here")
        assertNotNull(r.document)
        assertEquals(
            listOf(DiagnosticCode.SHOPPING_UNKNOWN_CATEGORY, DiagnosticCode.SHOPPING_NO_CATEGORY),
            r.warnings.map { it.code },
        )
        assertTrue(r.document!!.file.shoppingItems.all { it.category == "OTHER" })
    }

    @Test fun `every-week shopping has no week number`() {
        val f = file("# Meal plan: T\n## Every week\nShopping:\n- Pantry: salt\n## Week 1: D")
        assertNull(f.shoppingItems.single().weekNumber)
    }

    @Test fun `eat out orders weeks and venue text`() {
        val f = file(
            """
            # Meal plan: T
            ## Week 1: D
            ## Eat out
            ### Zaxby's
            The sandwich is the pick. Fried filets contain milk.
            - Grilled chicken sandwich, no ranch
              caution: Shared fryers.
            - Plain fries
              weeks: 1, 3-5
              estimate: 300 kcal, 4 g protein, 40 g carbs, 15 g fat
            """,
        )
        val o = f.eatOutOptions
        assertEquals(listOf<Int?>(null, 1, 3, 4, 5), o.map { it.weekNumber })
        assertEquals("The sandwich is the pick. Fried filets contain milk. Shared fryers.", o[0].cautionNote)
        assertEquals("The sandwich is the pick. Fried filets contain milk.", o[1].cautionNote)
        assertEquals(300, o[1].estimatedKcal)
        assertEquals(4, o[1].estimatedProtein)
        assertEquals(40, o[1].estimatedCarbs)
        assertEquals(15, o[1].estimatedFat)
        assertNull(o[0].estimatedKcal)
    }

    @Test fun `extra recipes`() {
        val w = week(
            """
            Servings: 2
            Ingredients:
            - beef
            ### Recipe: Side salad
            Servings: 1
            Time: 10 min
            Steps:
            1. Chop.
            """,
        )
        assertEquals(listOf("MAIN", "VARIATION"), w.recipes.map { it.role })
        assertEquals("Side salad", w.recipes[1].name)
        assertEquals(10, w.recipes[1].totalTimeMinutes)
    }

    @Test fun `html comments and crlf are tolerated`() {
        val f = MealPlanScript.parse("# Meal plan: T\r\n<!-- note -->\r\n## Week 1: Dish <!-- x -->\r\nProtein: beef\r\n").document!!.file
        assertEquals("Dish", f.weeks.single().mainDishName)
    }

    @Test fun `labels are case and spacing insensitive`() {
        val f = week("PROTEIN : beef\nserves: 4\ntotal time: 20 min\nIngredients :\n- beef\n  PER 100 G: 250 kcal, 20 g protein, 0 g carbs, 20 g fat\n  Weight: 500 g")
        assertEquals("beef", f.proteinType)
        assertEquals(4, f.recipes.single().servings)
        assertTrue(f.recipes.single().ingredients.single().hasNutrition)
    }

    @Test fun `the avoid list is informational and not part of the importer model`() {
        val doc = parse("# Meal plan: T\navoid: dairy\n## Week 1: Dairy-free dish").document!!
        assertEquals(listOf("dairy"), doc.avoid)
        assertTrue(doc.file.weeks.single().recipes.isEmpty())
    }

    // ---- errors ----

    @Test fun `unknown lines are errors with line and column`() {
        val d = firstError("# Meal plan: T\n## Week 1: D\nProtein: beef\nColour: red")
        assertEquals(DiagnosticCode.UNKNOWN_KEY, d.code)
        assertEquals(4, d.line)
        assertEquals(1, d.column)
        val e = firstError("# Meal plan: T\n## Week 1: D\nIngredients:\n3 lb beef")
        assertEquals(DiagnosticCode.UNEXPECTED_TEXT, e.code)
        assertEquals(4, e.line)
        assertTrue(e.message, e.message.contains("start with \"-\""))
    }

    @Test fun `structure errors`() {
        assertEquals(DiagnosticCode.NOT_AN_INTAKE_SCRIPT, firstError("hello").code)
        assertEquals(DiagnosticCode.NO_WEEKS, firstError("# Meal plan: T").code)
        assertEquals(DiagnosticCode.MISSING_PLAN_NAME, firstError("# Meal plan:\n## Week 1: D").code)
        assertEquals(DiagnosticCode.UNKNOWN_SECTION, firstError("# Meal plan: T\n## Month 1\n## Week 1: D").code)
        assertEquals(DiagnosticCode.BAD_WEEK_NUMBER, firstError("# Meal plan: T\n## Week 13: D").code)
        assertEquals(DiagnosticCode.DUPLICATE_WEEK, firstError("# Meal plan: T\n## Week 1: D\n## Week 1: E").code)
        assertEquals(DiagnosticCode.BAD_WEEK_HEADING, firstError("# Meal plan: T\n## Week 1").code)
        assertEquals(DiagnosticCode.DUPLICATE_KEY, firstError("# Meal plan: T\n## Week 1: D\nProtein: a\nProtein: b").code)
        assertEquals(DiagnosticCode.BAD_SERVINGS, firstError("# Meal plan: T\n## Week 1: D\nServings: lots").code)
        assertEquals(DiagnosticCode.BAD_TIME, firstError("# Meal plan: T\n## Week 1: D\nTime: soon").code)
        assertEquals(DiagnosticCode.INGREDIENT_NO_NAME, firstError("# Meal plan: T\n## Week 1: D\nIngredients:\n- 3 lb").code)
        assertEquals(DiagnosticCode.BAD_INGREDIENT_WEIGHT, firstError("# Meal plan: T\n## Week 1: D\nIngredients:\n- beef\n  weight: heavy").code)
        assertEquals(DiagnosticCode.BAD_NUTRITION, firstError("# Meal plan: T\n## Week 1: D\nIngredients:\n- beef\n  per 100g: lots of it").code)
        assertEquals(DiagnosticCode.NUTRITION_IMPLAUSIBLE, firstError("# Meal plan: T\n## Week 1: D\nIngredients:\n- beef\n  per 100g: 2000 kcal, 1 g protein, 1 g carbs, 1 g fat").code)
        assertEquals(DiagnosticCode.BAD_WEEKS_LIST, firstError("# Meal plan: T\n## Week 1: D\n## Eat out\n### V\n- order\n  weeks: 14").code)
        assertEquals(DiagnosticCode.BAD_ESTIMATE, firstError("# Meal plan: T\n## Week 1: D\n## Eat out\n### V\n- order\n  estimate: yes").code)
        assertEquals(DiagnosticCode.VENUE_NO_ORDERS, firstError("# Meal plan: T\n## Week 1: D\n## Eat out\n### V\nSome text").code)
        assertEquals(DiagnosticCode.UNEXPECTED_HEADING, firstError("# Meal plan: T\n## Week 1: D\n### Subway").code)
    }

    @Test fun `partial nutrition and partial macros are warnings`() {
        val r = parse(
            """
            # Meal plan: T
            ## Week 1: D
            Ingredients:
            - beef
              weight: 500 g
              per 100g: 250 kcal, 20 g protein, 0 g carbs, 20 g fat
            - rice
            - oil
              per 100g: 880 kcal
            """,
        )
        assertNotNull(r.document)
        assertEquals(
            setOf(DiagnosticCode.INCOMPLETE_NUTRITION, DiagnosticCode.INCOMPLETE_MACROS),
            r.warnings.map { it.code }.toSet(),
        )
    }

    // ---- printer ----

    @Test fun `print then parse gives the same plan`() {
        val file = PlanFile(
            format = PlanFileParser.FORMAT,
            version = 1,
            intro = "Intro one.\n\nIntro two.",
            standingBreakfasts = listOf("Eggs, toast"),
            standingSnacks = listOf("Nuts"),
            pantry = listOf("rice", "oil"),
            hiddenDairyChecklist = listOf("Seasoning packets"),
            eatOutNotes = "Careful.",
            weeks = listOf(
                PlanFileWeek(
                    weekNumber = 1, mainDishName = "Taco bowls", proteinType = "beef", notes = "A note.",
                    serving = "Rice and meat.", servingTitle = "Bowls", variations = listOf("Salad", "Boats"),
                    recipes = listOf(
                        PlanFileRecipe(
                            "Taco bowls", "MAIN", 5, 45,
                            listOf("Brown it, 10 min.", "Serve."),
                            listOf(
                                PlanFileIngredient("ground beef 90/10", "3 lb", 1360.8),
                                PlanFileIngredient("rice", "2 cups", 370.0, 176.0, 20.0, 0.0, 10.0, "milk"),
                                PlanFileIngredient("salt and pepper"),
                                PlanFileIngredient("cheese", "5-6 oz", 170.1),
                            ),
                        ),
                        PlanFileRecipe("Side", "VARIATION", 2, 10, listOf("Chop."), listOf(PlanFileIngredient("lettuce", "1", null))),
                    ),
                ),
                PlanFileWeek(weekNumber = 2, mainDishName = "Chili"),
            ),
            shoppingItems = listOf(
                PlanFileShoppingItem("salt", "", "PANTRY", false, null),
                PlanFileShoppingItem("avocados", "4", "PRODUCE", false, 1),
                PlanFileShoppingItem("ground beef", "3 lb", "MEAT", false, 1),
                PlanFileShoppingItem("beans", "", "PANTRY", true, 2),
            ),
            eatOutOptions = listOf(
                PlanFileEatOut("Subway", "Chicken, no cheese", "Say it early.", 450, 40, null, null, null),
                PlanFileEatOut("Wendy's", "Chili", "", null, null, null, null, 1),
                PlanFileEatOut("Wendy's", "Chili", "", null, null, null, null, 2),
                PlanFileEatOut("Wendy's", "Chili", "", null, null, null, null, 3),
                PlanFileEatOut("Wendy's", "Chili", "", null, null, null, null, 7),
                PlanFileEatOut("Wendy's", "Baked potato", "Plain", 300, 8, 60, 1, null),
            ),
        )
        val doc = MealPlanDoc("Round Trip", listOf("dairy"), file)
        val printed = MealPlanScript.print(doc)
        val again = MealPlanScript.parse(printed)
        assertTrue(again.diagnostics.toString() + "\n" + printed, again.errors.isEmpty())
        assertEquals(doc, again.document)
        assertEquals(printed, MealPlanScript.print(again.document!!))
        assertTrue(printed.contains("weeks: 1-3, 7"))
    }
}
