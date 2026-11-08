package com.zeroindex.intake.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import org.junit.Test

class HybridClockTest {
    private class Wall(var now: Long) : Clock {
        override fun nowMillis() = now
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    @Test
    fun `never goes backwards and never repeats`() {
        val wall = Wall(1_000)
        val clock = HybridClock(wall)
        val a = clock.nowMillis()
        wall.now = 900 // wall clock stepped back
        val b = clock.nowMillis()
        val c = clock.nowMillis()
        assertThat(listOf(a, b, c)).isInStrictOrder()
    }

    @Test
    fun `an edit after a sync sorts after what the sync brought in`() {
        val wall = Wall(10_000)
        val clock = HybridClock(wall)
        clock.observe(10_000 + 90_000) // other device's clock is 90 s ahead
        assertThat(clock.nowMillis()).isGreaterThan(100_000L)
    }

    @Test
    fun `a wildly wrong remote clock cannot drag this one into the future`() {
        val wall = Wall(10_000)
        val clock = HybridClock(wall, maxSkewMs = 60_000)
        clock.observe(10_000 + 365L * 86_400_000)
        assertThat(clock.nowMillis()).isAtMost(10_000L + 60_000 + 1)
    }
}
