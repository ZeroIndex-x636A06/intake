package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

/** QA Q4-5 (units in the explanations), Q4-7 (Settings g/kg honoured) and Q4-12 (what is missing). */
class GoalBuilderUnitsTest {

    private val profile = UserProfile(
        sex = Sex.MALE, birthYear = 1990, heightCm = 180.0, phase = Phase.CUT, pace = Pace.STEADY,
        activity = ActivityLevel.STANDING, trainingDaysPerWeek = 4,
    )

    private fun inputs(
        weight: Double = 87.1,
        weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
        volumeUnit: VolumeUnit = VolumeUnit.MILLILITRES,
        protein: Double? = null,
        fat: Double? = null,
    ) = GoalInputs(
        profile = profile, weightKg = weight, currentYear = 2026,
        proteinPerKg = protein, fatPerKg = fat, weightUnit = weightUnit, volumeUnit = volumeUnit,
    )

    @Test
    fun `metric explanations are unchanged`() {
        val plan = GoalBuilder.build(inputs())
        assertThat(plan.waterCard.headline).isEqualTo("3,050 ml a day")
        assertThat(plan.waterCard.why).contains("35 ml per kg x 87.1 kg = 3,050 ml")
        assertThat(plan.macroCard.detail).contains("87.1 kg")
    }

    @Test
    fun `imperial water and weight explanations carry no ml or kg`() {
        val plan = GoalBuilder.build(inputs(weightUnit = WeightUnit.POUNDS, volumeUnit = VolumeUnit.US_FLUID_OUNCES))
        assertThat(plan.waterCard.headline).isEqualTo("103.1 fl oz a day")
        assertThat(plan.waterCard.why).contains("fl oz per lb x 192 lb = 103.1 fl oz")
        assertThat(plan.waterCard.why).contains("plus 16.9 fl oz on each of your 4 training days")
        assertThat(plan.waterCard.detail).contains("Training days: 120")
        val texts = listOf(
            plan.waterCard.why, plan.waterCard.headline, plan.waterCard.detail, plan.macroCard.detail,
            plan.calorieCard.detail, plan.tdeeWhy,
        )
        texts.forEach {
            assertThat(Regex("""\d\s?ml\b""").containsMatchIn(it)).isFalse()
            assertThat(Regex("""\d\s?kg\b""").containsMatchIn(it)).isFalse()
        }
        assertThat(plan.macroCard.detail).contains("192 lb")
    }

    @Test
    fun `settings protein and fat per kg are used`() {
        val plan = GoalBuilder.build(inputs(weight = 80.0, protein = 1.9, fat = 0.8))
        assertThat(plan.macros.proteinPerKg).isWithin(0.01).of(1.9)
        assertThat(plan.macros.proteinG).isEqualTo(152)
        assertThat(plan.macros.fatPerKg).isWithin(0.01).of(0.8)
    }

    @Test
    fun `an edit on the plan screen beats the settings`() {
        val plan = GoalBuilder.build(inputs(weight = 80.0, protein = 1.9), PlanOverrides(proteinPerKg = 2.5))
        assertThat(plan.macros.proteinPerKg).isWithin(0.01).of(2.5)
    }

    @Test
    fun `readiness names what is missing and says nothing when complete`() {
        assertThat(AutoTargetReadiness.sentence(UserProfile(), hasWeighIn = false))
            .isEqualTo("Add your sex, your birth year, your height and a weigh-in first.")
        assertThat(AutoTargetReadiness.sentence(profile, hasWeighIn = false)).isEqualTo("Add a weigh-in first.")
        assertThat(AutoTargetReadiness.sentence(profile, hasWeighIn = true)).isNull()
    }
}
