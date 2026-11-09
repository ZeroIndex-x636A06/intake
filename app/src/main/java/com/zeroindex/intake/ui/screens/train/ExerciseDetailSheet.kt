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
import com.zeroindex.intake.domain.training.ExerciseGuideIndex
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
                settings.settings,
            ) { exercise, history, s -> ExerciseDetailState(exercise, history, s.weightUnit) }
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
            MuscleMapView(highlight, Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Estimated max")
                        InfoTip(Glossary.ESTIMATED_MAX)
                    }
                    Text(
                        text = history.bestEstimatedMaxKg?.let { "${weightText(unit.fromKg(it))} ${unit.label}" } ?: "Not enough data yet",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("Sets logged")
                    Text("${history.totalSets}", style = MaterialTheme.typography.titleLarge)
                }
            }
            Eyebrow("Your history")
            if (history.days.isEmpty()) {
                Text("You have not logged this exercise yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                history.days.take(8).forEach { day ->
                    Column {
                        Text(dateText(day.startedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            day.sets.joinToString("  ·  ") { "${weightText(unit.fromKg(it.weightKg))} × ${it.reps}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

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

private fun dateText(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
