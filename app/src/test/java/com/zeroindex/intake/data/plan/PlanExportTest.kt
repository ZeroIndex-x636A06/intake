package com.zeroindex.intake.data.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.script.DocsFiles
import com.zeroindex.intake.domain.script.MealPlanScript
import com.zeroindex.intake.domain.script.ScriptInput
import org.junit.Test

class PlanExportTest {

    private val noHits = AllergenCheck { _, _ -> AllergenScanResult(emptyList()) }

    private fun sample(): PlanFile =
        MealPlanScript.parse(DocsFiles.read("examples/meal-plan-sample.md")).document!!.file

    /** Rows exactly as the importer writes them, handed back to the exporter. */
    private fun roundTrip(file: PlanFile): PlanFile {
        val b = PlanImportBuilder.build(file, 1L, noHits)
        return PlanExporter.toPlanFile(
            PlanExportRows(b.weeks, b.links, b.recipes, b.ingredients, b.foods, b.shoppingItems, b.eatOutOptions),
        )
    }

    @Test fun `weeks and recipes survive rows and back`() {
        val original = sample()
        val back = roundTrip(original)
        assertThat(back.weeks.map { it.weekNumber }).isEqualTo(original.weeks.map { it.weekNumber })
        original.weeks.zip(back.weeks).forEach { (a, b) ->
            assertThat(b.mainDishName).isEqualTo(a.mainDishName.trim())
            assertThat(b.proteinType).isEqualTo(a.proteinType.trim())
            assertThat(b.notes).isEqualTo(a.notes.trim())
            assertThat(b.serving).isEqualTo(a.serving.trim())
            assertThat(b.servingTitle).isEqualTo(a.servingTitle.trim())
            assertThat(b.variations).isEqualTo(a.variations.map { it.trim() }.filter { it.isNotEmpty() })
            assertThat(b.recipes.map { it.name }).containsExactlyElementsIn(a.recipes.map { it.name.trim() })
            a.recipes.forEach { ra ->
                val rb = b.recipes.first { it.name == ra.name.trim() }
                assertThat(rb.role).isEqualTo(ra.role.uppercase())
                assertThat(rb.servings).isEqualTo(ra.servings.coerceAtLeast(1))
                assertThat(rb.totalTimeMinutes).isEqualTo(ra.totalTimeMinutes)
                assertThat(rb.steps).isEqualTo(ra.steps.map { it.trim() }.filter { it.isNotEmpty() })
                assertThat(rb.ingredients.map { it.name }).containsExactlyElementsIn(ra.ingredients.map { it.name })
                ra.ingredients.filter { it.hasNutrition }.forEach { ia ->
                    val ib = rb.ingredients.first { it.name == ia.name }
                    assertThat(ib.grams).isEqualTo(ia.grams)
                    assertThat(ib.kcalPer100g).isEqualTo(ia.kcalPer100g)
                    assertThat(ib.proteinPer100g).isEqualTo(ia.proteinPer100g)
                    assertThat(ib.carbsPer100g).isEqualTo(ia.carbsPer100g)
                    assertThat(ib.fatPer100g).isEqualTo(ia.fatPer100g)
                }
            }
        }
    }

    @Test fun `plan wide lists and eat out come back`() {
        val original = sample()
        val back = roundTrip(original)
        assertThat(back.intro).isEqualTo(original.intro.trim())
        assertThat(back.standingBreakfasts).isEqualTo(original.standingBreakfasts)
        assertThat(back.standingSnacks).isEqualTo(original.standingSnacks)
        assertThat(back.pantry).isEqualTo(original.pantry)
        assertThat(back.hiddenDairyChecklist).isEqualTo(original.hiddenDairyChecklist)
        assertThat(back.eatOutOptions.map { it.venueName to it.whatToOrder })
            .containsExactlyElementsIn(original.eatOutOptions.map { it.venueName.trim() to it.whatToOrder.trim() })
        original.eatOutOptions.forEach { a ->
            val b = back.eatOutOptions.first { it.venueName == a.venueName.trim() && it.whatToOrder == a.whatToOrder.trim() }
            assertThat(b.cautionNote).isEqualTo(a.cautionNote.trim())
            assertThat(b.estimatedKcal).isEqualTo(a.estimatedKcal?.takeIf { it > 0 })
            assertThat(b.weekNumber).isEqualTo(a.weekNumber)
        }
    }

    @Test fun `generated shopping lists stay generated`() {
        // With no hand-written shopping items and no pantry list, everything on the list was generated.
        val plain = sample().copy(shoppingItems = emptyList(), pantry = emptyList())
        assertThat(roundTrip(plain).shoppingItems).isEmpty()
    }

    @Test fun `hand written shopping items are exported per week`() {
        val withItem = sample().let { f ->
            f.copy(shoppingItems = listOf(com.zeroindex.intake.domain.plan.PlanFileShoppingItem(
                name = "Sparkling water", quantity = "2 cases", category = "OTHER", weekNumber = f.weeks.first().weekNumber,
            )))
        }
        val back = roundTrip(withItem)
        assertThat(back.shoppingItems.map { it.name }).contains("Sparkling water")
        assertThat(back.shoppingItems.first { it.name == "Sparkling water" }.weekNumber).isEqualTo(withItem.weeks.first().weekNumber)
    }

    @Test fun `the exported file prints and parses to the same plan`() {
        val back = roundTrip(sample())
        val text = MealPlanScript.printFile(back)
        val reparsed = MealPlanScript.parse(text)
        assertThat(reparsed.errors).isEmpty()
        assertThat(ScriptInput.isJson(text)).isFalse()
        // Printing the re-parsed file gives the same text: the script is a fixed point.
        assertThat(MealPlanScript.printFile(reparsed.document!!.file)).isEqualTo(text)
        assertThat(reparsed.document!!.file.weeks.map { it.mainDishName }).isEqualTo(back.weeks.map { it.mainDishName })
    }

    @Test fun `importing the exported file gives the same rows`() {
        val first = PlanImportBuilder.build(sample(), 1L, noHits)
        val exported = MealPlanScript.printFile(roundTrip(sample()))
        val again = PlanImportBuilder.build(MealPlanScript.parse(exported).document!!.file, 1L, noHits)
        assertThat(again.weeks.map { it.id }).containsExactlyElementsIn(first.weeks.map { it.id })
        assertThat(again.recipes.map { it.id }).containsExactlyElementsIn(first.recipes.map { it.id })
        assertThat(again.eatOutOptions.map { it.id }).containsExactlyElementsIn(first.eatOutOptions.map { it.id })
        assertThat(again.recipes.map { it.kcalPerServing }).containsExactlyElementsIn(first.recipes.map { it.kcalPerServing })
    }
}
