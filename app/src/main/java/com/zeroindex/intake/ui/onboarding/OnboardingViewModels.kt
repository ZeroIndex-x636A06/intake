package com.zeroindex.intake.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.onboarding.OnboardingPrefs
import com.zeroindex.intake.data.onboarding.OnboardingRepository
import com.zeroindex.intake.data.onboarding.StartChoice
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.goals.GoalBuilder
import com.zeroindex.intake.domain.onboarding.GateDecision
import com.zeroindex.intake.domain.onboarding.OnboardingFlow
import com.zeroindex.intake.domain.onboarding.OnboardingStep
import com.zeroindex.intake.domain.onboarding.PendingDestination
import com.zeroindex.intake.domain.onboarding.PendingDestinations
import com.zeroindex.intake.domain.onboarding.TipKey
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * App-level: whether the welcome flow comes first, whether the tour is due, and whether a plans
 * choice is waiting to open. Scoped to the activity, so it is resolved once per launch.
 */
@HiltViewModel
class AppFlowViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    /** Null while it is being worked out (a few milliseconds), then fixed for the rest of the launch. */
    val gate = MutableStateFlow<GateDecision?>(null)

    val prefs: StateFlow<OnboardingPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingPrefs())

    init {
        viewModelScope.launch { gate.value = repository.resolveGate() }
    }

    fun tourFinished() {
        viewModelScope.launch { repository.setTourPending(false) }
    }

    /** The destination to open now, if any; call [pendingOpened] right after navigating. */
    fun readyDestination(): PendingDestination? {
        val p = prefs.value
        return if (!p.loaded) null else PendingDestinations.readyToOpen(p.pending, p.done, p.tourPending)
    }

    fun pendingOpened() {
        viewModelScope.launch { repository.clearPending() }
    }
}

sealed interface EstimateResult {
    data class Applied(val kcal: Int, val proteinG: Int) : EstimateResult
    data object NeedsBasics : EstimateResult
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val prefs: StateFlow<OnboardingPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingPrefs())

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** What the "Use the estimate now" tile last did, shown under it. */
    val estimate = MutableStateFlow<EstimateResult?>(null)

    init {
        viewModelScope.launch {
            // Opening the flow is what marks it as started, so a relaunch resumes instead of auto-skipping.
            if (repository.current().stepKey == null) repository.setStep(OnboardingStep.WELCOME)
            repository.seedUnitsIfFirstRun(Locale.getDefault().country)
        }
    }

    fun goTo(step: OnboardingStep) {
        viewModelScope.launch { repository.setStep(step) }
    }

    fun next(from: OnboardingStep) = goTo(OnboardingFlow.next(from))

    fun back(from: OnboardingStep) = goTo(OnboardingFlow.back(from))

    fun skip(from: OnboardingStep) = goTo(OnboardingFlow.skip(from))

    /** "Skip setup" and the final button: the flow is done, everything entered so far is kept. */
    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.finish()
            onDone()
        }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settingsRepository.setWeightUnit(unit) }
    }

    fun setLengthUnit(unit: LengthUnit) {
        viewModelScope.launch { settingsRepository.setLengthUnit(unit) }
    }

    fun setVolumeUnit(unit: VolumeUnit) {
        viewModelScope.launch { settingsRepository.setVolumeUnit(unit) }
    }

    fun choosePending(destination: PendingDestination?) {
        viewModelScope.launch { repository.setPending(destination) }
    }

    /** "Learn my baseline first": starts the week. The screen asks for the notification permission. */
    fun chooseBaseline() {
        estimate.value = null
        viewModelScope.launch {
            // A week already running keeps its start date; tapping again must not restart it.
            if (!settings.value.baseline.active) profileRepository.startBaseline()
            repository.setStartChoice(StartChoice.BASELINE)
        }
    }

    /**
     * "Use the estimate now": builds the day-one plan from the profile and applies it through the
     * same call "Your plan" uses. Without the basics and a weigh-in there is nothing to build.
     */
    fun chooseEstimate() {
        viewModelScope.launch {
            val inputs = profileRepository.goalInputs()
            if (inputs == null) {
                estimate.value = EstimateResult.NeedsBasics
                return@launch
            }
            val plan = GoalBuilder.build(inputs)
            profileRepository.cancelBaseline()
            val summary = "${plan.calories.kcal} kcal, ${plan.macros.proteinG} g protein: ${plan.calories.why}"
            profileRepository.applyPlan(plan, null, summary)
            repository.setStartChoice(StartChoice.ESTIMATE)
            estimate.value = EstimateResult.Applied(plan.calories.kcal, plan.macros.proteinG)
        }
    }
}

/** Help: the three "again" rows. */
@HiltViewModel
class HelpFlowViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    fun replayTour(then: () -> Unit) {
        viewModelScope.launch {
            repository.setTourPending(true)
            then()
        }
    }

    fun showWelcomeAgain(then: () -> Unit) {
        viewModelScope.launch {
            repository.restart()
            then()
        }
    }

    fun showTipsAgain(then: () -> Unit) {
        viewModelScope.launch {
            repository.resetTips()
            then()
        }
    }
}

/** First-visit tips on Food, Train, Progress and More. */
@HiltViewModel
class TipsViewModel @Inject constructor(
    private val repository: OnboardingRepository,
) : ViewModel() {

    val prefs: StateFlow<OnboardingPrefs> = repository.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingPrefs())

    fun dismiss(key: TipKey) {
        viewModelScope.launch { repository.dismissTip(key) }
    }
}
