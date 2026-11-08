package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.components.ScreenHeading
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.components.TrendLineChart
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    val unit = state.settings.weightUnit

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
        item(key = "heading") { ScreenHeading("History") }

        item(key = "picker") {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = state.selectedExercise?.name ?: "Pick an exercise",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Exercise") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(
                        androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                    ),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    state.exercises.forEach { exercise ->
                        DropdownMenuItem(
                            text = { Text(exercise.name) },
                            onClick = {
                                viewModel.selectExercise(exercise.id)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        if (state.selectedExercise != null) {
            item(key = "chart") {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IntakeShape.Card,
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "Estimated max over time",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            // SPEC §10: show the formula used.
                            text = OneRepMax.FORMULA,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val points = state.oneRepMaxSeries.map {
                            ChartPoint(it.first.toDouble(), unit.fromKg(it.second))
                        }
                        TrendLineChart(
                            raw = points,
                            trend = points,
                            description = "Estimated one-rep max for " +
                                "${state.selectedExercise?.name}, " +
                                "${points.size} points, latest " +
                                "${points.lastOrNull()?.y?.roundToInt() ?: 0} ${unit.label}.",
                        )
                        state.personalRecord?.let { pr ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Best estimated max: " +
                                    "${unit.fromKg(pr).roundToInt()} ${unit.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        item(key = "sessions-heading") {
            Text("Sessions", style = MaterialTheme.typography.titleMedium)
        }

        if (state.sessions.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No sessions logged yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(items = state.sessions, key = { it.id }) { session ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = IntakeShape.Card,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = formatter.format(
                                Instant.ofEpochMilli(session.startedAt)
                                    .atZone(ZoneId.systemDefault()),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = session.finishedAt?.let {
                                "${(it - session.startedAt) / 60_000} minutes"
                            } ?: "Unfinished",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
