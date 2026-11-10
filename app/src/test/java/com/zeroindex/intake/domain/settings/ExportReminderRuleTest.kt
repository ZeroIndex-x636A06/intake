package com.zeroindex.intake.domain.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExportReminderRuleTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun `due after 30 days since the last export`() {
        assertThat(ExportReminderRule.isDue(true, false, now - 30 * day, null, now)).isTrue()
        assertThat(ExportReminderRule.isDue(true, false, now - 29 * day, null, now)).isFalse()
    }

    @Test fun `never exported counts from first use`() {
        assertThat(ExportReminderRule.isDue(true, false, null, now - 31 * day, now)).isTrue()
        assertThat(ExportReminderRule.isDue(true, false, null, now - 5 * day, now)).isFalse()
        assertThat(ExportReminderRule.isDue(true, false, null, null, now)).isFalse()
    }

    @Test fun `off when the switch is off or sync is paired`() {
        assertThat(ExportReminderRule.isDue(false, false, null, now - 90 * day, now)).isFalse()
        assertThat(ExportReminderRule.isDue(true, true, null, now - 90 * day, now)).isFalse()
    }

    @Test fun `the message says days then weeks`() {
        assertThat(ExportReminderRule.message(null, now)).contains("not exported")
        assertThat(ExportReminderRule.message(now - 10 * day, now)).contains("10 days")
        assertThat(ExportReminderRule.message(now - 35 * day, now)).contains("5 weeks")
    }
}
