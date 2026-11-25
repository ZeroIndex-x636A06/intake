package com.zeroindex.intake.domain.food

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * What a search box types, turned into the words worth searching for (Q11-1). Fast typing sends a key every few
 * milliseconds; a search per key ranked the whole reference database each time and froze the screen. A query counts
 * only once typing has paused for [DEBOUNCE_MS], is trimmed, and is skipped when it is the words already searched
 * ("yogurt" then "yogurt " is one search, not two).
 */
object SettledQuery {
    /** The pause after the last key before a search starts. */
    const val DEBOUNCE_MS = 250L

    /** The offline database is not searched for fewer letters than this; one letter matches most of it. */
    const val MIN_REFERENCE_CHARS = 2
}

@OptIn(FlowPreview::class)
fun Flow<String>.settled(debounceMs: Long = SettledQuery.DEBOUNCE_MS): Flow<String> =
    debounce(debounceMs).map { it.trim() }.distinctUntilChanged()

/** A search for each settled query; one still running when the next query settles is cancelled, so its answer never lands late. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<String>.searchLatest(debounceMs: Long = SettledQuery.DEBOUNCE_MS, search: suspend (String) -> T): Flow<T> =
    settled(debounceMs).mapLatest { search(it) }
