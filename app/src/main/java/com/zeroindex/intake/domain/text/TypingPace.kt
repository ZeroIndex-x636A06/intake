package com.zeroindex.intake.domain.text

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * How long a result list should wait after a key before it follows the box, given how fast keys are arriving.
 *
 * A fixed wait is wrong on a busy or slow device: when one key takes longer to handle than the wait, the list rebuilds
 * between every pair of keys (searches, allergen scans and a lazy list rebuild per key), which slows the next key even
 * more (Q5b-1: 3 to 7 s per key, then an ANR). So the wait follows the typing: a little longer than the last gap between
 * keys, never below [minMs] and never above [maxMs]. A gap longer than [resetMs] is a new burst and starts from [minMs].
 */
class TypingPace(
    private val minMs: Long = 250,
    private val maxMs: Long = 2_000,
    private val resetMs: Long = 6_000,
    private val factor: Double = 1.25,
) {
    private var lastKeyAt = Long.MIN_VALUE

    /** A key arrived at [nowMs]; the wait to use before the list follows it. */
    fun onKey(nowMs: Long): Long {
        val gap = if (lastKeyAt == Long.MIN_VALUE) Long.MAX_VALUE else nowMs - lastKeyAt
        lastKeyAt = nowMs
        if (gap > resetMs) return minMs
        return (gap * factor).toLong().coerceIn(minMs, maxMs)
    }
}

/**
 * Like [settled], but the wait comes from [pace] (see [TypingPace]). [nowMs] is the clock, injectable for tests.
 * A blank box is passed on at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<String>.settledByPace(pace: TypingPace, nowMs: () -> Long): Flow<String> = transformLatest { typed ->
    val wait = pace.onKey(nowMs())
    if (typed.isNotBlank()) delay(wait)
    emit(typed)
}
