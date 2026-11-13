package com.zeroindex.intake.domain.videos

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YoutubeKeyGuideTest {
    private val all = YoutubeKeyGuide.SECTIONS.flatMap { it.steps + listOfNotNull(it.note) }.joinToString(" ")

    @Test fun coversMakingRestrictingAndUsingTheKey() {
        assertThat(YoutubeKeyGuide.SECTIONS).hasSize(3)
        listOf("console.cloud.google.com", "New project", "YouTube Data API v3", "Enable", "Create credentials", "Public data").forEach {
            assertThat(all).contains(it)
        }
    }

    @Test fun saysWhatToRestrictAndWhatToLeaveAlone() {
        assertThat(all).contains("API restrictions")
        assertThat(all).contains("Application restrictions")
        assertThat(all).contains("None")
        assertThat(all).contains("Settings > API keys")
    }
}
