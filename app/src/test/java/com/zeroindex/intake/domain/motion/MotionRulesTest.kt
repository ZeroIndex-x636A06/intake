package com.zeroindex.intake.domain.motion

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class MotionRulesTest {

    @Test fun reduceMotionIsSystemZeroOrTheSetting() {
        assertThat(MotionRules.reduceMotion(1f, false)).isFalse()
        assertThat(MotionRules.reduceMotion(0f, false)).isTrue()
        assertThat(MotionRules.reduceMotion(1f, true)).isTrue()
        assertThat(MotionRules.reduceMotion(0f, true)).isTrue()
        assertThat(MotionRules.reduceMotion(0.5f, false)).isFalse()
    }

    @Test fun staggerCoversEightItemsThirtyMillisApart() {
        val delays = (0 until 8).map { MotionRules.staggerDelayMs(it) }
        assertThat(delays).containsExactly(0, 30, 60, 90, 120, 150, 180, 210).inOrder()
        assertThat(MotionRules.staggerDelayMs(8)).isNull()
        assertThat(MotionRules.staggerDelayMs(50)).isNull()
        assertThat(MotionRules.staggerDelayMs(-1)).isNull()
    }

    @Test fun proteinCelebrationFiresOnlyWhenCrossingOncePerDay() {
        val day = 20_000L
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, 120.0, 152.0, null, day)).isTrue()
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, 140.0, 150.0, day - 1, day)).isTrue()
        // Already celebrated today.
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, 120.0, 152.0, day, day)).isFalse()
        // Screen opened already over the target (no previous value).
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, null, 160.0, null, day)).isFalse()
        // Already over before this change.
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, 155.0, 170.0, null, day)).isFalse()
        // Not today, no target, zero target.
        assertThat(MotionRules.shouldCelebrateProtein(false, 150, 120.0, 152.0, null, day)).isFalse()
        assertThat(MotionRules.shouldCelebrateProtein(true, null, 120.0, 152.0, null, day)).isFalse()
        assertThat(MotionRules.shouldCelebrateProtein(true, 0, 0.0, 10.0, null, day)).isFalse()
        // Deleting an entry (going down) never celebrates.
        assertThat(MotionRules.shouldCelebrateProtein(true, 150, 160.0, 100.0, null, day)).isFalse()
    }

    @Test fun pulseOnlyWhenALogRaisesCalories() {
        assertThat(MotionRules.shouldPulseAfterLog(true, 500.0, 650.0)).isTrue()
        assertThat(MotionRules.shouldPulseAfterLog(true, null, 650.0)).isFalse()
        assertThat(MotionRules.shouldPulseAfterLog(true, 650.0, 500.0)).isFalse()
        assertThat(MotionRules.shouldPulseAfterLog(true, 500.0, 500.0)).isFalse()
        assertThat(MotionRules.shouldPulseAfterLog(false, 500.0, 650.0)).isFalse()
    }

    @Test fun countUpEndsOnTheExactFinalText() {
        assertThat(MotionRules.countUpText(1834.0, 1f, 0, Locale.US)).isEqualTo("1,834")
        assertThat(MotionRules.countUpText(1834.0, 0f, 0, Locale.US)).isEqualTo("0")
        assertThat(MotionRules.countUpText(1834.0, 0.5f, 0, Locale.US)).isEqualTo("917")
        assertThat(MotionRules.countUpText(72.46, 1f, 1, Locale.US)).isEqualTo("72.5")
        assertThat(MotionRules.countUpText(100.0, 2f, 0, Locale.US)).isEqualTo("100")
        assertThat(MotionRules.countUpText(100.0, -1f, 0, Locale.US)).isEqualTo("0")
    }

    @Test fun countUpNeverDecreasesAsItProgresses() {
        val values = (0..20).map { MotionRules.countUpText(2400.0, it / 20f, 0, Locale.US).replace(",", "").toInt() }
        assertThat(values).isInOrder()
    }

    @Test fun restFractionRunsFromOneToZero() {
        assertThat(MotionRules.restRemainingFraction(120, 120)).isEqualTo(1f)
        assertThat(MotionRules.restRemainingFraction(60, 120)).isEqualTo(0.5f)
        assertThat(MotionRules.restRemainingFraction(0, 120)).isEqualTo(0f)
        assertThat(MotionRules.restRemainingFraction(5, 0)).isEqualTo(0f)
        assertThat(MotionRules.restRemainingFraction(200, 120)).isEqualTo(1f)
    }
}
