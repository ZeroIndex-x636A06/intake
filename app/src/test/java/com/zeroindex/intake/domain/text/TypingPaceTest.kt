package com.zeroindex.intake.domain.text

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TypingPaceTest {
    @Test
    fun `the first key and a new burst use the shortest wait`() {
        val p = TypingPace()
        assertEquals(250, p.onKey(0))
        assertEquals(250, p.onKey(60_000))
    }

    @Test
    fun `fast typing keeps the shortest wait`() {
        val p = TypingPace()
        p.onKey(0)
        assertEquals(250, p.onKey(100))
        assertEquals(250, p.onKey(220))
    }

    @Test
    fun `the wait grows with the gap between keys and is capped`() {
        val p = TypingPace()
        p.onKey(0)
        assertEquals(1_250, p.onKey(1_000))
        assertEquals(2_000, p.onKey(5_000))
    }

    @Test
    fun `keys that each take longer than the fixed wait do not rebuild the list between them`() = runTest {
        val typed = flow {
            "almond".fold("") { acc, c -> (acc + c).also { emit(it); delay(1_400) } }
        }
        // A fixed 250 ms wait would pass on every prefix. Here the first key has no pace to go on and passes; after
        // that the wait outlasts the gap between keys, so only the last words come through.
        assertEquals(listOf("a", "almond"), typed.settledByPace(TypingPace()) { currentTime }.toList())
    }

    @Test
    fun `fast typing still settles once`() = runTest {
        val typed = flow {
            "almond milk".fold("") { acc, c -> (acc + c).also { emit(it); delay(80) } }
        }
        assertEquals(listOf("almond milk"), typed.settledByPace(TypingPace()) { currentTime }.toList())
    }

    @Test
    fun `clearing the box passes on at once`() = runTest {
        val typed = flow { emit("milk"); delay(100); emit(""); delay(3_000) }
        assertEquals(listOf(""), typed.settledByPace(TypingPace()) { currentTime }.toList())
    }
}
