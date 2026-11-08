package com.zeroindex.intake.ui.screens.plan

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.components.EmptyState

@Composable
fun PlanScreen(
    contentPadding: PaddingValues,
    onCookRecipe: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Plan",
        body = "The 12-week meal plan, cook mode, shopping list and eat-out swaps.",
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
