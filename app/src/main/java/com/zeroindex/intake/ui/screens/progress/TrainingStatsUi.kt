package com.zeroindex.intake.ui.screens.progress

import com.zeroindex.intake.domain.time.DateText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.charts.ChartMath
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.ZoneId
import java.time.format.DateTimeFormatter


/**
 * Estimated 1RM for the most-trained lifts, the latest records and this week's sets per body region: the same card on
 * Progress and on the Training stats screen. Honest empty state until a workout has working sets.
 */
@Composable
fun TrainingStatsCard(
    state: TrainingStatsUiState,
    onOpenLog: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val unit = state.settings.weightUnit
    val zone = ZoneId.systemDefault()
    fun w(kg: Double) = UnitConverter.round(unit.fromKg(kg), 1).toString()
    fun day(at: Long) = DateText.medium(ChartMath.localDate(at, zone))

    SectionCard(title = "Training", caption = "estimated max, records, this week", modifier = modifier) {
        if (state.stats.isEmpty) {
            EmptyState(body = "No workouts logged yet. Finish a session and your estimated maxes and records appear here.")
        } else {
            Eyebrow(text = "Estimated 1RM", modifier = Modifier.padding(bottom = 2.dp))
            state.stats.lifts.forEach { lift ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lift.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${w(lift.estimatedMaxKg)} ${unit.label}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    "from ${w(lift.weightKg)} x ${lift.reps}, ${day(lift.at)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Epley: weight x (1 + reps / 30). An estimate, not a tested max.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Eyebrow(text = "Recent records", modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            if (state.stats.recentPrs.isEmpty()) {
                Text(
                    "No records beaten yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.stats.recentPrs.forEach { pr ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        "${pr.exerciseName}: ${w(pr.weightKg)} ${unit.label} x ${pr.reps}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${pr.kind.label}, ${day(pr.at)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Eyebrow(text = "Sets this week", modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
        if (state.volume.all { it.sets == 0.0 }) {
            Text(
                "No working sets this week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.volume.filter { it.sets > 0.0 || it.target > 0 }.forEach { v ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(v.region.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${v.summary()} · ${WeeklyVolume.words(v.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onOpenLog != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenLog,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Open the training log") }
        }
    }
}

/** The Training stats screen the Progress row opens. */
@Composable
fun TrainingStatsScreen(
    contentPadding: PaddingValues,
    onOpenLog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp + contentPadding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            end = 16.dp + contentPadding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TrainingStatsCard(state, onOpenLog) }
    }
}
