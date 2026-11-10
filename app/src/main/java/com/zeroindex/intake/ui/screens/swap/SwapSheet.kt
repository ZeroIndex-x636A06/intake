package com.zeroindex.intake.ui.screens.swap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.SandNote

/**
 * STUB of the Swap sheet (round 3, R3-2), opened from Today's Next up card. It shows the three section headers the
 * Nutrition agent fills in: Plan & recipes, Saved, Eat out nearby. Nothing here swaps anything yet. When it is built:
 * every option shows its macros against the planned meal and its allergen status, a red option cannot be chosen, and
 * "Eat out nearby" runs only when the person taps Search (its radius is Settings > Nutrition, never a control here).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapSheet(slot: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Swap${slot?.let { " your ${it.lowercase()}" } ?: ""}", style = MaterialTheme.typography.headlineSmall)
            Section("Plan & recipes", "Another meal from your plan or your recipes.")
            Section("Saved", "A saved meal, a template, or a saved eat-out option. These work offline.")
            Section("Eat out nearby", "Find a place near you. Runs only when you tap Search, and uses your own AI key.")
            SandNote("Coming with the Nutrition rebuild. Nothing is swapped yet.")
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
