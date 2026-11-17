package com.zeroindex.intake.ui.screens.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YoutubeEmbedTest {
    @Test
    fun `only real video ids are embedded`() {
        assertThat(YoutubeEmbed.isValidId("dQw4w9WgXcQ")).isTrue()
        assertThat(YoutubeEmbed.isValidId("abc\"><script>")).isFalse()
        assertThat(YoutubeEmbed.isValidId("short")).isFalse()
    }

    @Test
    fun `the page is the nocookie embed and starts at the chapter`() {
        val html = YoutubeEmbed.html("dQw4w9WgXcQ", 95)
        assertThat(html).contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?")
        assertThat(html).contains("start=95")
        assertThat(html).contains("autoplay=1")
        assertThat(YoutubeEmbed.html("dQw4w9WgXcQ", 0)).contains("autoplay=0")
    }

    @Test
    fun `only the embed host may load`() {
        assertThat(YoutubeEmbed.allowsHost("www.youtube-nocookie.com")).isTrue()
        assertThat(YoutubeEmbed.allowsHost("evil.example.com")).isFalse()
        assertThat(YoutubeEmbed.allowsHost("youtube-nocookie.com.evil.com")).isFalse()
        assertThat(YoutubeEmbed.allowsHost(null)).isFalse()
    }

    @Test fun thePlayerFrameMayMoveBetweenYoutubeAndGoogleHostsOnly() {
        listOf("www.youtube-nocookie.com", "www.youtube.com", "consent.google.com", "i.ytimg.com").forEach {
            assertThat(YoutubeEmbed.allowsFrameHost(it)).isTrue()
        }
        listOf("evil.example.com", "youtube.com.evil.com", "notyoutube.com", null).forEach {
            assertThat(YoutubeEmbed.allowsFrameHost(it)).isFalse()
        }
    }

    @Test fun aTapOnThePictureStartsTheVideoPlaying() {
        assertThat(YoutubeEmbed.html("dQw4w9WgXcQ", 0, autoplay = true)).contains("autoplay=1")
        assertThat(com.zeroindex.intake.domain.videos.YoutubeIds.thumbnailUrl("dQw4w9WgXcQ")).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
    }
}
