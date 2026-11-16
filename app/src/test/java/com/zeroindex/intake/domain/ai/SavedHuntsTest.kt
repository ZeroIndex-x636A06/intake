package com.zeroindex.intake.domain.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SavedHuntsTest {

    private fun hunt(id: String, at: Long, titles: List<String> = listOf("Chili")) = SavedHunt(
        id = id,
        createdAt = at,
        query = SavedHuntQuery(minProteinPerServing = 40, maxKcalPerServing = 650, cuisines = listOf("Japanese"), notes = "one pan"),
        counts = SavedHuntCounts(kept = 3, close = 1, adaptable = 2, removed = 4, unverified = 1),
        costUsd = 0.046,
        rounds = 2,
        results = titles.map { SavedHuntRecipe(title = it, sourceUrl = "https://example.test/$it", ingredients = listOf("1 egg")) },
    )

    @Test
    fun `a saved search survives encoding with its parameters, counts, cost, time and results`() {
        val h = hunt("a", 1_000L, listOf("Chili", "Soup"))
        val back = RecentHunts.decode(RecentHunts.encode(listOf(h))).single()
        assertThat(back).isEqualTo(h)
        assertThat(back.results.map { it.sourceUrl }).containsExactly("https://example.test/Chili", "https://example.test/Soup")
        assertThat(back.query.toQuery(setOf("dairy")).avoidedAllergens).containsExactly("dairy")
    }

    @Test
    fun `damaged or empty storage reads as no searches and never throws`() {
        assertThat(RecentHunts.decode(null)).isEmpty()
        assertThat(RecentHunts.decode("")).isEmpty()
        assertThat(RecentHunts.decode("{not json")).isEmpty()
    }

    @Test
    fun `newest first`() {
        val all = listOf(hunt("old", 1), hunt("new", 3), hunt("mid", 2))
        assertThat(RecentHunts.live(all).map { it.id }).containsExactly("new", "mid", "old").inOrder()
    }

    @Test
    fun `only twenty are kept and the oldest are soft deleted, not erased`() {
        var all = emptyList<SavedHunt>()
        for (i in 1..23) all = RecentHunts.add(all, hunt("h$i", i.toLong()), now = 100L + i)
        val live = RecentHunts.live(all)
        assertThat(live).hasSize(RecentHunts.CAP)
        assertThat(live.first().id).isEqualTo("h23")
        assertThat(live.last().id).isEqualTo("h4")
        val gone = all.filter { it.isDeleted }
        assertThat(gone.map { it.id }).containsExactly("h1", "h2", "h3")
        // The marker stays, its payload does not.
        assertThat(gone.all { it.results.isEmpty() && it.deletedAt != null }).isTrue()
    }

    @Test
    fun `soft delete hides a search and keeps a marker`() {
        val all = RecentHunts.softDelete(listOf(hunt("a", 1), hunt("b", 2)), "a", now = 50L)
        assertThat(RecentHunts.live(all).map { it.id }).containsExactly("b")
        assertThat(all.single { it.id == "a" }.deletedAt).isEqualTo(50L)
    }

    @Test
    fun `saving the same search again replaces it instead of listing it twice`() {
        val first = RecentHunts.add(emptyList(), hunt("a", 1, listOf("Chili")), now = 2L)
        val more = RecentHunts.add(first, hunt("a", 1, listOf("Chili", "Soup")), now = 3L)
        assertThat(RecentHunts.live(more)).hasSize(1)
        assertThat(RecentHunts.live(more).single().results).hasSize(2)
    }

    @Test
    fun `old markers are trimmed so the file cannot grow without end`() {
        var all = emptyList<SavedHunt>()
        for (i in 1..120) all = RecentHunts.add(all, hunt("h$i", i.toLong()), now = 1000L + i)
        assertThat(all.count { it.isDeleted }).isAtMost(RecentHunts.MAX_MARKERS)
        assertThat(RecentHunts.live(all)).hasSize(RecentHunts.CAP)
    }
}
