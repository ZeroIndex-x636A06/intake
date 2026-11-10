package com.zeroindex.intake.ui.screens.train

import androidx.compose.runtime.Composable

/** A choice chip whose click handler comes last, so it can take a trailing lambda. */
@Composable
internal fun TrainChip(label: String, selected: Boolean, onClick: () -> Unit) {
    com.zeroindex.intake.ui.components.ChoiceChip(label = label, selected = selected, onClick = onClick)
}
