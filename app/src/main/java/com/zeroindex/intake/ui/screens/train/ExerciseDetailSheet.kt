@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.training.ExerciseFilter
import com.zeroindex.intake.data.training.ExerciseHistory
import com.zeroindex.intake.data.training.ExerciseHistoryCalc
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import com.zeroindex.intake.domain.training.ExerciseGuideIndex
import com.zeroindex.intake.domain.training.OneRepMax
import com.zeroindex.intake.domain.training.log.ExerciseStats
import com.zeroindex.intake.domain.training.log.ExerciseStatsBuilder
import com.zeroindex.intake.domain.training.log.LogSet
import com.zeroindex.intake.ui.components.ChartPoint
import com.zeroindex.intake.ui.components.TrendLineChart
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.muscles.MuscleMap
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.ExerciseImages
import com.zeroindex.intake.ui.components.ExercisePhotoPair
import com.zeroindex.intake.ui.components.Glossary
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.MuscleMapView
import com.zeroindex.intake.ui.theme.IntakeShape
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ExerciseDetailState(
    val exercise: ExerciseEntity? = null,
    val history: ExerciseHistory = ExerciseHistory(emptyList(), null, null, 0),
    val stats: ExerciseStats = ExerciseStatsBuilder.build(emptyList()),
    val unit: WeightUnit = WeightUnit.KILOGRAMS,
)

/** Loads one exercise (hidden or not) with its history, for the detail sheet. */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repository: TrainingRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val id = MutableStateFlow<String?>(null)

    fun show(exerciseId: String) { id.value = exerciseId }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ExerciseDetailState> = id.flatMapLatest { exerciseId ->
        if (exerciseId == null) {
            flow { emit(ExerciseDetailState()) }
        } else {
            combine(
                flow { emit(repository.exerciseAnyState(exerciseId)) },
                repository.observeExerciseSets(exerciseId)
                    .map { ExerciseHistoryCalc.summarize(it, java.util.TimeZone.getDefault().rawOffset.toLong()) },
                repository.observeSetsForExercise(exerciseId).map { rows ->
                    ExerciseStatsBuilder.build(rows.map { LogSet(it.sessionId, it.exerciseId, it.setIndex, it.weightKg, it.reps, it.isWarmup, it.completedAt) })
                },
                settings.settings,
            ) { exercise, history, stats, s -> ExerciseDetailState(exercise, history, stats, s.weightUnit) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailState())
}

/**
 * Everything about one exercise: start and end photos, the muscle map, how to do it, the equipment,
 * and the person's own history and estimated max. Opened from the library, the picker, the program
 * builder and the active workout. [onEdit] and [onHideToggle] are given only where editing makes sense.
 */
@Composable
fun ExerciseDetailSheet(
    exerciseId: String,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onHideToggle: (() -> Unit)? = null,
    viewModel: ExerciseDetailViewModel = hiltViewModel(key = "exercise-detail-$exerciseId"),
) {
    androidx.compose.runtime.LaunchedEffect(exerciseId) { viewModel.show(exerciseId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise
    val context = LocalContext.current
    var sideBySide by remember { mutableStateOf(false) }
    var fineMuscles by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (exercise == null) {
            Text(
                "Loading...", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            return@ModalBottomSheet
        }
        val custom = ExerciseFilter.isCustom(exercise)
        val hidden = exercise.deletedAt != null
        val secondary = exercise.secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val highlight = remember(exercise) { MuscleMap.highlightFor(exercise.name, exercise.primaryMuscle, secondary) }
        val pictures = remember(exercise.id, exercise.photoPath) { ExerciseImages.count(context, exercise.id, exercise.photoPath) }
        val steps = remember(exercise.id, exercise.notes) {
            ExerciseImages.index(context).guide(exercise.id)?.steps.orEmpty()
        }
        val unit = state.unit
        val history = state.history

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = buildString {
                    append(Muscles.label(exercise.primaryMuscle))
                    if (secondary.isNotEmpty()) append(" (also ${secondary.joinToString(", ")})")
                    if (exercise.equipment.isNotBlank()) append(" · ").append(Equipment.label(exercise.equipment))
                    if (exercise.isBodyweight) append(" · bodyweight")
                    append(" · rest ${exercise.defaultRestSec}s")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (pictures > 0) {
                ExercisePhotoPair(exercise.id, exercise.photoPath, sideBySide = sideBySide)
                if (pictures > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        ChoiceChip("Fade between", selected = !sideBySide, onClick = { sideBySide = false })
                        ChoiceChip("Side by side", selected = sideBySide, onClick = { sideBySide = true })
                    }
                }
            } else {
                Text(
                    text = if (custom) "No photo yet. Edit the exercise to add your own." else "No photo for this one. The muscle map shows what it works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Eyebrow("Muscles worked")
            MuscleMapView(highlight, Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally), detail = fineMuscles)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ChoiceChip("Six regions", selected = !fineMuscles, onClick = { fineMuscles = false })
                ChoiceChip("Show detail", selected = fineMuscles, onClick = { fineMuscles = true })
            }
            if (!highlight.isEmpty) {
                Text(
                    text = buildString {
                        if (highlight.primary.isNotEmpty()) append("Main: ").append(highlight.primary.joinToString { it.label.lowercase() })
                        if (highlight.secondary.isNotEmpty()) {
                            if (isNotEmpty()) append(". ")
                            append("Also: ").append(highlight.secondary.joinToString { it.label.lowercase() })
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val howTo = if (custom) exercise.notes?.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty() else steps
            if (howTo.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Eyebrow("How to")
                howTo.forEachIndexed { i, step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (howTo.size > 1) Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            YourHistory(state.stats, unit)

            if ((onEdit != null && custom && !hidden) || onHideToggle != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    if (onEdit != null && custom && !hidden) {
                        OutlinedButton(onClick = onEdit, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Edit") }
                    }
                    if (onHideToggle != null) {
                        OutlinedButton(onClick = onHideToggle, shape = IntakeShape.Button, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(if (hidden) "Show again" else "Hide")
                        }
                    }
                }
                if (!hidden && onHideToggle != null) {
                    Text(
                        text = "Hiding keeps your history and any program that uses it. It only stops it showing in pickers.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!custom && ExerciseImages.index(context).has(exercise.id)) {
                Text(ExerciseGuideIndex.CREDIT, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Your history": the numbers, the estimated max over time, best reps at each weight and the last sessions. */
@Composable
private fun YourHistory(stats: ExerciseStats, unit: WeightUnit) {
    Eyebrow("Your history")
    if (stats.isEmpty) {
        Text("No history yet. Log this exercise in a workout and your best sets, estimated max and past sessions show up here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    fun w(kg: Double) = "${weightText(unit.fromKg(kg))} ${unit.label}"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Best set")
                InfoTip(Glossary.ESTIMATED_MAX)
            }
            Text(stats.bestSet?.let { "${w(it.weightKg)} × ${it.reps}" } ?: "Reps only", style = MaterialTheme.typography.titleMedium)
            stats.bestSet?.let { Text("est. max ${w(it.estimatedMaxKg)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Column(Modifier.weight(1f)) {
            Eyebrow("Heaviest")
            Text(stats.heaviestKg?.let { w(it) } ?: "No weight", style = MaterialTheme.typography.titleMedium)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Eyebrow("Total volume")
            Text(w(stats.totalVolumeKg), style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f)) {
            Eyebrow("Sessions")
            Text("${stats.sessionCount} · ${stats.totalSets} sets", style = MaterialTheme.typography.titleMedium)
        }
    }
    if (stats.estimatedMaxSeries.isNotEmpty()) {
        Text("Estimated max over time", style = MaterialTheme.typography.titleSmall)
        Text(OneRepMax.FORMULA, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val points = stats.estimatedMaxSeries.map { ChartPoint(it.first.toDouble(), unit.fromKg(it.second)) }
        TrendLineChart(
            raw = points,
            trend = points,
            description = "Estimated one-rep max over ${points.size} sessions, latest ${weightText(points.last().y)} ${unit.label}.",
            valueSuffix = unit.label,
            gapDays = null,
        )
    }
    if (stats.bestRepsAtWeight.isNotEmpty()) {
        Text("Best reps at each weight", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.bestRepsAtWeight.take(12).forEach { (kg, reps) ->
                Text(
                    "${w(kg)} × $reps",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
    Text(
        "Last ${stats.sessions.size} ${if (stats.sessions.size == 1) "session" else "sessions"}" + if (stats.recordCount > 0) " · ${stats.recordCount} records so far" else "",
        style = MaterialTheme.typography.titleSmall,
    )
    stats.sessions.forEach { session ->
        Column {
            Text(dateText(session.at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            var n = 0
            session.sets.forEachIndexed { i, set ->
                val label = if (set.isWarmup) "Warm-up" else "Set ${++n}"
                val record = i in session.records
                Text(
                    "$label: ${if (set.weightKg > 0) "${w(set.weightKg)} × ${set.reps}" else "${set.reps} reps"}" + if (record) " (PR)" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun dateText(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
