package com.zeroindex.intake.domain.time

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

class WeekStripTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test fun `page 0 is the last six days and today`() {
        val days = WeekStrip.days(today, 0)
        assertThat(days).hasSize(7)
        assertThat(days.first()).isEqualTo(LocalDate.of(2026, 11, 10))
        assertThat(days.last()).isEqualTo(today)
    }

    @Test fun `each page goes back one week without gaps or overlap`() {
        val p0 = WeekStrip.days(today, 0)
        val p1 = WeekStrip.days(today, 1)
        assertThat(p1.last()).isEqualTo(p0.first().minusDays(1))
        assertThat(p1.first()).isEqualTo(LocalDate.of(2026, 11, 3))
        assertThat(p1.intersect(p0.toSet())).isEmpty()
    }

    @Test fun `a negative page is treated as page 0`() {
        assertThat(WeekStrip.days(today, -3)).isEqualTo(WeekStrip.days(today, 0))
    }

    @Test fun `pageOf finds the window that holds the selected day`() {
        assertThat(WeekStrip.pageOf(today, today)).isEqualTo(0)
        assertThat(WeekStrip.pageOf(today, today.minusDays(6))).isEqualTo(0)
        assertThat(WeekStrip.pageOf(today, today.minusDays(7))).isEqualTo(1)
        assertThat(WeekStrip.pageOf(today, today.minusDays(13))).isEqualTo(1)
        assertThat(WeekStrip.pageOf(today, today.minusDays(14))).isEqualTo(2)
    }

    @Test fun `every day of a page maps back to that page`() {
        for (page in 0..40) for (d in WeekStrip.days(today, page)) {
            assertThat(WeekStrip.pageOf(today, d)).isEqualTo(page)
        }
    }

    @Test fun `a future day is page 0 and not selectable`() {
        assertThat(WeekStrip.pageOf(today, today.plusDays(3))).isEqualTo(0)
        assertThat(WeekStrip.isSelectable(today, today.plusDays(1))).isFalse()
        assertThat(WeekStrip.isSelectable(today, today)).isTrue()
        assertThat(WeekStrip.isSelectable(today, today.minusYears(2))).isTrue()
        assertThat(WeekStrip.clampToToday(today, today.plusDays(5))).isEqualTo(today)
        assertThat(WeekStrip.clampToToday(today, today.minusDays(5))).isEqualTo(today.minusDays(5))
    }

    @Test fun `the strip always has a page for the selected day`() {
        assertThat(WeekStrip.pageCount(today, today)).isEqualTo(WeekStrip.MAX_PAGES)
        val old = today.minusYears(30)
        val count = WeekStrip.pageCount(today, old)
        assertThat(count).isGreaterThan(WeekStrip.pageOf(today, old))
        assertThat(WeekStrip.days(today, WeekStrip.pageOf(today, old))).contains(old)
    }

    @Test fun `paging is stable across a month and year boundary`() {
        val jan = LocalDate.of(2027, 1, 3)
        assertThat(WeekStrip.days(jan, 0).first()).isEqualTo(LocalDate.of(2026, 12, 28))
        assertThat(WeekStrip.pageOf(jan, LocalDate.of(2026, 12, 27))).isEqualTo(1)
    }
}
