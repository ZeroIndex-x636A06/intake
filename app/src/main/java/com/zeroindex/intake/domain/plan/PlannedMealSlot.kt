package com.zeroindex.intake.domain.plan

import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nfc.TemplateLink
import java.time.LocalTime

/**
 * The meal slot a planned main dish is logged to when the plan itself names none (a plan week holds recipes, not
 * slots). It is the time-of-day meal, except that the late-evening "snack" window becomes dinner: a planned main
 * dish is a meal, so a plan dish opened at 22:00 is not logged as a snack.
 */
object PlannedMealSlot {
    fun forTime(time: LocalTime): MealSlot = when (val slot = TemplateLink.slotFor(time)) {
        MealSlot.SNACK -> MealSlot.DINNER
        else -> slot
    }
}
