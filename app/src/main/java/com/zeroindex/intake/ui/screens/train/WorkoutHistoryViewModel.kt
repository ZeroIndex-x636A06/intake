package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.log.LogDay
import com.zeroindex.intake.domain.training.log.LogSession
import com.zeroindex.intake.domain.training.log.LogSet
import com.zeroindex.intake.domain.training.log.LogWeek
import com.zeroindex.intake.domain.training.log.TrainingLog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutHistoryUiState(
    val loaded: Boolean = false,
    val settings: AppSettings = AppSettings(),
    /** Every session, newest first, grouped by calendar week. */
    val weeks: List<LogWeek> = emptyList(),
)

/** The Training log: all recent sessions. Per-exercise stats live on the exercise's own page (Train > Exercises). */
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    repository: TrainingRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<WorkoutHistoryUiState> = combine(
        repository.observeSessions(),
        repository.observeAllSets(),
        repository.observeDayLabels(),
        settingsRepository.settings,
    ) { sessions, sets, labels, settings ->
        val days = labels.associate { it.dayId to LogDay(it.dayName, it.programName, it.standalone) }
        val rows = TrainingLog.rows(
            sessions = sessions.map { LogSession(it.id, it.startedAt, it.finishedAt, it.programDayId) },
            sets = sets.map { LogSet(it.sessionId, it.exerciseId, it.setIndex, it.weightKg, it.reps, it.isWarmup, it.completedAt) },
            days = days,
        )
        WorkoutHistoryUiState(true, settings, TrainingLog.weeks(rows, clock.nowMillis(), clock.zone()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHistoryUiState())
}
