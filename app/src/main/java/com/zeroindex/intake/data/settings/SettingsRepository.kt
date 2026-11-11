package com.zeroindex.intake.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import com.zeroindex.intake.domain.model.MealSlot
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.cost.AiBudget
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.PortionUnit
import com.zeroindex.intake.domain.settings.SodiumDisplay
import com.zeroindex.intake.domain.coach.CoachState
import com.zeroindex.intake.domain.goals.AppliedPlan
import com.zeroindex.intake.domain.goals.BaselineState
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.goals.WeekPattern
import com.zeroindex.intake.domain.hydration.HydrationTarget
import kotlinx.serialization.json.Json
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
    val hydrationTrainingBonusMl: Double = HydrationTarget.TRAINING_DAY_BONUS_ML,
    /** Amendment §1. Defaults to the privacy-preserving middle setting. */
    val aiPrivacy: AiPrivacy = AiPrivacy.NO_TRAINING,
    /** Whether free models that may keep prompts can be suggested. */
    val aiAllowRetainingModels: Boolean = false,
    /** A model the user chose by hand per task; absent means "use the suggestion". */
    val aiModelOverrides: Map<AiTask, String> = emptyMap(),
    /** Amendment §3 device sync. Null server = sync off. */
    val sync: SyncSettings = SyncSettings(),
    /**
     * Amendment §3. Off by default: it sends a summary of food, training and
     * body data off the phone, so it has to be asked for.
     */
    val dailyInsightEnabled: Boolean = false,
    /** Health Connect: watermark for the write sync, and when it last ran. */
    val healthLastWriteAt: Long = 0,
    val healthLastSyncAt: Long? = null,
    /** One line of what the last Health Connect sync read per type. */
    val healthLastReport: String? = null,
    /** Profile & goals: who the plan is for. One JSON value, see [UserProfile]. */
    val profile: UserProfile = UserProfile(),
    /** Baseline week progress marker. */
    val baseline: BaselineState = BaselineState(),
    /** The last few plans the person applied, newest first. */
    val planHistory: List<AppliedPlan> = emptyList(),
    /**
     * Advanced carb cycling / refeeds from the last applied plan. Only used while the stored
     * calorie target still equals [WeekPattern.baseKcal], so a later change to the target wins.
     */
    val weekPattern: WeekPattern? = null,
    /** Coach: ISO weekday (1 = Monday) of the weekly check-in; null = the day the baseline started, else Monday. */
    val coachWeekday: Int? = null,
    /** Coach: send the "Your weekly check-in is ready" notification. */
    val coachNotify: Boolean = true,
    /** Coach: reword suggestion text with AI. Off by default; needs an OpenRouter key. */
    val coachAiReword: Boolean = false,
    val coachState: CoachState = CoachState(),
    /** In-app "Reduce motion": on top of the system animation scale (see MotionRules.reduceMotion). */
    val reduceMotion: Boolean = false,
    /**
     * The master switch for every AI feature ("Use AI features"). A key is already an opt-in, so this is the
     * kill switch: off, no AI entry point is offered and nothing is sent. Defaults on so an existing key works.
     */
    val aiEnabled: Boolean = true,
    /** The monthly budget cap in USD; calls stop at it until the next month or until it is raised. */
    val aiMonthlyBudgetUsd: Double = AiBudget.DEFAULT_CAP_USD,
    /** The person's own uses-per-day for the cost estimate; absent means the feature's default. */
    val aiUsesPerDay: Map<AiFeature, Double> = emptyMap(),
    /** "Reset counters" marker: the usage meter shows calls from this instant on. History is kept. */
    val aiUsageResetAt: Long = 0L,
    /** The weekly menu re-import. Off by default; the job does nothing until this is on. */
    val menuAutoRefresh: Boolean = false,
    /** You: the name on the header and the avatar's letter. Optional, never sent anywhere. */
    val displayName: String = "",
    /** Settings > Appearance: how food portions are shown in the photo estimate and food detail. */
    val portionUnit: PortionUnit = PortionUnit.GRAMS,
    /** Settings > Appearance > Today's cards. Calories is always shown. */
    val todayCardNext: Boolean = true,
    val todayCardWater: Boolean = true,
    val todayCardWeight: Boolean = true,
    val todayCardMeals: Boolean = true,
    val todayCardActivity: Boolean = true,
    /** Settings > Nutrition: show salt or sodium on the dashboard (consumed by the Nutrition rebuild). */
    val showSodium: SodiumDisplay = SodiumDisplay.OFF,
    /** Settings > Nutrition: Swap > Eat out nearby search radius in miles (consumed by the Swap sheet). */
    val eatOutRadiusMi: Double = 1.0,
    /** Settings > Training: show jump tests in Progress. Hidden until enabled. */
    val jumpTests: Boolean = false,
    /** Settings > Training > Advanced: weekly sets per region (six numbers, chest..core) or empty = suggested. */
    val weeklySetsCsv: String = "",
    /** Settings > Videos (consumed by the Videos feature). */
    val onlyWorkouts: Boolean = true,
    /** Videos: leave YouTube Shorts out of search results and Recommended. */
    val hideShorts: Boolean = true,
    val personaliseVideos: Boolean = true,
    /** Settings > Reminders: progress photo reminder in weeks (0 = off, else 2, 3 or 4). */
    val photoReminderWeeks: Int = 0,
    /** Settings > Reminders: a monthly "export now" reminder, only while sync is off. */
    val exportReminder: Boolean = true,
    /** Settings > Sync and export: include progress photo files in an export (opt-in). */
    val exportIncludePhotos: Boolean = false,
    /** Settings > Reminders (opt-in). Times are minutes after midnight, local time. The once-per-day memory is device-local. */
    val workoutReminderOn: Boolean = false,
    val workoutReminderMinute: Int = 17 * 60,
    val mealReminderOn: Boolean = false,
    val breakfastReminderMinute: Int = 7 * 60 + 30,
    val lunchReminderMinute: Int = 12 * 60,
    val dinnerReminderMinute: Int = 18 * 60 + 30,
    val waterReminderOn: Boolean = false,
    /** Water slots run every two hours from the start hour up to and including the end hour. */
    val waterReminderStartHour: Int = 8,
    val waterReminderEndHour: Int = 20,
    /** When the app was first used (millis); starts the export reminder clock. Device-local, never exported. */
    val firstUseAtMillis: Long? = null,
) {
    val platesKgList: List<Double>
        get() = availablePlatesKg.split(',')
            .mapNotNull { it.trim().toDoubleOrNull() }
            .filter { it > 0 }
            .sortedDescending()
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
/**
 * Where this device is in the sync stream. [pulledSeq] is the server sequence
 * already applied; [pushedUpTo] the local updatedAt everything at or before
 * which has been sent. [schemaVersion] is the database version the cursor was
 * built with: after an app update the next sync pulls everything again, so
 * rows for tables this version did not know about are picked up.
 */
data class SyncSettings(
    val serverUrl: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val pulledSeq: Long = 0,
    val pushedUpTo: Long = 0,
    val schemaVersion: Int = 0,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
) {
    val paired: Boolean get() = serverUrl != null && deviceId != null
}

/** Lenient so a field added in a later version never wipes a saved destination. */
private val settingsJson = Json { ignoreUnknownKeys = true }

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
        val syncServer = stringPreferencesKey("sync_server_url")
        val syncDeviceId = stringPreferencesKey("sync_device_id")
        val syncDeviceName = stringPreferencesKey("sync_device_name")
        val syncPulledSeq = longPreferencesKey("sync_pulled_seq")
        val syncPushedUpTo = longPreferencesKey("sync_pushed_up_to")
        val syncSchema = intPreferencesKey("sync_schema_version")
        val syncLastAt = longPreferencesKey("sync_last_at")
        val syncLastError = stringPreferencesKey("sync_last_error")
        val lastExport = longPreferencesKey("last_export_at")
        val demoLoaded = booleanPreferencesKey("demo_data_loaded")
        val volumeUnit = stringPreferencesKey("volume_unit")
        val hydrationBase = doublePreferencesKey("hydration_base_ml")
        val hydrationDynamic = booleanPreferencesKey("hydration_dynamic")
        val hydrationTrainingBonus = doublePreferencesKey("hydration_training_bonus_ml")
        val aiPrivacy = stringPreferencesKey("ai_privacy")
        val aiAllowRetaining = booleanPreferencesKey("ai_allow_retaining")
        val dailyInsight = booleanPreferencesKey("daily_insight_enabled")
        fun aiModel(task: AiTask) = stringPreferencesKey("ai_model_${task.name}")
        val healthLastWrite = longPreferencesKey("health_last_write_at")
        val healthLastSync = longPreferencesKey("health_last_sync_at")
        val healthLastReport = stringPreferencesKey("health_last_report")
        val profile = stringPreferencesKey("profile_json")
        val baseline = stringPreferencesKey("baseline_json")
        val planHistory = stringPreferencesKey("plan_history_json")
        val weekPattern = stringPreferencesKey("week_pattern_json")
        val coachWeekday = intPreferencesKey("coach_weekday")
        val coachNotify = booleanPreferencesKey("coach_notify")
        val coachAiReword = booleanPreferencesKey("coach_ai_reword")
        val coachState = stringPreferencesKey("coach_state_json")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val celebratedProteinDay = longPreferencesKey("last_celebration_day")
        val aiEnabled = booleanPreferencesKey("ai_master_enabled")
        val aiBudget = doublePreferencesKey("ai_monthly_budget_usd")
        fun aiUses(feature: AiFeature) = doublePreferencesKey("ai_uses_${feature.name}")
        val aiUsageResetAt = longPreferencesKey("ai_usage_reset_at")
        val menuAutoRefresh = booleanPreferencesKey("ai_menu_auto_refresh")
        val displayName = stringPreferencesKey("profile_display_name")
        val portionUnit = stringPreferencesKey("portion_unit")
        val todayNext = booleanPreferencesKey("today_card_next")
        val todayWater = booleanPreferencesKey("today_card_water")
        val todayWeight = booleanPreferencesKey("today_card_weight")
        val todayMeals = booleanPreferencesKey("today_card_meals")
        val todayActivity = booleanPreferencesKey("today_card_activity")
        val showSodium = stringPreferencesKey("show_sodium")
        val eatOutRadius = doublePreferencesKey("eat_out_radius_mi")
        val jumpTests = booleanPreferencesKey("jump_tests")
        val weeklySets = stringPreferencesKey("weekly_sets_csv")
        val onlyWorkouts = booleanPreferencesKey("videos_only_workouts")
        val hideShorts = booleanPreferencesKey("videos_hide_shorts")
        val personalise = booleanPreferencesKey("videos_personalise")
        val photoReminder = intPreferencesKey("photo_reminder_weeks")
        val exportReminder = booleanPreferencesKey("export_reminder")
        val exportPhotos = booleanPreferencesKey("export_include_photos")
        val workoutReminderOn = booleanPreferencesKey("workout_reminder_on")
        val workoutReminderMinute = intPreferencesKey("workout_reminder_minute")
        val mealReminderOn = booleanPreferencesKey("meal_reminder_on")
        val breakfastReminderMinute = intPreferencesKey("breakfast_reminder_minute")
        val lunchReminderMinute = intPreferencesKey("lunch_reminder_minute")
        val dinnerReminderMinute = intPreferencesKey("dinner_reminder_minute")
        val waterReminderOn = booleanPreferencesKey("water_reminder_on")
        val waterReminderStartHour = intPreferencesKey("water_reminder_start_hour")
        val waterReminderEndHour = intPreferencesKey("water_reminder_end_hour")
        val firstUseAt = longPreferencesKey("first_use_at_ms")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val weightUnit = p[Keys.weightUnit]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
            ?: WeightUnit.POUNDS
        AppSettings(
            weightUnit = weightUnit,
            lengthUnit = p[Keys.lengthUnit]?.let { runCatching { LengthUnit.valueOf(it) }.getOrNull() }
                ?: LengthUnit.INCHES,
            themeMode = p[Keys.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamicColor] ?: false,
            defaultRestSec = p[Keys.defaultRestSec] ?: 120,
            restTimerVibrate = p[Keys.restVibrate] ?: true,
            // Until the person sets their own, the bar and plates are the ones a gym in their unit has.
            barWeightKg = p[Keys.barWeight] ?: com.zeroindex.intake.domain.training.PlateDefaults.barKg(weightUnit),
            availablePlatesKg = p[Keys.plates] ?: com.zeroindex.intake.domain.training.PlateDefaults.platesCsvKg(weightUnit),
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
            hydrationTrainingBonusMl = p[Keys.hydrationTrainingBonus] ?: HydrationTarget.TRAINING_DAY_BONUS_ML,
            aiPrivacy = p[Keys.aiPrivacy]?.let { runCatching { AiPrivacy.valueOf(it) }.getOrNull() }
                ?: AiPrivacy.NO_TRAINING,
            aiAllowRetainingModels = p[Keys.aiAllowRetaining] ?: false,
            aiModelOverrides = AiTask.entries.mapNotNull { task -> p[Keys.aiModel(task)]?.let { task to it } }.toMap(),
            sync = SyncSettings(
                serverUrl = p[Keys.syncServer],
                deviceId = p[Keys.syncDeviceId],
                deviceName = p[Keys.syncDeviceName],
                pulledSeq = p[Keys.syncPulledSeq] ?: 0,
                pushedUpTo = p[Keys.syncPushedUpTo] ?: 0,
                schemaVersion = p[Keys.syncSchema] ?: 0,
                lastSyncAt = p[Keys.syncLastAt],
                lastError = p[Keys.syncLastError],
            ),
            dailyInsightEnabled = p[Keys.dailyInsight] ?: false,
            healthLastWriteAt = p[Keys.healthLastWrite] ?: 0,
            healthLastSyncAt = p[Keys.healthLastSync],
            healthLastReport = p[Keys.healthLastReport],
            profile = p[Keys.profile]?.let { runCatching { settingsJson.decodeFromString<UserProfile>(it) }.getOrNull() }
                ?: UserProfile(),
            baseline = p[Keys.baseline]?.let { runCatching { settingsJson.decodeFromString<BaselineState>(it) }.getOrNull() }
                ?: BaselineState(),
            planHistory = p[Keys.planHistory]
                ?.let { runCatching { settingsJson.decodeFromString<List<AppliedPlan>>(it) }.getOrNull() }
                ?: emptyList(),
            weekPattern = p[Keys.weekPattern]?.let { runCatching { settingsJson.decodeFromString<WeekPattern>(it) }.getOrNull() },
            coachWeekday = p[Keys.coachWeekday]?.takeIf { it in 1..7 },
            coachNotify = p[Keys.coachNotify] ?: true,
            coachAiReword = p[Keys.coachAiReword] ?: false,
            coachState = p[Keys.coachState]?.let { runCatching { settingsJson.decodeFromString<CoachState>(it) }.getOrNull() }
                ?: CoachState(),
            reduceMotion = p[Keys.reduceMotion] ?: false,
            aiEnabled = p[Keys.aiEnabled] ?: true,
            aiMonthlyBudgetUsd = p[Keys.aiBudget]?.takeIf { it > 0 && it.isFinite() } ?: AiBudget.DEFAULT_CAP_USD,
            aiUsesPerDay = AiFeature.entries.mapNotNull { f -> p[Keys.aiUses(f)]?.takeIf { it >= 0 }?.let { f to it } }.toMap(),
            aiUsageResetAt = p[Keys.aiUsageResetAt] ?: 0L,
            menuAutoRefresh = p[Keys.menuAutoRefresh] ?: false,
            displayName = p[Keys.displayName].orEmpty(),
            portionUnit = p[Keys.portionUnit]?.let { runCatching { PortionUnit.valueOf(it) }.getOrNull() } ?: PortionUnit.GRAMS,
            todayCardNext = p[Keys.todayNext] ?: true,
            todayCardWater = p[Keys.todayWater] ?: true,
            todayCardWeight = p[Keys.todayWeight] ?: true,
            todayCardMeals = p[Keys.todayMeals] ?: true,
            todayCardActivity = p[Keys.todayActivity] ?: true,
            showSodium = p[Keys.showSodium]?.let { runCatching { SodiumDisplay.valueOf(it) }.getOrNull() } ?: SodiumDisplay.OFF,
            eatOutRadiusMi = p[Keys.eatOutRadius]?.takeIf { it > 0 } ?: 1.0,
            jumpTests = p[Keys.jumpTests] ?: false,
            weeklySetsCsv = p[Keys.weeklySets].orEmpty(),
            onlyWorkouts = p[Keys.onlyWorkouts] ?: true,
            hideShorts = p[Keys.hideShorts] ?: true,
            personaliseVideos = p[Keys.personalise] ?: true,
            photoReminderWeeks = p[Keys.photoReminder]?.takeIf { it in setOf(0, 2, 3, 4) } ?: 0,
            exportReminder = p[Keys.exportReminder] ?: true,
            exportIncludePhotos = p[Keys.exportPhotos] ?: false,
            workoutReminderOn = p[Keys.workoutReminderOn] ?: false,
            workoutReminderMinute = (p[Keys.workoutReminderMinute] ?: (17 * 60)).coerceIn(0, MINUTES_PER_DAY - 1),
            mealReminderOn = p[Keys.mealReminderOn] ?: false,
            breakfastReminderMinute = (p[Keys.breakfastReminderMinute] ?: (7 * 60 + 30)).coerceIn(0, MINUTES_PER_DAY - 1),
            lunchReminderMinute = (p[Keys.lunchReminderMinute] ?: (12 * 60)).coerceIn(0, MINUTES_PER_DAY - 1),
            dinnerReminderMinute = (p[Keys.dinnerReminderMinute] ?: (18 * 60 + 30)).coerceIn(0, MINUTES_PER_DAY - 1),
            waterReminderOn = p[Keys.waterReminderOn] ?: false,
            waterReminderStartHour = (p[Keys.waterReminderStartHour] ?: 8).coerceIn(0, 23),
            waterReminderEndHour = (p[Keys.waterReminderEndHour] ?: 20).coerceIn(0, 23),
            firstUseAtMillis = p[Keys.firstUseAt],
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
    suspend fun setHydrationTrainingBonusMl(ml: Double) = put { it[Keys.hydrationTrainingBonus] = ml.coerceIn(0.0, 3_000.0) }
    suspend fun setAiPrivacy(privacy: AiPrivacy) = put { it[Keys.aiPrivacy] = privacy.name }
    suspend fun setAiAllowRetaining(allow: Boolean) = put { it[Keys.aiAllowRetaining] = allow }
    suspend fun setDailyInsightEnabled(enabled: Boolean) = put { it[Keys.dailyInsight] = enabled }
    suspend fun setHealthSynced(writeWatermark: Long, at: Long, report: String? = null) = put {
        it[Keys.healthLastWrite] = writeWatermark
        it[Keys.healthLastSync] = at
        if (report == null) it.remove(Keys.healthLastReport) else it[Keys.healthLastReport] = report
    }

    suspend fun setProfile(profile: UserProfile) = put { it[Keys.profile] = settingsJson.encodeToString(profile) }

    suspend fun setWeekPattern(pattern: WeekPattern?) = put {
        if (pattern == null) it.remove(Keys.weekPattern) else it[Keys.weekPattern] = settingsJson.encodeToString(pattern)
    }

    suspend fun setCoachWeekday(isoWeekday: Int?) = put {
        if (isoWeekday == null) it.remove(Keys.coachWeekday) else it[Keys.coachWeekday] = isoWeekday.coerceIn(1, 7)
    }
    suspend fun setReduceMotion(enabled: Boolean) = put { it[Keys.reduceMotion] = enabled }
    suspend fun setCelebratedProteinDay(epochDay: Long) = put { it[Keys.celebratedProteinDay] = epochDay }
    val celebratedProteinDay: Flow<Long?> = context.dataStore.data.map { it[Keys.celebratedProteinDay] }
    suspend fun setCoachNotify(enabled: Boolean) = put { it[Keys.coachNotify] = enabled }
    suspend fun setCoachAiReword(enabled: Boolean) = put { it[Keys.coachAiReword] = enabled }
    suspend fun setAiEnabled(enabled: Boolean) = put { it[Keys.aiEnabled] = enabled }
    suspend fun setAiMonthlyBudget(usd: Double) = put { if (usd > 0 && usd.isFinite()) it[Keys.aiBudget] = usd }
    suspend fun setAiUsesPerDay(feature: AiFeature, uses: Double?) = put {
        if (uses == null || uses < 0 || !uses.isFinite()) it.remove(Keys.aiUses(feature)) else it[Keys.aiUses(feature)] = uses
    }
    suspend fun setAiUsageResetAt(at: Long) = put { it[Keys.aiUsageResetAt] = at }
    suspend fun setMenuAutoRefresh(enabled: Boolean) = put { it[Keys.menuAutoRefresh] = enabled }

    /** Reads, changes and writes the coach state in one DataStore edit, so two writers cannot lose an update. */
    suspend fun updateCoachState(block: (CoachState) -> CoachState) = put { p ->
        val current = p[Keys.coachState]?.let { runCatching { settingsJson.decodeFromString<CoachState>(it) }.getOrNull() }
            ?: CoachState()
        p[Keys.coachState] = settingsJson.encodeToString(block(current))
    }

    suspend fun setBaseline(state: BaselineState) = put { it[Keys.baseline] = settingsJson.encodeToString(state) }

    /** Newest first, capped so the value stays small. */
    suspend fun addAppliedPlan(plan: AppliedPlan) = put { p ->
        val current = p[Keys.planHistory]
            ?.let { runCatching { settingsJson.decodeFromString<List<AppliedPlan>>(it) }.getOrNull() }
            ?: emptyList()
        p[Keys.planHistory] = settingsJson.encodeToString((listOf(plan) + current).take(PLAN_HISTORY_LIMIT))
    }

    /** Null clears the override, going back to the suggested model. */
    suspend fun setAiModel(task: AiTask, modelId: String?) = put {
        if (modelId == null) it.remove(Keys.aiModel(task)) else it[Keys.aiModel(task)] = modelId
    }

    suspend fun setDisplayName(name: String) = put { if (name.isBlank()) it.remove(Keys.displayName) else it[Keys.displayName] = name.trim().take(40) }
    suspend fun setPortionUnit(unit: PortionUnit) = put { it[Keys.portionUnit] = unit.name }
    suspend fun setTodayCard(card: com.zeroindex.intake.domain.settings.TodayCard, shown: Boolean) = put {
        val key = when (card) {
            com.zeroindex.intake.domain.settings.TodayCard.NEXT -> Keys.todayNext
            com.zeroindex.intake.domain.settings.TodayCard.WATER -> Keys.todayWater
            com.zeroindex.intake.domain.settings.TodayCard.WEIGHT -> Keys.todayWeight
            com.zeroindex.intake.domain.settings.TodayCard.MEALS -> Keys.todayMeals
            com.zeroindex.intake.domain.settings.TodayCard.ACTIVITY -> Keys.todayActivity
        }
        it[key] = shown
    }
    suspend fun setShowSodium(mode: SodiumDisplay) = put { it[Keys.showSodium] = mode.name }
    suspend fun setEatOutRadiusMi(miles: Double) = put { it[Keys.eatOutRadius] = miles }
    suspend fun setJumpTests(on: Boolean) = put { it[Keys.jumpTests] = on }
    suspend fun setWeeklySetsCsv(csv: String) = put { if (csv.isBlank()) it.remove(Keys.weeklySets) else it[Keys.weeklySets] = csv }
    suspend fun setOnlyWorkouts(on: Boolean) = put { it[Keys.onlyWorkouts] = on }
    suspend fun setHideShorts(on: Boolean) = put { it[Keys.hideShorts] = on }
    suspend fun setPersonaliseVideos(on: Boolean) = put { it[Keys.personalise] = on }
    suspend fun setPhotoReminderWeeks(weeks: Int) = put { it[Keys.photoReminder] = if (weeks in setOf(2, 3, 4)) weeks else 0 }
    suspend fun setExportReminder(on: Boolean) = put { it[Keys.exportReminder] = on }
    suspend fun setExportIncludePhotos(on: Boolean) = put { it[Keys.exportPhotos] = on }
    suspend fun setWorkoutReminder(on: Boolean) = put { it[Keys.workoutReminderOn] = on }
    suspend fun setWorkoutReminderMinute(minute: Int) = put { it[Keys.workoutReminderMinute] = minute.coerceIn(0, MINUTES_PER_DAY - 1) }
    suspend fun setMealReminder(on: Boolean) = put { it[Keys.mealReminderOn] = on }
    /** Breakfast, lunch or dinner time. Snacks have no reminder. */
    suspend fun setMealReminderMinute(slot: MealSlot, minute: Int) = put {
        val m = minute.coerceIn(0, MINUTES_PER_DAY - 1)
        when (slot) {
            MealSlot.BREAKFAST -> it[Keys.breakfastReminderMinute] = m
            MealSlot.LUNCH -> it[Keys.lunchReminderMinute] = m
            MealSlot.DINNER -> it[Keys.dinnerReminderMinute] = m
            MealSlot.SNACK -> Unit
        }
    }
    suspend fun setWaterReminder(on: Boolean) = put { it[Keys.waterReminderOn] = on }
    suspend fun setWaterReminderHours(startHour: Int, endHour: Int) = put {
        it[Keys.waterReminderStartHour] = startHour.coerceIn(0, 23)
        it[Keys.waterReminderEndHour] = endHour.coerceIn(0, 23)
    }
    /** Records the first use once; later calls change nothing. */
    suspend fun ensureFirstUse(now: Long) = put { if (it[Keys.firstUseAt] == null) it[Keys.firstUseAt] = now }

    suspend fun setSyncPaired(serverUrl: String, deviceId: String, deviceName: String) = put {
        it[Keys.syncServer] = serverUrl
        it[Keys.syncDeviceId] = deviceId
        it[Keys.syncDeviceName] = deviceName
        it[Keys.syncPulledSeq] = 0
        it[Keys.syncPushedUpTo] = 0
        it.remove(Keys.syncLastError)
    }

    suspend fun setSyncCursor(pulledSeq: Long? = null, pushedUpTo: Long? = null, schemaVersion: Int? = null) = put {
        pulledSeq?.let { v -> it[Keys.syncPulledSeq] = v }
        pushedUpTo?.let { v -> it[Keys.syncPushedUpTo] = v }
        schemaVersion?.let { v -> it[Keys.syncSchema] = v }
    }

    suspend fun setSyncResult(at: Long, error: String?) = put {
        it[Keys.syncLastAt] = at
        if (error == null) it.remove(Keys.syncLastError) else it[Keys.syncLastError] = error
    }

    suspend fun clearSync() = put { p ->
        listOf(Keys.syncServer, Keys.syncDeviceId, Keys.syncDeviceName, Keys.syncLastError).forEach { p.remove(it) }
        listOf(Keys.syncPulledSeq, Keys.syncPushedUpTo, Keys.syncLastAt).forEach { p.remove(it) }
        p.remove(Keys.syncSchema)
    }

    /** Raw preferences for the settings backup section; filtered by [com.zeroindex.intake.domain.backup.PortableSettings]. */
    suspend fun rawSnapshot(): Map<String, Any?> = context.dataStore.rawSnapshot()

    suspend fun restorePortable(values: Map<String, com.zeroindex.intake.domain.backup.PortableSettings.Value>) =
        context.dataStore.restore(values)

    private companion object {
        const val PLAN_HISTORY_LIMIT = 12
        const val MINUTES_PER_DAY = 24 * 60
    }

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
