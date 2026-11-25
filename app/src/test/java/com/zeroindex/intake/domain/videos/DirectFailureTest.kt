package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import java.net.UnknownHostException
import org.junit.Test

class DirectFailureTest {
    @Test
    fun `no network is conclusive whatever the error says`() {
        assertThat(DirectFailure.classify(false, "ERROR: something odd")).isEqualTo(DirectFailure.NO_NETWORK)
    }

    @Test
    fun `connection errors from the downloader are no connection, not a changed site`() {
        val e = RuntimeException("yt-dlp failed", Exception("ERROR: Unable to download webpage: <urlopen error [Errno -3] Temporary failure in name resolution>"))
        assertThat(DirectFailure.classify(true, *DirectFailure.messagesOf(e))).isEqualTo(DirectFailure.NO_NETWORK)
        assertThat(DirectFailure.classify(true, *DirectFailure.messagesOf(UnknownHostException("youtube.com")))).isEqualTo(DirectFailure.NO_NETWORK)
    }

    @Test
    fun `anything else with a connection is an extraction failure`() {
        assertThat(DirectFailure.classify(true, "ERROR: Sign in to confirm you are not a bot")).isEqualTo(DirectFailure.EXTRACTION)
        assertThat(DirectFailure.classify(true)).isEqualTo(DirectFailure.EXTRACTION)
    }

    @Test
    fun `the words differ and only extraction blames a change at YouTube`() {
        assertThat(DirectFailure.NO_NETWORK.playbackNote()).doesNotContain("changed")
        assertThat(DirectFailure.TIMEOUT.playbackNote()).doesNotContain("changed")
        assertThat(DirectFailure.EXTRACTION.playbackNote()).contains("changed")
        assertThat(DirectFailure.NO_NETWORK.captionsMessage()).contains("No connection")
        assertThat(DirectFailure.NO_NETWORK.captionsMessage()).doesNotContain("changed")
        assertThat(DirectFailure.STREAM_TIMEOUT_MS).isAtMost(30_000L)
    }
}
