package com.zeroindex.intake.ui.screens.videos.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zeroindex.intake.domain.videos.LoopBounds

enum class PlayerBackend { MEDIA3, YOUTUBE }

/** [message] is words for the person; it never holds a stream address or file path. */
data class PlayerError(val message: String, val canRetry: Boolean = true)

/**
 * What the player's controls talk to, the same for both backends (Media3 and YouTube's IFrame API). Everything the controls
 * read is Compose state, but the position is kept apart ([positionMs]) and read only inside the time texts and the scrubber's
 * draw block, so the 4 Hz position ticker redraws those and nothing else.
 */
@Stable
abstract class PlayerController {
    abstract val backend: PlayerBackend

    var isPlaying by mutableStateOf(false)
        protected set
    var buffering by mutableStateOf(true)
        protected set
    var ended by mutableStateOf(false)
        protected set
    var durationMs by mutableLongStateOf(0L)
        protected set
    var rate by mutableFloatStateOf(1f)
        protected set
    var error by mutableStateOf<PlayerError?>(null)
        protected set

    /** The chapter being repeated, or null. Playback jumps back to its start when it reaches its end. */
    var loop by mutableStateOf<LoopBounds?>(null)

    private val position = mutableLongStateOf(0L)
    val positionMs: Long get() = position.longValue

    protected fun publishPosition(ms: Long) {
        position.longValue = ms.coerceAtLeast(0L)
        val l = loop
        if (l != null && isPlaying && l.shouldJumpBack(ms)) {
            position.longValue = l.startMs
            seekTo(l.startMs)
        }
    }

    abstract fun play()
    abstract fun pause()
    abstract fun seekTo(ms: Long)
    abstract fun changeRate(rate: Float)

    /** Try again after an [error]; keeps the current position. */
    abstract fun retry()

    /** Called a few times a second while the screen is visible, on the main thread. */
    open fun poll() {}

    open fun onHostPause() {}
    open fun onHostResume() {}

    /** The screen went to the background: stop playing. */
    open fun onHostStop() = pause()

    abstract fun release()

    fun togglePlay() {
        when {
            ended -> { seekTo(0L); play() }
            isPlaying -> pause()
            else -> play()
        }
    }
}
