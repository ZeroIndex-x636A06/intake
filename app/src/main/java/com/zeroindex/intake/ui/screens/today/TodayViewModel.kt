package com.zeroindex.intake.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.insight.DailyInsights
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationDay
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One immutable state object per screen (SPEC §2). */
data class TodayUiState(
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val isToday: Boolean = true,
    val isFuture: Boolean = false,
    val progress: DayProgress = DayProgress(Macros.ZERO, null),
    val slots: List<MealSlotUi> = emptyList(),
    val hasTarget: Boolean = false,
    val hydration: HydrationDay? = null,
    val hydrationPresets: List<HydrationPresetEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /** Net workout kcal included in the target (SPEC §12: clearly labelled). */
    val activityBonusKcal: Int = 0,
    /** Amendment §3: today's insight, if enabled, generated and not dismissed. */
    val insight: AiInsightEntity? = null,
    /** The latest weigh-in and its change over the last week, for the weight tile. */
    val latestWeightKg: Double? = null,
    val weightWeekDeltaKg: Double? = null,
    /** False only for the placeholder before the first database emission, so motion never fires on it. */
    val loaded: Boolean = false,
) {
    val totalEntries: Int get() = slots.sumOf { it.entries.size }
}

data class MealSlotUi(
    val slot: MealSlot,
    val entries: List<FoodLogEntity>,
) {
    val kcal: Double get() = entries.sumOf { it.kcal }
    val protein: Double get() = entries.sumOf { it.protein }
}

internal fun MealSlot.displayName(): String = when (this) {
    MealSlot.BREAKFAST -> "Breakfast"
    MealSlot.LUNCH -> "Lunch"
    MealSlot.DINNER -> "Dinner"
    MealSlot.SNACK -> "Snacks"
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val foodLogRepository: FoodLogRepository,
    private val targetRepository: TargetRepository,
    private val hydrationRepository: HydrationRepository,
    private val settingsRepository: SettingsRepository,
    private val templates: MealTemplateRepository,
    private val insights: DailyInsights,
    private val body: BodyRepository,
    private val clock: Clock,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(clock.today())

    init {
        viewModelScope.launch { hydrationRepository.ensureDefaultPresets() }
        viewModelScope.launch { hydrationRepository.refreshWeather() }
        viewModelScope.launch { insights.ensureToday() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                foodLogRepository.observeDay(date),
                foodLogRepository.observeDayTotals(date),
                targetRepository.observeTargetFor(date),
                hydrationRepository.observeDay(date),
                combine(
                    hydrationRepository.observePresets(),
                    settingsRepository.settings,
                    targetRepository.observeActivityBonus(date),
                    insights.observeToday(),
                    body.observeAll(),
                ) { presets, settings, bonus, insight, metrics -> Extras(presets, settings, bonus, insight, metrics) },
            ) { logs, totals, target, hydration, presetsAndSettings ->
                val today = clock.today()
                TodayUiState(
                    date = date,
                    isToday = date == today,
                    isFuture = date.isAfter(today),
                    progress = DayProgress(consumed = totals, target = target),
                    slots = MealSlot.entries.map { slot ->
                        MealSlotUi(slot = slot, entries = logs.filter { it.mealSlot == slot })
                    },
                    hasTarget = target != null,
                    hydration = hydration,
                    hydrationPresets = presetsAndSettings.presets,
                    settings = presetsAndSettings.settings,
                    activityBonusKcal = presetsAndSettings.bonus,
                    insight = presetsAndSettings.insight?.takeIf { date == today && it.dismissedAt == null },
                    latestWeightKg = presetsAndSettings.metrics.firstOrNull()?.weightKg,
                    weightWeekDeltaKg = weekDelta(presetsAndSettings.metrics, clock.nowMillis()),
                    loaded = true,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(date = clock.today()),
        )

    private data class Extras(
        val presets: List<HydrationPresetEntity>,
        val settings: AppSettings,
        val bonus: Int,
        val insight: AiInsightEntity?,
        val metrics: List<com.zeroindex.intake.data.db.entity.BodyMetricEntity>,
    )

    /** Newest weigh-in minus the newest one at least six days older; null without both. [metrics] is newest first. */
    private fun weekDelta(metrics: List<com.zeroindex.intake.data.db.entity.BodyMetricEntity>, now: Long): Double? {
        val latest = metrics.firstOrNull() ?: return null
        val older = metrics.firstOrNull { latest.measuredAt - it.measuredAt >= 6 * 86_400_000L } ?: return null
        return latest.weightKg - older.weightKg
    }

    /** Water card "+": one more of the same drink and size. */
    fun addAnother(sample: HydrationLogEntity) {
        viewModelScope.launch {
            hydrationRepository.log(sample.volumeMl, timestampForSelectedDay(), sample.presetId, sample.drinkType)
        }
    }

    /** Water card "-": removes the newest entry of that drink and size (a soft delete). */
    fun removeLast(sample: HydrationLogEntity) { deleteWater(sample) }

    /** The epoch day the protein burst last played; it plays once per day. */
    val lastCelebration: StateFlow<Long?> = settingsRepository.celebratedProteinDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markCelebrated(epochDay: Long) { viewModelScope.launch { settingsRepository.setCelebratedProteinDay(epochDay) } }

    fun dismissInsight(insight: AiInsightEntity) = viewModelScope.launch { insights.dismiss(insight.id) }

    fun showPreviousDay() { selectedDate.value = selectedDate.value.minusDays(1) }
    fun showNextDay() { selectedDate.value = selectedDate.value.plusDays(1) }
    fun showToday() { selectedDate.value = clock.today() }

    fun deleteLog(log: FoodLogEntity) = viewModelScope.launch {
        foodLogRepository.softDelete(log.id)
    }

    fun copyLogToSlot(log: FoodLogEntity, slot: MealSlot) = viewModelScope.launch {
        foodLogRepository.copyToSlot(log, slot)
    }

    fun moveLogToSlot(log: FoodLogEntity, slot: MealSlot) = viewModelScope.launch {
        foodLogRepository.moveToSlot(log, slot)
    }

    fun updateQuantity(log: FoodLogEntity, grams: Double) = viewModelScope.launch {
        foodLogRepository.updateQuantity(log, grams)
    }

    /** Amendment §2: a prepped meal, saved so a tag or one tap logs it again. */
    fun saveSlotAsTemplate(slot: MealSlot, name: String, onSaved: () -> Unit) = viewModelScope.launch {
        templates.saveSlotAsTemplate(selectedDate.value, slot, name)
        onSaved()
    }

    // --- Water (amendment §3) ---------------------------------------------

    fun logWater(
        volumeMl: Double,
        presetId: String? = null,
        drink: DrinkType? = null,
        onLogged: (HydrationLogEntity) -> Unit = {},
    ) =
        viewModelScope.launch {
            onLogged(hydrationRepository.log(volumeMl, timestampForSelectedDay(), presetId, drink))
        }

    fun deleteWater(log: HydrationLogEntity) = viewModelScope.launch { hydrationRepository.delete(log.id) }
    fun restoreWater(log: HydrationLogEntity) = viewModelScope.launch { hydrationRepository.restore(log) }

    fun saveWaterPreset(name: String, volumeMl: Double, existing: HydrationPresetEntity?, drink: DrinkType? = null) =
        viewModelScope.launch {
            if (drink == null) hydrationRepository.savePreset(name, volumeMl, existing)
            else hydrationRepository.savePreset(name, volumeMl, existing, drink)
        }

    fun deleteWaterPreset(preset: HydrationPresetEntity) =
        viewModelScope.launch { hydrationRepository.deletePreset(preset.id) }


    /**
     * Timestamp to attach when logging onto the viewed day. Logging onto a past
     * day keeps midday rather than "now", so the entry lands on the day the
     * user is looking at (SPEC §3.4).
     */
    fun timestampForSelectedDay(): Long {
        val date = selectedDate.value
        return if (date == clock.today()) {
            clock.nowMillis()
        } else {
            clock.rangeFor(date).startInclusive + 12 * 60 * 60 * 1000L
        }
    }
}
