package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.ProgramDayEntity
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.entity.ProgressionRuleEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.seed.ProgramSeed
import com.zeroindex.intake.domain.model.ProgressionType
import com.zeroindex.intake.domain.training.ExpressionEvaluator
import com.zeroindex.intake.domain.training.ExpressionResult
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
import kotlinx.coroutines.launch

data class ProgramBuilderUiState(
    val program: ProgramEntity? = null,
    val allPrograms: List<ProgramEntity> = emptyList(),
    val days: List<ProgramDayEntity> = emptyList(),
    val exerciseCounts: Map<String, Int> = emptyMap(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val rules: List<ProgressionRuleEntity> = emptyList(),
    val selectedDayId: String? = null,
    val chosenExerciseId: String? = null,
    val chosenRuleId: String? = null,
    val expressionError: String? = null,
    val message: String? = null,
) {
    val selectedDayName: String? get() = days.firstOrNull { it.id == selectedDayId }?.name
    val chosenExercise: ExerciseEntity? get() = exercises.firstOrNull { it.id == chosenExerciseId }
    val chosenRule: ProgressionRuleEntity? get() = rules.firstOrNull { it.id == chosenRuleId }
}

@HiltViewModel
class ProgramBuilderViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val programSeed: ProgramSeed,
) : ViewModel() {

    private val selectedDayId = MutableStateFlow<String?>(null)
    private val chosenExerciseId = MutableStateFlow<String?>(null)
    private val chosenRuleId = MutableStateFlow<String?>(null)
    private val expressionError = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val startingNew = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgramBuilderUiState> = combine(
        repository.observeActiveProgram(),
        repository.observePrograms(),
        repository.observeExercises(),
        repository.observeRules(),
        startingNew,
    ) { active, all, exercises, rules, isNew ->
        Inputs(if (isNew) null else active, all, exercises, rules)
    }.flatMapLatest { inputs ->
        val daysFlow = inputs.program?.let { repository.observeDays(it.id) } ?: flowOf(emptyList())
        combine(
            daysFlow,
            selectedDayId,
            chosenExerciseId,
            combine(chosenRuleId, expressionError, message) { r, e, m -> Triple(r, e, m) },
            counts,
        ) { days, dayId, exerciseId, ruleAndErrors, exerciseCounts ->
            ProgramBuilderUiState(
                program = inputs.program,
                allPrograms = inputs.all,
                days = days,
                exerciseCounts = exerciseCounts,
                exercises = inputs.exercises,
                rules = inputs.rules,
                selectedDayId = dayId ?: days.firstOrNull()?.id,
                chosenExerciseId = exerciseId,
                chosenRuleId = ruleAndErrors.first,
                expressionError = ruleAndErrors.second,
                message = ruleAndErrors.third,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProgramBuilderUiState(),
    )

    private data class Inputs(
        val program: ProgramEntity?,
        val all: List<ProgramEntity>,
        val exercises: List<ExerciseEntity>,
        val rules: List<ProgressionRuleEntity>,
    )

    init {
        viewModelScope.launch {
            programSeed.ensureExerciseLibrary()
            programSeed.ensureRules()
            refreshCounts()
        }
    }

    fun createProgram(name: String, description: String) = viewModelScope.launch {
        val program = repository.createProgram(name, description)
        repository.activate(program.id)
        startingNew.value = false
        message.value = "Created \"$name\" and made it active. Add days next."
    }

    fun startNewProgram() { startingNew.value = true }

    fun activate(programId: String) = viewModelScope.launch {
        repository.activate(programId)
        startingNew.value = false
    }

    fun addDay(name: String) = viewModelScope.launch {
        val program = uiState.value.program ?: return@launch
        val index = uiState.value.days.size
        val day = repository.addDay(program.id, index, name)
        selectedDayId.value = day.id
        refreshCounts()
    }

    fun selectDay(id: String) { selectedDayId.value = id }
    fun chooseExercise(id: String) { chosenExerciseId.value = id }
    fun chooseRule(id: String?) { chosenRuleId.value = id }

    fun addExerciseToDay(
        sets: Int,
        repsMin: Int,
        repsMax: Int,
        targetRpe: Double?,
        restSec: Int,
    ) = viewModelScope.launch {
        val dayId = uiState.value.selectedDayId ?: return@launch
        val exerciseId = chosenExerciseId.value ?: return@launch
        val order = repository.programExercises(dayId).size
        repository.addProgramExercise(
            programDayId = dayId,
            exerciseId = exerciseId,
            displayOrder = order,
            targetSets = sets,
            targetRepsMin = repsMin,
            targetRepsMax = maxOf(repsMin, repsMax),
            targetRpe = targetRpe,
            progressionRuleId = chosenRuleId.value,
            restSec = restSec,
        )
        message.value = "Added to ${uiState.value.selectedDayName}."
        refreshCounts()
    }

    /** SPEC §10: custom rules are validated in the builder, not at workout time. */
    fun validateExpression(expression: String) {
        expressionError.value = when (val result = ExpressionEvaluator.validate(expression)) {
            is ExpressionResult.Value -> null
            is ExpressionResult.Error -> result.message
        }
        if (expressionError.value == null) message.value = "That rule reads fine."
    }

    fun createRule(
        name: String,
        type: ProgressionType,
        incrementKg: Double?,
        percentageOf1rm: Double?,
        expression: String,
    ) = viewModelScope.launch {
        if (type == ProgressionType.CUSTOM) {
            val check = ExpressionEvaluator.validate(expression)
            if (check is ExpressionResult.Error) {
                expressionError.value = check.message
                return@launch
            }
        }
        val params = buildString {
            append("{")
            val parts = mutableListOf<String>()
            incrementKg?.let { parts += "\"incrementKg\":$it" }
            percentageOf1rm?.let { parts += "\"percentageOf1rm\":$it" }
            if (expression.isNotBlank()) {
                parts += "\"expression\":\"${expression.replace("\"", "\\\"")}\""
            }
            append(parts.joinToString(","))
            append("}")
        }
        repository.saveRule(name, type, params)
        expressionError.value = null
        message.value = "Saved \"$name\"."
    }

    private suspend fun refreshCounts() {
        val program = repository.activeProgram() ?: return
        val days = repository.days(program.id)
        counts.value = days.associate { it.id to repository.programExercises(it.id).size }
    }
}
