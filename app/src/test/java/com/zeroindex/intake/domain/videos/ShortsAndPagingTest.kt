package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.remote.youtube.YoutubeJson
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import org.junit.Test

class ShortsAndPagingTest {

    private fun v(title: String, sec: Int, description: String = "", channel: String = "Coach") =
        VideoMeta(id = title.hashCode().toString(), title = title, description = description, channelTitle = channel, categoryId = "17", durationSec = sec)

    @Test
    fun `the owner's 1 minute 2 seconds back workout is a Short`() {
        val owner = v("The COMPLETE Back Workout (under 1 minute)", 62)
        assertThat(ShortsRule.isShort(owner)).isTrue()
        assertThat(ShortsRule.reason(owner)).isEqualTo("says it is a Short")
        val hidden = VideoBrowse.apply(VideoBrowse.score(listOf(owner), emptyList()), onlyWorkouts = false, showEverything = false, length = null, hideShorts = true)
        assertThat(hidden.shown).isEmpty()
        assertThat(hidden.hiddenShorts).isEqualTo(1)
    }

    @Test
    fun `sixty seconds or less is always a Short`() {
        assertThat(ShortsRule.isShort(v("Abs", 60))).isTrue()
        assertThat(ShortsRule.isShort(v("Abs", 1))).isTrue()
        assertThat(ShortsRule.isShort(v("Abs", 0))).isFalse()
    }

    @Test
    fun `hashtags make a Short at any length`() {
        assertThat(ShortsRule.isShort(v("Core #shorts", 900))).isTrue()
        assertThat(ShortsRule.isShort(v("Core", 900, "Follow along #Short"))).isTrue()
        assertThat(ShortsRule.isShort(v("Core #ytshorts", 120))).isTrue()
        assertThat(ShortsRule.isShort(v("Core #shortsvideo", 120))).isTrue()
        assertThat(ShortsRule.isShort(v("Shortstop drills", 900))).isFalse()
    }

    @Test
    fun `up to three minutes a Short is told by its words`() {
        assertThat(ShortsRule.isShort(v("Leg day in 60 seconds", 90))).isTrue()
        assertThat(ShortsRule.isShort(v("Biceps in 2 minutes", 150))).isTrue()
        assertThat(ShortsRule.isShort(v("45-second ab finisher", 170))).isTrue()
        assertThat(ShortsRule.isShort(v("Chest", 120, "A YouTube Short for your push day"))).isTrue()
        assertThat(ShortsRule.isShort(v("Back", 100, "back day, under a minute"))).isTrue()
    }

    @Test
    fun `those words do not count over three minutes or at an unknown length`() {
        assertThat(ShortsRule.isShort(v("Back workout (under 1 minute)", 181))).isFalse()
        assertThat(ShortsRule.isShort(v("Back workout (under 1 minute)", 0))).isFalse()
        assertThat(ShortsRule.isShort(v("Full body in 20 minutes", 150))).isFalse()
        assertThat(ShortsRule.isShort(v("30 minute full body workout", 1800))).isFalse()
        assertThat(ShortsRule.isShort(v("Rest 90 seconds between sets", 1800))).isFalse()
    }

    @Test
    fun `filters count and words`() {
        assertThat(VideoBrowse.activeFilterCount(null, null, null)).isEqualTo(0)
        assertThat(VideoBrowse.activeFilterCount(VideoStyle.PUSH, BodyRegion.CHEST, VideoLength.UNDER_15)).isEqualTo(3)
        assertThat(VideoBrowse.searchQuery("beginner", null, BodyRegion.BACK)).isEqualTo("beginner back workout")
        assertThat(VideoBrowse.cacheFilters(VideoStyle.PUSH, BodyRegion.CHEST)).isEqualTo("PUSH+CHEST")
        assertThat(VideoBrowse.cacheFilters(null, null)).isEmpty()
    }

    @Test
    fun `page and token keys keep page one on the plain key`() {
        val base = VideoCachePolicy.searchKey("back workout", "")
        assertThat(VideoPaging.pageKey(base, 1)).isEqualTo(base)
        assertThat(VideoPaging.pageKey(base, 2)).isNotEqualTo(base)
        assertThat(VideoPaging.pageKey(base, 2)).isNotEqualTo(VideoPaging.pageKey(base, 3))
        assertThat(VideoPaging.tokenKey(base, 1)).isNotEqualTo(VideoPaging.pageKey(base, 2))
    }

    @Test
    fun `paging stops at the cap, at the end and when the quota estimate is low`() {
        assertThat(VideoPaging.canLoadMore(1, VideoPaging.MAX_SEARCH_PAGES, hasMore = true, usedUnits = 0)).isTrue()
        assertThat(VideoPaging.canLoadMore(VideoPaging.MAX_SEARCH_PAGES, VideoPaging.MAX_SEARCH_PAGES, true, 0)).isFalse()
        assertThat(VideoPaging.canLoadMore(1, VideoPaging.MAX_SEARCH_PAGES, hasMore = false, usedUnits = 0)).isFalse()
        // 9,500 of 10,000 units used: 5 searches left, which is kept back.
        assertThat(VideoPaging.canLoadMore(1, VideoPaging.MAX_SEARCH_PAGES, true, 9_500)).isFalse()
        assertThat(VideoPaging.canLoadMore(1, VideoPaging.MAX_SEARCH_PAGES, true, 9_000)).isTrue()
    }

    @Test
    fun `the next page is wanted within five items of the end`() {
        assertThat(VideoPaging.nearEnd(lastVisibleIndex = 20, total = 30)).isFalse()
        assertThat(VideoPaging.nearEnd(lastVisibleIndex = 24, total = 30)).isTrue()
        assertThat(VideoPaging.nearEnd(lastVisibleIndex = 29, total = 30)).isTrue()
        assertThat(VideoPaging.nearEnd(lastVisibleIndex = -1, total = 0)).isFalse()
    }

    @Test
    fun `the next page token is read from a search answer`() {
        assertThat(YoutubeJson.nextPageToken("""{"nextPageToken":"CBkQAA","items":[]}""")).isEqualTo("CBkQAA")
        assertThat(YoutubeJson.nextPageToken("""{"items":[]}""")).isNull()
        assertThat(YoutubeJson.nextPageToken("not json")).isNull()
    }

    @Test
    fun `saved videos lead to a search from their channel and shared words`() {
        val saved = listOf(
            v("Pilates Core Class for Beginners", 1200, channel = "Move With Mia"),
            v("Pilates Legs and Glutes", 900, channel = "Move With Mia"),
            v("Heavy deadlift tutorial", 600, channel = "Iron Dan"),
        )
        assertThat(SavedSimilarity.topChannel(saved.map { it.channelTitle })).isEqualTo("Move With Mia")
        assertThat(SavedSimilarity.keywords(saved.map { it.title }).first()).isEqualTo("pilates")
        val q = SavedSimilarity.query(saved)!!
        assertThat(q).contains("Move With Mia")
        assertThat(q).contains("pilates")
        assertThat(SavedSimilarity.reason(saved)).isEqualTo("Because you saved videos from Move With Mia")
        assertThat(SavedSimilarity.query(emptyList())).isNull()
    }

    @Test
    fun `Recommended uses the saved channel when no style word is saved`() {
        val q = RecommendedQueries.build(
            true,
            RecommendInput(savedTitles = listOf("Heavy deadlift tutorial"), savedChannels = listOf("Iron Dan")),
        )
        val row = q.first { it.reason.startsWith("Because you saved") }
        assertThat(row.query).contains("Iron Dan")
        assertThat(row.query).contains("deadlift")
    }
}
