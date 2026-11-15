package com.zeroindex.intake.ui.allergen

import androidx.compose.runtime.Composable
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot

/**
 * A log written from an entry or a typed name (copy to another meal, quick add calories) that the local engine flagged,
 * waiting on the standard warning sheet. Nothing is written until "Log it anyway"; the new entry then keeps
 * "Logged despite: ..." (`AllergenOverride.noteFor`).
 */
data class PendingEntryAllergen(
    val action: Action,
    val name: String,
    val scan: AllergenScanResult,
) {
    sealed interface Action {
        data class Copy(val entry: FoodLogEntity, val slot: MealSlot) : Action
        data class EatOut(val option: com.zeroindex.intake.data.db.entity.EatOutOptionEntity, val macros: com.zeroindex.intake.domain.nutrition.Macros?) : Action
        data class QuickCalories(val name: String, val kcal: Double, val slot: MealSlot, val at: Long) : Action
    }
}

/** The warning sheet for a [PendingEntryAllergen]. */
@Composable
fun PendingEntryAllergenSheet(pending: PendingEntryAllergen, onDismiss: () -> Unit, onLogAnyway: () -> Unit) {
    AllergenWarningSheet(
        foodName = pending.name,
        scan = pending.scan,
        onDismiss = onDismiss,
        onLogAnyway = onLogAnyway,
    )
}
