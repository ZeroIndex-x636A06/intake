package com.zeroindex.intake.domain.time

import java.time.ZoneId

/**
 * Amendment §3's logical clock, in the spirit of a hybrid logical clock:
 * wall time, but never going backwards and never behind a timestamp already
 * seen from another device. Every `updatedAt` Intake writes comes from here,
 * so an edit made after a sync always sorts after what the sync brought in,
 * even when this phone's clock runs a little slow.
 *
 * [observe] only pulls the clock forward by up to [maxSkewMs] past wall
 * time: a device whose clock is set to next year must not drag every
 * timestamp on this phone into the future with it.
 */
class HybridClock(
    private val wall: Clock,
    private val maxSkewMs: Long = 5 * 60_000L,
) : Clock {
    private var last = 0L

    @Synchronized
    override fun nowMillis(): Long {
        val t = maxOf(wall.nowMillis(), last + 1)
        last = t
        return t
    }

    override fun zone(): ZoneId = wall.zone()

    /** Records a timestamp from elsewhere, so the next local one is later. */
    @Synchronized
    fun observe(remoteMillis: Long) {
        val ceiling = wall.nowMillis() + maxSkewMs
        last = maxOf(last, minOf(remoteMillis, ceiling))
    }
}
