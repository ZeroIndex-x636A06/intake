package com.zeroindex.intake.ui.screens.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.videos.DraftItem
import com.zeroindex.intake.domain.videos.DraftKind
import com.zeroindex.intake.domain.videos.DraftSource
import com.zeroindex.intake.ui.components.AiWaitCard
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.screens.train.ExercisePickerSheet
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Make this a workout (IA v5 7c). Review what was read from the video, edit anything, then save it as a My workout (and
 * start it, if you like). Nothing is saved until Save.
 */
@Composable
fun MakeWorkoutScreen(
    contentPadding: PaddingValues,
    onSaved: (programId: String, sessionId: String?) -> Unit,
    onAddByHand: (programId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MakeWorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingFor by remember { mutableStateOf<Int?>(null) }

    if (state.loading) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.meta?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 2)
                    Text(
                        when (state.source) {
                            DraftSource.NOTHING -> "No chapters or exercise lines found."
                            else -> state.source.label + ". Check each row, change what is wrong, and turn off what is not an exercise."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.error?.let { message ->
                item(key = "error") { ErrorState(body = message) }
            }
            if (!state.hasDescription && state.source == DraftSource.NOTHING) {
                item(key = "no-description") {
                    SandNote("This video was opened from a pasted link without a key, so only its title is known. Add the exercises by hand, or ask AI below.")
                }
            }

            if (state.items.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "Nothing to review yet",
                        body = "Build the list by hand, or ask AI to suggest one from the video's text.",
                    )
                }
            }
            items(state.items, key = { "draft-${it.key}" }) { item ->
                DraftCard(
                    item = item,
                    canUp = state.items.firstOrNull()?.key != item.key,
                    canDown = state.items.lastOrNull()?.key != item.key,
                    onChange = { change -> viewModel.edit(item.key, change) },
                    onRename = { viewModel.rename(item.key, it) },
                    onPick = { pickingFor = item.key },
                    onMove = { viewModel.move(item.key, it) },
                    onRemove = { viewModel.remove(item.key) },
                )
            }
            item(key = "add") {
                OutlinedButton(onClick = viewModel::addBlank, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Add an exercise")
                }
            }

            if (state.aiAvailable) {
                item(key = "ai") {
                    IntakeCard {
                        Text("Improve with AI", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Optional. Sends this video's title, chapters and description (nothing else) to your AI model, and shows the answer here for you to check. It counts towards your AI budget.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        )
                        if (state.aiBusy) {
                            AiWaitCard(message = "Reading the video's text", startedAtMillis = state.aiStartedAt, onCancel = viewModel::cancelAi)
                        } else {
                            OutlinedButton(onClick = viewModel::improveWithAi, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                Text("  Improve with AI")
                            }
                        }
                        state.aiError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            } else if (state.items.isEmpty()) {
                item(key = "ai-off") {
                    Text(
                        "AI help is off. You can turn it on in Settings > AI, or add the exercises by hand.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.items.isEmpty()) {
                item(key = "by-hand") {
                    TextButton(onClick = { viewModel.addByHand(onAddByHand) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Open the builder with an empty workout")
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Workout name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.save(false, onSaved) },
                        enabled = state.includedCount > 0 && !state.saving && state.name.isNotBlank(),
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Save workout") }
                    Button(
                        onClick = { viewModel.save(true, onSaved) },
                        enabled = state.includedCount > 0 && !state.saving && state.name.isNotBlank() && !state.workoutRunning,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text("Save and start") }
                }
                if (state.workoutRunning) {
                    Text(
                        "A workout is already running, so it cannot be started now. Save it and start it after.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.includedCount == 0) {
                    Text("Turn on at least one exercise to save.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pickingFor?.let { key ->
        ExercisePickerSheet(
            title = "Use which exercise?",
            onPick = { id -> viewModel.pickExercise(key, id); pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }
}

@Composable
private fun DraftCard(
    item: DraftItem,
    canUp: Boolean,
    canDown: Boolean,
    onChange: ((DraftItem) -> DraftItem) -> Unit,
    onRename: (String) -> Unit,
    onPick: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val timed = item.seconds != null
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(item.kind.label)
            Spacer(Modifier.weight(1f))
            Text(if (item.include) "Include" else "Left out", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.padding(start = 8.dp))
            Switch(checked = item.include, onCheckedChange = { on -> onChange { it.copy(include = on) } })
        }
        if (item.kind == DraftKind.MATCHED) {
            Text(item.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        } else {
            OutlinedTextField(
                value = item.exerciseName,
                onValueChange = onRename,
                label = { Text("Exercise name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        if (item.text.isNotBlank() && !item.text.equals(item.exerciseName, ignoreCase = true)) {
            Text("From: \"${item.text.take(80)}\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(Modifier.weight(1f), "Sets", item.sets.toString()) { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(sets = n.coerceIn(1, 20)) } } }
            if (timed) {
                NumberField(Modifier.weight(1f), "Seconds", item.seconds.toString()) { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(seconds = n.coerceIn(5, 3600)) } } }
            } else {
                NumberField(Modifier.weight(1f), "Reps", item.reps.toString()) { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(reps = n.coerceIn(1, 100)) } } }
            }
            NumberField(Modifier.weight(1f), "Rest (s)", item.restSec.toString()) { v -> v.toIntOrNull()?.let { n -> onChange { it.copy(restSec = n.coerceIn(0, 600)) } } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (item.kind == DraftKind.MATCHED) "Use another" else "Pick from library") }
            TextButton(
                onClick = { onChange { it.copy(seconds = if (timed) null else 30, reps = if (timed) VideoDefaults.REPS else 1) } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (timed) "Count reps" else "Timed") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onMove(-1) }, enabled = canUp) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
            IconButton(onClick = { onMove(1) }, enabled = canDown) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove this row") }
        }
    }
}

private object VideoDefaults { const val REPS = 10 }

@Composable
private fun NumberField(modifier: Modifier, label: String, value: String, onValue: (String) -> Unit) {
    // Keeps what is typed (even an empty box) while the number is only applied when it reads as one.
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw -> text = raw.filter { c -> c.isDigit() }.take(4); onValue(text) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
