package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Test

class PhotoItemsTest {

    private val items = PhotoItems.of(
        listOf(
            PhotoItemInput(EstimateLine("chicken", 150.0, 250.0, 46.0, 0.0, 5.0), 120.0, 180.0, "high"),
            PhotoItemInput(EstimateLine("rice", 180.0, 234.0, 4.0, 50.0, 1.0), 150.0, 220.0, "medium"),
            PhotoItemInput(EstimateLine("sauce", 40.0, 90.0, 1.0, 3.0, 8.0), null, null, "low"),
        ),
    )

    @Test
    fun `every food gets a stable id and starts ticked`() {
        assertThat(items.map { it.id }).containsExactly(0, 1, 2).inOrder()
        assertThat(items.all { it.included }).isTrue()
    }

    @Test
    fun `unticking a food takes it out of the total and the log`() {
        val without = PhotoItems.toggle(items, 2)
        assertThat(PhotoItems.total(without, 1.0).kcal).isEqualTo(484.0)
        assertThat(PhotoItems.shown(without, 1.0).map { it.line.name }).containsExactly("chicken", "rice").inOrder()
        assertThat(PhotoItems.toggle(without, 2).all { it.included }).isTrue()
    }

    @Test
    fun `each ticked food becomes its own entry`() {
        val logged = PhotoItems.loggable(PhotoItems.shown(PhotoItems.toggle(items, 1), 1.0))
        assertThat(logged.map { it.line.name }).containsExactly("chicken", "sauce").inOrder()
    }

    @Test
    fun `an item with no calories and no protein is not logged`() {
        val empty = PhotoItems.of(listOf(PhotoItemInput(EstimateLine("water", 250.0, 0.0, 0.0, 0.0, 0.0), null, null, "high")))
        assertThat(PhotoItems.loggable(PhotoItems.shown(empty, 1.0))).isEmpty()
    }

    @Test
    fun `renaming keeps the id and the numbers`() {
        val renamed = PhotoItems.rename(items, 1, "brown rice")
        assertThat(renamed[1].id).isEqualTo(1)
        assertThat(renamed[1].line.name).isEqualTo("brown rice")
        assertThat(renamed[1].line.kcal).isEqualTo(234.0)
    }

    @Test
    fun `setting a portion rescales that foods macros and its range`() {
        val doubled = PhotoItems.setGrams(items, 1, shownGrams = 360.0, scale = 1.0)
        assertThat(doubled[1].line.grams).isEqualTo(360.0)
        assertThat(doubled[1].line.kcal).isWithin(1e-9).of(468.0)
        assertThat(doubled[1].gramsLow).isWithin(1e-9).of(300.0)
        assertThat(doubled[1].gramsHigh).isWithin(1e-9).of(440.0)
        // The other foods are untouched.
        assertThat(doubled[0]).isEqualTo(items[0])
    }

    @Test
    fun `a portion typed while the slider is not at one is stored at one`() {
        // Shown at 2x, the rice reads 360 g. Typing 400 g means the stored base is 200 g.
        val set = PhotoItems.setGrams(items, 1, shownGrams = 400.0, scale = 2.0)
        assertThat(set[1].line.grams).isEqualTo(200.0)
        assertThat(PhotoItems.shown(set, 2.0)[1].line.grams).isEqualTo(400.0)
    }

    @Test
    fun `editing a macro is stored back at one times`() {
        val edited = PhotoItems.setMacro(items, 0, scale = 2.0) { it.copy(kcal = 1000.0) }
        assertThat(edited[0].line.kcal).isEqualTo(500.0)
        assertThat(edited[0].line.name).isEqualTo("chicken")
    }

    // --- units ---------------------------------------------------------------------------------------

    @Test
    fun `pounds show ounces and kilograms show grams`() {
        assertThat(PortionUnit.forWeightUnit(WeightUnit.POUNDS)).isEqualTo(PortionUnit.OUNCES)
        assertThat(PortionUnit.forWeightUnit(WeightUnit.KILOGRAMS)).isEqualTo(PortionUnit.GRAMS)
    }

    @Test
    fun `grams convert to ounces from the stored value`() {
        assertThat(PortionUnit.OUNCES.fromGrams(170.0)).isWithin(0.001).of(5.997)
        assertThat(PortionUnit.OUNCES.format(170.0)).isEqualTo("6.0 oz")
        assertThat(PortionUnit.GRAMS.format(170.4)).isEqualTo("170 g")
        assertThat(PortionUnit.OUNCES.format(28.349523125)).isEqualTo("1.0 oz")
    }

    @Test
    fun `typed ounces convert back to grams and a comma decimal is read`() {
        assertThat(PortionUnit.OUNCES.parseToGrams("6")!!).isWithin(0.001).of(170.097)
        assertThat(PortionUnit.OUNCES.parseToGrams("6,5")!!).isWithin(0.001).of(184.272)
        assertThat(PortionUnit.GRAMS.parseToGrams(" 150 ")).isEqualTo(150.0)
        assertThat(PortionUnit.GRAMS.parseToGrams("abc")).isNull()
        assertThat(PortionUnit.GRAMS.parseToGrams("-5")).isNull()
        assertThat(PortionUnit.GRAMS.parseToGrams("")).isNull()
    }

    @Test
    fun `ranges format in the unit and collapse when the ends match`() {
        assertThat(PortionUnit.GRAMS.formatRange(120.0, 180.0)).isEqualTo("120–180 g")
        assertThat(PortionUnit.OUNCES.formatRange(150.0, 190.0)).isEqualTo("5.3–6.7 oz")
        assertThat(PortionUnit.GRAMS.formatRange(100.0, 100.2)).isEqualTo("100 g")
    }

    @Test
    fun `an ounce round trip does not drift`() {
        val grams = 123.0
        val typed = PortionUnit.OUNCES.number(grams)
        assertThat(PortionUnit.OUNCES.parseToGrams(typed)!!).isWithin(PortionUnit.GRAMS_PER_OUNCE / 20).of(grams)
    }

    // --- the context note ----------------------------------------------------------------------------

    @Test
    fun `the context note is trimmed, flattened and capped`() {
        assertThat(VisionContext.clean(null)).isNull()
        assertThat(VisionContext.clean("   \n ")).isNull()
        assertThat(VisionContext.clean("  cooked in\n\nbutter  ")).isEqualTo("cooked in butter")
        assertThat(VisionContext.clean("x".repeat(1000))!!.length).isEqualTo(VisionContext.MAX_CHARS)
    }
}
