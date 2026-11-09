package com.zeroindex.intake.domain.report

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.Locale
import org.junit.Test

class WeekLabelTest {

    @Test
    fun aWeekInsideOneMonthShowsOneMonth() {
        // Monday 9 November 2026 to Sunday 15 November.
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 11, 9), Locale.US)).isEqualTo("Nov 9–15")
    }

    @Test
    fun aWeekAcrossTwoMonthsNamesBoth() {
        // Monday 26 October 2026 runs to Sunday 1 November.
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 10, 26), Locale.US)).isEqualTo("Oct 26 – Nov 1")
        assertThat(WeekLabel.range(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5), Locale.US)).isEqualTo("Oct 30 – Nov 5")
    }

    @Test
    fun aWeekAcrossTwoYearsAddsTheYearToBothEnds() {
        // Monday 29 December 2025 runs to Sunday 4 January 2026.
        assertThat(WeekLabel.forWeek(LocalDate.of(2025, 12, 29), Locale.US)).isEqualTo("Dec 29, 2025 – Jan 4, 2026")
    }

    @Test
    fun theWeekIsSevenDaysInclusive() {
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 3, 2), Locale.US)).isEqualTo("Mar 2–8")
        // The last day of a month is the end of its week, not the start of the next label.
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 3, 25), Locale.US)).isEqualTo("Mar 25–31")
    }

    @Test
    fun dayFirstLocalesPutTheDayBeforeTheMonth() {
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 11, 9), Locale.UK)).isEqualTo("9–15 Nov")
        assertThat(WeekLabel.range(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 5), Locale.UK)).isEqualTo("30 Oct – 5 Nov")
        assertThat(WeekLabel.forWeek(LocalDate.of(2025, 12, 29), Locale.UK)).isEqualTo("29 Dec 2025 – 4 Jan 2026")
    }

    @Test
    fun monthNamesFollowTheLocale() {
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 11, 9), Locale.GERMANY)).contains("Nov")
        assertThat(WeekLabel.forWeek(LocalDate.of(2026, 2, 9), Locale.FRANCE)).startsWith("9–15")
    }
}
