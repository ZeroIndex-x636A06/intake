package com.zeroindex.intake.domain.nearby

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NearbyDomainTest {

    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/nearby/$name")).bufferedReader().readText()

    // ---- travel radius ----

    @Test
    fun `radius follows the documented speeds`() {
        // 4.8 km/h for 10 min = 800 m, over 1.3 for the road being longer than the straight line.
        assertThat(TravelRadius.meters(10, TravelMode.WALK)).isEqualTo(615)
        // 40 km/h for 10 min = 6667 m, over 1.3.
        assertThat(TravelRadius.meters(10, TravelMode.DRIVE)).isEqualTo(5128)
        assertThat(TravelRadius.meters(15, TravelMode.DRIVE)).isEqualTo(7692)
        assertThat(TravelBudget(15, TravelMode.WALK).label).isEqualTo("15 min walk")
    }

    @Test
    fun `old mile settings migrate to a reach no smaller than asked`() {
        assertThat(TravelRadius.fromLegacyMiles(0.5)).isEqualTo(TravelBudget(10, TravelMode.WALK))
        assertThat(TravelRadius.fromLegacyMiles(1.0)).isEqualTo(TravelBudget(10, TravelMode.DRIVE))
        assertThat(TravelRadius.fromLegacyMiles(2.0)).isEqualTo(TravelBudget(10, TravelMode.DRIVE))
        assertThat(TravelRadius.fromLegacyMiles(5.0)).isEqualTo(TravelBudget(15, TravelMode.DRIVE))
        assertThat(TravelRadius.fromLegacyMiles(null)).isEqualTo(TravelBudget.DEFAULT)
        assertThat(TravelRadius.clampMinutes(12)).isEqualTo(10)
        assertThat(TravelRadius.clampMinutes(15)).isEqualTo(15)
    }

    // ---- Overpass ----

    @Test
    fun `the query asks for the three amenities around the point in one request`() {
        val q = OverpassParser.query(40.0, -75.0, 5128)
        assertThat(q).contains("restaurant|fast_food|cafe")
        assertThat(q).contains("(around:5128,40.0000,-75.0000)")
        assertThat(q).contains("out center tags")
    }

    private val places by lazy { OverpassParser.parse(fixture("overpass-sample.json"), 40.0, -75.0, 5128) }

    @Test
    fun `places are parsed, filtered and sorted by distance`() {
        // Dropped: the bar, the unnamed place, the one 170 km away.
        assertThat(places.map { it.osmId }).containsExactly("node/101", "node/103", "way/201", "node/107", "node/102").inOrder()
        assertThat(places.zipWithNext().all { (a, b) -> a.distanceM <= b.distanceM }).isTrue()
        assertThat(places.first().distanceM).isWithin(2.0).of(111.2)
    }

    @Test
    fun `brand, wikidata, website and cuisine are read, and a way uses its centre`() {
        val mc = places.first { it.osmId == "node/101" }
        assertThat(mc.brand).isEqualTo("McDonald's")
        assertThat(mc.brandWikidata).isEqualTo("Q38076")
        assertThat(mc.cuisine).isEqualTo("burger")
        assertThat(mc.website).isEqualTo("https://www.mcdonalds.com/us/en-us/location/x.html")
        val subway = places.first { it.osmId == "way/201" }
        assertThat(subway.lat).isEqualTo(40.0030)
        assertThat(subway.website).isEqualTo("https://subway.com")
        assertThat(places.first { it.osmId == "node/107" }.website).isNull()
    }

    @Test
    fun `branches of one chain collapse to the nearest`() {
        val deduped = OverpassParser.dedupeByBrand(places)
        assertThat(deduped.map { it.osmId }).containsExactly("node/101", "node/103", "way/201", "node/107").inOrder()
    }

    @Test
    fun `garbage in gives nothing`() {
        assertThat(OverpassParser.parse("<html>busy</html>", 0.0, 0.0, 1000)).isEmpty()
        assertThat(OverpassParser.parse("{\"elements\":[]}", 0.0, 0.0, 1000)).isEmpty()
    }

    // ---- chain table ----

    private val chains by lazy {
        ChainDirectory.parse(
            """{"chains":[
              {"name":"McDonald's","wikidata":"Q38076","aliases":["mcdonalds"],"urls":[{"url":"https://m.example/n","kind":"page","status":"blocked"}]},
              {"name":"Subway","aliases":["subway"],"urls":[{"url":"https://s.example/n","kind":"page","status":"verified"}]}]}""",
        )
    }

    @Test
    fun `a place matches its chain by wikidata first and by name second`() {
        assertThat(ChainDirectory.match(places.first { it.osmId == "node/102" }, chains)?.name).isEqualTo("McDonald's")
        assertThat(ChainDirectory.match(places.first { it.osmId == "way/201" }, chains)?.urls?.single()?.url).isEqualTo("https://s.example/n")
        assertThat(ChainDirectory.match(places.first { it.osmId == "node/103" }, chains)).isNull()
    }

    @Test
    fun `the bundled table covers the named chains`() {
        val text = java.io.File("src/main/assets/nearby_chains.json").takeIf { it.isFile }?.readText()
            ?: java.io.File("app/src/main/assets/nearby_chains.json").readText()
        val all = ChainDirectory.parse(text)
        val names = listOf(
            "McDonald's", "Burger King", "Wendy's", "Taco Bell", "Chick-fil-A", "Subway", "Chipotle", "Arby's", "Panera Bread",
            "Starbucks", "Domino's", "Pizza Hut", "KFC", "Popeyes", "Sonic Drive-In", "Dairy Queen", "Panda Express", "Five Guys",
            "Jimmy John's", "Jersey Mike's", "Dunkin'", "Culver's", "Zaxby's",
        )
        assertThat(all.map { it.name }).containsAtLeastElementsIn(names)
        assertThat(all.all { it.urls.isNotEmpty() && it.urls.all { u -> u.url.startsWith("https://") } }).isTrue()
    }

    // ---- link scoring ----

    private val page = """
        <a href="/about">About us</a>
        <a href="/menu/nutrition-info">Nutrition Information</a>
        <a href="https://cdn.example.org/files/allergen-guide.pdf">Allergen guide (PDF)</a>
        <a href="/our-menu">Our Menu</a>
        <a href="/careers">Careers</a>
        <a href="https://www.facebook.com/menu">Menu on Facebook</a>
        <a href="https://other.example/menu">Menu elsewhere</a>
        <a href="mailto:hi@x.example">Menu mail</a>
        <a href='/gift-cards'>Gift cards and menu</a>
    """.trimIndent()

    @Test
    fun `menu links are ranked, resolved and filtered`() {
        val links = MenuLinkScorer.candidates("https://www.joes.example/home", page)
        assertThat(links.map { it.url }).containsExactly(
            "https://www.joes.example/menu/nutrition-info",
            "https://cdn.example.org/files/allergen-guide.pdf",
            "https://www.joes.example/our-menu",
        ).inOrder()
    }

    @Test
    fun `a page with no such links gives none`() {
        assertThat(MenuLinkScorer.candidates("https://a.example", "<a href='/x'>Home</a><a href='/y'>Contact</a>")).isEmpty()
    }

    // ---- saved sources ----

    @Test
    fun `a saved source is found by normalised name or chain`() {
        val known = listOf(SourceMatch.Known("s1", "McDonalds"), SourceMatch.Known("s2", "Corner Beans Cafe"))
        val mc = places.first { it.osmId == "node/101" }
        assertThat(SourceMatch.find(mc, chains[0], known)?.id).isEqualTo("s1")
        assertThat(SourceMatch.find(places.first { it.osmId == "node/103" }, null, known)?.id).isEqualTo("s2")
        assertThat(SourceMatch.find(places.first { it.osmId == "way/201" }, chains[1], known)).isNull()
    }

    // ---- pipeline ----

    private fun rows() = listOf(RestaurantRow("a", "A", 100.0), RestaurantRow("b", "B", 200.0))

    @Test
    fun `rows move forward and finish the search when all are done`() {
        var s = NearbyPipeline.reduce(PipelineState(), PipelineEvent.Started)
        assertThat(s.stage).isEqualTo(SearchStage.LOCATING)
        s = NearbyPipeline.reduce(s, PipelineEvent.Located)
        s = NearbyPipeline.reduce(s, PipelineEvent.Restaurants(rows()))
        assertThat(s.stage).isEqualTo(SearchStage.WORKING)
        s = NearbyPipeline.reduce(s, PipelineEvent.Phase("a", RowPhase.LOOKING))
        assertThat(s.rows[0].statusText).isEqualTo("looking for menu…")
        s = NearbyPipeline.reduce(s, PipelineEvent.Phase("a", RowPhase.READING))
        assertThat(s.rows[0].statusText).isEqualTo("reading menu…")
        s = NearbyPipeline.reduce(s, PipelineEvent.Phase("a", RowPhase.LOOKING)) // never goes back
        assertThat(s.rows[0].phase).isEqualTo(RowPhase.READING)
        s = NearbyPipeline.reduce(s, PipelineEvent.Menu("a", MenuOrigin.CHAIN, 3))
        assertThat(s.rows[0].statusText).isEqualTo("3 healthier picks")
        assertThat(s.stage).isEqualTo(SearchStage.WORKING)
        s = NearbyPipeline.reduce(s, PipelineEvent.NoMenu("b", null))
        assertThat(s.rows[1].statusText).isEqualTo("no menu found")
        assertThat(s.stage).isEqualTo(SearchStage.DONE)
        assertThat(NearbyPipeline.summary(s)).isEqualTo("2 restaurants checked · 1 with a menu · 3 picks")
    }

    @Test
    fun `stopping freezes the list`() {
        var s = PipelineState(stage = SearchStage.WORKING, rows = rows())
        s = NearbyPipeline.reduce(s, PipelineEvent.Stopped)
        s = NearbyPipeline.reduce(s, PipelineEvent.Menu("a", MenuOrigin.SAVED, 5))
        assertThat(s.rows[0].picks).isEqualTo(0)
        assertThat(s.stage).isEqualTo(SearchStage.STOPPED)
        assertThat(NearbyPipeline.summary(s)).startsWith("Stopped.")
    }

    @Test
    fun `an empty area finishes at once and a failure is kept`() {
        val empty = NearbyPipeline.reduce(PipelineState(stage = SearchStage.FINDING), PipelineEvent.Restaurants(emptyList()))
        assertThat(empty.stage).isEqualTo(SearchStage.DONE)
        val failed = NearbyPipeline.reduce(PipelineState(stage = SearchStage.FINDING), PipelineEvent.Failed("Map server busy"))
        assertThat(failed.stage).isEqualTo(SearchStage.FAILED)
        assertThat(NearbyPipeline.summary(failed)).isEqualTo("Map server busy")
    }

    @Test
    fun `AI lookups and their cost are counted`() {
        var s = PipelineState(stage = SearchStage.WORKING, rows = rows())
        s = NearbyPipeline.reduce(s, PipelineEvent.AiUsed(0.02))
        s = NearbyPipeline.reduce(s, PipelineEvent.AiUsed(0.03))
        assertThat(s.aiLookups).isEqualTo(2)
        assertThat(s.costUsd).isWithin(1e-9).of(0.05)
    }

    @Test
    fun `initial rows keep distance order and the cap`() {
        val many = (1..50).map { places.first().copy(osmId = "n/$it", distanceM = it.toDouble()) }
        val r = NearbyPipeline.initialRows(many)
        assertThat(r).hasSize(NearbyPipeline.MAX_RESTAURANTS)
        assertThat(r.first().key).isEqualTo("n/1")
    }
}
