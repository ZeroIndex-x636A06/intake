package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgramExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.ExpressionEvaluator
import com.zeroindex.intake.domain.training.ExpressionResult
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BuilderRow(
    val row: ProgramExerciseEntity,
    val exercise: ExerciseEntity?,
    val rule: ProgressionRuleEntity?,
)

data class BuilderDay(val day: ProgramDayEntity, val rows: List<BuilderRow>)

data class ProgramBuilderUiState(
    val loaded: Boolean = false,
    val program: ProgramEntity? = null,
    val days: List<BuilderDay> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
    val message: String? = null,
)

/**
 * Edits one program. With no program id the screen shows the "new program" step first; once the
 * program exists the same screen becomes the editor. Every change is saved as it is made.
 */
@HiltViewModel
class ProgramBuilderViewModel @Inject constructor(
    private val repository: TrainingRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val programId = MutableStateFlow(savedStateHandle.toRoute<Route.ProgramBuilder>().programId)
    private val message = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgramBuilderUiState> = programId.flatMapLatest { id ->
        if (id == null) {
            combine(settingsRepository.settings, message) { settings, m ->
                ProgramBuilderUiState(loaded = true, weightUnit = settings.weightUnit, message = m)
            }
        } else {
            val daysFlow: Flow<List<BuilderDay>> = combine(
                repository.observeDays(id),
                repository.observeExercises(),
                repository.observeHiddenExercises(),
                repository.observeRules(),
            ) { days, visible, hidden, rules -> BuilderInputs(days, (visible + hidden).associateBy { it.id }, rules.associateBy { it.id }) }
                .flatMapLatest { inputs ->
                    if (inputs.days.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(inputs.days.map { d -> repository.observeProgramExercises(d.id) }) { perDay ->
                            inputs.days.mapIndexed { i, d ->
                                BuilderDay(
                                    d,
                                    perDay[i].map { pe -> BuilderRow(pe, inputs.exercises[pe.exerciseId], pe.progressionRuleId?.let { inputs.rules[it] }) },
                                )
                            }
                        }
                    }
                }
            combine(repository.observeProgram(id), daysFlow, settingsRepository.settings, message) { program, days, settings, m ->
                ProgramBuilderUiState(true, program, days, settings.weightUnit, m)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramBuilderUiState())

    private class BuilderInputs(
        val days: List<ProgramDayEntity>,
        val exercises: Map<String, ExerciseEntity>,
        val rules: Map<String, ProgressionRuleEntity>,
    )

    init {
        viewModelScope.launch { repository.ensureSeedExercises(); repository.ensureDefaultRules() }
    }

    fun clearMessage() { message.value = null }
    fun showMessage(text: String) { message.value = text }

    fun createProgram(name: String, description: String) = viewModelScope.launch {
        val program = repository.createProgram(name.trim(), description.trim())
        if (repository.activeProgram() == null) repository.activate(program.id)
        programId.value = program.id
        message.value = "Created \"${program.name}\". Add a day to begin."
    }

    fun rename(name: String, description: String) = viewModelScope.launch {
        programId.value?.let { repository.renameProgram(it, name, description) }
    }

    fun activate() = viewModelScope.launch {
        val id = programId.value ?: return@launch
        repository.activate(id)
        message.value = "This is now your active program."
    }

    fun addDay(name: String) = viewModelScope.launch {
        val id = programId.value ?: return@launch
        repository.addDayTo(id, name.trim().ifBlank { "Day ${uiState.value.days.size + 1}" })
    }

    fun renameDay(dayId: String, name: String) = viewModelScope.launch { repository.renameDay(dayId, name) }
    fun copyDayAsWorkout(dayId: String) = viewModelScope.launch {
        repository.copyDayAsWorkout(dayId)?.let { message.value = "Copied to My workouts as \"${it.name}\"." }
    }
    fun setDayWeekday(dayId: String, weekday: Int?) = viewModelScope.launch { repository.setDayWeekday(dayId, weekday) }
    fun moveDay(dayId: String, delta: Int) = viewModelScope.launch { repository.moveDay(dayId, delta) }
    fun duplicateDay(dayId: String) = viewModelScope.launch { repository.duplicateDay(dayId); message.value = "Day copied." }
    fun deleteDay(dayId: String) = viewModelScope.launch { repository.deleteDay(dayId) }

    fun addExercise(dayId: String, exerciseId: String) = viewModelScope.launch { repository.addExerciseToDay(dayId, exerciseId) }
    fun removeExercise(id: String) = viewModelScope.launch { repository.deleteProgramExercise(id) }
    fun moveExercise(id: String, delta: Int) = viewModelScope.launch { repository.moveProgramExercise(id, delta) }

    /** Saves an exercise's settings; [rule] picks its progression in plain words. */
    fun saveExercise(
        row: ProgramExerciseEntity,
        rule: RuleChoice?,
        copyTo: CopyScope,
    ) = viewModelScope.launch {
        val ruleId = rule?.let { repository.ruleIdFor(it.type, it.incrementKg, it.percentOfMax, it.expression) }
        val saved = row.copy(progressionRuleId = if (rule == null) null else ruleId)
        repository.updateProgramExercise(saved)
        if (copyTo != CopyScope.None) {
            val n = repository.copySettingsToOthers(row.id, wholeProgram = copyTo == CopyScope.Program)
            message.value = "Settings copied to $n other exercise${if (n == 1) "" else "s"}."
        }
    }

    fun checkFormula(expression: String): String? = when (val r = ExpressionEvaluator.validate(expression)) {
        is ExpressionResult.Value -> null
        is ExpressionResult.Error -> r.message
    }

    /** Script text and suggested file name for the program, for "Export program as script". */
    suspend fun exportScript(): Pair<String, String>? = programId.value?.let { repository.exportProgramScript(it) }
}

/** A plain-language progression choice and its settings. */
data class RuleChoice(
    val type: ProgressionType,
    val incrementKg: Double? = null,
    val percentOfMax: Double? = null,
    val expression: String? = null,
)

enum class CopyScope { None, Day, Program }
