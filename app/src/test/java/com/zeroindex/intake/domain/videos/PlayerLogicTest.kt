package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerLogicTest {
    private val chapters = listOf(Chapter(0, "Warm up"), Chapter(60, "Squats"), Chapter(150, "Lunges"))

    @Test fun timeFormats() {
        assertThat(PlayerTime.format(0)).isEqualTo("0:00")
        assertThat(PlayerTime.format(-5)).isEqualTo("0:00")
        assertThat(PlayerTime.format(247_900)).isEqualTo("4:07")
        assertThat(PlayerTime.format(3_723_000)).isEqualTo("1:02:03")
        assertThat(PlayerTime.remaining(65_000, 240_000)).isEqualTo("-2:55")
        assertThat(PlayerTime.remaining(300_000, 240_000)).isEqualTo("-0:00")
        assertThat(PlayerTime.remaining(1_000, 0)).isEqualTo("-:--")
        assertThat(PlayerTime.spoken(65_000, 240_000)).isEqualTo("Elapsed 1:05 of 4:00")
        assertThat(PlayerTime.spoken(65_000, 0)).isEqualTo("Elapsed 1:05")
    }

    @Test fun chapterAtPosition() {
        assertThat(PlayerChapters.indexAt(chapters, 0)).isEqualTo(0)
        assertThat(PlayerChapters.indexAt(chapters, 59)).isEqualTo(0)
        assertThat(PlayerChapters.indexAt(chapters, 60)).isEqualTo(1)
        assertThat(PlayerChapters.indexAt(chapters, 9999)).isEqualTo(2)
        assertThat(PlayerChapters.indexAt(listOf(Chapter(30, "Late start")), 10)).isEqualTo(-1)
        assertThat(PlayerChapters.indexAt(emptyList(), 10)).isEqualTo(-1)
        assertThat(PlayerChapters.labelAt(chapters, 100)).isEqualTo("Squats")
        assertThat(PlayerChapters.labelAt(emptyList(), 100)).isNull()
    }

    @Test fun tickFractionsSkipStartAndOutOfRange() {
        assertThat(PlayerChapters.tickFractions(chapters, 300)).containsExactly(0.2f, 0.5f).inOrder()
        assertThat(PlayerChapters.tickFractions(chapters, 100)).containsExactly(0.6f)
        assertThat(PlayerChapters.tickFractions(chapters, 0)).isEmpty()
        assertThat(PlayerChapters.tickFractions(listOf(Chapter(300, "End")), 300)).isEmpty()
    }

    @Test fun doubleTapSeekIsClamped() {
        assertThat(PlayerSeek.target(5_000, -PlayerSeek.STEP_MS, 100_000)).isEqualTo(0)
        assertThat(PlayerSeek.target(95_000, PlayerSeek.STEP_MS, 100_000)).isEqualTo(100_000)
        assertThat(PlayerSeek.target(50_000, PlayerSeek.STEP_MS, 100_000)).isEqualTo(60_000)
        assertThat(PlayerSeek.target(50_000, PlayerSeek.STEP_MS, 0)).isEqualTo(60_000)
        assertThat(PlayerSeek.target(2_000, -PlayerSeek.STEP_MS, 0)).isEqualTo(0)
    }

    @Test fun scrubberFractions() {
        assertThat(PlayerSeek.positionAt(0.5f, 200_000)).isEqualTo(100_000)
        assertThat(PlayerSeek.positionAt(1.7f, 200_000)).isEqualTo(200_000)
        assertThat(PlayerSeek.positionAt(0.5f, 0)).isEqualTo(0)
        assertThat(PlayerSeek.fractionOf(50_000, 200_000)).isEqualTo(0.25f)
        assertThat(PlayerSeek.fractionOf(50_000, 0)).isEqualTo(0f)
    }

    @Test fun loopBounds() {
        val squats = LoopBounds.forChapter(chapters, 1, 300)!!
        assertThat(squats).isEqualTo(LoopBounds(60_000, 150_000))
        // The last chapter ends with the video.
        assertThat(LoopBounds.forChapter(chapters, 2, 300)).isEqualTo(LoopBounds(150_000, 300_000))
        // Unknown length: the last chapter has no end, so no loop.
        assertThat(LoopBounds.forChapter(chapters, 2, 0)).isNull()
        assertThat(LoopBounds.forChapter(chapters, -1, 300)).isNull()
        assertThat(squats.shouldJumpBack(149_999)).isFalse()
        assertThat(squats.shouldJumpBack(150_000)).isTrue()
        assertThat(squats.shouldJumpBack(10_000)).isTrue()
        assertThat(squats.shouldJumpBack(59_500)).isFalse()
    }

    @Test fun rates() {
        assertThat(PlaybackRates.ALL.first()).isEqualTo(0.5f)
        assertThat(PlaybackRates.ALL.last()).isEqualTo(2f)
        assertThat(PlaybackRates.label(1f)).isEqualTo("1×")
        assertThat(PlaybackRates.label(0.5f)).isEqualTo("0.5×")
        assertThat(PlaybackRates.label(1.25f)).isEqualTo("1.25×")
        assertThat(PlaybackRates.nearest(1.1f)).isEqualTo(1f)
        assertThat(PlaybackRates.nearest(3f)).isEqualTo(2f)
    }

    @Test fun youtubeErrors() {
        assertThat(YoutubePlayerErrors.message(101)).contains("does not allow")
        assertThat(YoutubePlayerErrors.message(150)).isEqualTo(YoutubePlayerErrors.message(101))
        assertThat(YoutubePlayerErrors.message(-1)).contains("connection")
        assertThat(YoutubePlayerErrors.message(999)).isEqualTo("This video could not be played.")
        assertThat(YoutubePlayerErrors.canRetry(-1)).isTrue()
        assertThat(YoutubePlayerErrors.canRetry(100)).isFalse()
    }

    @Test fun autoHide() {
        assertThat(ControlsVisibility.shouldAutoHide(true, false, false, 3_000)).isTrue()
        assertThat(ControlsVisibility.shouldAutoHide(true, false, false, 2_999)).isFalse()
        assertThat(ControlsVisibility.shouldAutoHide(false, false, false, 9_000)).isFalse()
        assertThat(ControlsVisibility.shouldAutoHide(true, true, false, 9_000)).isFalse()
        assertThat(ControlsVisibility.shouldAutoHide(true, false, true, 9_000)).isFalse()
    }
}
