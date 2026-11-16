package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.nutrition.MacroTarget
import kotlin.math.roundToInt

/**
 * Hand-set targets (QA Q5b-8, Q5b-9). The dated target series already keeps every target with a flag for whether it was
 * worked out automatically, so the person's own numbers are never lost: turning "Work my targets out from my data" off
 * brings the latest hand-set target back, and a manual calorie figure below the safe floor says so.
 */
object ManualTargets {

    /** One dated target as it was stored. */
    data class Point(val effectiveFrom: Long, val target: MacroTarget, val isAuto: Boolean)

    /** The latest target the person set by hand, or null if every stored target was worked out. */
    fun lastManual(history: List<Point>): MacroTarget? =
        history.filter { !it.isAuto }.maxByOrNull { it.effectiveFrom }?.target

    /**
     * The target to bring back when automatic targets are switched off: the latest hand-set one, but only when it is not
     * already the active target. Null means leave things as they are.
     */
    fun restoreOnAutoOff(history: List<Point>, active: MacroTarget?): MacroTarget? =
        lastManual(history)?.takeIf { it != active }

    /**
     * The macros to store for a typed calorie figure. Retyping the same calories as the last hand-set target keeps that
     * target's own protein, carbs and fat; anything else is split from [computed] (body weight times the Settings g/kg).
     */
    fun forTypedKcal(kcal: Int, lastManual: MacroTarget?, computed: MacroTarget): MacroTarget =
        if (lastManual != null && lastManual.kcal == kcal) lastManual else computed

    /** Calories at rest times [PhasePlanner.FLOOR_OF_BMR], rounded: the lowest target the app plans without an override. */
    fun floorKcal(bmrKcal: Double): Int = (bmrKcal * PhasePlanner.FLOOR_OF_BMR).roundToInt()

    /**
     * The warning for a calorie target typed by hand, or null when it is fine. [floorKcal] null (not enough profile data
     * to know the floor) gives no warning. The number is kept either way: this only says it is low.
     */
    fun floorWarning(kcal: Int, floorKcal: Int?, allowBelowFloor: Boolean): String? {
        if (floorKcal == null || kcal >= floorKcal) return null
        return if (allowBelowFloor) {
            "$kcal kcal is below $floorKcal kcal (1.1 x your calories at rest). You allowed this in Advanced. " +
                "Eating this low for long costs muscle, energy and mood."
        } else {
            "$kcal kcal is below the usual floor of $floorKcal kcal (1.1 x your calories at rest). It is saved as you typed it. " +
                "Eating this low for long costs muscle, energy and mood; consider a higher number."
        }
    }
}
