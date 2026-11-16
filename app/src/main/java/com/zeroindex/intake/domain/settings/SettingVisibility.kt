package com.zeroindex.intake.domain.settings

import com.zeroindex.intake.domain.goals.Phase

/**
 * Which controls a Settings page draws for this profile. A row that only applies under a switch or a phase is not drawn
 * when it does not apply, and the Advanced count on the page counts only the rows that are drawn.
 */
object SettingVisibility {

    fun shown(id: String, phase: Phase, carbCycling: Boolean): Boolean = when (id) {
        "carbCycleShiftPct" -> carbCycling
        "contestWeeklyLossPct" -> phase == Phase.CONTEST_PREP
        "reverseStepKcal" -> phase == Phase.REVERSE_DIET
        else -> true
    }
}
