package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.db.projection.MuscleSetCount
import com.zeroindex.intake.data.repository.ProgramImportPreview
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.training.ProgramImportBatch
import com.zeroindex.intake.data.seed.ProgramSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainUiState(
    val activeProgram: ProgramEntity? = null,
    val days: List<ProgramDayEntity> = emptyList(),
    val nextDay: ProgramDayEntity? = null,
    val inProgress: WorkoutSessionEntity? = null,
    val recentSessions: List<WorkoutSessionEntity> = emptyList(),
    val weeklySets: List<MuscleSetCount> = emptyList(),
    val allPrograms: List<ProgramEntity> = emptyList(),
    val isWorking: Boolean = false,
) {
    /** SPEC §10: flag muscle groups under 10 working sets a week. */
    val underworked: List<MuscleSetCount>
        get() = weeklySets.filter { it.setCount < WEEKLY_SET_FLOOR }

    companion object {
        const val WEEKLY_SET_FLOOR = 10
    }
}

data class ProgramImportUi(
    val reading: Boolean = false,
    val preview: ProgramImportPreview? = null,
)

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val programSeed: ProgramSeed,
) : ViewModel() {

    private val working = MutableStateFlow(false)

    private val _import = MutableStateFlow(ProgramImportUi())
    val importState: StateFlow<ProgramImportUi> = _import.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    fun startReading() { _import.value = ProgramImportUi(reading = true) }

    fun importFailed(message: String) {
        _import.value = ProgramImportUi(preview = ProgramImportPreview.Invalid(message))
    }

    fun previewImport(text: String) = viewModelScope.launch {
        _import.value = ProgramImportUi(preview = repository.previewProgramImport(text))
    }

    fun confirmImport(batch: ProgramImportBatch) = viewModelScope.launch {
        repository.applyProgramImport(batch)
        _import.value = ProgramImportUi()
        _notice.value = (if (batch.summary.updatingExisting) "Updated " else "Imported ") +
            "\"${batch.summary.programName}\" with ${batch.summary.exerciseCount} exercises."
    }

    fun cancelImport() { _import.value = ProgramImportUi() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TrainUiState> = repository.observeActiveProgram()
        .flatMapLatest { program ->
            val daysFlow = if (program == null) {
                flowOf(emptyList())
            } else {
                repository.observeDays(program.id)
            }
            combine(
                daysFlow,
                repository.observeInProgress(),
                repository.observeSessions(),
                repository.observeWeeklySetsPerMuscle(),
                combine(repository.observePrograms(), working) { all, busy -> all to busy },
            ) { days, inProgress, sessions, weekly, programsAndBusy ->
                TrainUiState(
                    activeProgram = program,
                    days = days,
                    nextDay = days.getOrNull(program?.currentDayIndex ?: 0) ?: days.firstOrNull(),
                    inProgress = inProgress,
                    recentSessions = sessions.take(8),
                    weeklySets = weekly,
                    allPrograms = programsAndBusy.first,
                    isWorking = programsAndBusy.second,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TrainUiState(),
        )

    fun startSession(programDayId: String?, onStarted: (String) -> Unit) = viewModelScope.launch {
        working.value = true
        try {
            val session = repository.startSession(programDayId)
            onStarted(session.id)
        } finally {
            working.value = false
        }
    }

    fun activate(programId: String) = viewModelScope.launch {
        repository.activate(programId)
    }

    /**
     * Creates a sane starter program so the Train tab is usable before anyone
     * builds one by hand. Seeds the exercise library too if it is empty.
     */
    fun createStarterProgram(onCreated: () -> Unit = {}) = viewModelScope.launch {
        working.value = true
        try {
            programSeed.createUpperLowerProgram()
            onCreated()
        } finally {
            working.value = false
        }
    }
}
