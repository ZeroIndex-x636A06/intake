package com.zeroindex.intake.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.insight.DailyInsights
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationDay
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
    val date: LocalDate = LocalDate.EPOCH,
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
    private val clock: Clock,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(clock.today())

    init {
        viewModelScope.launch { hydrationRepository.ensureDefaultPresets() }
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
                ) { presets, settings, bonus, insight -> Extras(presets, settings, bonus, insight) },
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
    )

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

    fun logWater(volumeMl: Double, presetId: String? = null, onLogged: (HydrationLogEntity) -> Unit = {}) =
        viewModelScope.launch {
            onLogged(hydrationRepository.log(volumeMl, timestampForSelectedDay(), presetId))
        }

    fun deleteWater(log: HydrationLogEntity) = viewModelScope.launch { hydrationRepository.delete(log.id) }
    fun restoreWater(log: HydrationLogEntity) = viewModelScope.launch { hydrationRepository.restore(log) }

    fun saveWaterPreset(name: String, volumeMl: Double, existing: HydrationPresetEntity?) =
        viewModelScope.launch { hydrationRepository.savePreset(name, volumeMl, existing) }

    fun deleteWaterPreset(preset: HydrationPresetEntity) =
        viewModelScope.launch { hydrationRepository.deletePreset(preset.id) }

    fun setVolumeUnit(unit: VolumeUnit) = viewModelScope.launch { settingsRepository.setVolumeUnit(unit) }
    fun setHydrationBase(ml: Double?) = viewModelScope.launch { settingsRepository.setHydrationBaseMl(ml) }
    fun setHydrationDynamic(on: Boolean) = viewModelScope.launch { settingsRepository.setHydrationDynamic(on) }

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
