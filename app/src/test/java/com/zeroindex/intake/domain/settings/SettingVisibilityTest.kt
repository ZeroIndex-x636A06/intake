package com.zeroindex.intake.domain.settings

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.Phase
import org.junit.Test

class SettingVisibilityTest {

    @Test fun carbCycleShiftIsHiddenWithCarbCyclingOff() {
        assertThat(SettingVisibility.shown("carbCycleShiftPct", Phase.CUT, carbCycling = false)).isFalse()
        assertThat(SettingVisibility.shown("carbCycleShiftPct", Phase.CUT, carbCycling = true)).isTrue()
    }

    @Test fun phaseRowsAreShownOnlyInTheirPhase() {
        assertThat(SettingVisibility.shown("contestWeeklyLossPct", Phase.CUT, carbCycling = false)).isFalse()
        assertThat(SettingVisibility.shown("contestWeeklyLossPct", Phase.CONTEST_PREP, carbCycling = false)).isTrue()
        assertThat(SettingVisibility.shown("reverseStepKcal", Phase.CUT, carbCycling = false)).isFalse()
        assertThat(SettingVisibility.shown("reverseStepKcal", Phase.REVERSE_DIET, carbCycling = false)).isTrue()
    }

    @Test fun ordinaryRowsAreAlwaysShown() {
        assertThat(SettingVisibility.shown("refeedDaysPerWeek", Phase.CUT, carbCycling = false)).isTrue()
        assertThat(SettingVisibility.shown("allowBelowFloor", Phase.CUT, carbCycling = false)).isTrue()
    }

    @Test fun aCutWithCarbCyclingOffShowsFourAdvancedRowsOfTheSeven() {
        val advanced = SettingsRegistry.inGroup(SettingsGroup.NUTRITION).filter { it.advanced }
        assertThat(advanced).hasSize(7)
        val shown = advanced.filter { SettingVisibility.shown(it.id, Phase.CUT, carbCycling = false) }
        assertThat(shown.map { it.id }).containsExactly(
            "allowBelowFloor", "carbCycling", "refeedDaysPerWeek", "dietBreakEveryWeeks",
        ).inOrder()
    }

    @Test fun carbCyclingOnAddsTheShiftRow() {
        val advanced = SettingsRegistry.inGroup(SettingsGroup.NUTRITION).filter { it.advanced }
        val shown = advanced.filter { SettingVisibility.shown(it.id, Phase.CUT, carbCycling = true) }
        assertThat(shown.map { it.id }).contains("carbCycleShiftPct")
        assertThat(shown).hasSize(5)
    }
}
