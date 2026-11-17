package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NutritionLabelTest {

    private val bar = NutrientsPer100(
        kcal = 400.0, protein = 20.0, carbs = 50.0, fat = 15.0,
        fiber = 5.0, sugar = 20.0, sodiumMg = 400.0, saturatedFat = 5.0,
    )

    private fun row(label: NutritionLabel, n: LabelNutrient) = label.rows.first { it.nutrient == n }

    @Test
    fun `per portion scales the per 100 values by the grams`() {
        val label = NutritionLabel.build(bar, grams = 50.0, mode = LabelMode.PER_PORTION, servingText = "1 bar (50 g)")
        assertThat(label.calories).isEqualTo(200.0)
        assertThat(row(label, LabelNutrient.TOTAL_FAT).amount).isEqualTo(7.5)
        assertThat(row(label, LabelNutrient.SODIUM).amount).isEqualTo(200.0)
    }

    @Test
    fun `per 100 ignores the portion`() {
        val label = NutritionLabel.build(bar, grams = 50.0, mode = LabelMode.PER_100, servingText = "100 g")
        assertThat(label.calories).isEqualTo(400.0)
        assertThat(row(label, LabelNutrient.PROTEIN).amount).isEqualTo(20.0)
    }

    @Test
    fun `percent daily value uses the FDA values and rounds half up`() {
        // 3.5 g of saturated fat is 17.5% of 20 g, which the label writes as 18%.
        val label = NutritionLabel.build(bar.copy(saturatedFat = 3.5), grams = 100.0, mode = LabelMode.PER_PORTION, servingText = "100 g")
        assertThat(row(label, LabelNutrient.SATURATED_FAT).percentDv).isEqualTo(18)
        assertThat(DailyValues.percent(78.0, DailyValues.TOTAL_FAT_G)).isEqualTo(100)
        assertThat(DailyValues.percent(2300.0, DailyValues.SODIUM_MG)).isEqualTo(100)
        assertThat(DailyValues.percent(0.0, DailyValues.FIBER_G)).isEqualTo(0)
    }

    @Test
    fun `a nutrient the source did not list is not listed and has no percent`() {
        val sparse = bar.copy(fiber = null, sodiumMg = null, saturatedFat = null)
        val label = NutritionLabel.build(sparse, grams = 100.0, mode = LabelMode.PER_PORTION, servingText = "100 g")
        listOf(LabelNutrient.FIBER, LabelNutrient.SODIUM, LabelNutrient.SATURATED_FAT).forEach {
            val r = row(label, it)
            assertThat(r.listed).isFalse()
            assertThat(r.amount).isNull()
            assertThat(r.percentDv).isNull()
            assertThat(LabelFormat.amount(r)).isNull()
        }
        // The nutrients Intake never stores are always "not listed", never 0.
        assertThat(row(label, LabelNutrient.TRANS_FAT).listed).isFalse()
        assertThat(row(label, LabelNutrient.CHOLESTEROL).listed).isFalse()
        assertThat(row(label, LabelNutrient.ADDED_SUGARS).listed).isFalse()
    }

    @Test
    fun `a real zero is listed as zero`() {
        val label = NutritionLabel.build(bar.copy(fiber = 0.0), grams = 100.0, mode = LabelMode.PER_PORTION, servingText = "100 g")
        val fiber = row(label, LabelNutrient.FIBER)
        assertThat(fiber.listed).isTrue()
        assertThat(fiber.percentDv).isEqualTo(0)
    }

    @Test
    fun `the salt setting swaps sodium for salt in grams`() {
        val label = NutritionLabel.build(bar, grams = 100.0, mode = LabelMode.PER_PORTION, servingText = "100 g", saltInsteadOfSodium = true)
        assertThat(label.rows.none { it.nutrient == LabelNutrient.SODIUM }).isTrue()
        val salt = row(label, LabelNutrient.SALT)
        assertThat(salt.amount).isWithin(1e-9).of(1.0) // 400 mg sodium x 2.5 = 1 g salt
        assertThat(salt.unit).isEqualTo("g")
        assertThat(salt.percentDv).isEqualTo(17)
    }

    @Test
    fun `the label rounds the way the FDA does`() {
        assertThat(LabelFormat.calories(47.0)).isEqualTo(45)
        assertThat(LabelFormat.calories(232.0)).isEqualTo(230)
        assertThat(LabelFormat.grams(0.3)).isEqualTo("0")
        assertThat(LabelFormat.grams(2.3)).isEqualTo("2.5")
        assertThat(LabelFormat.grams(12.4)).isEqualTo("12")
        assertThat(LabelFormat.milligrams(3.0)).isEqualTo("0")
        assertThat(LabelFormat.milligrams(137.0)).isEqualTo("135")
        assertThat(LabelFormat.milligrams(243.0)).isEqualTo("240")
    }

    @Test
    fun `unlisted columns become null per 100 values`() {
        val per100 = NutrientsPer100.of(100.0, 1.0, 2.0, 3.0, 0.0, 0.0, 0.0, 0.0, setOf(NutrientKey.FIBER, NutrientKey.SODIUM))
        assertThat(per100.fiber).isNull()
        assertThat(per100.sodiumMg).isNull()
        assertThat(per100.sugar).isEqualTo(0.0)
        assertThat(per100.saturatedFat).isEqualTo(0.0)
    }

    @Test
    fun `unlisted keys round trip through their stored text`() {
        val keys = setOf(NutrientKey.SUGAR, NutrientKey.SATURATED_FAT)
        assertThat(Unlisted.decode(Unlisted.encode(keys))).isEqualTo(keys)
        assertThat(Unlisted.decode(null)).isEmpty()
        assertThat(Unlisted.decode("")).isEmpty()
    }
}
