package com.zeroindex.intake.ui.components

import android.os.SystemClock
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.zeroindex.intake.domain.text.SearchTextSync
import com.zeroindex.intake.domain.text.TypingPace
import com.zeroindex.intake.domain.text.settledByPace
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * A search box's text. The field is a [TextFieldState] that this class owns: the keyboard edits it directly, so a
 * keystroke never waits for a recomposition (a String value handed back through a busy screen is what dropped the end of
 * fast typing, Q1-3 / Q5a-3). The view model is told afterwards and its echo is filtered by [SearchTextSync].
 *
 * [settled] is the text a results list should follow: it lags the field until typing pauses, so the list is not rebuilt
 * per key. Read [settled] (not the field) anywhere outside the field itself.
 */
class SyncedText internal constructor(
    initial: String,
    /** True when the view model never echoes the words back: it is then sent only what has settled, not every key. */
    private val sendSettled: Boolean = false,
    private val send: (String) -> Unit,
) {
    private val sync = SearchTextSync(initial)

    /** Pass to `TextField(state = ...)`. */
    val box = TextFieldState(initial)

    /** What the results should follow; see the class comment. */
    var settled by mutableStateOf(initial)
        private set

    /** The text now. Reading it in composition re-reads on every keystroke: use it in click handlers and the field's own lambdas. */
    val text: String get() = box.text.toString()

    /** A clear button, or a suggested query. */
    fun edit(typed: String) {
        box.setTextAndPlaceCursorAtEnd(typed)
    }

    internal suspend fun run(settleMs: Long) = coroutineScope {
        // Words that reach the view model: every value the field settles on (the flow conflates, which is fine).
        if (!sendSettled) launch {
            snapshotFlow { box.text.toString() }.collect { typed ->
                if (typed != sync.text) {
                    sync.edit(typed)
                    send(typed)
                }
            }
        }
        // The wait follows how fast keys arrive (TypingPace): on a slow or busy device a fixed wait would let the
        // results rebuild between every two keys, which is what made each key slower than the last (Q5b-1).
        snapshotFlow { box.text.toString() }.settledByPace(TypingPace(minMs = settleMs)) { SystemClock.uptimeMillis() }.collectLatest {
            settled = it
            if (sendSettled && it != sync.text) {
                sync.edit(it)
                send(it)
            }
        }
    }

    internal fun external(seen: String) {
        if (sync.external(seen)) {
            box.setTextAndPlaceCursorAtEnd(sync.text)
            settled = sync.text
        }
    }
}

/**
 * Binds a search field to a view model's query without making the field wait on the view model. [external] is the view
 * model's copy (null when it never changes the words by itself), [onChange] sends the typed words to it (it debounces them).
 */
@Composable
fun rememberSyncedText(external: String?, onChange: (String) -> Unit, initial: String = external.orEmpty(), settleMs: Long = 250): SyncedText {
    val latest by rememberUpdatedState(onChange)
    val synced = remember { SyncedText(initial, sendSettled = external == null) { latest(it) } }
    LaunchedEffect(Unit) { synced.run(settleMs) }
    if (external != null) LaunchedEffect(external) { synced.external(external) }
    return synced
}
