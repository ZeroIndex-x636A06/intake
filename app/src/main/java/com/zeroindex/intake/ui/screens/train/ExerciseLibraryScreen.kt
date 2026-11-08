package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.components.ScreenHeading

@Composable
fun ExerciseLibraryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("") }
    var equipment by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "heading") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ScreenHeading("Exercises")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${exercises.size} in the library. Primary muscle drives the weekly " +
                        "set counts on the Train tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = muscle,
                        onValueChange = { muscle = it },
                        label = { Text("Muscle") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Equipment (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.add(name.trim(), muscle.trim().lowercase(), equipment.trim())
                        name = ""
                        muscle = ""
                        equipment = ""
                    },
                    enabled = name.isNotBlank() && muscle.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Add exercise") }
                Spacer(Modifier.height(16.dp))
            }
        }

        items(items = exercises, key = { it.id }) { exercise ->
            ListItem(
                headlineContent = { Text(exercise.name) },
                supportingContent = {
                    Text(
                        buildString {
                            append(exercise.primaryMuscle)
                            if (exercise.equipment.isNotBlank()) append(" · ${exercise.equipment}")
                            if (exercise.isBodyweight) append(" · bodyweight")
                        },
                    )
                },
            )
            HorizontalDivider()
        }
    }
}
