package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.training.OneRepMax
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class WorkoutHistoryUiState(
    val settings: AppSettings = AppSettings(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val selectedExercise: ExerciseEntity? = null,
    /** (timestamp, estimated 1RM in kg), oldest first. */
    val oneRepMaxSeries: List<Pair<Long, Double>> = emptyList(),
    val personalRecord: Double? = null,
    val sessions: List<WorkoutSessionEntity> = emptyList(),
)

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val repository: TrainingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WorkoutHistoryUiState> = combine(
        repository.observeExercises(),
        repository.observeSessions(),
        settingsRepository.settings,
        selectedId,
    ) { exercises, sessions, settings, chosen ->
        Quad(exercises, sessions, settings, chosen ?: exercises.firstOrNull()?.id)
    }.flatMapLatest { (exercises, sessions, settings, chosen) ->
        val selected = exercises.firstOrNull { it.id == chosen }
        if (selected == null) {
            flowOf(
                WorkoutHistoryUiState(
                    settings = settings,
                    exercises = exercises,
                    sessions = sessions,
                ),
            )
        } else {
            repository.observeSetPoints(selected.id).let { points ->
                combine(points, flowOf(Unit)) { setPoints, _ ->
                    val series = setPoints
                        .filter { it.reps >= 1 && it.weightKg > 0 }
                        .map { it.completedAt to OneRepMax.epley(it.weightKg, it.reps) }
                    WorkoutHistoryUiState(
                        settings = settings,
                        exercises = exercises,
                        selectedExercise = selected,
                        oneRepMaxSeries = series,
                        personalRecord = series.maxOfOrNull { it.second },
                        sessions = sessions,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutHistoryUiState(),
    )

    fun selectExercise(id: String) { selectedId.value = id }

    private data class Quad(
        val exercises: List<ExerciseEntity>,
        val sessions: List<WorkoutSessionEntity>,
        val settings: AppSettings,
        val chosen: String?,
    )
}
