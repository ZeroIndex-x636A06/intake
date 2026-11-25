package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutDurationTest {
    private fun ms(min: Int, sec: Int = 0) = (min * 60 + sec) * 1000L

    @Test fun `the same session reads the same minutes everywhere`() {
        val d = ms(22, 40) // the summary said 23, the list said 22
        assertThat(WorkoutDuration.minutes(d)).isEqualTo(23)
        assertThat(WorkoutDuration.short(d)).isEqualTo("23 min")
        assertThat(WorkoutDuration.long(d)).isEqualTo("23 minutes")
        assertThat(WorkoutDuration.statValue(d)).isEqualTo("23")
        assertThat(WorkoutDuration.statCaption(d)).isEqualTo("minutes")
    }

    @Test fun `under a minute reads less than one, never zero`() {
        for (d in listOf(0L, ms(0, 10), ms(0, 59))) {
            assertThat(WorkoutDuration.short(d)).isEqualTo("<1 min")
            assertThat(WorkoutDuration.long(d)).isEqualTo("<1 minute")
            assertThat(WorkoutDuration.statValue(d)).isEqualTo("<1")
        }
    }

    @Test fun `one minute is singular`() {
        assertThat(WorkoutDuration.short(ms(1))).isEqualTo("1 min")
        assertThat(WorkoutDuration.long(ms(1, 10))).isEqualTo("1 minute")
        assertThat(WorkoutDuration.statCaption(ms(1))).isEqualTo("minute")
    }

    @Test fun `a negative span is under a minute`() {
        assertThat(WorkoutDuration.short(-5_000L)).isEqualTo("<1 min")
    }
}
