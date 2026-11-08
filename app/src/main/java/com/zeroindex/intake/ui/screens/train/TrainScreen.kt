package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeroindex.intake.ui.components.EmptyState

@Composable
fun TrainScreen(
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExercises: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = "Train",
        body = "Program builder, progression rules and the active workout screen.",
        contentPadding = contentPadding,
        modifier = modifier,
    )
}
