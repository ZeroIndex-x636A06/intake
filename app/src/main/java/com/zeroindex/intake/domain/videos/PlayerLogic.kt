package com.zeroindex.intake.domain.videos

/** The pure rules behind the in-app player's controls (DESIGN-V6 players). No Android, so each one is unit-tested. */
object PlayerTime {
    /** "0:00", "4:07", "1:02:03". Negative and unknown values show as 0:00. */
    fun format(ms: Long): String = YoutubeIds.clock((ms.coerceAtLeast(0L) / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

    /** "-3:21": what is left. An unknown length ([durationMs] <= 0) shows "-:--" so the screen does not claim a length. */
    fun remaining(positionMs: Long, durationMs: Long): String =
        if (durationMs <= 0L) "-:--" else "-" + format((durationMs - positionMs).coerceAtLeast(0L))

    /** "Elapsed 1:05 of 4:00" for TalkBack. */
    fun spoken(positionMs: Long, durationMs: Long): String =
        if (durationMs <= 0L) "Elapsed ${format(positionMs)}" else "Elapsed ${format(positionMs)} of ${format(durationMs)}"
}

object PlayerChapters {
    /** Index of the chapter playing at [positionSec] (the last one that has started), or -1 before the first or with none. */
    fun indexAt(chapters: List<Chapter>, positionSec: Int): Int {
        var found = -1
        for (i in chapters.indices) {
            if (chapters[i].startSec <= positionSec) found = i else break
        }
        return found
    }

    /** The chapter to mark as playing: none until the video has started (a page that has not been played shows no "Playing"). */
    fun playingIndex(started: Boolean, chapters: List<Chapter>, positionSec: Int): Int =
        if (started) indexAt(chapters, positionSec) else -1

    /** Where chapter [index] ends: the next chapter's start, or the video's end ([durationSec], or -1 when unknown). */
    fun endSec(chapters: List<Chapter>, index: Int, durationSec: Int): Int =
        chapters.getOrNull(index + 1)?.startSec ?: if (durationSec > 0) durationSec else -1

    /**
     * Where the scrubber draws a tick for each chapter start, as a fraction 0..1 of its length. The first chapter at 0:00 and
     * any that fall outside the video are left out; with an unknown length there are no ticks.
     */
    fun tickFractions(chapters: List<Chapter>, durationSec: Int): List<Float> {
        if (durationSec <= 0) return emptyList()
        return chapters.map { it.startSec }
            .filter { it in 1 until durationSec }
            .map { it.toFloat() / durationSec }
    }

    /** The label shown while scrubbing: the title of the chapter at [positionSec], or null with no chapters. */
    fun labelAt(chapters: List<Chapter>, positionSec: Int): String? = chapters.getOrNull(indexAt(chapters, positionSec))?.title
}

object PlayerSeek {
    /** How far a double-tap jumps. */
    const val STEP_MS = 10_000L

    /** [positionMs] moved by [deltaMs], kept inside the video. An unknown length ([durationMs] <= 0) only clamps at the start. */
    fun target(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val moved = (positionMs + deltaMs).coerceAtLeast(0L)
        return if (durationMs > 0L) moved.coerceAtMost(durationMs) else moved
    }

    /** Scrubber fraction (0..1) to a position; 0 when the length is unknown. */
    fun positionAt(fraction: Float, durationMs: Long): Long =
        if (durationMs <= 0L) 0L else (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong()

    /** Position to scrubber fraction (0..1). */
    fun fractionOf(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** Repeat one chapter, for drilling an exercise. */
data class LoopBounds(val startMs: Long, val endMs: Long) {
    /** True once playback has reached the end of the chapter (or gone past it): jump back to [startMs]. */
    fun shouldJumpBack(positionMs: Long): Boolean = positionMs >= endMs || positionMs < startMs - 1_000L

    companion object {
        /** The loop for chapter [index]; null with no such chapter or an empty one (the end must be after the start). */
        fun forChapter(chapters: List<Chapter>, index: Int, durationSec: Int): LoopBounds? {
            val c = chapters.getOrNull(index) ?: return null
            val end = PlayerChapters.endSec(chapters, index, durationSec)
            if (end <= c.startSec) return null
            return LoopBounds(c.startSec * 1000L, end * 1000L)
        }
    }
}

object PlaybackRates {
    val ALL: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    /** "1×", "0.5×", "1.25×". */
    fun label(rate: Float): String {
        val s = if (rate == rate.toInt().toFloat()) rate.toInt().toString() else rate.toString().trimEnd('0')
        return "$s×"
    }

    /** The rate in [ALL] closest to [rate]. */
    fun nearest(rate: Float): Float = ALL.minByOrNull { kotlin.math.abs(it - rate) } ?: 1f
}

object ControlsVisibility {
    const val AUTO_HIDE_MS = 3_000L

    /** Controls hide on their own only while playing, with nothing being dragged and no menu open. */
    fun shouldAutoHide(playing: Boolean, scrubbing: Boolean, menuOpen: Boolean, idleMs: Long): Boolean =
        playing && !scrubbing && !menuOpen && idleMs >= AUTO_HIDE_MS
}

/** What to tell the person for an error code from YouTube's player (IFrame API `onError`) or from loading its script (-1). */
object YoutubePlayerErrors {
    const val SCRIPT_FAILED = -1

    fun message(code: Int): String = when (code) {
        SCRIPT_FAILED -> "Could not reach YouTube. Check your connection."
        2 -> "YouTube says this link is not a valid video."
        5 -> "YouTube's player hit a problem with this video."
        100 -> "This video was removed or is private."
        101, 150, 153 -> "The owner of this video does not allow it to play outside YouTube."
        else -> "This video could not be played."
    }

    /** A new try can only help when the cause was the connection or a one-off player fault. */
    fun canRetry(code: Int): Boolean = code !in listOf(2, 100, 101, 150, 153)
}
