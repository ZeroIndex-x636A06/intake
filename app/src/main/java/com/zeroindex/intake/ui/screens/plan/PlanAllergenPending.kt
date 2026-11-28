package com.zeroindex.intake.ui.screens.plan

import androidx.compose.runtime.Composable
import com.zeroindex.intake.data.repository.RecipeContent
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.ui.allergen.AllergenWarningSheet

/**
 * A planned meal that matches the person's allergens, waiting on the same warning sheet as Log food. Nothing is logged
 * until "Log it anyway"; the entry then keeps "Logged despite: ..." (`AllergenOverride.noteFor`).
 */
data class PendingPlanAllergen(
    val content: RecipeContent,
    val slot: MealSlot,
    val at: Long?,
    val macros: Macros?,
    val scan: AllergenScanResult,
)

/** The warning sheet for a [PendingPlanAllergen]. */
@Composable
fun PlanAllergenSheet(pending: PendingPlanAllergen, onDismiss: () -> Unit, onLogAnyway: () -> Unit) {
    AllergenWarningSheet(
        onFindSwap = com.zeroindex.intake.ui.allergen.rememberFindSwap(),
        foodName = pending.content.recipe.name,
        scan = pending.scan,
        ingredientText = pending.content.ingredientLines.joinToString(", ").takeIf { it.isNotBlank() },
        onDismiss = onDismiss,
        onLogAnyway = onLogAnyway,
    )
}
