package com.zeroindex.intake.domain.time

import java.time.LocalDate
import java.time.ZoneId

/**
 * Injected rather than called statically so day-boundary logic is testable
 * without freezing the machine clock (SPEC §3.4 makes those boundaries
 * load-bearing).
 */
interface Clock {
    fun nowMillis(): Long
    fun zone(): ZoneId

    fun today(): LocalDate = localDateOf(nowMillis(), zone())
    fun todayRange(): DayRange = DayRange.of(today(), zone())
    fun rangeFor(date: LocalDate): DayRange = DayRange.of(date, zone())
}

class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
