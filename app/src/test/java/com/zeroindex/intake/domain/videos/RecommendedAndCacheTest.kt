package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import org.junit.Test

class RecommendedAndCacheTest {

    @Test
    fun `personalise off gives fixed popular queries and ignores local data`() {
        val input = RecommendInput(underTarget = listOf(BodyRegion.SHOULDERS), equipment = setOf("dumbbell"), savedTitles = listOf("Pilates"))
        val queries = RecommendedQueries.build(personalise = false, input = input)
        assertThat(queries).isEqualTo(RecommendedQueries.GENERIC.take(RecommendedQueries.MAX_QUERIES))
        assertThat(queries.none { "shoulder" in it.query }).isTrue()
    }

    @Test
    fun `under target muscles become queries with the reason`() {
        val q = RecommendedQueries.build(true, RecommendInput(underTarget = listOf(BodyRegion.SHOULDERS, BodyRegion.BACK), equipment = setOf("dumbbell", "bodyweight")))
        assertThat(q[0].query).isEqualTo("shoulder workout dumbbell")
        assertThat(q[0].reason).isEqualTo("For: shoulders under target this week")
        assertThat(q[1].query).isEqualTo("back workout dumbbell")
    }

    @Test
    fun `equipment words`() {
        assertThat(RecommendedQueries.equipmentWords(setOf("bodyweight"))).isEqualTo("no equipment")
        assertThat(RecommendedQueries.equipmentWords(setOf("dumbbell", "bodyweight"))).isEqualTo("dumbbell")
        assertThat(RecommendedQueries.equipmentWords(setOf("barbell", "dumbbell"))).isEqualTo("gym")
        assertThat(RecommendedQueries.equipmentWords(emptySet())).isEmpty()
    }

    @Test
    fun `a saved pilates video leads to a pilates query`() {
        val q = RecommendedQueries.build(true, RecommendInput(savedTitles = listOf("Leg day, no barbell", "Beginner Pilates Class")))
        val row = q.first { it.reason == "Because you saved a pilates video" }
        assertThat(row.query).contains("pilates")
    }

    @Test
    fun `the program focus and session length add queries`() {
        val q = RecommendedQueries.build(true, RecommendInput(programFocus = "Upper / Lower", sessionMinutes = 42, equipment = setOf("bodyweight")))
        assertThat(q.map { it.query }).contains("upper lower split workout no equipment")
        assertThat(q.map { it.query }).contains("40 minute workout no equipment")
    }

    @Test
    fun `never more than the cap and no duplicates`() {
        val q = RecommendedQueries.build(
            true,
            RecommendInput(
                underTarget = listOf(BodyRegion.CHEST, BodyRegion.CHEST, BodyRegion.BACK),
                savedTitles = listOf("Yoga"), programFocus = "Push", sessionMinutes = 30,
            ),
        )
        assertThat(q.size).isAtMost(RecommendedQueries.MAX_QUERIES)
        assertThat(q.map { it.query }.toSet().size).isEqualTo(q.size)
    }

    @Test
    fun `nothing local falls back to the popular queries`() {
        assertThat(RecommendedQueries.build(true, RecommendInput())).isEqualTo(RecommendedQueries.GENERIC.take(3))
    }

    // --- cache ----------------------------------------------------------------------------------------------

    @Test
    fun `freshness follows the ttl`() {
        val now = 1_000_000_000L
        assertThat(VideoCachePolicy.isFresh(now - VideoCachePolicy.SEARCH_TTL_MS + 1, now, VideoCachePolicy.SEARCH_TTL_MS)).isTrue()
        assertThat(VideoCachePolicy.isFresh(now - VideoCachePolicy.SEARCH_TTL_MS, now, VideoCachePolicy.SEARCH_TTL_MS)).isFalse()
        // A clock that went backwards is not fresh either.
        assertThat(VideoCachePolicy.isFresh(now + 5, now, VideoCachePolicy.SEARCH_TTL_MS)).isFalse()
    }

    @Test
    fun `nothing is kept longer than thirty days`() {
        val now = 100L * VideoCachePolicy.DAY_MS
        assertThat(VideoCachePolicy.purgeCutoff(now)).isEqualTo(70L * VideoCachePolicy.DAY_MS)
        assertThat(VideoCachePolicy.isExpired(now - 29 * VideoCachePolicy.DAY_MS, now)).isFalse()
        assertThat(VideoCachePolicy.isExpired(now - 31 * VideoCachePolicy.DAY_MS, now)).isTrue()
        assertThat(VideoCachePolicy.SEARCH_TTL_MS).isLessThan(VideoCachePolicy.MAX_AGE_MS)
    }

    @Test
    fun `search keys ignore case and spacing`() {
        assertThat(VideoCachePolicy.searchKey("  Full  Body WORKOUT ", "")).isEqualTo(VideoCachePolicy.searchKey("full body workout"))
        assertThat(VideoCachePolicy.searchKey("a", "yoga")).isNotEqualTo(VideoCachePolicy.searchKey("a", "hiit"))
    }

    @Test
    fun `quota hint in plain words`() {
        assertThat(VideoQuota.searchesLeft(0)).isEqualTo(100)
        assertThat(VideoQuota.searchesLeft(350)).isEqualTo(96)
        assertThat(VideoQuota.searchesLeft(20_000)).isEqualTo(0)
        assertThat(VideoQuota.hint(0)).contains("100 searches")
        assertThat(VideoQuota.hint(10_000)).contains("used")
    }
}
