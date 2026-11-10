package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoBrowseTest {

    private fun video(id: String, title: String, sec: Int = 1800, category: String = "17", channel: String = "Fit Coach") =
        VideoMeta(id = id, title = title, categoryId = category, durationSec = sec, channelTitle = channel)

    private val workout = video("a", "30 minute full body workout, follow along")
    private val vlog = video("b", "My gym vlog and what I eat", category = "22", channel = "Daily Life")

    @Test
    fun `the search words are the typed text plus the style word`() {
        assertThat(VideoBrowse.searchQuery("  shoulder   mobility ", null)).isEqualTo("shoulder mobility")
        assertThat(VideoBrowse.searchQuery("beginner", VideoStyle.PILATES)).isEqualTo("beginner pilates")
        assertThat(VideoBrowse.searchQuery("", VideoStyle.YOGA)).isEqualTo("yoga")
        assertThat(VideoBrowse.searchQuery("pilates abs", VideoStyle.PILATES)).isEqualTo("pilates abs")
    }

    @Test
    fun `only the style changes the cache key`() {
        assertThat(VideoBrowse.cacheFilters(null)).isEmpty()
        assertThat(VideoBrowse.cacheFilters(VideoStyle.HIIT)).isEqualTo("HIIT")
    }

    @Test
    fun `workouts only hides what the classifier rejects and counts it`() {
        val scored = VideoBrowse.score(listOf(workout, vlog), emptyList())
        val filtered = VideoBrowse.apply(scored, onlyWorkouts = true, showEverything = false, length = null)
        assertThat(filtered.shown.map { it.video.id }).containsExactly("a")
        assertThat(filtered.hiddenAsNotWorkouts).isEqualTo(1)
        assertThat(filtered.hiddenLine()).isEqualTo("1 of 2 results hidden as not workouts.")
    }

    @Test
    fun `show everything lifts the filter for the search and changes no score`() {
        val scored = VideoBrowse.score(listOf(workout, vlog), emptyList())
        val all = VideoBrowse.apply(scored, onlyWorkouts = true, showEverything = true, length = null)
        assertThat(all.shown).hasSize(2)
        assertThat(all.hiddenLine()).isNull()
        assertThat(all.shown.map { it.verdict.score }).isEqualTo(scored.map { it.verdict.score })
    }

    @Test
    fun `with the filter off nothing is hidden`() {
        val scored = VideoBrowse.score(listOf(workout, vlog), emptyList())
        assertThat(VideoBrowse.apply(scored, onlyWorkouts = false, showEverything = false, length = null).shown).hasSize(2)
    }

    @Test
    fun `length buckets are half open`() {
        assertThat(VideoLength.UNDER_15.accepts(14 * 60 + 59)).isTrue()
        assertThat(VideoLength.UNDER_15.accepts(15 * 60)).isFalse()
        assertThat(VideoLength.FROM_15.accepts(15 * 60)).isTrue()
        assertThat(VideoLength.FROM_30.accepts(45 * 60)).isFalse()
        assertThat(VideoLength.OVER_45.accepts(45 * 60)).isTrue()
        assertThat(VideoLength.OVER_45.accepts(3 * 3600)).isTrue()
    }

    @Test
    fun `the length chip filters the rows, keeping videos whose length is unknown`() {
        val short = video("s", "10 minute abs workout", sec = 600)
        val unknown = video("u", "full body workout", sec = 0)
        val scored = VideoBrowse.score(listOf(short, workout, unknown), emptyList())
        val f = VideoBrowse.apply(scored, onlyWorkouts = false, showEverything = false, length = VideoLength.UNDER_15)
        assertThat(f.shown.map { it.video.id }).containsExactly("s", "u").inOrder()
    }

    @Test
    fun `chapters are counted from the description`() {
        val v = workout.copy(description = "0:00 Intro\n1:00 Squats\n5:00 Lunges\n9:00 Plank")
        assertThat(VideoBrowse.chapterCount(v)).isEqualTo(4)
        assertThat(VideoBrowse.chapterCount(workout)).isEqualTo(0)
    }

    @Test
    fun `an AI draft is matched to the library and marked`() {
        val library = listOf(ExerciseEntry("sq", "Barbell squat"), ExerciseEntry("pl", "Plank"))
        val draft = VideoWorkoutDrafter.fromLines(
            listOf(
                ParsedExerciseLine(rawText = "Squat", name = "Squat", sets = 3, reps = 10),
                ParsedExerciseLine(rawText = "Plank", name = "Plank", sets = 2, seconds = 45),
            ),
            library,
            DraftSource.AI,
        )
        assertThat(draft.source).isEqualTo(DraftSource.AI)
        assertThat(draft.items).hasSize(2)
        assertThat(draft.items[1].exerciseId).isEqualTo("pl")
        assertThat(draft.items[1].seconds).isEqualTo(45)
        assertThat(draft.items[0].include).isTrue()
    }
}
