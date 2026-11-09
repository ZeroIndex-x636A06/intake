package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuStructuredTest {

    private fun page(ld: String) = """<html><head><script type="application/ld+json">$ld</script></head><body>menu</body></html>"""

    @Test
    fun `menu items in a restaurant graph are read as published`() {
        val items = MenuStructured.parse(
            page(
                """
                {"@context":"https://schema.org","@type":"Restaurant","name":"Cafe",
                 "hasMenu":{"@type":"Menu","hasMenuSection":[
                   {"@type":"MenuSection","name":"Mains","hasMenuItem":[
                     {"@type":"MenuItem","name":"Chicken bowl","description":"Rice, chicken, greens",
                      "nutrition":{"@type":"NutritionInformation","calories":"620 calories","proteinContent":"45 g","carbohydrateContent":"60g","fatContent":"18 g"},
                      "offers":{"@type":"Offer","price":"13.50","priceCurrency":"USD"}},
                     {"@type":"MenuItem","name":"Side salad"}
                   ]}]}}
                """.trimIndent(),
            ),
        )
        assertThat(items.map { it.name }).containsExactly("Chicken bowl", "Side salad").inOrder()
        val bowl = items.first()
        assertThat(bowl.description).isEqualTo("Rice, chicken, greens")
        assertThat(bowl.kcal).isEqualTo(620.0)
        assertThat(bowl.protein).isEqualTo(45.0)
        assertThat(bowl.carbs).isEqualTo(60.0)
        assertThat(bowl.fat).isEqualTo(18.0)
        assertThat(bowl.priceCents).isEqualTo(1350.0)
        val salad = items.last()
        assertThat(salad.kcal).isNull()
        assertThat(salad.priceCents).isNull()
    }

    @Test
    fun `a graph and duplicate names are handled`() {
        val items = MenuStructured.parse(
            page("""{"@graph":[{"@type":"MenuItem","name":"Soup"},{"@type":["MenuItem"],"name":"soup"},{"@type":"Thing","name":"Other"}]}"""),
        )
        assertThat(items.map { it.name }).containsExactly("Soup")
    }

    @Test
    fun `a page with no structured menu returns nothing so the text path can decide`() {
        assertThat(MenuStructured.parse("<html><body><h1>Menu</h1><p>Burger 12</p></body></html>")).isEmpty()
        assertThat(MenuStructured.parse(page("""{"@type":"Recipe","name":"Soup"}"""))).isEmpty()
        assertThat(MenuStructured.parse(page("{not json"))).isEmpty()
        assertThat(MenuStructured.parse("")).isEmpty()
    }

    @Test
    fun `kilojoules are converted and items without a name are skipped`() {
        val items = MenuStructured.parse(
            page("""[{"@type":"MenuItem","name":"Bar","nutrition":{"calories":"418.4 kJ"}},{"@type":"MenuItem","description":"no name"}]"""),
        )
        assertThat(items).hasSize(1)
        assertThat(items.single().kcal).isWithin(0.01).of(100.0)
    }
}
