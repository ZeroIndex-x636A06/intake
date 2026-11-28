package com.zeroindex.intake.domain.text

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettledQueryTest {
    @Test
    fun `a fast burst of keys settles once, on the last words`() = runTest {
        val typed = flow {
            "almond milk".fold("") { acc, c -> (acc + c).also { emit(it); delay(20) } }
        }
        assertEquals(listOf("almond milk"), typed.settled(250).toList())
    }

    @Test
    fun `a pause lets the earlier words through`() = runTest {
        val typed = flow {
            emit("alm"); delay(400)
            emit("almo"); delay(400)
        }
        assertEquals(listOf("alm", "almo"), typed.settled(250).toList())
    }

    @Test
    fun `clearing the box passes on at once and cancels the wait`() = runTest {
        val typed = flow {
            emit("milk"); delay(100)
            emit(""); delay(500)
        }
        assertEquals(listOf(""), typed.settled(250).toList())
    }
}
