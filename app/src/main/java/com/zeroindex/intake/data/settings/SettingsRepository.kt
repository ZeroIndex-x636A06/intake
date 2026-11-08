package com.zeroindex.intake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "intake_settings")

/** Everything the user can change that is not a database row. */
data class AppSettings(
    val weightUnit: WeightUnit = WeightUnit.POUNDS,
    val lengthUnit: LengthUnit = LengthUnit.INCHES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Off by default: the designed palette is the intended look. */
    val dynamicColor: Boolean = false,
    val defaultRestSec: Int = 120,
    val restTimerVibrate: Boolean = true,
    val barWeightKg: Double = 20.0,
    /** Comma-joined plate weights in kg, one side. */
    val availablePlatesKg: String = "25,20,15,10,5,2.5,1.25",
    val goalWeightKg: Double? = null,
    /** Negative to lose, positive to gain, kg per week. */
    val goalRateKgPerWeek: Double = 0.0,
    val proteinGramsPerKg: Double = 2.0,
    val fatGramsPerKg: Double = 0.8,
    val autoCalculateTargets: Boolean = true,
    /** SPEC §12: off by default, because double-counting wearable burn stalls cuts. */
    val addActiveCaloriesToTarget: Boolean = false,
    val currentPlanWeek: Int = 1,
    val lastExportAtMillis: Long? = null,
    val demoDataLoaded: Boolean = false,
    /** Imperial by default, to match the pounds/inches defaults (SPEC §3.5). */
    val volumeUnit: VolumeUnit = VolumeUnit.US_FLUID_OUNCES,
    /** Null means "work it out from bodyweight". */
    val hydrationBaseMl: Double? = null,
    val hydrationDynamic: Boolean = true,
    /** Amendment §1. Defaults to the privacy-preserving middle setting. */
    val aiPrivacy: AiPrivacy = AiPrivacy.NO_TRAINING,
    /** Whether free models that may keep prompts can be suggested. */
    val aiAllowRetainingModels: Boolean = false,
    /** A model the user chose by hand per task; absent means "use the suggestion". */
    val aiModelOverrides: Map<AiTask, String> = emptyMap(),
    /**
     * Amendment §3. Off by default: it sends a summary of food, training and
     * body data off the phone, so it has to be asked for.
     */
    val dailyInsightEnabled: Boolean = false,
) {
    val platesKgList: List<Double>
        get() = availablePlatesKg.split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it > 0 }
            .sortedDescending()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val weightUnit = stringPreferencesKey("weight_unit")
        val lengthUnit = stringPreferencesKey("length_unit")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val defaultRestSec = intPreferencesKey("default_rest_sec")
        val restVibrate = booleanPreferencesKey("rest_vibrate")
        val barWeight = doublePreferencesKey("bar_weight_kg")
        val plates = stringPreferencesKey("available_plates_kg")
        val goalWeight = doublePreferencesKey("goal_weight_kg")
        val goalRate = doublePreferencesKey("goal_rate_kg_per_week")
        val proteinPerKg = doublePreferencesKey("protein_g_per_kg")
        val fatPerKg = doublePreferencesKey("fat_g_per_kg")
        val autoTargets = booleanPreferencesKey("auto_targets")
        val addActiveKcal = booleanPreferencesKey("add_active_kcal")
        val planWeek = intPreferencesKey("current_plan_week")
        val lastExport = longPreferencesKey("last_export_at")
        val demoLoaded = booleanPreferencesKey("demo_data_loaded")
        val volumeUnit = stringPreferencesKey("volume_unit")
        val hydrationBase = doublePreferencesKey("hydration_base_ml")
        val hydrationDynamic = booleanPreferencesKey("hydration_dynamic")
        val aiPrivacy = stringPreferencesKey("ai_privacy")
        val aiAllowRetaining = booleanPreferencesKey("ai_allow_retaining")
        val dailyInsight = booleanPreferencesKey("daily_insight_enabled")
        fun aiModel(task: AiTask) = stringPreferencesKey("ai_model_${task.name}")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            weightUnit = p[Keys.weightUnit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                ?: WeightUnit.POUNDS,
            lengthUnit = p[Keys.lengthUnit]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() }
                ?: LengthUnit.INCHES,
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: false,
            defaultRestSec = p[Keys.defaultRestSec] ?: 120,
            restTimerVibrate = p[Keys.restVibrate] ?: true,
            barWeightKg = p[Keys.barWeight] ?: 20.0,
            availablePlatesKg = p[Keys.plates] ?: "25,20,15,10,5,2.5,1.25",
            goalWeightKg = p[Keys.goalWeight],
            goalRateKgPerWeek = p[Keys.goalRate] ?: 0.0,
            proteinGramsPerKg = p[Keys.proteinPerKg] ?: 2.0,
            fatGramsPerKg = p[Keys.fatPerKg] ?: 0.8,
            autoCalculateTargets = p[Keys.autoTargets] ?: true,
            addActiveCaloriesToTarget = p[Keys.addActiveKcal] ?: false,
            currentPlanWeek = p[Keys.planWeek] ?: 1,
            lastExportAtMillis = p[Keys.lastExport],
            demoDataLoaded = p[Keys.demoLoaded] ?: false,
            volumeUnit = p[Keys.volumeUnit]?.let { runCatching { VolumeUnit.valueOf(it) }.getOrNull() }
                ?: VolumeUnit.US_FLUID_OUNCES,
            hydrationBaseMl = p[Keys.hydrationBase],
            hydrationDynamic = p[Keys.hydrationDynamic] ?: true,
            aiPrivacy = p[Keys.aiPrivacy]?.let { runCatching { AiPrivacy.valueOf(it) }.getOrNull() }
                ?: AiPrivacy.NO_TRAINING,
            aiAllowRetainingModels = p[Keys.aiAllowRetaining] ?: false,
            aiModelOverrides = AiTask.entries.mapNotNull { task -> p[Keys.aiModel(task)]?.let { task to it } }.toMap(),
            dailyInsightEnabled = p[Keys.dailyInsight] ?: false,
        )
    }

    suspend fun setWeightUnit(unit: WeightUnit) = put { it[Keys.weightUnit] = unit.name }
    suspend fun setLengthUnit(unit: LengthUnit) = put { it[Keys.lengthUnit] = unit.name }
    suspend fun setThemeMode(mode: ThemeMode) = put { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = put { it[Keys.dynamicColor] = enabled }
    suspend fun setDefaultRestSec(seconds: Int) = put { it[Keys.defaultRestSec] = seconds }
    suspend fun setRestVibrate(enabled: Boolean) = put { it[Keys.restVibrate] = enabled }
    suspend fun setBarWeightKg(kg: Double) = put { it[Keys.barWeight] = kg }
    suspend fun setAvailablePlates(csv: String) = put { it[Keys.plates] = csv }
    suspend fun setGoalWeightKg(kg: Double?) = put {
        if (kg == null) it.remove(Keys.goalWeight) else it[Keys.goalWeight] = kg
    }
    suspend fun setGoalRateKgPerWeek(rate: Double) = put { it[Keys.goalRate] = rate }
    suspend fun setProteinPerKg(value: Double) = put { it[Keys.proteinPerKg] = value }
    suspend fun setFatPerKg(value: Double) = put { it[Keys.fatPerKg] = value }
    suspend fun setAutoCalculateTargets(enabled: Boolean) = put { it[Keys.autoTargets] = enabled }
    suspend fun setAddActiveCalories(enabled: Boolean) = put { it[Keys.addActiveKcal] = enabled }
    suspend fun setCurrentPlanWeek(week: Int) = put { it[Keys.planWeek] = week }
    suspend fun setLastExportAt(millis: Long) = put { it[Keys.lastExport] = millis }
    suspend fun setDemoDataLoaded(loaded: Boolean) = put { it[Keys.demoLoaded] = loaded }
    suspend fun setVolumeUnit(unit: VolumeUnit) = put { it[Keys.volumeUnit] = unit.name }
    suspend fun setHydrationBaseMl(ml: Double?) = put {
        if (ml == null) it.remove(Keys.hydrationBase) else it[Keys.hydrationBase] = ml
    }
    suspend fun setHydrationDynamic(enabled: Boolean) = put { it[Keys.hydrationDynamic] = enabled }
    suspend fun setAiPrivacy(privacy: AiPrivacy) = put { it[Keys.aiPrivacy] = privacy.name }
    suspend fun setAiAllowRetaining(allow: Boolean) = put { it[Keys.aiAllowRetaining] = allow }
    suspend fun setDailyInsightEnabled(enabled: Boolean) = put { it[Keys.dailyInsight] = enabled }

    /** Null clears the override, going back to the suggested model. */
    suspend fun setAiModel(task: AiTask, modelId: String?) = put {
        if (modelId == null) it.remove(Keys.aiModel(task)) else it[Keys.aiModel(task)] = modelId
    }

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
