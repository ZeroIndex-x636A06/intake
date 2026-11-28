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
import com.zeroindex.intake.domain.training.log.SessionLogRow
import com.zeroindex.intake.domain.training.history.BackdateOutcome
import com.zeroindex.intake.domain.training.history.BackdatedLog
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    /** The same sessions as one list, for the calendar. */
    val rows: List<SessionLogRow> = emptyList(),
)

/** A day of the active program that a past workout can be logged against. */
data class DayChoice(val id: String, val name: String)

/** The Training log: all recent sessions. Per-exercise stats live on the exercise's own page (Train > Exercises). */
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val repository: TrainingRepository,
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
        WorkoutHistoryUiState(true, settings, TrainingLog.weeks(rows, clock.nowMillis(), clock.zone()), rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHistoryUiState())

    private val _dayChoices = MutableStateFlow<List<DayChoice>>(emptyList())

    /** The days of the active program, for "Log a past workout". Empty when there is no program: a blank workout only. */
    val dayChoices: StateFlow<List<DayChoice>> = _dayChoices.asStateFlow()

    init {
        viewModelScope.launch {
            val program = repository.activeProgram() ?: return@launch
            _dayChoices.value = repository.days(program.id).map { DayChoice(it.id, it.name) }
        }
    }

    fun today(): LocalDate = clock.today()

    /** The refusal in plain words, or null when the plan is fine. */
    fun problemWith(date: LocalDate, time: LocalTime, minutes: Int?): String? {
        if (minutes == null) return "Type the length in minutes."
        val plan = BackdatedLog.plan(date, time, minutes, clock.nowMillis(), clock.zone())
        return (plan as? BackdateOutcome.Refused)?.let { BackdatedLog.words(it.problem) }
    }

    /** Creates the finished session on the chosen date, then reports its id so the sets can be added on its page. */
    fun logPast(date: LocalDate, time: LocalTime, minutes: Int, dayId: String?, onCreated: (String) -> Unit) = viewModelScope.launch {
        val plan = BackdatedLog.plan(date, time, minutes, clock.nowMillis(), clock.zone())
        repository.logPastWorkout(dayId, plan)?.let { onCreated(it.id) }
    }
}
