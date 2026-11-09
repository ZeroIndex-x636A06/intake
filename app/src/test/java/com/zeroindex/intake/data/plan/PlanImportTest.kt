package com.zeroindex.intake.data.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.AllergenSeed
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.domain.allergen.AllergenAlias
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.model.PlanRecipeRole
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileEatOut
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanFileParse
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanFileRecipe
import com.zeroindex.intake.domain.plan.PlanFileWeek
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanWeekExtras
import org.junit.Test

class PlanImportTest {
    private val aliases = com.zeroindex.intake.domain.allergen.AllergenCatalog.aliases().filter { it.allergen == "dairy" }
    private val dairyCheck = AllergenCheck { name, text ->
        AllergenMatcher.scan(aliases, setOf("dairy"), name = name, ingredients = text)
    }

    private fun example(): PlanFile {
        val text = javaClass.classLoader!!.getResourceAsStream("example-meal-plan.json")!!.readBytes().toString(Charsets.UTF_8)
        return (PlanFileParser.parse(text) as PlanFileParse.Ok).file
    }

    private fun ingredient(name: String, tags: String = "") = PlanFileIngredient(
        name = name, grams = 100.0, kcalPer100g = 100.0, proteinPer100g = 5.0, carbsPer100g = 10.0, fatPer100g = 2.0, allergenTags = tags,
    )

    private fun fileWith(vararg ingredients: PlanFileIngredient, eatOut: List<PlanFileEatOut> = emptyList()) = PlanFile(
        format = "intake-meal-plan", version = 1,
        weeks = listOf(
            PlanFileWeek(
                weekNumber = 3, mainDishName = "Test dish", proteinType = "Beef",
                recipes = listOf(PlanFileRecipe(name = "Test dish", servings = 2, ingredients = ingredients.toList())),
            ),
        ),
        eatOutOptions = eatOut,
    )

    // ---- parsing and validation ------------------------------------------------------------

    @Test fun `the example file parses and builds`() {
        val f = example()
        assertThat(f.weeks.single().weekNumber).isEqualTo(1)
        val b = PlanImportBuilder.build(f, 1000L, dairyCheck)
        assertThat(b.recipes).hasSize(1)
        assertThat(b.ingredients).hasSize(3)
        assertThat(b.foods).hasSize(3)
        assertThat(b.links.single().role).isEqualTo(PlanRecipeRole.MAIN)
        assertThat(b.eatOutOptions).hasSize(2)
        assertThat(b.weeks.map { it.weekNumber }).containsExactly(1)
    }

    @Test fun `an ingredient with no nutrition stays text and the recipe has no stored macros`() {
        val b = PlanImportBuilder.build(example(), 1000L, dairyCheck)
        assertThat(b.ingredientsWithoutNutrition).isEqualTo(1)
        val r = b.recipes.single()
        assertThat(r.ingredientsText).isEqualTo("Lemon")
        assertThat(r.kcalPerServing).isNull()
    }

    @Test fun `a fully specified recipe stores per serving macros`() {
        val b = PlanImportBuilder.build(fileWith(ingredient("Rice")), 1L, dairyCheck)
        // 100 g at 100 kcal over 2 servings
        assertThat(b.recipes.single().kcalPerServing).isWithin(1e-9).of(50.0)
    }

    @Test fun `plan wide content and week extras are stored without schema changes`() {
        val b = PlanImportBuilder.build(example(), 1000L, dairyCheck)
        assertThat(b.weeks.none { it.weekNumber == 0 }).isTrue()
        val meta = PlanMeta.of(example())
        assertThat(meta.standingBreakfasts).containsExactly("Two boiled eggs and a banana")
        assertThat(meta.hiddenDairyChecklist).hasSize(2)
        val extras = PlanWeekExtras.decode(b.weeks.first { it.weekNumber == 1 }.notes)
        assertThat(extras.variations).hasSize(2)
        assertThat(extras.note).isEqualTo("Example note for the week.")
        assertThat(extras.servingTitle).isEqualTo("The bowls")
    }

    @Test fun `plain text notes still read as a note`() {
        assertThat(PlanWeekExtras.decode("just words").note).isEqualTo("just words")
        assertThat(PlanWeekExtras(note = "only a note").encode()).isEqualTo("only a note")
    }

    @Test fun `pantry becomes staple items on every week and file shopping items keep free text quantity`() {
        val b = PlanImportBuilder.build(example(), 1000L, dairyCheck)
        assertThat(b.shoppingItems.filter { it.isPantryStaple }.map { it.name }).containsAtLeast("Olive oil", "Sea salt")
        assertThat(b.shoppingItems.first { it.name == "Fresh parsley" }.quantity).isEqualTo("1 bunch")
    }

    @Test fun `eat out options without macros store zero and are marked no estimate`() {
        val b = PlanImportBuilder.build(example(), 1000L, dairyCheck)
        assertThat(b.eatOutWithoutEstimate).isEqualTo(1)
        val none = b.eatOutOptions.first { it.venueName == "Example Gas Station" }
        assertThat(none.estimatedKcal).isEqualTo(0)
        assertThat(none.planWeekId).isNull()
        assertThat(b.eatOutOptions.first { it.venueName == "Example Sandwich Shop" }.estimatedKcal).isEqualTo(420)
    }

    @Test fun `validation reports every problem`() {
        val bad = """{"format":"other","version":2,"weeks":[{"weekNumber":105,"mainDishName":""},{"weekNumber":1,"mainDishName":"A"},{"weekNumber":1,"mainDishName":"B"}]}"""
        val errors = (PlanFileParser.parse(bad) as PlanFileParse.Invalid).errors
        assertThat(errors.joinToString("\n")).contains("format should be")
        assertThat(errors.joinToString("\n")).contains("version 2")
        assertThat(errors.joinToString("\n")).contains("outside 1-104")
        assertThat(errors.joinToString("\n")).contains("no mainDishName")
        assertThat(errors.joinToString("\n")).contains("more than once")
    }

    @Test fun `impossible nutrition and bad enums are rejected`() {
        val bad = """{"format":"intake-meal-plan","version":1,"weeks":[{"weekNumber":1,"mainDishName":"A","recipes":[
          {"name":"A","role":"SIDE","servings":0,"ingredients":[{"name":"x","grams":100,"kcalPer100g":2000,"proteinPer100g":80,"carbsPer100g":80,"fatPer100g":0}]}]}],
          "shoppingItems":[{"name":"y","category":"JUNK"}]}"""
        val text = (PlanFileParser.parse(bad) as PlanFileParse.Invalid).errors.joinToString("\n")
        assertThat(text).contains("must be MAIN or VARIATION")
        assertThat(text).contains("servings must be at least 1")
        assertThat(text).contains("900 kcal")
        assertThat(text).contains("exceeds 100 g")
        assertThat(text).contains("category")
    }

    @Test fun `not json and a newer version are refused`() {
        assertThat(PlanFileParser.parse("hello")).isInstanceOf(PlanFileParse.Invalid::class.java)
        val newer = """{"format":"intake-meal-plan","version":9,"weeks":[{"weekNumber":1,"mainDishName":"A"}]}"""
        assertThat((PlanFileParser.parse(newer) as PlanFileParse.Invalid).errors.single()).contains("Update the app")
    }

    @Test fun `unknown keys are ignored so later minor additions still read`() {
        val ok = """{"format":"intake-meal-plan","version":1,"future":true,"weeks":[{"weekNumber":2,"mainDishName":"A","mood":"x"}]}"""
        assertThat(PlanFileParser.parse(ok)).isInstanceOf(PlanFileParse.Ok::class.java)
    }

    // ---- allergen rules --------------------------------------------------------------------

    @Test fun `a file marking milk allergen free is still flagged`() {
        val f = fileWith(ingredient("Whole milk", tags = "none, allergen-free"))
        val b = PlanImportBuilder.build(f, 1L, dairyCheck)
        assertThat(b.blocked.map { it.subject }.single()).contains("Whole milk")
        assertThat(b.recipes.single().allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
    }

    @Test fun `file tags can add a warning to an innocent name but never clear one`() {
        val added = PlanImportBuilder.build(fileWith(ingredient("Seasoning blend", tags = "contains milk")), 1L, dairyCheck)
        assertThat(added.recipes.single().allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
        val cleared = PlanImportBuilder.build(fileWith(ingredient("Cheddar cheese", tags = "dairy-free")), 1L, dairyCheck)
        assertThat(cleared.recipes.single().allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
    }

    @Test fun `eat out orders go through the engine and store the verdict`() {
        val f = fileWith(
            ingredient("Rice"),
            eatOut = listOf(
                PlanFileEatOut(venueName = "Diner", whatToOrder = "Grilled cheese sandwich", estimatedKcal = 500),
                PlanFileEatOut(venueName = "Deli", whatToOrder = "Turkey on rye, mustard", estimatedKcal = 400),
            ),
        )
        val b = PlanImportBuilder.build(f, 1L, dairyCheck)
        assertThat(b.eatOutOptions.first { it.venueName == "Diner" }.allergenStatus).isEqualTo(AllergenStatus.CONTAINS)
        assertThat(b.eatOutOptions.first { it.venueName == "Deli" }.allergenStatus).isEqualTo(AllergenStatus.CLEAR)
        assertThat(b.blocked.map { it.subject }.single()).contains("Diner")
    }

    @Test fun `food rows keep only the file's own tags`() {
        val b = PlanImportBuilder.build(fileWith(ingredient("Rice", tags = " soy ")), 1L, dairyCheck)
        assertThat(b.foods.single().allergenTags).isEqualTo("soy")
    }

    @Test fun `with no allergens avoided nothing is flagged`() {
        val none = AllergenCheck { name, text -> AllergenMatcher.scan(aliases, emptySet(), name = name, ingredients = text) }
        val b = PlanImportBuilder.build(fileWith(ingredient("Whole milk")), 1L, none)
        assertThat(b.findings).isEmpty()
    }

    /**
     * What the local engine does with the strings a real dairy-free plan contains. The file's
     * claim ("dairy-free") never lowers the verdict; these are the engine's own results.
     */
    @Test fun `hidden dairy strings from a dairy free plan`() {
        fun status(text: String) = PlanImportBuilder.statusOf(dairyCheck.scan(text, null))
        // "Dairy-free" substitutes and plant milks are check the label with a reason: never blocked, never silent.
        assertThat(status("Dairy-free provolone")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Dairy-free cheese")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Dairy-free cheese slice")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Oat milk")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Cheese")).isEqualTo(AllergenStatus.CONTAINS)
        // The allergen's own name inside its free claim is not a hit.
        assertThat(status("Boxed dairy-free mac (Daiya)")).isEqualTo(AllergenStatus.CLEAR)
        // No alias names these, but they commonly hide milk: the soft "check the label" tier, never blocking.
        assertThat(status("Taco Bell mild seasoning")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Flour tortillas")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Hoagie rolls")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Breadcrumbs")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Saltines")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Fritos")).isEqualTo(AllergenStatus.CHECK_LABEL)
        assertThat(status("Burger buns")).isEqualTo(AllergenStatus.CHECK_LABEL)
    }

    // ---- idempotent re-import ----------------------------------------------------------------

    /** A stand-in for the tables: Room's @Upsert is "replace the row with this id", which is all this models. */
    private class FakeTables {
        val rows = HashMap<String, MutableMap<String, Any>>()
        fun apply(b: PlanImportBatch) {
            fun put(table: String, id: String, row: Any) { rows.getOrPut(table) { HashMap() }[id] = row }
            b.foods.forEach { put("food", it.id, it) }
            b.recipes.forEach { put("recipe", it.id, it) }
            b.ingredients.forEach { put("recipe_ingredient", it.id, it) }
            b.weeks.forEach { put("plan_week", it.id, it) }
            b.links.forEach { put("plan_week_recipe", it.id, it) }
            b.shoppingItems.forEach { put("shopping_list_item", it.id, it) }
            b.eatOutOptions.forEach { put("eat_out_option", it.id, it) }
        }
        fun counts() = rows.mapValues { it.value.size }
    }

    @Test fun `importing the same file twice adds no rows`() {
        val tables = FakeTables()
        tables.apply(PlanImportBuilder.build(example(), 1_000L, dairyCheck))
        val first = tables.counts()
        tables.apply(PlanImportBuilder.build(example(), 2_000_000L, dairyCheck))
        assertThat(tables.counts()).isEqualTo(first)
        assertThat(first.values.sum()).isGreaterThan(10)
    }

    @Test fun `ids depend on the content, not the time or the order`() {
        val a = PlanImportBuilder.build(example(), 1L, dairyCheck)
        val b = PlanImportBuilder.build(example(), 99L, dairyCheck)
        assertThat(a.recipes.map { it.id }).isEqualTo(b.recipes.map { it.id })
        assertThat(a.shoppingItems.map { it.id }.toSet()).isEqualTo(b.shoppingItems.map { it.id }.toSet())
        assertThat(a.eatOutOptions.map { it.id }).isEqualTo(b.eatOutOptions.map { it.id })
        assertThat(a.recipes.single().id).isEqualTo(PlanImportBuilder.recipeId(PlanImportBuilder.LEGACY_PLAN_ID, 1, "Example chicken and rice bowls"))
    }

    @Test fun `the same food in two weeks is one row but different nutrition is two`() {
        val week = { n: Int, kcal: Double ->
            PlanFileWeek(
                weekNumber = n, mainDishName = "D$n",
                recipes = listOf(PlanFileRecipe(name = "R$n", ingredients = listOf(ingredient("Rice").copy(kcalPer100g = kcal)))),
            )
        }
        val same = PlanFile(format = "intake-meal-plan", version = 1, weeks = listOf(week(1, 100.0), week(2, 100.0)))
        assertThat(PlanImportBuilder.build(same, 1L, dairyCheck).foods).hasSize(1)
        val diff = PlanFile(format = "intake-meal-plan", version = 1, weeks = listOf(week(1, 100.0), week(2, 130.0)))
        assertThat(PlanImportBuilder.build(diff, 1L, dairyCheck).foods).hasSize(2)
    }

    @Test fun `import timestamps come from the clock passed in`() {
        val b = PlanImportBuilder.build(example(), 1234L, dairyCheck)
        val foods: List<FoodEntity> = b.foods
        assertThat(foods.all { it.createdAt == 1234L && it.updatedAt == 1234L }).isTrue()
        assertThat(b.recipes.all { it.createdAt == 1234L }).isTrue()
        assertThat(b.weeks.all { it.updatedAt == 1234L }).isTrue()
    }
}
