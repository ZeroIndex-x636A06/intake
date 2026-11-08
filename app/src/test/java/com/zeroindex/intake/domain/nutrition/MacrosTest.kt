package com.zeroindex.intake.domain.nutrition

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MacrosTest {

    @Test
    fun `per 100g scaling matches the portion`() {
        // Chicken breast, 165 kcal and 31 g protein per 100 g, logged at 150 g.
        val macros = Macros.fromPer100g(150.0, kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6)
        assertThat(macros.kcal).isWithin(1e-9).of(247.5)
        assertThat(macros.protein).isWithin(1e-9).of(46.5)
        assertThat(macros.fat).isWithin(1e-9).of(5.4)
    }

    @Test
    fun `macros add and scale`() {
        val a = Macros(kcal = 200.0, protein = 20.0)
        val b = Macros(kcal = 100.0, protein = 5.0)
        assertThat((a + b).kcal).isEqualTo(300.0)
        assertThat((a + b).protein).isEqualTo(25.0)
        assertThat((a * 0.5).kcal).isEqualTo(100.0)
    }

    @Test
    fun `zero grams logs nothing`() {
        assertThat(Macros.fromPer100g(0.0, 500.0, 50.0, 10.0, 20.0).kcal).isEqualTo(0.0)
    }
}

class MacroSplitTest {

    @Test
    fun `protein defaults to 2 grams per kilo of goal weight`() {
        assertThat(MacroSplit.forKcal(2400, 80.0).proteinG).isEqualTo(160)
    }

    @Test
    fun `protein is clamped to the spec range`() {
        // SPEC §8: 1.6 to 2.2 g/kg.
        assertThat(MacroSplit.forKcal(2400, 80.0, proteinPerKg = 0.5).proteinG).isEqualTo(128)
        assertThat(MacroSplit.forKcal(2400, 80.0, proteinPerKg = 5.0).proteinG).isEqualTo(176)
    }

    @Test
    fun `fat never goes below the floor`() {
        assertThat(MacroSplit.forKcal(2400, 80.0, fatPerKg = 0.1).fatG).isEqualTo(48)
    }

    @Test
    fun `carbs fill the remainder and the split adds back up`() {
        val target = MacroSplit.forKcal(2400, 80.0, proteinPerKg = 2.0, fatPerKg = 0.8)
        val reconstructed = target.proteinG * 4 + target.carbsG * 4 + target.fatG * 9
        assertThat(reconstructed).isIn(2392..2408)
    }

    @Test
    fun `carbs cannot go negative on a very low intake`() {
        assertThat(MacroSplit.forKcal(900, 90.0).carbsG).isAtLeast(0)
    }
}

class DayProgressTest {

    private val target = MacroTarget(kcal = 2400, proteinG = 180, carbsG = 250, fatG = 70)

    @Test
    fun `remaining is target minus consumed`() {
        val progress = DayProgress(
            Macros(kcal = 1800.0, protein = 120.0, carbs = 150.0, fat = 50.0),
            target,
        )
        assertThat(progress.kcalRemaining).isEqualTo(600)
        assertThat(progress.proteinRemaining).isEqualTo(60)
    }

    @Test
    fun `going over target reports negative remaining and flags over`() {
        val progress = DayProgress(Macros(kcal = 2600.0), target)
        assertThat(progress.kcalRemaining).isEqualTo(-200)
        assertThat(progress.isOverKcal).isTrue()
    }

    @Test
    fun `ring fractions clamp so the arc never wraps twice`() {
        val progress = DayProgress(Macros(kcal = 4800.0, protein = 360.0), target)
        assertThat(progress.kcalFraction).isEqualTo(1f)
        assertThat(progress.proteinFraction).isEqualTo(1f)
    }

    @Test
    fun `with no target there is nothing to remain and no crash`() {
        val progress = DayProgress(Macros(kcal = 1200.0), null)
        assertThat(progress.kcalRemaining).isNull()
        assertThat(progress.kcalFraction).isEqualTo(0f)
        assertThat(progress.isOverKcal).isFalse()
    }
}
