package com.zeroindex.intake.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/** Time for the keyboard to start opening before the page is asked to scroll, so the target lands above it. */
private const val KEYBOARD_SETTLE_MS = 250L

/**
 * Put on a text field's container (or on the card that holds a field and the button under it). When focus is inside it,
 * the page scrolls so the whole container is in view, which keeps the field and its button above the keyboard. Only
 * works inside a scrollable parent (LazyColumn, verticalScroll). Pair it with `Modifier.imePadding()` on that parent.
 */
@Composable
fun Modifier.scrollIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            delay(KEYBOARD_SETTLE_MS)
            requester.bringIntoView()
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.hasFocus }
}
