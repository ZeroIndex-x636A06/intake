package com.zeroindex.intake.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.profile.BaselineSnapshot
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.domain.goals.GoalBuilder
import com.zeroindex.intake.domain.goals.GoalInputs
import com.zeroindex.intake.domain.goals.GoalPlan
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val settings: AppSettings = AppSettings(),
    val latestWeightKg: Double? = null,
    val latestBodyFat: Double? = null,
    val baseline: BaselineSnapshot? = null,
    /** A live run of the goal builder, so the pace line and warnings update as choices change. */
    val preview: GoalPlan? = null,
    val loaded: Boolean = false,
) {
    val profile: UserProfile get() = settings.profile

    /** Enough to build a plan: the basics plus one weigh-in. */
    val canBuildPlan: Boolean get() = profile.hasBasics && latestWeightKg != null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val settingsRepository: com.zeroindex.intake.data.settings.SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        repository.observeSettings(),
        repository.observeLatestWeigh(),
        repository.observeBaseline(),
    ) { settings, latest, baseline ->
        val profile = settings.profile
        val preview = if (profile.hasBasics && latest != null) {
            runCatching {
                GoalBuilder.build(
                    GoalInputs(
                        profile = profile.copy(bodyFatPercent = profile.bodyFatPercent ?: latest.bodyFatPercent),
                        weightKg = latest.weightKg,
                        currentYear = clock.today().year,
                        averageSteps = baseline?.averageSteps,
                        baseline = baseline?.measured,
                    ),
                )
            }.getOrNull()
        } else {
            null
        }
        ProfileUiState(
            settings = settings,
            latestWeightKg = latest?.weightKg,
            latestBodyFat = latest?.bodyFatPercent,
            baseline = baseline,
            preview = preview,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    val currentYear: Int get() = clock.today().year

    /** Every edit saves at once; there is no Save button to forget. */
    fun update(block: (UserProfile) -> UserProfile) {
        viewModelScope.launch { repository.update(block) }
    }

    fun setDisplayName(name: String) { viewModelScope.launch { settingsRepository.setDisplayName(name) } }
    fun setGoalWeight(kg: Double?) { viewModelScope.launch { settingsRepository.setGoalWeightKg(kg) } }
    fun setGoalRate(kgPerWeek: Double) { viewModelScope.launch { settingsRepository.setGoalRateKgPerWeek(kgPerWeek) } }

    fun logWeighIn(weightKg: Double, bodyFatPercent: Double?) {
        viewModelScope.launch { repository.logWeighIn(weightKg, bodyFatPercent) }
    }

    fun startBaseline() {
        viewModelScope.launch { repository.startBaseline() }
    }

    fun cancelBaseline() {
        viewModelScope.launch { repository.cancelBaseline() }
    }
}
