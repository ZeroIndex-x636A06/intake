package com.zeroindex.intake.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

class DateTextTest {
    private val d = LocalDate.of(2026, 11, 16)

    @Test fun mediumFollowsTheLocale() {
        assertThat(DateText.medium(d, Locale.UK)).isEqualTo("16 Nov 2026")
        assertThat(DateText.medium(d, Locale.US)).isEqualTo("Nov 16, 2026")
    }

    @Test fun weekdayVariantUsesTheSameOrder() {
        assertThat(DateText.mediumWithWeekday(d, Locale.UK)).isEqualTo("Mon 16 Nov 2026")
        assertThat(DateText.mediumWithWeekday(d, Locale.US)).isEqualTo("Mon, Nov 16, 2026")
    }

    @Test fun epochMillisUseTheGivenZoneDay() {
        // 23:30 UTC on 15 Nov is already 16 Nov in UTC+2.
        val millis = java.time.LocalDateTime.of(2026, 11, 15, 23, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        assertThat(DateText.medium(millis, java.time.ZoneId.of("Europe/Berlin"), Locale.UK)).isEqualTo("16 Nov 2026")
    }
}
