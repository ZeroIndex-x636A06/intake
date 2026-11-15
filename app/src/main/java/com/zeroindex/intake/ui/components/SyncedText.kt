package com.zeroindex.intake.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.zeroindex.intake.domain.text.SearchTextSync

/** A search box's text, held here and changed synchronously; see [SearchTextSync]. */
class SyncedText internal constructor(initial: String, private val send: (String) -> Unit) {
    private val sync = SearchTextSync(initial)
    var text by mutableStateOf(initial)
        private set

    /** For the field's onValueChange and a clear button. */
    fun edit(typed: String) {
        sync.edit(typed)
        text = typed
        send(typed)
    }

    internal fun external(seen: String) {
        if (sync.external(seen)) text = sync.text
    }
}

/**
 * Binds a text field to a view model's query without making the field wait on the view model: [external] is the
 * view model's copy, [onChange] sends the typed words to it (it debounces them).
 */
@Composable
fun rememberSyncedText(external: String, onChange: (String) -> Unit): SyncedText {
    val latest by rememberUpdatedState(onChange)
    val synced = remember { SyncedText(external) { latest(it) } }
    LaunchedEffect(external) { synced.external(external) }
    return synced
}
