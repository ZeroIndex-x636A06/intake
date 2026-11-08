package com.zeroindex.intake.data.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.domain.allergen.AllergenCatalog
import com.zeroindex.intake.domain.allergen.AllergenMatcher
import com.zeroindex.intake.domain.allergen.HiddenCarriers
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanFileEatOut
import com.zeroindex.intake.domain.plan.PlanFileIngredient
import com.zeroindex.intake.domain.plan.PlanFileRecipe
import com.zeroindex.intake.domain.plan.PlanFileWeek
import org.junit.Test

/** The import preview: one row per item, full text, grouped by tier, plain reasons. */
class PlanImportAllergenPreviewTest {

    private val aliases = AllergenCatalog.aliases().filter { it.allergen == "dairy" }

    /** The same shape the app uses: plan text is free text, a file's allergen tags are panel text. */
    private val check = object : AllergenCheck {
        override fun scan(name: String, ingredientsText: String?) = scan(name, ingredientsText, null)
        override fun scan(name: String, ingredientsText: String?, panelText: String?) = AllergenMatcher.scan(
            aliases, setOf("dairy"), name = name, ingredients = ingredientsText,
            checkLabelTerms = HiddenCarriers.forAllergen("dairy"), checkLabelInIngredients = true,
            freeText = true, panelText = panelText,
        )
    }

    private fun ing(name: String, tags: String = "") = PlanFileIngredient(
        name = name, grams = 100.0, kcalPer100g = 100.0, proteinPer100g = 5.0, carbsPer100g = 10.0, fatPer100g = 2.0, allergenTags = tags,
    )

    private val longIdea = "Chicken over rice with teriyaki or buffalo sauce made with oil, not butter, " +
        "served in a big bowl with lots of extra toppings so the sentence is far longer than forty characters"

    private fun file() = PlanFile(
        format = "intake-meal-plan", version = 1,
        weeks = listOf(
            PlanFileWeek(
                weekNumber = 9, mainDishName = "Dairy-free mac and beef", proteinType = "Beef",
                variations = listOf(longIdea, "Cheese quesadillas"),
                recipes = listOf(
                    PlanFileRecipe(
                        name = "Dairy-free mac and beef", servings = 2,
                        ingredients = listOf(ing("oat milk"), ing("dairy-free shreds"), ing("shredded cheese"), ing("rice", tags = "no milk")),
                    ),
                ),
            ),
        ),
        eatOutOptions = listOf(PlanFileEatOut(venueName = "Subway", whatToOrder = "Chicken sub, no cheese, oil and vinegar")),
    )

    private val batch = PlanImportBuilder.build(file(), 1L, check)

    @Test fun `the week 9 dish named twice is one row`() {
        val rows = batch.findings.filter { it.item == "Dairy-free mac and beef" }
        assertThat(rows.size).isAtMost(1)
        assertThat(batch.findings.map { it.subject to it.status }).containsNoDuplicates()
    }

    @Test fun `full text is kept, never cut at forty characters`() {
        val row = batch.mentionedAvoided.single { it.item.startsWith("Chicken over rice") }
        assertThat(row.item).isEqualTo(longIdea)
        assertThat(batch.eatOutOptions.single().whatToOrder.length).isGreaterThan(0)
        assertThat(batch.findings.any { it.item.endsWith("no cheese, oil and vinegar") }).isTrue()
    }

    @Test fun `tiers and the summary line`() {
        assertThat(batch.blocked.map { it.item }).containsExactly("Cheese quesadillas", "shredded cheese", "rice")
        assertThat(batch.checkLabel.map { it.item }).containsAtLeast("oat milk", "dairy-free shreds")
        assertThat(batch.mentionedAvoided.map { it.item }).hasSize(2)
        assertThat(batch.summaryLine).startsWith("3 need a decision")
        assertThat(batch.summaryLine).endsWith("2 mentioned as avoided")
    }

    @Test fun `quesadilla is a may contain, not a block, when it only says quesadilla`() {
        // "Cheese quesadillas" says cheese: blocked. A bare quesadilla is the softer may contain.
        val bare = PlanImportBuilder.statusOf(check.scan("Quesadillas", null))
        assertThat(bare).isEqualTo(AllergenStatus.MAY_CONTAIN)
    }

    @Test fun `reasons are plain and carry the key word`() {
        val avoided = batch.mentionedAvoided.first { it.item.startsWith("Chicken over rice") }
        assertThat(avoided.reasons).containsExactly("Mentions butter as something to avoid")
        assertThat(avoided.terms).containsExactly("butter")
        val shreds = batch.checkLabel.first { it.item == "dairy-free shreds" }
        assertThat(shreds.reasons).containsExactly("Some dairy-free cheeses still contain casein")
        val blocked = batch.blocked.first { it.item == "shredded cheese" }
        assertThat(blocked.reasons).containsExactly("Says cheese")
    }

    @Test fun `a file's allergen tags can add a warning but never lower one`() {
        val tagged = PlanImportBuilder.build(
            PlanFile(
                format = "intake-meal-plan", version = 1,
                weeks = listOf(
                    PlanFileWeek(
                        weekNumber = 1, mainDishName = "Bowl",
                        recipes = listOf(PlanFileRecipe(name = "Bowl", servings = 1, ingredients = listOf(ing("cheese", tags = "no cheese, not dairy")))),
                    ),
                ),
            ),
            1L, check,
        )
        assertThat(tagged.blocked.map { it.item }).contains("cheese")
        val tagOnly = PlanImportBuilder.build(
            PlanFile(
                format = "intake-meal-plan", version = 1,
                weeks = listOf(
                    PlanFileWeek(
                        weekNumber = 1, mainDishName = "Bowl",
                        recipes = listOf(PlanFileRecipe(name = "Bowl", servings = 1, ingredients = listOf(ing("rice", tags = "contains milk, no cheese")))),
                    ),
                ),
            ),
            1L, check,
        )
        assertThat(tagOnly.blocked.map { it.item }).contains("rice")
    }
}
