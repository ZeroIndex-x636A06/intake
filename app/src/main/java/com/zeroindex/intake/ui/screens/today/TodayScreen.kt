package com.zeroindex.intake.ui.screens.today

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.components.EmptyState

@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Today",
        body = "Macro ring, meal slots and the add-food paths.",
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
