package com.zeroindex.intake.domain.template

import com.zeroindex.intake.domain.model.MealSlot

/**
 * The snackbar text for a logged meal template, the same on every phone path (the Meal templates sheet and an NFC tag
 * tap): "Logged Post-workout shake to Snacks", with the allergen note after it when there is one.
 */
object TemplateLogMessage {
    fun slotLabel(slot: MealSlot): String = when (slot) {
        MealSlot.BREAKFAST -> "Breakfast"
        MealSlot.LUNCH -> "Lunch"
        MealSlot.DINNER -> "Dinner"
        MealSlot.SNACK -> "Snacks"
        MealSlot.DRINKS -> "Drinks"
    }

    fun logged(name: String, slot: MealSlot, allergenNote: String?): String =
        "Logged $name to ${slotLabel(slot)}" + (allergenNote?.let { ". $it" } ?: "")
}
