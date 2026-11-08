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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.ui.components.ScreenHeading
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrainScreen(
    contentPadding: PaddingValues,
    onOpenSession: (String) -> Unit,
    onOpenBuilder: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenExercises: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") { ScreenHeading("Train") }

        item(key = "today") {
            Section {
                val program = state.activeProgram
                val inProgress = state.inProgress
                when {
                    inProgress != null -> {
                        Text("Session in progress", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Started ${timeOf(inProgress.startedAt)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { onOpenSession(inProgress.id) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Carry on") }
                    }

                    program == null -> {
                        Text("No program yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "A program here is data with progression rules attached, so " +
                                "the weights move on their own. Start from a four-day " +
                                "upper/lower template and change whatever you like.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.createStarterProgram() },
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(if (state.isWorking) "Setting up..." else "Create a starter program") }
                        OutlinedButton(
                            onClick = onOpenBuilder,
                            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                        ) { Text("Build one from scratch") }
                    }

                    else -> {
                        Text(program.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Week ${program.currentWeek} · next up: " +
                                (state.nextDay?.name ?: "nothing scheduled"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.startSession(state.nextDay?.id, onOpenSession)
                            },
                            enabled = !state.isWorking,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text("Start ${state.nextDay?.name ?: "a session"}") }
                        OutlinedButton(
                            onClick = { viewModel.startSession(null, onOpenSession) },
                            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 8.dp),
                        ) { Text("Freestyle session") }
                    }
                }
            }
        }

        if (state.days.isNotEmpty()) {
            item(key = "days") {
                Section {
                    Text("Program days", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    state.days.forEach { day ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(day.name, style = MaterialTheme.typography.bodyLarge)
                            OutlinedButton(
                                onClick = { viewModel.startSession(day.id, onOpenSession) },
                            ) { Text("Start") }
                        }
                    }
                }
            }
        }

        item(key = "volume") {
            Section {
                Text("This week's sets", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (state.weeklySets.isEmpty()) {
                    Text(
                        text = "Nothing logged in the last seven days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.weeklySets.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = row.primaryMuscle.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${row.setCount} sets",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (row.setCount < TrainUiState.WEEKLY_SET_FLOOR) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    // SPEC §10: flag anything under ten working sets a week, in
                    // words as well as colour.
                    if (state.underworked.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Under ten sets this week: " +
                                state.underworked.joinToString(", ") { it.primaryMuscle },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }

        if (state.recentSessions.isNotEmpty()) {
            item(key = "recent-heading") {
                Text("Recent sessions", style = MaterialTheme.typography.titleMedium)
            }
            items(items = state.recentSessions, key = { it.id }) { session ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = dateOf(session.startedAt),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (session.finishedAt != null) {
                                val minutes = (session.finishedAt - session.startedAt) / 60_000
                                "$minutes minutes"
                            } else {
                                "Unfinished"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "links") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("History and analytics") }
                OutlinedButton(
                    onClick = onOpenExercises,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Exercise library") }
                OutlinedButton(
                    onClick = onOpenBuilder,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Program builder") }
            }
        }
    }
}

@Composable
private fun Section(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

private fun timeOf(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun dateOf(millis: Long): String = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
