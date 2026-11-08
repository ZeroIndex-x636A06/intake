package com.zeroindex.intake.domain.plan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CookStepsTest {
    private fun secs(text: String) = CookSteps.durations(text).map { it.seconds }

    @Test fun `minutes in several spellings`() {
        assertThat(secs("Sear 5 minutes per side")).containsExactly(300)
        assertThat(secs("Simmer 10 min")).containsExactly(600)
        assertThat(secs("Rest 2 mins")).containsExactly(120)
        assertThat(secs("Bake 20 minute")).containsExactly(1200)
    }

    @Test fun `hours and seconds`() {
        assertThat(secs("Slow cook 1 hour")).containsExactly(3600)
        assertThat(secs("Cook 8 hours on low")).containsExactly(28800)
        assertThat(secs("Toast for 30 seconds")).containsExactly(30)
        assertThat(secs("Stir 45 sec")).containsExactly(45)
    }

    @Test fun `a range uses the upper value`() {
        assertThat(secs("Cook 3-4 minutes")).containsExactly(240)
        assertThat(secs("Cook 3 to 4 minutes")).containsExactly(240)
        assertThat(secs("Chill 1–2 hours")).containsExactly(7200)
    }

    @Test fun `decimals`() {
        assertThat(secs("Rest 1.5 hours")).containsExactly(5400)
    }

    @Test fun `hour and minutes together are one timer`() {
        val t = CookSteps.durations("Roast 1 hour 30 minutes until tender")
        assertThat(t).hasSize(1)
        assertThat(t.single().seconds).isEqualTo(5400)
        assertThat(t.single().text).isEqualTo("1 hour 30 minutes")
    }

    @Test fun `several timers in one step and temperatures are ignored`() {
        assertThat(secs("Sear 5 minutes per side to 165°F, rest 5 minutes")).containsExactly(300, 300)
        assertThat(secs("Bake at 425°F")).isEmpty()
    }

    @Test fun `no false positives inside words`() {
        assertThat(secs("Add 2 tablespoons of oil")).isEmpty()
        assertThat(secs("Use 3 minced cloves")).isEmpty()
        assertThat(secs("Set aside")).isEmpty()
    }

    @Test fun `positions point at the text`() {
        val text = "Sear 5 minutes per side"
        val t = CookSteps.durations(text).single()
        assertThat(text.substring(t.start, t.end)).isEqualTo("5 minutes")
    }

    @Test fun `split strips numbering and blanks`() {
        val steps = CookSteps.split("1. Rinse rice\n\n2) Cook it\nStep 3: Serve\n  Plain line  ")
        assertThat(steps).containsExactly("Rinse rice", "Cook it", "Serve", "Plain line").inOrder()
    }

    @Test fun `join and split round trip`() {
        val steps = listOf("Rinse", "Cook", "Serve")
        assertThat(CookSteps.split(CookSteps.join(steps))).isEqualTo(steps)
    }

    @Test fun `formatting`() {
        assertThat(CookSteps.formatShort(300)).isEqualTo("5 min")
        assertThat(CookSteps.formatShort(5400)).isEqualTo("1 h 30 min")
        assertThat(CookSteps.formatShort(30)).isEqualTo("30 sec")
        assertThat(CookSteps.formatClock(300)).isEqualTo("5:00")
        assertThat(CookSteps.formatClock(3725)).isEqualTo("1:02:05")
        assertThat(CookSteps.formatClock(-4)).isEqualTo("0:00")
    }
}
