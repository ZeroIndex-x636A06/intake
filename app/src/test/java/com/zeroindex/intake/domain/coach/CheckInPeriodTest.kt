package com.zeroindex.intake.domain.coach

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

class CheckInPeriodTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test fun mondayCheckInCoversThePreviousCalendarWeek() {
        // Mon 16 Nov 2026: the window is 10 to 16 Nov, six of its days are in the week of Mon 9 Nov.
        assertThat(CheckInPeriod.weekCovering(day(2026, 11, 16))).isEqualTo(LocalDate.of(2026, 11, 9))
    }

    @Test fun sundayCheckInCoversItsOwnWeek() {
        assertThat(CheckInPeriod.weekCovering(day(2026, 11, 15))).isEqualTo(LocalDate.of(2026, 11, 9))
    }

    @Test fun midweekCheckInCoversTheWeekWithMostOfItsDays() {
        // Thu 19 Nov: window 13 to 19 Nov; Fri to Sun is 3 days, Mon to Thu is 4.
        assertThat(CheckInPeriod.weekCovering(day(2026, 11, 19))).isEqualTo(LocalDate.of(2026, 11, 16))
    }

    @Test fun labelNamesTheCheckInsOwnDays() {
        assertThat(CheckInPeriod.label(day(2026, 11, 16), locale = Locale.UK)).isEqualTo("10–16 Nov")
        assertThat(CheckInPeriod.label(day(2026, 11, 16), locale = Locale.US)).isEqualTo("Nov 10–16")
    }
}
