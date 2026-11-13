package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuickAddLimitsTest {

    @Test
    fun `a blank field is acceptable and counts as not typed`() {
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, "")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, "   ")).isNull()
    }

    @Test
    fun `the absurd protein value from the bug report is refused`() {
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, "348416")).isEqualTo("Over 500 g for one entry")
    }

    @Test
    fun `each limit includes its maximum and refuses just above it`() {
        assertThat(QuickAddLimits.problem(QuickAddField.KCAL, "5000")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.KCAL, "5001")).isNotNull()
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, "500")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, "500.1")).isNotNull()
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, "500")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.FAT, "501")).isNotNull()
        assertThat(QuickAddLimits.problem(QuickAddField.FIBRE, "300")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.FIBRE, "301")).isNotNull()
        assertThat(QuickAddLimits.problem(QuickAddField.SUGAR, "300")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.SUGAR, "301")).isNotNull()
        assertThat(QuickAddLimits.problem(QuickAddField.SODIUM, "20000")).isNull()
        assertThat(QuickAddLimits.problem(QuickAddField.SODIUM, "20001")).isEqualTo("Over 20000 mg for one entry")
    }

    @Test
    fun `negative numbers are refused`() {
        assertThat(QuickAddLimits.problem(QuickAddField.KCAL, "-5")).isEqualTo("Cannot be negative")
        assertThat(QuickAddLimits.problem(QuickAddField.SODIUM, "-0.5")).isEqualTo("Cannot be negative")
        assertThat(QuickAddLimits.problem(QuickAddField.KCAL, -1.0)).isEqualTo("Cannot be negative")
    }

    @Test
    fun `non-numbers are refused`() {
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, "abc")).isEqualTo("Enter a number")
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, ".")).isEqualTo("Enter a number")
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, "1.2.3")).isEqualTo("Enter a number")
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, "NaN")).isEqualTo("Enter a number")
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, "1e400")).isEqualTo("Enter a number")
        assertThat(QuickAddLimits.problem(QuickAddField.CARBS, Double.NaN)).isEqualTo("Enter a number")
    }

    @Test
    fun `surrounding spaces and a decimal are fine`() {
        assertThat(QuickAddLimits.problem(QuickAddField.PROTEIN, " 12.5 ")).isNull()
    }

    @Test
    fun `firstProblem names the first bad field and is null when all are acceptable`() {
        assertThat(QuickAddLimits.firstProblem(Macros(kcal = 300.0, protein = 20.0, carbs = 40.0, fat = 10.0))).isNull()
        assertThat(QuickAddLimits.firstProblem(Macros(kcal = 300.0, protein = 348416.0)))
            .isEqualTo("Protein: Over 500 g for one entry")
        assertThat(QuickAddLimits.firstProblem(Macros(kcal = 6000.0, fiber = -1.0)))
            .isEqualTo("Calories: Over 5000 kcal for one entry")
        assertThat(QuickAddLimits.firstProblem(Macros(sodium = 25000.0)))
            .isEqualTo("Sodium: Over 20000 mg for one entry")
    }
}
