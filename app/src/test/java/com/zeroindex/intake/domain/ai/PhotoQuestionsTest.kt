package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import org.junit.Test

class PhotoQuestionsTest {

    private fun q(text: String, vararg choices: String) = RawPhotoQuestion(text, choices.toList())

    private val oatmeal = q("Is this a packet of instant oatmeal?", "Plain", "Flavoured", "Home-made")

    @Test
    fun `a confident model asks nothing, whatever it returned`() {
        assertThat(PhotoQuestions.toAsk("high", listOf(oatmeal))).isEmpty()
        assertThat(PhotoQuestions.toAsk(" HIGH ", listOf(oatmeal))).isEmpty()
    }

    @Test
    fun `an unsure model gets its questions shown`() {
        assertThat(PhotoQuestions.toAsk("medium", listOf(oatmeal))).containsExactly(
            PhotoQuestion("Is this a packet of instant oatmeal?", listOf("Plain", "Flavoured", "Home-made")),
        )
        assertThat(PhotoQuestions.toAsk("low", listOf(oatmeal))).hasSize(1)
    }

    @Test
    fun `no questions means none to show`() {
        assertThat(PhotoQuestions.toAsk("low", emptyList())).isEmpty()
    }

    @Test
    fun `at most three questions with two to four choices each`() {
        val many = (1..5).map { q("Question $it?", "a", "b", "c", "d", "e", "f") }
        val shown = PhotoQuestions.toAsk("medium", many)
        assertThat(shown).hasSize(PhotoQuestions.MAX_QUESTIONS)
        assertThat(shown.all { it.choices.size == PhotoQuestions.MAX_CHOICES }).isTrue()
    }

    @Test
    fun `a question without two usable different choices is dropped`() {
        val shown = PhotoQuestions.toAsk(
            "medium",
            listOf(
                q("One choice?", "only"),
                q("Same twice?", "Water", "water"),
                q("", "a", "b"),
                q("Blank choices?", " ", ""),
                q("Cooked with water or milk?", "Water", "Milk"),
            ),
        )
        assertThat(shown.map { it.text }).containsExactly("Cooked with water or milk?")
    }

    @Test
    fun `repeated questions are asked once`() {
        val shown = PhotoQuestions.toAsk("low", listOf(oatmeal, oatmeal.copy(question = "is this a packet of instant oatmeal?")))
        assertThat(shown).hasSize(1)
    }

    @Test
    fun `only answers that name a real choice are used`() {
        val qs = PhotoQuestions.toAsk("low", listOf(oatmeal, q("Apple or peach?", "Apple", "Peach")))
        val answered = PhotoQuestions.answered(qs, mapOf(0 to "Flavoured", 1 to "Mango", 7 to "x"))
        assertThat(answered.map { it.first.text to it.second }).containsExactly("Is this a packet of instant oatmeal?" to "Flavoured")
    }

    @Test
    fun `tapping a choice again takes the answer back, and another choice replaces it`() {
        val one = PhotoQuestions.toggle(emptyMap(), 0, "Plain")
        assertThat(one).containsExactly(0, "Plain")
        assertThat(PhotoQuestions.toggle(one, 0, "Plain")).isEmpty()
        assertThat(PhotoQuestions.toggle(one, 0, "Flavoured")).containsExactly(0, "Flavoured")
    }

    @Test
    fun `the follow-up carries the first look and the answers, adds nothing by default and asks nothing more`() {
        val text = PhotoFollowUp.prompt(
            items = listOf(FirstLookItem("cooked oatmeal", 200.0, 190.0, 5.0, 33.0, 4.0)),
            portionAssumption = "a small bowl",
            answers = listOf(PhotoQuestion("Cooked with water or milk?", listOf("Water", "Milk")) to "Water"),
            note = "no sugar added",
        )
        assertThat(text).contains("cooked oatmeal: 200 g, 190 kcal")
        assertThat(text).contains("Cooked with water or milk? -> Water")
        assertThat(text).contains("no sugar added")
        assertThat(text).contains("do not add it by default")
        assertThat(text).contains("questions as an empty list")
        assertThat(text).contains("Keep every allergen")
    }

    @Test
    fun `an allergen the first look raised survives a follow-up that dropped it`() {
        val first = listOf(
            ModelAllergenClaim("cooked oatmeal porridge", listOf("dairy"), "may be made with milk"),
            ModelAllergenClaim("diced peaches", emptyList(), ""),
        )
        val second = listOf(
            ModelAllergenClaim("instant oatmeal packet", emptyList(), ""),
            ModelAllergenClaim("diced peaches", emptyList(), ""),
        )
        val merged = PhotoFollowUp.carryAllergens(first, second)
        assertThat(merged[0].allergens).containsExactly("dairy")
        assertThat(merged[0].reasoning).contains("first look")
        assertThat(merged[0].reasoning).contains("may be made with milk")
        assertThat(merged[1].allergens).isEmpty()
    }

    @Test
    fun `a claim goes to the food with the most words in common, else the first`() {
        val first = listOf(ModelAllergenClaim("peach cobbler", listOf("gluten"), ""))
        val second = listOf(
            ModelAllergenClaim("oatmeal", emptyList(), ""),
            ModelAllergenClaim("canned peach slices", emptyList(), ""),
        )
        assertThat(PhotoFollowUp.carryAllergens(first, second)[1].allergens).containsExactly("gluten")
        val none = listOf(ModelAllergenClaim("zzz", listOf("soy"), ""))
        assertThat(PhotoFollowUp.carryAllergens(none, second)[0].allergens).containsExactly("soy")
    }

    @Test
    fun `carrying never removes or duplicates what the follow-up itself claims`() {
        val first = listOf(ModelAllergenClaim("oatmeal", listOf("Dairy"), "milk"))
        val second = listOf(ModelAllergenClaim("oatmeal", listOf("dairy", "gluten"), "oats and milk"))
        val merged = PhotoFollowUp.carryAllergens(first, second)
        assertThat(merged.single().allergens).containsExactly("dairy", "gluten")
        assertThat(merged.single().reasoning).isEqualTo("oats and milk")
        assertThat(PhotoFollowUp.carryAllergens(first, emptyList())).isEmpty()
    }

    @Test
    fun `a hidden calorie guess is not counted`() {
        assertThat(HiddenCalorieRules.isCounted("guess")).isFalse()
        assertThat(HiddenCalorieRules.isCounted("Guess")).isFalse()
        listOf("seen_in_photo", "your_note", "your_answer").forEach { assertThat(HiddenCalorieRules.isCounted(it)).isTrue() }
        assertThat(HiddenCalorieRules.BASES).contains("guess")
    }

    @Test
    fun `a named product becomes a search, nothing becomes none`() {
        assertThat(PackagedProduct.searchQuery("  Quaker  Apples & Cinnamon\ninstant oatmeal ")).isEqualTo("Quaker Apples & Cinnamon instant oatmeal")
        assertThat(PackagedProduct.searchQuery(null)).isNull()
        assertThat(PackagedProduct.searchQuery("")).isNull()
        assertThat(PackagedProduct.searchQuery("null")).isNull()
        assertThat(PackagedProduct.searchQuery("None")).isNull()
        assertThat(PackagedProduct.searchQuery("x".repeat(200))!!.length).isEqualTo(80)
    }
}
