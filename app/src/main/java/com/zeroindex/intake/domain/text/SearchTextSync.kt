package com.zeroindex.intake.domain.text

/**
 * Keeps a search box's text in step with a view model that holds the same words.
 *
 * The box owns its text and changes it at once, on every keystroke. The view model's copy comes back later (through
 * flows that may be busy), and an old echo must never overwrite what has been typed since: that is what dropped and
 * reordered letters ("protein" became "proet"). So the words sent are remembered until they come back, and an echo of
 * one of them is ignored. Anything else the view model sets (a cleared search, a suggested query) is taken as is.
 *
 * Pure Kotlin, no Android: the Compose wrapper is `rememberSyncedText`.
 */
class SearchTextSync(initial: String = "") {
    var text: String = initial
        private set

    private val inFlight = ArrayDeque<String>()

    /** The person typed [typed]. Show it now; the caller then sends it on. */
    fun edit(typed: String) {
        text = typed
        inFlight.addLast(typed)
    }

    /** The view model now holds [seen]. Returns true when the box's text changed because of it. */
    fun external(seen: String): Boolean {
        val at = inFlight.indexOfLast { it == seen }
        if (at >= 0) {
            // An echo of something sent: everything up to it has been seen. It is not news.
            repeat(at + 1) { inFlight.removeFirst() }
            return false
        }
        inFlight.clear()
        if (seen == text) return false
        text = seen
        return true
    }
}
