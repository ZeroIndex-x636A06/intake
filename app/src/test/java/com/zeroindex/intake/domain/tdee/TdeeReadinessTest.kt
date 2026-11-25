package com.zeroindex.intake.domain.tdee

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Q11-6: the maintenance card says what is missing, counting the days that have both a weigh-in and a log. */
class TdeeReadinessTest {
    private val today = 1_000L

    @Test fun `21 logged days and 17 weigh-in days can still be short of 14 shared days`() {
        val logged = (today - 20..today).toSet()                 // 21 days
        val weighed = (today - 27..today - 11).toSet()           // 17 days, mostly before the logs began
        val r = TdeeCalculator.readiness(weighed, logged, today)
        assertThat(r.loggedDays).isEqualTo(21)
        assertThat(r.weighInDays).isEqualTo(17)
        assertThat(r.bothDays).isEqualTo(10)
        assertThat(r.ready).isFalse()
        assertThat(r.message()).isEqualTo(
            "Needs 14 days with both a weigh-in and a food log. Last 28 days: 17 with a weigh-in, 21 logged, 10 with both.",
        )
    }

    @Test fun `enough shared days is ready`() {
        val days = (today - 14..today).toSet()
        val r = TdeeCalculator.readiness(days, days, today)
        assertThat(r.bothDays).isEqualTo(15)
        assertThat(r.ready).isTrue()
        assertThat(r.message()).startsWith("Enough data")
    }

    @Test fun `days older than the window do not count`() {
        val old = (today - 100..today - 60).toSet()
        val r = TdeeCalculator.readiness(old, old, today)
        assertThat(r.bothDays).isEqualTo(0)
    }
}
