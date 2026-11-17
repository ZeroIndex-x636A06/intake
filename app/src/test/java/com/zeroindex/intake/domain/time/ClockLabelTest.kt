package com.zeroindex.intake.domain.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClockLabelTest {
    @Test fun `24 hour phone`() {
        assertThat(ClockLabel.fromMinute(6 * 60 + 30, true)).isEqualTo("6:30")
        assertThat(ClockLabel.fromMinute(18 * 60, true)).isEqualTo("18:00")
        assertThat(ClockLabel.fromHour(21, true)).isEqualTo("21:00")
    }

    @Test fun `12 hour phone`() {
        assertThat(ClockLabel.fromMinute(6 * 60 + 30, false)).isEqualTo("6:30 AM")
        assertThat(ClockLabel.fromMinute(12 * 60, false)).isEqualTo("12:00 PM")
        assertThat(ClockLabel.fromMinute(0, false)).isEqualTo("12:00 AM")
        assertThat(ClockLabel.fromMinute(18 * 60, false)).isEqualTo("6:00 PM")
        assertThat(ClockLabel.fromHour(22, false)).isEqualTo("10:00 PM")
    }
}
