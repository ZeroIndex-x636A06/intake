package com.zeroindex.intake.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.domain.goals.GoalBuilder
import com.zeroindex.intake.domain.goals.GoalInputs
import com.zeroindex.intake.domain.goals.GoalPlan
import com.zeroindex.intake.domain.goals.PlanOverrides
import com.zeroindex.intake.domain.goals.TrainingSummaries
import com.zeroindex.intake.domain.goals.TrainingSummary
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import com.zeroindex.intake.domain.training.generator.generateProgram
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YourPlanUiState(
    val loading: Boolean = true,
    /** Why there is no plan yet, in words the person can act on. */
    val cannotBuildBecause: String? = null,
    val profile: UserProfile = UserProfile(),
    val weightUnit: WeightUnit = WeightUnit.POUNDS,
    val plan: GoalPlan? = null,
    val overrides: PlanOverrides = PlanOverrides(),
    val program: GeneratedProgram? = null,
    val training: TrainingSummary? = null,
    val hasActiveProgram: Boolean = false,
    val includeProgram: Boolean = true,
    val applying: Boolean = false,
    val applied: Boolean = false,
    val appliedProgramId: String? = null,
    /** True when applying will also turn the nightly target updater off, which the screen must say. */
    val willPauseAutoTargets: Boolean = false,
)

/**
 * Runs the pure goal builder over the profile and the baseline, keeps the person's edits as
 * overrides, and applies the result only when they confirm. Nothing is saved before that.
 */
@HiltViewModel
class YourPlanViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val training: TrainingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(YourPlanUiState())
    val state: StateFlow<YourPlanUiState> = _state.asStateFlow()

    private var inputs: GoalInputs? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val settings = repository.observeSettings().first()
        val built = repository.goalInputs()
        if (built == null) {
            _state.update {
                it.copy(
                    loading = false,
                    weightUnit = settings.weightUnit,
                    profile = settings.profile,
                    cannotBuildBecause = if (!settings.profile.hasBasics) {
                        "Add your sex, birth year and height in Profile & goals first."
                    } else {
                        "Log one weigh-in so the plan knows your weight."
                    },
                )
            }
            return
        }
        inputs = built
        val program = generateProgram(built.profile.toGeneratorInputs(repository.hiddenExerciseIds()))
        val active = training.observeActiveProgram().first() != null
        _state.update {
            it.copy(
                loading = false,
                profile = built.profile,
                weightUnit = settings.weightUnit,
                program = program,
                training = TrainingSummaries.of(program, built.profile),
                hasActiveProgram = active,
                includeProgram = !active,
            )
        }
        recompute()
    }

    private fun recompute() {
        val i = inputs ?: return
        val plan = GoalBuilder.build(i, _state.value.overrides)
        _state.update { it.copy(plan = plan, willPauseAutoTargets = ProfileRepository.needsManualTargets(plan)) }
    }

    fun editCalories(kcal: Int?) = edit { it.copy(kcal = kcal) }

    /** Protein and fat in g/kg. Null leaves a value as the engine worked it out. */
    fun editMacros(proteinPerKg: Double?, fatPerKg: Double?) = edit { it.copy(proteinPerKg = proteinPerKg, fatPerKg = fatPerKg) }

    fun editWater(ml: Int?) = edit { it.copy(waterMl = ml) }

    fun resetEdits() = edit { PlanOverrides() }

    private fun edit(block: (PlanOverrides) -> PlanOverrides) {
        _state.update { it.copy(overrides = block(it.overrides)) }
        recompute()
    }

    fun setIncludeProgram(on: Boolean) = _state.update { it.copy(includeProgram = on) }

    fun apply() {
        val s = _state.value
        val plan = s.plan ?: return
        if (s.applying || s.applied) return
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            try {
                val program = s.program?.takeIf { s.includeProgram }
                val summary = "${plan.calories.kcal} kcal, ${plan.macros.proteinG} g protein: ${plan.calories.why}"
                val programId = repository.applyPlan(plan, program, summary)
                _state.update { it.copy(applied = true, appliedProgramId = programId) }
            } finally {
                _state.update { it.copy(applying = false) }
            }
        }
    }
}
