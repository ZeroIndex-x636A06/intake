package com.zeroindex.intake.domain.training

/** What the rest strip does after a +15 s or -15 s tap: the new time left and the ring's new length. */
data class RestAdjust(val remainingSeconds: Int, val totalSeconds: Int)

/** Pure rules for the rest timer: its limits, the presets, and the +15 s / -15 s maths. */
object RestRules {
    /** 0 means no timer: finishing a set starts nothing. */
    const val NO_TIMER = 0
    const val MAX_SECONDS = 900
    const val STEP_SECONDS = 15

    /** The quick choices in the rest sheet: "No timer", then the usual rests up to 15 minutes. */
    val presets: List<Int> = listOf(NO_TIMER, 30, 45, 60, 90, 120, 180, 240, 300, 600, MAX_SECONDS)

    fun clamp(seconds: Int): Int = seconds.coerceIn(NO_TIMER, MAX_SECONDS)

    /** A step up or down from [seconds] by 15 s, held to the limits. */
    fun step(seconds: Int, direction: Int): Int = clamp(seconds + direction * STEP_SECONDS)

    /** "No timer", "45 s", "1:30" or "15:00" for the sheet and the button. */
    fun label(seconds: Int): String = when {
        seconds <= NO_TIMER -> "No timer"
        seconds < 60 -> "$seconds s"
        else -> "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /**
     * +15 s or -15 s on a running rest. Adding grows the ring with it (capped at 15 minutes); taking away shortens the time
     * left and leaves the ring's length alone. Going to zero or below ends the rest, which is null.
     */
    fun adjust(remainingSeconds: Int, totalSeconds: Int, deltaSeconds: Int): RestAdjust? {
        val remaining = remainingSeconds + deltaSeconds
        if (remaining <= 0) return null
        val cappedRemaining = remaining.coerceAtMost(MAX_SECONDS)
        val total = if (deltaSeconds > 0) (totalSeconds + deltaSeconds).coerceAtMost(MAX_SECONDS) else totalSeconds
        return RestAdjust(cappedRemaining, maxOf(total, cappedRemaining))
    }
}

/** The short tone that can play when a rest ends. Vibration is its own older setting. */
enum class RestSound(val label: String) {
    OFF("Off"),
    BEEP("Beep"),
    DOUBLE("Double beep"),
    CHIME("Chime"),
    ;

    companion object {
        fun fromName(name: String?): RestSound = entries.firstOrNull { it.name == name } ?: OFF
    }
}
