package com.zeroindex.intake.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.keys.ApiKeyState
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodSearchRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.RemoteResult
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.FoodDataPrefs
import com.zeroindex.intake.data.settings.FoodDataSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.nutrition.MacroSplit
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.settings.TodayCard
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of a test-connection tap (SPEC §13). */
sealed interface KeyTestResult {
    data object Untested : KeyTestResult
    data object Testing : KeyTestResult
    data class Ok(val detail: String) : KeyTestResult
    data class Failed(val message: String) : KeyTestResult
}

/** Everything the Settings pages read, in one object. */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val foodData: FoodDataSettings = FoodDataSettings(),
    val keys: ApiKeyState = ApiKeyState(),
    val presets: List<HydrationPresetEntity> = emptyList(),
    val currentKcal: Int? = null,
    val latestWeightKg: Double? = null,
    val usdaTest: KeyTestResult = KeyTestResult.Untested,
    val youtubeTest: KeyTestResult = KeyTestResult.Untested,
    val message: String? = null,
) {
    val profile: UserProfile get() = settings.profile
}

/** The one view model behind the Settings home and every group page except the AI, Sync and Health sections. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val foodDataPrefs: FoodDataPrefs,
    private val apiKeyStore: ApiKeyStore,
    private val foodSearchRepository: FoodSearchRepository,
    private val targetRepository: TargetRepository,
    private val tdeeRepository: TdeeRepository,
    private val bodyRepository: BodyRepository,
    private val hydrationRepository: HydrationRepository,
    private val profileRepository: ProfileRepository,
    private val clock: Clock,
    private val youtubeTester: com.zeroindex.intake.data.remote.youtube.YoutubeKeyTester,
) : ViewModel() {

    private val usdaTest = MutableStateFlow<KeyTestResult>(KeyTestResult.Untested)
    private val youtubeTest = MutableStateFlow<KeyTestResult>(KeyTestResult.Untested)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        foodDataPrefs.settings,
        apiKeyStore.state,
        hydrationRepository.observePresets(),
        combine(
            targetRepository.observeTargetFor(clock.today()),
            bodyRepository.observeLatest(),
            usdaTest,
            message,
            youtubeTest,
        ) { target, latest, test, msg, yt -> arrayOf(target?.kcal, latest?.weightKg, test, msg, yt) },
    ) { settings, food, keys, presets, rest ->
        SettingsUiState(
            settings = settings,
            foodData = food,
            keys = keys,
            presets = presets,
            currentKcal = rest[0] as Int?,
            latestWeightKg = rest[1] as Double?,
            usdaTest = rest[2] as KeyTestResult,
            message = rest[3] as String?,
            youtubeTest = rest[4] as KeyTestResult,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // ---- Appearance and units ----
    fun setWeightUnit(unit: WeightUnit) = launchSetting { setWeightUnit(unit) }
    fun setLengthUnit(unit: LengthUnit) = launchSetting { setLengthUnit(unit) }
    fun setVolumeUnit(unit: VolumeUnit) = launchSetting { setVolumeUnit(unit) }
    fun setPortionUnit(unit: PortionUnit) = launchSetting { setPortionUnit(unit) }
    fun setThemeMode(mode: ThemeMode) = launchSetting { setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launchSetting { setDynamicColor(enabled) }
    fun setReduceMotion(enabled: Boolean) = launchSetting { setReduceMotion(enabled) }
    fun setTodayCard(card: TodayCard, shown: Boolean) = launchSetting { setTodayCard(card, shown) }

    // ---- Nutrition ----
    fun setAutoTargets(enabled: Boolean) = launchSetting { setAutoCalculateTargets(enabled) }
    fun setAddActiveCalories(enabled: Boolean) = launchSetting { setAddActiveCalories(enabled) }
    fun setProteinPerKg(value: Double) = launchSetting { setProteinPerKg(value) }
    fun setFatPerKg(value: Double) = launchSetting { setFatPerKg(value) }
    fun setShowSodium(mode: SodiumDisplay) = launchSetting { setShowSodium(mode) }
    fun setEatOutRadius(miles: Double) = launchSetting { setEatOutRadiusMi(miles) }
    fun updateProfile(block: (UserProfile) -> UserProfile) { viewModelScope.launch { profileRepository.update(block) } }

    /** Manual calorie entry, which switches auto-calculation off (SPEC §13). */
    fun setManualCalories(kcal: Int) = viewModelScope.launch {
        val s = uiState.value
        val weight = s.latestWeightKg ?: s.settings.goalWeightKg ?: 70.0
        targetRepository.setTarget(
            MacroSplit.forKcal(
                kcal = kcal,
                goalWeightKg = s.settings.goalWeightKg ?: weight,
                proteinPerKg = s.settings.proteinGramsPerKg,
                fatPerKg = s.settings.fatGramsPerKg,
            ),
            isAutoCalculated = false,
        )
        settingsRepository.setAutoCalculateTargets(false)
        message.value = "Target set to $kcal kcal by hand. Working it out from your data is off."
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

    // ---- Water and drinks ----
    fun setHydrationBaseMl(ml: Double?) = launchSetting { setHydrationBaseMl(ml) }
    fun setHydrationDynamic(on: Boolean) = launchSetting { setHydrationDynamic(on) }
    fun setHydrationTrainingBonusMl(ml: Double) = launchSetting { setHydrationTrainingBonusMl(ml) }
    fun savePreset(name: String, volumeMl: Double, drink: DrinkType) =
        viewModelScope.launch { hydrationRepository.savePreset(name, volumeMl, null, drink) }
    fun deletePreset(preset: HydrationPresetEntity) = viewModelScope.launch { hydrationRepository.deletePreset(preset.id) }

    // ---- Training ----
    fun setRestSeconds(seconds: Int) = launchSetting { setDefaultRestSec(seconds) }
    fun setRestVibrate(enabled: Boolean) = launchSetting { setRestVibrate(enabled) }
    fun setBarWeightKg(kg: Double) = launchSetting { setBarWeightKg(kg) }
    fun setPlates(csv: String) = launchSetting { setAvailablePlates(csv) }
    fun setJumpTests(on: Boolean) = launchSetting { setJumpTests(on) }
    fun setWeeklySets(csv: String) = launchSetting { setWeeklySetsCsv(csv) }

    // ---- Food database ----
    fun setOffSearch(on: Boolean) { viewModelScope.launch { foodDataPrefs.setOffSearch(on) } }
    fun setOffLanguage(code: String) { viewModelScope.launch { foodDataPrefs.setOffLanguage(code) } }
    fun setOffOtherLanguages(on: Boolean) { viewModelScope.launch { foodDataPrefs.setOffOtherLanguages(on) } }
    fun setOffContribute(on: Boolean) { viewModelScope.launch { foodDataPrefs.setOffContribute(on) } }
    fun saveOffCredentials(username: String?, password: String?) {
        apiKeyStore.setOffCredentials(username, password)
        message.value = if (username.isNullOrBlank()) "Open Food Facts login removed." else "Open Food Facts login saved, encrypted on this device."
    }

    // ---- Videos / reminders / sync and export ----
    fun setOnlyWorkouts(on: Boolean) = launchSetting { setOnlyWorkouts(on) }
    fun setHideShorts(on: Boolean) = launchSetting { setHideShorts(on) }
    fun setPersonalise(on: Boolean) = launchSetting { setPersonaliseVideos(on) }
    fun setCoachNotify(on: Boolean) = launchSetting { setCoachNotify(on) }
    fun setCoachWeekday(isoWeekday: Int?) = launchSetting { setCoachWeekday(isoWeekday) }
    fun setPhotoReminderWeeks(weeks: Int) = launchSetting { setPhotoReminderWeeks(weeks) }
    fun setExportReminder(on: Boolean) = launchSetting { setExportReminder(on) }
    fun setExportIncludePhotos(on: Boolean) = launchSetting { setExportIncludePhotos(on) }
    fun setWorkoutReminder(on: Boolean) = launchSetting { setWorkoutReminder(on) }
    fun setWorkoutReminderMinute(minute: Int) = launchSetting { setWorkoutReminderMinute(minute) }
    fun setMealReminder(on: Boolean) = launchSetting { setMealReminder(on) }
    fun setMealReminderMinute(slot: com.zeroindex.intake.domain.model.MealSlot, minute: Int) = launchSetting { setMealReminderMinute(slot, minute) }
    fun setWaterReminder(on: Boolean) = launchSetting { setWaterReminder(on) }
    fun setWaterReminderHours(startHour: Int, endHour: Int) = launchSetting { setWaterReminderHours(startHour, endHour) }

    // ---- API keys ----
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
            is RemoteResult.Success -> KeyTestResult.Ok("Worked: ${result.value.size} results came back.")
            RemoteResult.NotFound -> KeyTestResult.Ok("Connected, but that test query was empty.")
            RemoteResult.MissingKey -> KeyTestResult.Failed("No key saved yet.")
            is RemoteResult.Failed -> KeyTestResult.Failed(result.message)
        }
    }

    fun testYoutubeKey() = viewModelScope.launch {
        youtubeTest.value = KeyTestResult.Testing
        val (ok, text) = youtubeTester.test()
        youtubeTest.value = if (ok) KeyTestResult.Ok(text) else KeyTestResult.Failed(text)
    }

    fun setYoutubeKey(key: String?) {
        youtubeTest.value = KeyTestResult.Untested
        apiKeyStore.setYoutubeKey(key)
        message.value = if (key.isNullOrBlank()) "YouTube key removed." else "YouTube key saved, encrypted on this device."
    }

    fun clearMessage() { message.value = null }

    suspend fun currentSettings(): AppSettings = settingsRepository.settings.first()

    private fun launchSetting(block: suspend SettingsRepository.() -> Unit) = viewModelScope.launch {
        settingsRepository.block()
    }
}
