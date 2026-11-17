package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.repository.ProgramImportPreview
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.seed.ProgramTemplates
import com.zeroindex.intake.data.training.ProgramImportBatch
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

data class ProgramsUiState(
    val activeProgram: ProgramEntity? = null,
    val days: List<ProgramDayEntity> = emptyList(),
    /** Every live program (not saved workouts), the active one included. */
    val allPrograms: List<ProgramEntity> = emptyList(),
    val archived: List<ProgramEntity> = emptyList(),
    val isWorking: Boolean = false,
) {
    val otherPrograms: List<ProgramEntity> get() = allPrograms.filter { it.id != activeProgram?.id }
}

data class ProgramImportUi(
    val reading: Boolean = false,
    val preview: ProgramImportPreview? = null,
)

/**
 * The Programs area behind Train: the active program and the others, new programs (build, generate, template, import a
 * script), archive and restore, duplicate and export as a script. Archive is a soft delete: logged sessions stay.
 */
@HiltViewModel
class ProgramsViewModel @Inject constructor(
    private val repository: TrainingRepository,
) : ViewModel() {

    private val working = MutableStateFlow(false)

    private val _import = MutableStateFlow(ProgramImportUi())
    val importState: StateFlow<ProgramImportUi> = _import.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }
    fun showNotice(text: String) { _notice.value = text }

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

    init {
        // Make sure the exercise library exists before anyone opens a picker.
        viewModelScope.launch { repository.ensureSeedExercises() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgramsUiState> = repository.observeActiveProgram()
        .flatMapLatest { program ->
            val daysFlow = if (program == null) flowOf(emptyList()) else repository.observeDays(program.id)
            combine(
                daysFlow,
                repository.observePrograms(),
                repository.observeArchivedPrograms(),
                working,
            ) { days, all, archived, busy ->
                ProgramsUiState(activeProgram = program, days = days, allPrograms = all, archived = archived, isWorking = busy)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUiState())

    fun activate(programId: String) = viewModelScope.launch {
        repository.activate(programId)
        _notice.value = "That program is now your active one."
    }

    /** Copies a template into a new, ordinary program. It becomes active only if nothing else is. */
    fun createFromTemplate(templateId: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val template = ProgramTemplates.byId(templateId) ?: return@launch
        working.value = true
        try {
            val activate = repository.activeProgram() == null
            val program = repository.createProgramFromBlueprint(template.blueprint, activate = activate)
            onCreated(program.id)
        } finally {
            working.value = false
        }
    }

    /** Soft delete: the program leaves the list and shows under Archived; its history stays. */
    fun archive(programId: String) = viewModelScope.launch {
        repository.archiveProgram(programId)
        _notice.value = "Program archived. Your logged workouts are kept, and you can restore it below."
    }

    fun restore(programId: String) = viewModelScope.launch {
        repository.restoreProgram(programId)
        _notice.value = "Program restored. Make it active to follow it."
    }

    fun duplicate(programId: String) = viewModelScope.launch {
        repository.duplicateProgram(programId)?.let { _notice.value = "Made \"${it.name}\"." }
    }

    /** The program as script text and a file name, or null if it is gone. */
    suspend fun exportScript(programId: String): Pair<String, String>? = repository.exportProgramScript(programId)
}
