package com.zeroindex.intake.ui.screens.videos

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.ui.screens.videos.player.FullscreenEntry
import com.zeroindex.intake.ui.screens.videos.player.FullscreenHost
import org.junit.Test

/** Q12-2: fullscreen lives on one app-wide layer; a player going away must not clear the one that replaced it. */
class FullscreenHostTest {
    private fun entry() = FullscreenEntry(onExit = { }, content = { })

    @Test
    fun `nothing is shown until a player enters`() {
        val host = FullscreenHost()
        assertThat(host.active).isFalse()
        assertThat(host.entry).isNull()
    }

    @Test
    fun `enter shows the entry and leave clears it`() {
        val host = FullscreenHost()
        val a = entry()
        host.enter(a)
        assertThat(host.active).isTrue()
        assertThat(host.entry).isSameInstanceAs(a)
        host.leave(a)
        assertThat(host.active).isFalse()
    }

    @Test
    fun `a stale leave does not clear a newer entry`() {
        val host = FullscreenHost()
        val old = entry()
        val newer = entry()
        host.enter(old)
        host.enter(newer)
        host.leave(old)
        assertThat(host.entry).isSameInstanceAs(newer)
    }

    @Test
    fun `leaving twice is harmless`() {
        val host = FullscreenHost()
        val a = entry()
        host.enter(a)
        host.leave(a)
        host.leave(a)
        assertThat(host.active).isFalse()
    }
}
