package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.training.generator.BodyArea
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.RepRange
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import com.zeroindex.intake.domain.training.generator.VolumeGuide
import com.zeroindex.intake.domain.training.generator.generateProgram
import com.zeroindex.intake.domain.training.generator.splitFor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GeneratorUiState(
    val inputs: GeneratorInputs = GeneratorInputs(),
    val preset: EquipmentPreset = EquipmentPreset.FULL_GYM,
    val step: Int = 0,
    val result: GeneratedProgram? = null,
    val saving: Boolean = false,
) {
    /** The weekly sets the rules would ask for, by muscle, for the Advanced table. */
    val volumeTable: List<Pair<String, Int>>
        get() {
            val muscles = splitFor(inputs.daysPerWeek, inputs.experience).first.flatMap { it.muscles }.distinct()
            return muscles.map { m ->
                m to (inputs.weeklySetOverrides[m]
                    ?: VolumeGuide.target(inputs.experience, inputs.goal, m, m in inputs.focusMuscles))
            }
        }
}

/**
 * The "Make one for me" wizard. All the thinking lives in `generateProgram` (pure, tested); this
 * only holds the answers, runs it, and saves the result through the same path as a template.
 */
@HiltViewModel
class ProgramGeneratorViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    init {
        // Profile & goals already asked these questions: start from those answers when they exist.
        viewModelScope.launch {
            val profile = settings.settings.first().profile
            if (!profile.hasBasics) return@launch
            _state.update {
                it.copy(inputs = profile.toGeneratorInputs(), preset = profile.equipmentPreset)
            }
        }
    }

    private fun edit(block: (GeneratorInputs) -> GeneratorInputs) = _state.update { it.copy(inputs = block(it.inputs)) }

    fun setGoal(goal: TrainingGoal) = edit { it.copy(goal = goal) }
    fun setExperience(exp: Experience) = edit { it.copy(experience = exp, deloadEveryWeeks = null) }
    fun setDays(days: Int) = edit { it.copy(daysPerWeek = days.coerceIn(2, 6)) }
    fun setMinutes(minutes: Int) = edit { it.copy(sessionMinutes = minutes) }

    fun setPreset(preset: EquipmentPreset) = _state.update {
        val equipment = if (preset == EquipmentPreset.CUSTOM) it.inputs.equipment else preset.kinds
        it.copy(preset = preset, inputs = it.inputs.copy(equipment = equipment))
    }

    fun toggleEquipment(kind: String) = _state.update {
        val next = if (kind in it.inputs.equipment) it.inputs.equipment - kind else it.inputs.equipment + kind
        it.copy(preset = EquipmentPreset.CUSTOM, inputs = it.inputs.copy(equipment = next))
    }

    fun toggleFocus(muscle: String) = edit {
        it.copy(focusMuscles = if (muscle in it.focusMuscles) it.focusMuscles - muscle else it.focusMuscles + muscle)
    }

    fun toggleAvoid(area: BodyArea) = edit {
        it.copy(avoid = if (area in it.avoid) it.avoid - area else it.avoid + area)
    }

    fun setVolume(muscle: String, sets: Int) = edit {
        it.copy(weeklySetOverrides = it.weeklySetOverrides + (muscle to sets.coerceIn(0, 30)))
    }

    fun resetVolume() = edit { it.copy(weeklySetOverrides = emptyMap()) }
    fun setCompoundReps(range: RepRange?) = edit { it.copy(compoundReps = range) }
    fun setIsolationReps(range: RepRange?) = edit { it.copy(isolationReps = range) }
    fun setDeload(weeks: Int?) = edit { it.copy(deloadEveryWeeks = weeks) }

    fun next() = _state.update { it.copy(step = it.step + 1) }
    fun back() = _state.update { if (it.result != null) it.copy(result = null) else it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun generate() = viewModelScope.launch {
        val hidden = repository.hiddenExerciseIds()
        val inputs = _state.value.inputs.copy(hiddenExerciseIds = hidden)
        _state.update { it.copy(result = generateProgram(inputs)) }
    }

    fun save(activate: Boolean, onSaved: (String) -> Unit) = viewModelScope.launch {
        val result = _state.value.result ?: return@launch
        _state.update { it.copy(saving = true) }
        try {
            val program = repository.createProgramFromBlueprint(result, activate = activate)
            onSaved(program.id)
        } finally {
            _state.update { it.copy(saving = false) }
        }
    }
}
