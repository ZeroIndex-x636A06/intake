package com.zeroindex.intake.domain.script

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.plan.PlanFileParser
import com.zeroindex.intake.domain.plan.PlanFileParse
import org.junit.Test

class MealPlanScheduleHeaderTest {

    private fun weeks(n: Int) = (1..n).joinToString("\n") { "## Week $it: Dish $it" }

    @Test fun `starts and repeat are read from the header`() {
        val r = MealPlanScript.parse("# Meal plan: Cut\nstarts: 2026-11-09\nrepeat: 3 times\n${weeks(2)}")
        assertThat(r.errors).isEmpty()
        val f = r.document!!.file
        assertThat(f.name).isEqualTo("Cut")
        assertThat(f.startsOn).isEqualTo("2026-11-09")
        assertThat(f.repeat).isEqualTo("3 times")
    }

    @Test fun `both are optional`() {
        val f = MealPlanScript.parse("# Meal plan: Cut\n${weeks(1)}").document!!.file
        assertThat(f.startsOn).isEmpty()
        assertThat(f.repeat).isEmpty()
    }

    @Test fun `no and yes are accepted`() {
        assertThat(MealPlanScript.parse("# Meal plan: A\nrepeat: no\n${weeks(1)}").errors).isEmpty()
        assertThat(MealPlanScript.parse("# Meal plan: A\nrepeat: yes\n${weeks(1)}").errors).isEmpty()
    }

    @Test fun `a bad date is bad-start-date`() {
        val d = MealPlanScript.parse("# Meal plan: A\nstarts: next monday\n${weeks(1)}").errors.single()
        assertThat(d.code).isEqualTo(DiagnosticCode.BAD_START_DATE)
        assertThat(d.line).isEqualTo(2)
        assertThat(MealPlanScript.parse("# Meal plan: A\nstarts: 2026-13-40\n${weeks(1)}").errors.single().code)
            .isEqualTo(DiagnosticCode.BAD_START_DATE)
    }

    @Test fun `a bad repeat is bad-repeat`() {
        val d = MealPlanScript.parse("# Meal plan: A\nrepeat: sometimes\n${weeks(1)}").errors.single()
        assertThat(d.code).isEqualTo(DiagnosticCode.BAD_REPEAT)
    }

    @Test fun `each can be given once`() {
        assertThat(MealPlanScript.parse("# Meal plan: A\nrepeat: no\nrepeat: yes\n${weeks(1)}").errors.single().code)
            .isEqualTo(DiagnosticCode.DUPLICATE_KEY)
    }

    @Test fun `print and parse keep the schedule`() {
        val f = MealPlanScript.parse("# Meal plan: Cut\nstarts: 2026-11-09\nrepeat: yes\n${weeks(2)}").document!!
        val text = MealPlanScript.print(f)
        assertThat(text).contains("starts: 2026-11-09")
        assertThat(text).contains("repeat: yes")
        assertThat(MealPlanScript.parse(text).document).isEqualTo(f)
    }

    @Test fun `plans up to 104 weeks parse and 105 does not`() {
        val long = MealPlanScript.parse("# Meal plan: Long\n${weeks(104)}")
        assertThat(long.errors).isEmpty()
        assertThat(long.document!!.file.weeks).hasSize(104)
        assertThat(MealPlanScript.parse("# Meal plan: Long\n${weeks(105)}").errors.single().code)
            .isEqualTo(DiagnosticCode.BAD_WEEK_NUMBER)
    }

    @Test fun `json files accept weeks beyond twelve and validate the schedule`() {
        fun json(week: Int, extra: String = "") =
            """{"format":"intake-meal-plan","version":1$extra,"weeks":[{"weekNumber":$week,"mainDishName":"A"}]}"""
        assertThat(PlanFileParser.parse(json(40))).isInstanceOf(PlanFileParse.Ok::class.java)
        assertThat(PlanFileParser.parse(json(104))).isInstanceOf(PlanFileParse.Ok::class.java)
        assertThat(PlanFileParser.parse(json(105))).isInstanceOf(PlanFileParse.Invalid::class.java)
        assertThat(PlanFileParser.parse(json(1, ""","startsOn":"2026-11-09","repeat":"2 times""""))).isInstanceOf(PlanFileParse.Ok::class.java)
        assertThat((PlanFileParser.parse(json(1, ""","startsOn":"soon"""")) as PlanFileParse.Invalid).errors.single()).contains("startsOn")
        assertThat((PlanFileParser.parse(json(1, ""","repeat":"maybe"""")) as PlanFileParse.Invalid).errors.single()).contains("repeat")
    }
}
