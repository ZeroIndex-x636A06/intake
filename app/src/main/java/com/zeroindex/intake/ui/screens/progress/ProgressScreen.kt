package com.zeroindex.intake.ui.screens.progress

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.components.EmptyState

@Composable
fun ProgressScreen(
    contentPadding: PaddingValues,
    onAddMeasurement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Progress",
        body = "Weight trend, measurements, adherence heatmap and the weekly summary.",
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
