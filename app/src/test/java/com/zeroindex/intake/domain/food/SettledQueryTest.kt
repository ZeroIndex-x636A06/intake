package com.zeroindex.intake.domain.food

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettledQueryTest {

    @Test
    fun fastTypingSearchesOnlyTheSettledWord() = runTest {
        val typed = MutableStateFlow("")
        val searched = mutableListOf<String>()
        val job = launch { typed.searchLatest { searched += it; it }.collect { } }
        advanceTimeBy(1_000)
        searched.clear()
        for (prefix in listOf("y", "yo", "yog", "yogu", "yogur", "yogurt")) {
            typed.value = prefix
            advanceTimeBy(40)
        }
        advanceTimeBy(1_000)
        assertThat(searched).containsExactly("yogurt")
        job.cancel()
    }

    @Test
    fun trailingSpaceIsNotASecondSearch() = runTest {
        val typed = MutableStateFlow("yogurt")
        val searched = mutableListOf<String>()
        val job = launch { typed.searchLatest { searched += it; it }.collect { } }
        advanceTimeBy(1_000)
        typed.value = "yogurt "
        advanceTimeBy(1_000)
        assertThat(searched).containsExactly("yogurt")
        job.cancel()
    }

    @Test
    fun aSlowSearchIsCancelledWhenTheNextQuerySettles() = runTest {
        val typed = MutableStateFlow("egg")
        val finished = mutableListOf<String>()
        val job = launch {
            typed.searchLatest { q -> delay(5_000); finished += q; q }.collect { }
        }
        advanceTimeBy(1_000) // "egg" is searching
        typed.value = "eggs"
        advanceTimeBy(10_000)
        assertThat(finished).containsExactly("eggs")
        job.cancel()
    }

    @Test
    fun nothingSearchesBeforeThePauseEnds() = runTest {
        val typed = MutableStateFlow("")
        val searched = mutableListOf<String>()
        val job = launch { typed.searchLatest { searched += it; it }.collect { } }
        advanceTimeBy(1_000)
        searched.clear()
        typed.value = "rice"
        advanceTimeBy(SettledQuery.DEBOUNCE_MS - 10)
        assertThat(searched).isEmpty()
        advanceTimeBy(20)
        assertThat(searched).containsExactly("rice")
        job.cancel()
    }
}
