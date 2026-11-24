package com.zeroindex.intake.ui.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekStartTest {
    @Test fun pickerKeepsTheDeviceLocale() {
        assertEquals(Locale.US, pickerLocale(Locale.US))
        assertEquals(Locale.UK, pickerLocale(Locale.UK))
    }
}
