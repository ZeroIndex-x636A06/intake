package com.zeroindex.intake.domain.text

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformLatest

/**
 * The words a result list should follow, as opposed to the words in the box. The box changes on every keystroke; the
 * list only needs to change once typing pauses for [windowMs], so a burst of keys rebuilds it once, not once per key.
 * A blank box is passed on at once (clearing should not wait). Newer words cancel the wait for older ones.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<String>.settled(windowMs: Long): Flow<String> = transformLatest { typed ->
    if (typed.isNotBlank()) delay(windowMs)
    emit(typed)
}
