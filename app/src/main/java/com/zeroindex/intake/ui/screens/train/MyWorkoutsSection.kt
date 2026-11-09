package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.projection.WorkoutSummary
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Saved single-session workouts ("Arm day", "Hotel gym"): see docs/WORKOUTS.md. */
@HiltViewModel
class MyWorkoutsViewModel @Inject constructor(
    private val repository: TrainingRepository,
) : ViewModel() {
    val workouts: StateFlow<List<WorkoutSummary>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start(dayId: String, onStarted: (String) -> Unit) = viewModelScope.launch {
        onStarted(repository.startSession(dayId).id)
    }

    fun create(name: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        onCreated(repository.createWorkout(name).id)
    }

    fun rename(programId: String, name: String) = viewModelScope.launch { repository.renameWorkout(programId, name) }
    fun duplicate(programId: String) = viewModelScope.launch { repository.duplicateWorkout(programId) }
    fun delete(programId: String) = viewModelScope.launch { repository.deleteWorkout(programId) }
}

/** The "My workouts" section on Train: a Start button per workout, and a way to make a new one. */
@Composable
fun MyWorkoutsSection(
    onOpenSession: (String) -> Unit,
    onEditWorkout: (programId: String) -> Unit,
    viewModel: MyWorkoutsViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<WorkoutSummary?>(null) }
    var deleting by remember { mutableStateOf<WorkoutSummary?>(null) }

    IntakeCard {
        Eyebrow("My workouts")
        Text(
            text = if (workouts.isEmpty()) {
                "Save a routine you repeat, like \"Arm day\" or \"Hotel gym\", and start it any time. It does not need a program."
            } else {
                "Start any of these any time. They sit outside your program."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            workouts.forEach { w ->
                var menu by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${w.exerciseCount} ${if (w.exerciseCount == 1) "exercise" else "exercises"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { w.dayId?.let { viewModel.start(it, onOpenSession) } },
                        enabled = w.dayId != null && w.exerciseCount > 0,
                        shape = IntakeShape.Chip,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Start") }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More for ${w.name}")
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit exercises") }, onClick = { menu = false; onEditWorkout(w.programId) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = w })
                            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; viewModel.duplicate(w.programId) })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = w })
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { naming = true },
            shape = IntakeShape.Button,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
        ) { Text("New workout") }
    }

    if (naming) {
        NameDialog("New workout", "", "Create", onDismiss = { naming = false }) { name ->
            naming = false
            viewModel.create(name, onEditWorkout)
        }
    }
    renaming?.let { w ->
        NameDialog("Rename workout", w.name, "Save", onDismiss = { renaming = null }) { name ->
            renaming = null
            viewModel.rename(w.programId, name)
        }
    }
    deleting?.let { w ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${w.name}?") },
            text = { Text("It leaves My workouts. Sessions you already logged are kept in your history.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(w.programId); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
