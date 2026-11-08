package com.zeroindex.intake.ui.screens.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.navigation.Route

@Composable
fun MoreScreen(
    contentPadding: PaddingValues,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "More",
        body = "Allergen reference, settings, export and import.",
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
