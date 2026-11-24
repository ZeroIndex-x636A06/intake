package com.zeroindex.intake.ui.components

import java.time.DayOfWeek
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekStartTest {
    @Test fun usWeekStartsOnSundayWithoutTheHelper() {
        assertEquals(DayOfWeek.SUNDAY, firstDayOf(Locale.US))
    }

    @Test fun usLocaleBecomesMondayFirstAndKeepsItsLanguage() {
        val l = mondayFirstLocale(Locale.US)
        assertEquals(DayOfWeek.MONDAY, firstDayOf(l))
        assertEquals("en", l.language)
    }

    @Test fun otherLanguagesKeepTheirLanguage() {
        val l = mondayFirstLocale(Locale("es", "MX"))
        assertEquals(DayOfWeek.MONDAY, firstDayOf(l))
        assertEquals("es", l.language)
    }

    @Test fun aMondayLocaleIsLeftAlone() {
        assertEquals(Locale.UK, mondayFirstLocale(Locale.UK))
        assertEquals(Locale.GERMANY, mondayFirstLocale(Locale.GERMANY))
    }
}
