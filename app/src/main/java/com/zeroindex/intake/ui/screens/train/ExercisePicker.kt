@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Pick one exercise from the library, with search and muscle/equipment filters, or add a new one
 * on the spot. Used by the program builder and by freestyle workouts. [onPick] gets the exercise id.
 */
@Composable
fun ExercisePickerSheet(
    title: String = "Pick an exercise",
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(key = "exercise-picker"),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            ExerciseFilterBar(state, viewModel, showHiddenSwitch = false)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                if (state.shown.isEmpty()) {
                    item {
                        Text(
                            "No exercise matches. Use \"Add your own exercise\" below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = IntakeShape.Card,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                state.shown.forEachIndexed { i, e ->
                                    ExerciseRow(e, onClick = { onPick(e.id) }, onInfo = { info = e.id })
                                    if (i != state.shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { adding = true },
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp),
                    ) { Text("Add your own exercise") }
                }
            }
        }
    }
    info?.let { id -> ExerciseDetailSheet(exerciseId = id, onDismiss = { info = null }) }
    if (adding) {
        ExerciseFormSheet(
            existing = null,
            viewModel = viewModel,
            initialName = state.query,
            onDismiss = { adding = false },
            onSave = { form ->
                viewModel.add(form) { created -> onPick(created.id) }
                adding = false
            },
        )
    }
}
