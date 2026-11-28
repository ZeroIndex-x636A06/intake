package com.zeroindex.intake.domain.goals

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.nutrition.MacroTarget
import org.junit.Test

/** Q5b-8 and Q5b-9. */
class ManualTargetsTest {
    private val manual = MacroTarget(kcal = 2260, proteinG = 170, carbsG = 245, fatG = 70)
    private val auto = MacroTarget(kcal = 2341, proteinG = 166, carbsG = 270, fatG = 68)

    private val history = listOf(
        ManualTargets.Point(1_000, manual, isAuto = false),
        ManualTargets.Point(2_000, auto, isAuto = true),
    )

    @Test fun `switching auto off brings the hand-set macros back`() {
        assertThat(ManualTargets.restoreOnAutoOff(history, active = auto)).isEqualTo(manual)
    }

    @Test fun `nothing to restore when the manual target is already active or none exists`() {
        assertThat(ManualTargets.restoreOnAutoOff(history, active = manual)).isNull()
        assertThat(ManualTargets.restoreOnAutoOff(listOf(ManualTargets.Point(1, auto, true)), auto)).isNull()
        assertThat(ManualTargets.restoreOnAutoOff(emptyList(), null)).isNull()
    }

    @Test fun `the latest hand-set target wins`() {
        val newer = manual.copy(kcal = 2400, carbsG = 285)
        val h = history + ManualTargets.Point(3_000, newer, isAuto = false)
        assertThat(ManualTargets.lastManual(h)).isEqualTo(newer)
    }

    @Test fun `retyping the hand-set calories keeps the hand-set macros`() {
        val computed = MacroTarget(2260, 159, 255, 67)
        assertThat(ManualTargets.forTypedKcal(2260, manual, computed)).isEqualTo(manual)
        assertThat(ManualTargets.forTypedKcal(2200, manual, computed)).isEqualTo(computed)
        assertThat(ManualTargets.forTypedKcal(2260, null, computed)).isEqualTo(computed)
    }

    @Test fun `saving the calories of the active target keeps its macros`() {
        val active = MacroTarget(2260, 170, 245, 70)
        val computed = MacroTarget(2260, 166, 242, 70)
        val lastManual = MacroTarget(1000, 100, 100, 30)
        assertThat(ManualTargets.forTypedKcal(2260, lastManual, computed, active)).isEqualTo(active)
        assertThat(ManualTargets.forTypedKcal(1000, lastManual, computed, active)).isEqualTo(lastManual)
        assertThat(ManualTargets.forTypedKcal(2300, lastManual, computed, active)).isEqualTo(computed)
    }

    @Test fun `a manual target below the floor warns, at or above does not`() {
        val floor = ManualTargets.floorKcal(bmrKcal = 1_800.0)
        assertThat(floor).isEqualTo(1980)
        assertThat(ManualTargets.floorWarning(1000, floor, allowBelowFloor = false)).contains("below the usual floor of 1980")
        assertThat(ManualTargets.floorWarning(1000, floor, allowBelowFloor = true)).contains("You allowed this")
        assertThat(ManualTargets.floorWarning(1980, floor, false)).isNull()
        assertThat(ManualTargets.floorWarning(2260, floor, false)).isNull()
        assertThat(ManualTargets.floorWarning(1000, null, false)).isNull()
    }
}
