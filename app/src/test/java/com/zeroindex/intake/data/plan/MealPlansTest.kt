package com.zeroindex.intake.data.plan

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.MealPlanEntity
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.PlanLoopMode
import com.zeroindex.intake.domain.plan.PlanFile
import com.zeroindex.intake.domain.plan.PlanMeta
import com.zeroindex.intake.domain.plan.PlanSchedule
import com.zeroindex.intake.domain.script.DocsFiles
import com.zeroindex.intake.domain.script.MealPlanScript
import org.junit.Test

class MealPlansTest {
    private val noHits = AllergenCheck { _, _ -> AllergenScanResult(emptyList()) }

    private fun sample(): PlanFile = MealPlanScript.parse(DocsFiles.read("examples/meal-plan-sample.md")).document!!.file

    @Test fun `two plans with the same weeks never share rows`() {
        val a = PlanImportBuilder.build(sample(), 1L, noHits, planId = "plan-a")
        val b = PlanImportBuilder.build(sample(), 1L, noHits, planId = "plan-b")
        assertThat(a.weeks.map { it.id }.toSet().intersect(b.weeks.map { it.id }.toSet())).isEmpty()
        assertThat(a.recipes.map { it.id }.toSet().intersect(b.recipes.map { it.id }.toSet())).isEmpty()
        assertThat(a.links.map { it.id }.toSet().intersect(b.links.map { it.id }.toSet())).isEmpty()
        assertThat(a.weeks.map { it.planId }.toSet()).containsExactly("plan-a")
        assertThat(b.weeks.map { it.planId }.toSet()).containsExactly("plan-b")
    }

    @Test fun `importing into the same plan twice gives the same ids so nothing is duplicated`() {
        val first = PlanImportBuilder.build(sample(), 1L, noHits, planId = "plan-a")
        val again = PlanImportBuilder.build(
            sample(), 99L, noHits, planId = "plan-a",
            existingWeekIds = first.weeks.associate { it.weekNumber to it.id },
        )
        assertThat(again.weeks.map { it.id }).isEqualTo(first.weeks.map { it.id })
        assertThat(again.recipes.map { it.id }).isEqualTo(first.recipes.map { it.id })
        assertThat(again.links.map { it.id }).isEqualTo(first.links.map { it.id })
        assertThat(again.shoppingItems.map { it.id }.toSet()).isEqualTo(first.shoppingItems.map { it.id }.toSet())
    }

    @Test fun `existing week rows are reused even when their ids came from the migration`() {
        val legacy = PlanImportBuilder.build(sample(), 1L, noHits) // default = the migrated plan's id formulas
        val adopted = PlanImportBuilder.build(
            sample(), 2L, noHits, planId = PlanImportBuilder.LEGACY_PLAN_ID,
            existingWeekIds = legacy.weeks.associate { it.weekNumber to "kept-${it.weekNumber}" },
        )
        assertThat(adopted.weeks.map { it.id }).containsExactly("kept-1", "kept-2").inOrder()
        // Recipes of the migrated plan keep their pre-plan ids, so re-importing updates them.
        assertThat(adopted.recipes.map { it.id }).isEqualTo(legacy.recipes.map { it.id })
    }

    @Test fun `plan wide content is not a week row any more`() {
        val b = PlanImportBuilder.build(sample(), 1L, noHits, planId = "p")
        assertThat(b.weeks.map { it.weekNumber }).doesNotContain(0)
        assertThat(PlanMeta.of(sample()).isEmpty).isFalse()
    }

    @Test fun `plans longer than twelve weeks build`() {
        val base = sample()
        val week = base.weeks.first()
        val long = base.copy(weeks = (1..40).map { week.copy(weekNumber = it, mainDishName = "Dish $it", recipes = emptyList()) }, shoppingItems = emptyList(), eatOutOptions = emptyList())
        val b = PlanImportBuilder.build(long, 1L, noHits, planId = "p")
        assertThat(b.weeks).hasSize(40)
        assertThat(b.weeks.last().weekNumber).isEqualTo(40)
    }

    @Test fun `export carries the name schedule and plan wide content and only that plan's weeks`() {
        val file = sample().copy(name = "Winter cut")
        val b = PlanImportBuilder.build(file, 1L, noHits, planId = "p")
        val plan = MealPlanEntity(
            id = "p", name = "Winter cut", startDate = java.time.LocalDate.of(2026, 11, 9).toEpochDay(),
            loopMode = PlanLoopMode.REPEAT_N, loopCount = 3, isActive = true,
            metaJson = PlanMeta.of(file).encode(), createdAt = 1, updatedAt = 1,
        )
        val out = PlanExporter.toPlanFile(PlanExportRows(plan, b.weeks, b.links, b.recipes, b.ingredients, b.foods, b.shoppingItems, b.eatOutOptions))
        assertThat(out.name).isEqualTo("Winter cut")
        assertThat(out.startsOn).isEqualTo("2026-11-09")
        assertThat(out.repeat).isEqualTo("3 times")
        assertThat(out.intro).isEqualTo(file.intro.trim())

        val script = MealPlanScript.printFile(out)
        assertThat(script).startsWith("# Meal plan: Winter cut")
        val back = MealPlanScript.parse(script)
        assertThat(back.errors).isEmpty()
        assertThat(back.document!!.file.startsOn).isEqualTo("2026-11-09")
        assertThat(back.document!!.file.repeat).isEqualTo("3 times")
        assertThat(PlanSchedule.parseRepeat(back.document!!.file.repeat)).isEqualTo(PlanLoopMode.REPEAT_N to 3)
    }
}
