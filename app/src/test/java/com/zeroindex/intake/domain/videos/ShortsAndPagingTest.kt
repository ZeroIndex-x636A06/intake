package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.remote.youtube.YoutubeJson
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import org.junit.Test

class ShortsAndPagingTest {

    private fun v(title: String, sec: Int, description: String = "", channel: String = "Coach", w: Int? = null, h: Int? = null) =
        VideoMeta(id = title.hashCode().toString(), title = title, description = description, channelTitle = channel, categoryId = "17", durationSec = sec, embedWidth = w, embedHeight = h)

    @Test
    fun `a vertical 1 minute 2 seconds video is a Short`() {
        val owner = v("The COMPLETE Back Workout (under 1 minute)", 62, w = 360, h = 640)
        assertThat(ShortsRule.isShort(owner)).isTrue()
        assertThat(ShortsRule.reason(owner)).isEqualTo("vertical video")
        val hidden = VideoBrowse.apply(VideoBrowse.score(listOf(owner), emptyList()), onlyWorkouts = false, showEverything = false, length = null, hideShorts = true)
        assertThat(hidden.shown).isEmpty()
        assertThat(hidden.hiddenShorts).isEqualTo(1)
    }

    @Test
    fun `a horizontal 2 minute 30 second video is not a Short`() {
        assertThat(ShortsRule.isShort(v("Quick core burner", 150, w = 640, h = 360))).isFalse()
        // Words and length do not make a Short any more once the shape is known to be horizontal.
        assertThat(ShortsRule.isShort(v("Back workout (under 1 minute)", 150, w = 640, h = 360))).isFalse()
        assertThat(ShortsRule.isShort(v("Abs", 45, w = 640, h = 360))).isFalse()
    }

    @Test
    fun `a vertical video is a Short at any length`() {
        assertThat(ShortsRule.isShort(v("Long vertical", 170, w = 360, h = 640))).isTrue()
        assertThat(ShortsRule.isShort(v("Square", 100, w = 500, h = 500))).isFalse()
    }

    @Test
    fun `hashtags are a backup signal at any length and with any shape`() {
        assertThat(ShortsRule.isShort(v("Core #shorts", 900, w = 640, h = 360))).isTrue()
        assertThat(ShortsRule.isShort(v("Core", 900, "Follow along #Short"))).isTrue()
        assertThat(ShortsRule.isShort(v("Core #ytshorts", 120))).isTrue()
        assertThat(ShortsRule.isShort(v("Shortstop drills", 900))).isFalse()
    }

    @Test
    fun `with no player size only 60 seconds or less counts by length`() {
        assertThat(ShortsRule.isShort(v("Abs", 60))).isTrue()
        assertThat(ShortsRule.isShort(v("Abs", 1))).isTrue()
        assertThat(ShortsRule.isShort(v("Abs", 62))).isFalse()
        assertThat(ShortsRule.isShort(v("Abs", 0))).isFalse()
        assertThat(ShortsRule.isShort(v("Back workout (under 1 minute)", 150))).isFalse()
    }

    @Test
    fun `the player size is read from videos list`() {
        val body = """{"items":[{"id":"abcdefghijk","snippet":{"title":"t"},"contentDetails":{"duration":"PT1M2S"},"player":{"embedHeight":"640","embedWidth":360}}]}"""
        val meta = YoutubeJson.videos(body).single()
        assertThat(meta.embedHeight).isEqualTo(640)
        assertThat(meta.embedWidth).isEqualTo(360)
        assertThat(meta.isVertical).isTrue()
        assertThat(YoutubeJson.videos("""{"items":[{"id":"abcdefghijk","snippet":{"title":"t"}}]}""").single().isVertical).isNull()
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
