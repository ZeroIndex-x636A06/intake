package com.zeroindex.intake.domain.nearby

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class NearbyAiBatchTest {

    private fun place(id: String, name: String, distance: Double, brand: String? = null, wikidata: String? = null, alt: List<String> = emptyList()) =
        NearbyPlace(id, name, brand, wikidata, null, null, "fast_food", 0.0, 0.0, distance, alt)

    private val chains by lazy {
        ChainDirectory.parse(File("src/main/assets/nearby_chains.json").readText())
    }

    private fun chainOf(p: NearbyPlace) = ChainDirectory.match(p, chains)?.name

    // ---- chain matching ----

    @Test
    fun `DQ Grill and Chill is Dairy Queen`() {
        assertThat(chainOf(place("n1", "DQ Grill & Chill", 1.0))).isEqualTo("Dairy Queen")
        assertThat(chainOf(place("n2", "DQ Grill & Chill Restaurant", 1.0))).isEqualTo("Dairy Queen")
        assertThat(chainOf(place("n3", "Dairy Queen", 1.0))).isEqualTo("Dairy Queen")
        assertThat(chainOf(place("n4", "Local Name", 1.0, brand = "Dairy Queen Grill & Chill"))).isEqualTo("Dairy Queen")
    }

    @Test
    fun `Popeyes Louisiana Kitchen is Popeyes by name, brand or wikidata`() {
        assertThat(chainOf(place("n1", "Popeyes Louisiana Kitchen", 1.0))).isEqualTo("Popeyes")
        assertThat(chainOf(place("n2", "Popeyes", 1.0))).isEqualTo("Popeyes")
        assertThat(chainOf(place("n3", "Chicken shop", 1.0, brand = "Popeyes Louisiana Kitchen"))).isEqualTo("Popeyes")
        assertThat(chainOf(place("n4", "Chicken shop", 1.0, wikidata = "Q1330910"))).isEqualTo("Popeyes")
    }

    @Test
    fun `Hardees matches with or without the apostrophe`() {
        assertThat(chainOf(place("n1", "Hardee's", 1.0))).isEqualTo("Hardee's")
        assertThat(chainOf(place("n2", "Hardees", 1.0))).isEqualTo("Hardee's")
        assertThat(chainOf(place("n3", "Hardee’s", 1.0))).isEqualTo("Hardee's")
    }

    @Test
    fun `the added chains are all matched`() {
        listOf(
            "Carl's Jr.", "Krystal", "Huddle House", "Waffle House", "Bojangles", "Cook Out", "Whataburger", "Jack in the Box",
            "Raising Cane's", "Wingstop", "Buffalo Wild Wings", "Little Caesars", "Papa John's", "Arby's", "Zaxby's", "IHOP",
            "Denny's", "Applebee's", "Chili's", "Olive Garden", "Texas Roadhouse", "Cracker Barrel", "Panda Express", "Qdoba",
            "Moe's Southwest Grill", "Firehouse Subs",
        ).forEach { name -> assertThat(chainOf(place("x", name, 1.0))).isEqualTo(name) }
    }

    @Test
    fun `a local place that only starts with a chain's name is not that chain`() {
        assertThat(chainOf(place("n1", "Popeyes Sports Bar and Tavern", 1.0))).isNull()
        assertThat(chainOf(place("n2", "Dick's Wings and Grill", 1.0))).isNull()
        assertThat(chainOf(place("n3", "DQ Pizza Palace", 1.0))).isNull()
    }

    @Test
    fun `alt names count`() {
        assertThat(chainOf(place("n1", "Restaurant 1234", 1.0, alt = listOf("Krystal")))).isEqualTo("Krystal")
    }

    // ---- batching and the cap ----

    private val twenty = (1..30).map { place("n$it", "Place $it", (31 - it) * 10.0) }

    @Test
    fun `batches take the nearest places first, eight to a call, at most three calls`() {
        val plan = NearbyAiBatch.plan(twenty)
        assertThat(plan).hasSize(3)
        assertThat(plan.map { it.size }).containsExactly(8, 8, 8)
        val flat = plan.flatten()
        assertThat(flat.zipWithNext().all { (a, b) -> a.distanceM <= b.distanceM }).isTrue()
        assertThat(flat.first().osmId).isEqualTo("n30")
        assertThat(flat.map { it.osmId }).doesNotContain("n1")
    }

    @Test
    fun `few places make one call and none make no call`() {
        assertThat(NearbyAiBatch.plan(twenty.take(5))).hasSize(1)
        assertThat(NearbyAiBatch.plan(emptyList())).isEmpty()
        assertThat(NearbyAiBatch.callsFor(0)).isEqualTo(0)
        assertThat(NearbyAiBatch.callsFor(5)).isEqualTo(1)
        assertThat(NearbyAiBatch.callsFor(9)).isEqualTo(2)
        assertThat(NearbyAiBatch.callsFor(100)).isEqualTo(3)
        assertThat(NearbyAiBatch.coveredBy(100)).isEqualTo(24)
        assertThat(NearbyAiBatch.coveredBy(5)).isEqualTo(5)
    }

    @Test
    fun `the cost note names the calls and what they cover before anything runs`() {
        assertThat(NearbyAiBatch.costNote(5)).startsWith("1 AI call for 5 restaurants")
        assertThat(NearbyAiBatch.costNote(30)).startsWith("3 AI calls for the nearest 24 restaurants")
        assertThat(NearbyPipeline.MAX_AI_LOOKUPS).isEqualTo(3)
    }

    // ---- prompt ----

    @Test
    fun `the prompt lists every restaurant by id and asks for known allergens`() {
        val asked = NearbyAiBatch.restaurantsOf(listOf(place("a", "Hardee's", 1.0), place("b", "Dick's Wings and Grill", 2.0)), { it.name == "Hardee's" }) { it.name }
        val user = NearbyAiBatch.userText(asked)
        assertThat(user).contains("r1: Hardee's (a chain)")
        assertThat(user).contains("r2: Dick's Wings and Grill (local or independent)")
        val system = NearbyAiBatch.system(emptySet())
        assertThat(system).contains("containsAllergens")
        assertThat(system).contains("Hotcakes")
        assertThat(system).contains("milk, egg and wheat")
        assertThat(system).contains("Never guess an address")
        assertThat(system).doesNotContain("The user avoids")
        assertThat(NearbyAiBatch.system(setOf("dairy"))).contains("The user avoids dairy")
    }

    // ---- parsing ----

    private val asked = NearbyAiBatch.restaurantsOf(
        listOf(place("a", "Hardee's", 1.0), place("b", "Dick's Wings", 2.0), place("c", "Krystal", 3.0)), { true }) { it.name }

    @Test
    fun `an answer is read and matched to the restaurants asked about`() {
        val json = """
            {"restaurants":[
              {"id":"r1","nutritionUrl":"https://www.hardees.com/nutrition.pdf","items":[]},
              {"id":"r2","nutritionUrl":"","items":[
                {"name":"Wings","description":"","kcal":600,"protein":40,"carbs":10,"fat":40,"containsAllergens":["milk"]},
                {"name":"Fries","description":"","kcal":300,"protein":4,"carbs":40,"fat":15,"containsAllergens":[]},
                {"name":"Slaw","description":"","kcal":150,"protein":2,"carbs":12,"fat":10,"containsAllergens":["egg"]},
                {"name":"No numbers","description":"","kcal":null,"protein":null,"carbs":null,"fat":null,"containsAllergens":[]}]},
              {"id":"r9","nutritionUrl":"https://elsewhere.example/x","items":[]}
            ]}
        """.trimIndent()
        val answer = checkNotNull(NearbyAiBatch.parse(json))
        val r = NearbyAiBatch.resolve(asked, answer)
        assertThat(r.keys).containsExactly("r1", "r2")
        assertThat(r.getValue("r1").nutritionUrl).isEqualTo("https://www.hardees.com/nutrition.pdf")
        assertThat(r.getValue("r2").nutritionUrl).isNull()
        assertThat(r.getValue("r2").items.map { it.name }).containsExactly("Wings", "Fries", "Slaw").inOrder()
        assertThat(r.getValue("r2").items.first().containsAllergens).containsExactly("milk")
        assertThat(r).doesNotContainKey("r3")
    }

    @Test
    fun `too few usable items is no menu, and a bad address is dropped`() {
        val answer = BatchAnswer(
            listOf(
                BatchEntry("r1", "http://insecure.example/menu", listOf(BatchItem("A", kcal = 1.0, protein = 1.0), BatchItem("B", kcal = 1.0, protein = 1.0))),
                BatchEntry("r2", "https://x.example/a b", emptyList()),
            ),
        )
        val r = NearbyAiBatch.resolve(asked, answer)
        assertThat(r.getValue("r1").nutritionUrl).isNull()
        assertThat(r.getValue("r1").items).isEmpty()
        assertThat(r.getValue("r2").nutritionUrl).isNull()
    }

    @Test
    fun `text that is not the expected json gives no answer`() {
        assertThat(NearbyAiBatch.parse("sorry, I could not")).isNull()
        assertThat(NearbyAiBatch.parse("")).isNull()
    }
}
