package com.zeroindex.intake.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.keys.ApiKeyState
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodSearchRepository
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.nutrition.MacroSplit
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of a test-connection tap (SPEC §13). */
sealed interface KeyTestResult {
    data object Untested : KeyTestResult
    data object Testing : KeyTestResult
    data class Ok(val detail: String) : KeyTestResult
    data class Failed(val message: String) : KeyTestResult
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val keys: ApiKeyState = ApiKeyState(),
    val avoidedAllergens: Set<String> = emptySet(),
    val allergenSeverity: AllergenSeverity = AllergenSeverity.AVOID,
    /**
     * Every term the engine will actually catch for the avoided allergens.
     * Shown so the profile is auditable: "dairy" alone does not tell you that
     * casein and lactalbumin are covered.
     */
    val knownAllergenTerms: List<String> = emptyList(),
    val usdaTest: KeyTestResult = KeyTestResult.Untested,
    val latestWeightKg: Double? = null,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val allergenRepository: AllergenRepository,
    private val foodSearchRepository: FoodSearchRepository,
    private val targetRepository: TargetRepository,
    private val tdeeRepository: TdeeRepository,
    private val bodyRepository: BodyRepository,
) : ViewModel() {

    private val usdaTest = MutableStateFlow<KeyTestResult>(KeyTestResult.Untested)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        apiKeyStore.state,
        allergenRepository.observeProfile(),
        allergenRepository.observeAliasesByAllergen(),
        combine(
            usdaTest,
            bodyRepository.observeLatest(),
            message,
        ) { test, latest, msg -> Triple(test, latest, msg) },
    ) { settings, keys, profile, aliases, rest ->
        val avoided = profile?.avoidedAllergens
            ?.split(',')
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
        SettingsUiState(
            settings = settings,
            keys = keys,
            avoidedAllergens = avoided,
            allergenSeverity = profile?.severityLevel ?: AllergenSeverity.AVOID,
            knownAllergenTerms = avoided
                .flatMap { allergen -> aliases[allergen].orEmpty() }
                .distinct()
                .sorted(),
            usdaTest = rest.first,
            latestWeightKg = rest.second?.weightKg,
            message = rest.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setWeightUnit(unit: WeightUnit) = launchSetting { setWeightUnit(unit) }
    fun setLengthUnit(unit: LengthUnit) = launchSetting { setLengthUnit(unit) }
    fun setThemeMode(mode: ThemeMode) = launchSetting { setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launchSetting { setDynamicColor(enabled) }
    fun setRestSeconds(seconds: Int) = launchSetting { setDefaultRestSec(seconds) }
    fun setRestVibrate(enabled: Boolean) = launchSetting { setRestVibrate(enabled) }
    fun setBarWeightKg(kg: Double) = launchSetting { setBarWeightKg(kg) }
    fun setPlates(csv: String) = launchSetting { setAvailablePlates(csv) }
    fun setAutoTargets(enabled: Boolean) = launchSetting { setAutoCalculateTargets(enabled) }
    fun setAddActiveCalories(enabled: Boolean) = launchSetting { setAddActiveCalories(enabled) }
    fun setGoalWeightKg(kg: Double?) = launchSetting { setGoalWeightKg(kg) }
    fun setGoalRate(kgPerWeek: Double) = launchSetting { setGoalRateKgPerWeek(kgPerWeek) }
    fun setProteinPerKg(value: Double) = launchSetting { setProteinPerKg(value) }
    fun setFatPerKg(value: Double) = launchSetting { setFatPerKg(value) }

    fun setAnthropicKey(key: String?) {
        apiKeyStore.setAnthropicKey(key)
        message.value = if (key.isNullOrBlank()) {
            "Anthropic key cleared. Photo estimates are off; everything else is unaffected."
        } else {
            "Anthropic key saved, encrypted on this device."
        }
    }

    fun setUsdaKey(key: String?) {
        apiKeyStore.setUsdaKey(key)
        usdaTest.value = KeyTestResult.Untested
        message.value = if (key.isNullOrBlank()) {
            "USDA key cleared. Search now covers only foods already on this phone."
        } else {
            "USDA key saved, encrypted on this device."
        }
    }

    /** SPEC §13: a test-connection button on each key. */
    fun testUsdaKey() = viewModelScope.launch {
        usdaTest.value = KeyTestResult.Testing
        usdaTest.value = when (val result = foodSearchRepository.searchRemote("chicken breast")) {
            is RemoteResult.Success ->
                KeyTestResult.Ok("Worked: ${result.value.size} results came back.")
            RemoteResult.NotFound -> KeyTestResult.Ok("Connected, but that test query was empty.")
            RemoteResult.MissingKey -> KeyTestResult.Failed("No key saved yet.")
            is RemoteResult.Failed -> KeyTestResult.Failed(result.message)
        }
    }

    fun setAvoidedAllergens(avoided: Set<String>, severity: AllergenSeverity) =
        viewModelScope.launch {
            allergenRepository.setProfile(avoided, severity, notes = "")
            message.value = if (avoided.isEmpty()) {
                "Allergen checks are off. Nothing will be flagged."
            } else {
                "Checking every food against: ${avoided.joinToString(", ")}."
            }
        }

    fun addAlias(allergen: String, term: String) = viewModelScope.launch {
        if (allergen.isBlank() || term.isBlank()) return@launch
        allergenRepository.addAlias(allergen, term)
        message.value = "Added \"$term\" to $allergen."
    }

    /** Manual target entry, which switches auto-calculation off (SPEC §13). */
    fun setManualTarget(kcal: Int, goalWeightKg: Double) = viewModelScope.launch {
        val settings = uiState.value.settings
        targetRepository.setTarget(
            MacroSplit.forKcal(
                kcal = kcal,
                goalWeightKg = goalWeightKg,
                proteinPerKg = settings.proteinGramsPerKg,
                fatPerKg = settings.fatGramsPerKg,
            ),
            isAutoCalculated = false,
        )
        settingsRepository.setAutoCalculateTargets(false)
        message.value = "Target set to $kcal kcal by hand. Auto-calculation is off."
    }

    fun recalculateTargetFromTdee() = viewModelScope.launch {
        val result = tdeeRepository.recalculate()
        message.value = if (result == null) {
            "Not enough data yet: 14 days with both a weigh-in and a logged day are needed."
        } else {
            val shift = tdeeRepository.refreshTargetFromTdee(result)
            if (shift == null) {
                "Maintenance is about ${result.estimatedTdee.toInt()} kcal. Target unchanged."
            } else {
                "Target is now ${shift.newKcal} kcal, from your own data."
            }
        }
    }

    fun clearMessage() { message.value = null }

    private fun launchSetting(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch {
        settingsRepository.block()
    }
}
