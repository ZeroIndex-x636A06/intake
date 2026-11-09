package com.zeroindex.intake.domain.motion

import java.util.Locale
import kotlin.math.roundToLong

/**
 * The rules behind the motion polish, kept free of Android so they are unit-tested on the JVM.
 * The Compose side (ui/motion) only turns these answers into pixels.
 */
object MotionRules {

    /** Reduce motion is on if the system animator scale is 0 OR the in-app setting is on. */
    fun reduceMotion(systemAnimatorScale: Float, userSetting: Boolean): Boolean =
        systemAnimatorScale == 0f || userSetting

    /** At most this many list items stagger in; later ones simply appear. */
    const val STAGGER_MAX_ITEMS = 8
    const val STAGGER_STEP_MS = 30

    /**
     * Start delay for the item at [index], or null when it should not animate at all (beyond the
     * eighth item, or a negative index). The last staggered item starts at 7 x 30 = 210 ms.
     */
    fun staggerDelayMs(index: Int): Int? =
        if (index in 0 until STAGGER_MAX_ITEMS) index * STAGGER_STEP_MS else null

    /**
     * The protein celebration plays once per calendar day, only on today, and only when this change
     * is the one that crosses the target (not when the screen opens already over it).
     */
    fun shouldCelebrateProtein(
        isToday: Boolean,
        targetG: Int?,
        previousG: Double?,
        currentG: Double,
        lastCelebratedEpochDay: Long?,
        todayEpochDay: Long,
    ): Boolean {
        if (!isToday || targetG == null || targetG <= 0 || previousG == null) return false
        if (lastCelebratedEpochDay == todayEpochDay) return false
        return previousG < targetG && currentG >= targetG
    }

    /** The ring pulses when a log raised today's calories; opening the screen or deleting never pulses. */
    fun shouldPulseAfterLog(isToday: Boolean, previousKcal: Double?, currentKcal: Double): Boolean =
        isToday && previousKcal != null && currentKcal > previousKcal + 0.5

    /**
     * The number shown while counting up: [fraction] (0..1) of the way to [target]. At 1 it is exactly
     * the final value's text, so what the screen settles on is what the accessibility tree already said.
     */
    fun countUpText(target: Double, fraction: Float, decimals: Int = 0, locale: Locale = Locale.getDefault()): String {
        val f = fraction.coerceIn(0f, 1f)
        val shown = if (f >= 1f) target else target * f
        val places = decimals.coerceAtLeast(0)
        val value = if (places == 0) shown.roundToLong().toDouble() else shown
        return String.format(locale, "%,.${places}f", value)
    }

    /** Rest ring: fraction of the rest still to run, 1 at the start and 0 at the end. */
    fun restRemainingFraction(remainingSec: Int, totalSec: Int): Float =
        if (totalSec <= 0) 0f else (remainingSec.toFloat() / totalSec).coerceIn(0f, 1f)
}
