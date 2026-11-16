package com.zeroindex.intake.domain.training

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduledDeloadTest {

    @Test fun everySixthWeekIsAnEasyWeek() {
        val easy = (1..18).filter { ScheduledDeload.startsWith(it, 6) }
        assertThat(easy).containsExactly(6, 12, 18).inOrder()
    }

    @Test fun aProgramWithNoCadenceNeverDeloadsByItself() {
        assertThat((1..30).any { ScheduledDeload.startsWith(it, null) }).isFalse()
        assertThat((1..30).any { ScheduledDeload.startsWith(it, 1) }).isFalse()
        assertThat((1..30).any { ScheduledDeload.startsWith(it, 0) }).isFalse()
    }
}
