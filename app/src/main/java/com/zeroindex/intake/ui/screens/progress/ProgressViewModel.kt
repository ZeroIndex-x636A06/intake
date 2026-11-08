package com.zeroindex.intake.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.BodyMetricEntity
import com.zeroindex.intake.data.db.entity.TdeeEstimateEntity
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.PerformanceRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.performance.OverlayWeek
import com.zeroindex.intake.domain.tdee.TrendPoint
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
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

/** SPEC §11: selectable 1M / 3M / 6M / 1Y / All ranges. */
/**
 * [caption] is what the mockup prints beside a card title ("last 3 months"), so
 * every figure on the screen says which window it covers.
 */
enum class ProgressRange(val label: String, val days: Int?, val caption: String) {
    MONTH("1M", 30, "last month"),
    THREE_MONTHS("3M", 90, "last 3 months"),
    SIX_MONTHS("6M", 180, "last 6 months"),
    YEAR("1Y", 365, "last year"),
    ALL("All", null, "all time"),
}

data class ProgressUiState(
    val range: ProgressRange = ProgressRange.MONTH,
    val settings: AppSettings = AppSettings(),
    val metrics: List<BodyMetricEntity> = emptyList(),
    val trend: List<TrendPoint> = emptyList(),
    /** Oldest first, one slot per day in the range; null means nothing logged. */
    val dailyKcal: List<Double?> = emptyList(),
    val target: MacroTarget? = null,
    val tdee: TdeeEstimateEntity? = null,
    val daysLogged: Int = 0,
    val daysInWindow: Int = 21,
    val proteinHitRate: Double? = null,
    val photos: List<BodyMetricEntity> = emptyList(),
    val isRecalculating: Boolean = false,
    /** Amendment §2: weekly tonnage against best jump, for the overlay card. */
    val performance: List<OverlayWeek> = emptyList(),
) {
    val latestWeightKg: Double? get() = metrics.maxByOrNull { it.measuredAt }?.weightKg
    val trendWeightKg: Double? get() = trend.lastOrNull()?.trendKg

    /** Average weekly change from the smoothed trend, in kilograms. */
    val weeklyChangeKg: Double?
        get() {
            if (trend.size < 2) return null
            val first = trend.first()
            val last = trend.last()
            val days = (last.dayIndex - first.dayIndex).toDouble()
            if (days <= 0) return null
            return (last.trendKg - first.trendKg) / days * 7.0
        }

    val averageIntake: Double?
        get() = dailyKcal.filterNotNull().filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val foodLogRepository: FoodLogRepository,
    private val targetRepository: TargetRepository,
    private val tdeeRepository: TdeeRepository,
    private val settingsRepository: SettingsRepository,
    private val performanceRepository: PerformanceRepository,
    private val clock: Clock,
) : ViewModel() {

    private val range = MutableStateFlow(ProgressRange.MONTH)
    private val recalculating = MutableStateFlow(false)
    private val adherence = MutableStateFlow(AdherenceSnapshot())

    private data class AdherenceSnapshot(
        val daysLogged: Int = 0,
        val daysInWindow: Int = 21,
        val proteinHitRate: Double? = null,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ProgressUiState> = range
        .flatMapLatest { selected ->
            val today = clock.today()
            val from = selected.days?.let { today.minusDays((it - 1).toLong()) }
                ?: LocalDate.of(2000, 1, 1)

            combine(
                bodyRepository.observeInRange(from, today),
                bodyRepository.observeTrend(from, today),
                foodLogRepository.observeDailyIntake(from, today),
                targetRepository.observeTargetFor(today),
                combine(
                    tdeeRepository.observeLatest(),
                    settingsRepository.settings,
                    bodyRepository.observeWithPhotos(),
                    adherence,
                    combine(recalculating, performanceRepository.observeOverlay(from, today)) { busy, weeks ->
                        busy to weeks
                    },
                ) { tdee, settings, photos, snapshot, busyAndWeeks ->
                    Extras(tdee, settings, photos, snapshot, busyAndWeeks.first, busyAndWeeks.second)
                },
            ) { metrics, trend, intake, target, extras ->
                val byDay = intake.associate {
                    localDateOf(it.dayStartMillis, clock.zone()) to (it.kcal ?: 0.0)
                }
                val firstDay = if (selected.days != null) {
                    from
                } else {
                    metrics.minOfOrNull { localDateOf(it.measuredAt, clock.zone()) }
                        ?: byDay.keys.minOrNull()
                        ?: today
                }
                val dayCount = (today.toEpochDay() - firstDay.toEpochDay() + 1)
                    .coerceIn(1, 400).toInt()
                val series = (0 until dayCount).map { offset ->
                    byDay[firstDay.plusDays(offset.toLong())]?.takeIf { it > 0 }
                }

                ProgressUiState(
                    range = selected,
                    settings = extras.settings,
                    metrics = metrics,
                    trend = trend,
                    dailyKcal = series,
                    target = target,
                    tdee = extras.tdee,
                    daysLogged = extras.snapshot.daysLogged,
                    daysInWindow = extras.snapshot.daysInWindow,
                    proteinHitRate = extras.snapshot.proteinHitRate,
                    photos = extras.photos,
                    isRecalculating = extras.busy,
                    performance = extras.performance,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProgressUiState(),
        )

    private data class Extras(
        val tdee: TdeeEstimateEntity?,
        val settings: AppSettings,
        val photos: List<BodyMetricEntity>,
        val snapshot: AdherenceSnapshot,
        val busy: Boolean,
        val performance: List<OverlayWeek>,
    )

    init {
        refreshAdherence()
    }

    fun selectRange(next: ProgressRange) {
        range.value = next
    }

    /**
     * SPEC §11: never a streak that resets to zero. "18 of the last 21 days" is
     * the framing, so this counts days rather than consecutive days.
     */
    fun refreshAdherence() = viewModelScope.launch {
        val today = clock.today()
        val window = 21
        val from = today.minusDays((window - 1).toLong())
        val logged = foodLogRepository.countDaysLogged(from, today)

        // SPEC §11: protein target hit rate, over the days that were logged at
        // all - a day with nothing logged is a missing measurement, not a miss.
        val target = targetRepository.targetFor(today)
        val hitRate = if (target != null && logged > 0) {
            val proteinByDay = foodLogRepository.proteinByDay(from, today)
            val hits = proteinByDay.values.count { it >= target.proteinG * PROTEIN_HIT_FRACTION }
            hits.toDouble() / logged
        } else {
            null
        }

        adherence.value = AdherenceSnapshot(
            daysLogged = logged,
            daysInWindow = window,
            proteinHitRate = hitRate,
        )
    }

    private companion object {
        /** Close enough counts: 90% of the protein target is a hit. */
        const val PROTEIN_HIT_FRACTION = 0.9
    }

    fun recalculateTdee() = viewModelScope.launch {
        recalculating.value = true
        try {
            tdeeRepository.recalculate()?.let { tdeeRepository.refreshTargetFromTdee(it) }
            refreshAdherence()
        } finally {
            recalculating.value = false
        }
    }
}
