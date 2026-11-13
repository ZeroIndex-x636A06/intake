package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DirectPlaybackTest {
    @Test
    fun `YouTube's own player is the default and the opt-in is needed for direct playback`() {
        assertThat(DirectPlayback.choose(optedIn = false, hasDownloadedFile = false)).isEqualTo(PlayerKind.YOUTUBE)
        assertThat(DirectPlayback.choose(optedIn = true, hasDownloadedFile = false)).isEqualTo(PlayerKind.DIRECT_STREAM)
    }

    @Test
    fun `a downloaded file plays offline whether or not the opt-in is on`() {
        assertThat(DirectPlayback.choose(optedIn = true, hasDownloadedFile = true)).isEqualTo(PlayerKind.OFFLINE_FILE)
        assertThat(DirectPlayback.choose(optedIn = false, hasDownloadedFile = true)).isEqualTo(PlayerKind.OFFLINE_FILE)
    }

    @Test
    fun `sizes read in plain units`() {
        assertThat(DirectPlayback.size(0)).isEqualTo("0 B")
        assertThat(DirectPlayback.size(2048)).isEqualTo("2 KB")
        assertThat(DirectPlayback.size(13_000_000)).isEqualTo("12.4 MB")
        assertThat(DirectPlayback.size(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB")
        assertThat(DirectPlayback.size(-5)).isEqualTo("0 B")
    }

    @Test
    fun `progress text`() {
        assertThat(DirectPlayback.progressText(-1f)).isEqualTo("Downloading")
        assertThat(DirectPlayback.progressText(42.7f)).isEqualTo("Downloading 42%")
        assertThat(DirectPlayback.progressText(140f)).isEqualTo("Downloading 100%")
    }

    @Test
    fun `the Shorts player swipes through the Shorts of the list the person came from`() {
        val list = listOf("a", "b", "c")
        assertThat(ShortsQueue.playlist(list, "b")).containsExactly("a", "b", "c").inOrder()
        assertThat(ShortsQueue.indexOf(ShortsQueue.playlist(list, "b"), "b")).isEqualTo(1)
        // Opened from somewhere else (a pasted link): just that video.
        assertThat(ShortsQueue.playlist(list, "z")).containsExactly("z")
        assertThat(ShortsQueue.indexOf(listOf("z"), "z")).isEqualTo(0)
    }
}
