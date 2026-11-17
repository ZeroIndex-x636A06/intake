package com.zeroindex.intake.domain.swap

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.Macros
import org.junit.Test

class SwapMathTest {

    private val planned = Macros(kcal = 640.0, protein = 48.0, carbs = 62.0, fat = 18.0)

    private fun option(
        key: String,
        kcal: Double,
        protein: Double,
        kind: SwapKind = SwapKind.PLAN_RECIPE,
        allergen: SwapAllergen = SwapAllergen.CLEAR,
        reason: String? = null,
    ) = SwapOption(key, key, null, kind, Macros(kcal = kcal, protein = protein), estimate = false, allergen = allergen, allergenReason = reason)

    @Test
    fun `deltas are signed whole numbers against the planned meal`() {
        val d = SwapMath.delta(Macros(kcal = 760.0, protein = 44.0, carbs = 71.0, fat = 28.0), planned)
        assertThat(d).isEqualTo(SwapDelta(kcal = 120, protein = -4, carbs = 9, fat = 10))
        assertThat(SwapMath.label(d)).isEqualTo("+120 kcal, −4 g protein")
        assertThat(SwapMath.chips(d)).containsExactly("+120 kcal", "−4 g protein").inOrder()
        assertThat(SwapMath.label(SwapMath.delta(planned, planned))).isEqualTo("±0 kcal, ±0 g protein")
    }

    @Test
    fun `only a contains verdict blocks`() {
        assertThat(option("a", 1.0, 1.0, allergen = SwapAllergen.CONTAINS).blocked).isTrue()
        listOf(SwapAllergen.CLEAR, SwapAllergen.CHECK_LABEL, SwapAllergen.MAY_CONTAIN).forEach {
            assertThat(option("a", 1.0, 1.0, allergen = it).blocked).isFalse()
        }
    }

    @Test
    fun `a model claim can raise a verdict and never lower it`() {
        assertThat(SwapMath.raise(SwapAllergen.CLEAR, SwapAllergen.MAY_CONTAIN)).isEqualTo(SwapAllergen.MAY_CONTAIN)
        assertThat(SwapMath.raise(SwapAllergen.CONTAINS, SwapAllergen.CLEAR)).isEqualTo(SwapAllergen.CONTAINS)
        assertThat(SwapMath.raise(SwapAllergen.MAY_CONTAIN, SwapAllergen.CHECK_LABEL)).isEqualTo(SwapAllergen.MAY_CONTAIN)
    }

    @Test
    fun `an allergen scan reads worst tier first`() {
        assertThat(SwapMath.verdict(contains = true, mayContain = true, checkLabel = true)).isEqualTo(SwapAllergen.CONTAINS)
        assertThat(SwapMath.verdict(contains = false, mayContain = true, checkLabel = true)).isEqualTo(SwapAllergen.MAY_CONTAIN)
        assertThat(SwapMath.verdict(contains = false, mayContain = false, checkLabel = true)).isEqualTo(SwapAllergen.CHECK_LABEL)
        assertThat(SwapMath.verdict(contains = false, mayContain = false, checkLabel = false)).isEqualTo(SwapAllergen.CLEAR)
    }

    @Test
    fun `blocked options sort last even when they are the closest`() {
        val sorted = SwapMath.order(
            listOf(
                option("blocked-exact", 640.0, 48.0, allergen = SwapAllergen.CONTAINS),
                option("far", 300.0, 20.0),
                option("close", 600.0, 40.0),
            ),
            planned,
        )
        assertThat(sorted.map { it.key }).containsExactly("close", "far", "blocked-exact").inOrder()
    }

    @Test
    fun `sections come in order, nearby only when found, and a blocked option cannot be chosen`() {
        val blocked = option("salmon", 610.0, 38.0, SwapKind.NEARBY, SwapAllergen.CONTAINS, "Contains sesame")
        val sections = SwapSections.group(
            listOf(option("saved", 620.0, 44.0, SwapKind.SAVED_EAT_OUT), option("plan", 600.0, 40.0, SwapKind.PLAN_RECIPE), blocked),
            planned,
        )
        assertThat(sections.map { it.kind }).containsExactly(SwapKind.PLAN_RECIPE, SwapKind.SAVED_MEAL, SwapKind.SAVED_EAT_OUT, SwapKind.NEARBY).inOrder()
        assertThat(sections[1].options).isEmpty()
        assertThat(SwapSections.canChoose(blocked)).isFalse()
        assertThat(SwapSections.blockedReason(blocked)).isEqualTo("Contains sesame")
        assertThat(SwapSections.canChoose(option("plan", 1.0, 1.0))).isTrue()
        assertThat(SwapSections.blockedReason(option("plan", 1.0, 1.0))).isNull()
        // No nearby results: no Nearby section.
        assertThat(SwapSections.group(emptyList(), planned).map { it.kind }).doesNotContain(SwapKind.NEARBY)
    }
}
