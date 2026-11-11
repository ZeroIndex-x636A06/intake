package com.zeroindex.intake.ui.screens.ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.data.ai.CatalogState
import com.zeroindex.intake.data.ai.ModelCatalogRepository
import com.zeroindex.intake.data.ai.benchmark.BenchmarkProgress
import com.zeroindex.intake.data.ai.benchmark.BenchmarkReport
import com.zeroindex.intake.data.ai.benchmark.BenchmarkRunner
import com.zeroindex.intake.data.ai.benchmark.BenchmarkSelection
import androidx.work.WorkManager
import com.zeroindex.intake.data.db.dao.AiUsageDao
import com.zeroindex.intake.data.db.entity.AiUsageEntity
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.work.MenuIngestionWorker
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.cost.AiBudget
import com.zeroindex.intake.domain.ai.cost.AiCostModel
import com.zeroindex.intake.domain.ai.cost.AiRecommendation
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord
import com.zeroindex.intake.domain.ai.cost.FailureCount
import com.zeroindex.intake.domain.ai.cost.BudgetStatus
import com.zeroindex.intake.domain.ai.cost.CostSummary
import com.zeroindex.intake.domain.ai.cost.FeatureCost
import com.zeroindex.intake.domain.ai.cost.FeatureCostInput
import com.zeroindex.intake.domain.ai.cost.PeriodTotals
import com.zeroindex.intake.domain.ai.cost.Recommendation
import com.zeroindex.intake.domain.ai.cost.UsagePeriods
import com.zeroindex.intake.domain.ai.cost.UsageSummarizer
import com.zeroindex.intake.domain.ai.cost.UsageSummary
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelFit
import com.zeroindex.intake.domain.ai.ModelSuggester
import com.zeroindex.intake.domain.ai.PrivacyFit
import com.zeroindex.intake.domain.ai.RankedModel
import com.zeroindex.intake.domain.ai.Suggestion
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkCandidates
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import com.zeroindex.intake.domain.ai.benchmark.ExcludedModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface KeyCheck {
    data object Idle : KeyCheck
    data object Checking : KeyCheck
    data class Ok(val detail: String) : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

data class TaskRow(
    val task: AiTask,
    val suggestion: Suggestion,
    /** What will actually run: the user's pick, else the suggestion. */
    val active: RankedModel?,
    val isOverride: Boolean,
    /** The user picked a model that the live list no longer has. */
    val overrideMissing: Boolean,
)

/** The usage meter and the budget, worked out from the local usage log. */
data class AiMeterState(
    val budget: BudgetStatus = BudgetStatus(AiBudget.DEFAULT_CAP_USD, 0.0),
    val usage: UsageSummary = UsageSummary(emptyList(), PeriodTotals.ZERO, PeriodTotals.ZERO, PeriodTotals.ZERO),
    /** When "Reset counters" was last pressed; 0 if never. */
    val resetAt: Long = 0L,
    /** The start of next month, when the budget starts again. */
    val nextMonthStart: Long = 0L,
    /** This month's failed calls by feature and reason. */
    val failures: List<FailureCount> = emptyList(),
)

/** One line of the "Estimated cost" card. */
data class CostRowUi(
    val cost: FeatureCost,
    val recommendation: Recommendation?,
    /** The uses per day shown, and whether it is still the built-in default. */
    val usesPerDay: Double,
    val usesIsDefault: Boolean,
    /** Why the feature counts as zero, for opt-in features that are switched off. */
    val offNote: String?,
)

data class AiSettingsUiState(
    val hasKey: Boolean = false,
    val keyCheck: KeyCheck = KeyCheck.Idle,
    val settings: AppSettings = AppSettings(),
    val catalog: CatalogState = CatalogState(),
    val rows: List<TaskRow> = emptyList(),
    val benchmarks: List<BenchmarkScore> = emptyList(),
    val meter: AiMeterState = AiMeterState(),
    val costRows: List<CostRowUi> = emptyList(),
    val costSummary: CostSummary = CostSummary(emptyList(), 0.0, 0.0, emptyList()),
)

sealed interface BenchmarkPhase {
    data object Idle : BenchmarkPhase
    data class Running(val progress: BenchmarkProgress?) : BenchmarkPhase
    data class Finished(val report: BenchmarkReport) : BenchmarkPhase

    /** Models that finished before the cancel are already stored. */
    data object Cancelled : BenchmarkPhase
    data class Failed(val message: String) : BenchmarkPhase
}

data class BenchmarkUiState(
    val tasks: Set<AiTask> = BenchmarkSuite.tasks.toSet(),
    val capText: String = "%.2f".format(BenchmarkSuite.DEFAULT_CAP_USD),
    /** Null while the text is not a positive amount. */
    val capUsd: Double? = BenchmarkSuite.DEFAULT_CAP_USD,
    val plan: List<BenchmarkSelection> = emptyList(),
    /** Models left out of the plan, with why: free tier, or no provider meeting the privacy setting. */
    val excluded: List<ExcludedModel> = emptyList(),
    /** True while the providers of the likely candidates are being looked up. */
    val checkingProviders: Boolean = false,
    val estimateUsd: Double = 0.0,
    val phase: BenchmarkPhase = BenchmarkPhase.Idle,
) {
    val canStart: Boolean
        get() = phase !is BenchmarkPhase.Running && capUsd != null && plan.any { it.models.isNotEmpty() }
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageDao: AiUsageDao,
    private val clock: Clock,
    private val keys: ApiKeyStore,
    private val gateway: AiGateway,
    private val catalog: ModelCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val runner: BenchmarkRunner,
) : ViewModel() {

    private val keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)

    /** The base state: key, settings, catalog, benchmarks and the per-task model rows. */
    private val baseState: kotlinx.coroutines.flow.Flow<AiSettingsUiState> = combine(
        keys.state,
        keyCheck,
        settingsRepository.settings,
        combine(catalog.state, catalog.endpoints, ::Pair),
        catalog.observeBenchmarks(),
    ) { keyState, check, settings, (catalogState, endpoints), benchmarks ->
        val fit = PrivacyFit(settings.aiPrivacy != AiPrivacy.ANY, endpoints)
        AiSettingsUiState(
            hasKey = keyState.hasOpenRouterKey,
            keyCheck = check,
            settings = settings,
            catalog = catalogState,
            benchmarks = benchmarks,
            rows = AiTask.entries.map { task ->
                val suggestion = ModelSuggester.suggest(
                    task, catalogState.models, benchmarks, settings.aiAllowRetainingModels, fit,
                )
                val overrideId = settings.aiModelOverrides[task]
                val overridden = overrideId?.let { id -> suggestion.candidates.firstOrNull { it.model.id == id } }
                TaskRow(
                    task = task,
                    suggestion = suggestion,
                    active = overridden ?: suggestion.suggested,
                    isOverride = overrideId != null,
                    overrideMissing = overrideId != null && overridden == null && catalogState.models.isNotEmpty(),
                )
            },
        )
    }

    /** Ticks so "today" and "this month" roll over while the screen stays open. */
    private val ticks = flow {
        while (true) {
            emit(clock.nowMillis())
            delay(60_000)
        }
    }

    val uiState: StateFlow<AiSettingsUiState> = combine(
        baseState,
        usageDao.observeSince(clock.nowMillis() - USAGE_HISTORY_MS),
        ticks,
    ) { base, entities, now ->
        val records = entities.mapNotNull(::toRecord)
        val zone = clock.zone()
        val settings = base.settings
        val meter = AiMeterState(
            budget = AiBudget.status(settings.aiMonthlyBudgetUsd, UsageSummarizer.monthSpend(records, now, zone)),
            usage = UsageSummarizer.summarize(records, now, zone, settings.aiUsageResetAt),
            resetAt = settings.aiUsageResetAt,
            nextMonthStart = UsagePeriods.nextMonthStart(now, zone),
            failures = UsageSummarizer.failures(records, now, zone, settings.aiUsageResetAt),
        )
        val inputs = AiFeature.entries.filter { it.inEstimate }.map { f ->
            val model = base.rows.firstOrNull { it.task == f.task }?.active?.model
            val enabled = when (f) {
                AiFeature.DAILY_INSIGHT -> settings.dailyInsightEnabled
                AiFeature.COACH_REWORD -> settings.coachAiReword
                else -> true
            }
            FeatureCostInput(
                feature = f,
                enabled = enabled,
                usesPerDay = settings.aiUsesPerDay[f] ?: f.defaultUsesPerDay,
                model = model,
                measured = UsageSummarizer.measured(records, f),
                benchmarkUsdPerCall = if (f.benchmarkRepresentative && model != null) {
                    base.benchmarks.firstOrNull { it.task == f.task && it.modelId == model.id }?.costUsdPerRequest
                } else {
                    null
                },
            )
        }
        val summary = AiCostModel.estimate(inputs)
        val costRows = summary.rows.map { c ->
            val f = c.feature
            val row = base.rows.firstOrNull { it.task == f.task }
            CostRowUi(
                cost = c,
                recommendation = row?.let { AiRecommendation.forTask(it.task, it.suggestion) },
                usesPerDay = settings.aiUsesPerDay[f] ?: f.defaultUsesPerDay,
                usesIsDefault = settings.aiUsesPerDay[f] == null,
                offNote = when {
                    f == AiFeature.DAILY_INSIGHT && !settings.dailyInsightEnabled -> "Off, so it counts as zero"
                    f == AiFeature.COACH_REWORD && !settings.coachAiReword -> "Off, so it counts as zero"
                    else -> null
                },
            )
        }
        base.copy(meter = meter, costRows = costRows, costSummary = summary)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUiState())

    private fun toRecord(e: AiUsageEntity): AiUsageRecord? {
        val feature = AiFeature.parse(e.feature) ?: return null
        val trigger = runCatching { AiTrigger.valueOf(e.triggerKind) }.getOrNull() ?: return null
        return AiUsageRecord(
            feature, e.model, trigger, e.promptTokens, e.completionTokens, e.costUsd, e.costReported, e.success, e.at,
            failure = AiFailureKind.parse(e.failureKind),
        )
    }

    private val benchmarkTasks = MutableStateFlow(BenchmarkSuite.tasks.toSet())
    private val benchmarkCapText = MutableStateFlow("%.2f".format(BenchmarkSuite.DEFAULT_CAP_USD))
    private val benchmarkPhase = MutableStateFlow<BenchmarkPhase>(BenchmarkPhase.Idle)
    private var benchmarkJob: Job? = null

    private val checkingProviders = MutableStateFlow(false)

    val benchmarkState: StateFlow<BenchmarkUiState> = combine(
        combine(benchmarkTasks, benchmarkCapText, benchmarkPhase, ::Triple),
        catalog.state,
        catalog.endpoints,
        combine(catalog.observeBenchmarks(), settingsRepository.settings, checkingProviders, ::Triple),
    ) { (tasks, capText, phase), catalogState, endpoints, (benchmarks, settings, checking) ->
        // "No data collection" and "Zero retention" are both checked against the zero-retention endpoint list.
        val requireZdr = settings.aiPrivacy != AiPrivacy.ANY
        val plans = BenchmarkSuite.tasks.filter { it in tasks }.map { task ->
            BenchmarkCandidates.plan(task, catalogState.models, benchmarks, requireZdr, endpoints)
        }
        BenchmarkUiState(
            tasks = tasks,
            capText = capText,
            capUsd = capText.toDoubleOrNull()?.takeIf { it > 0 },
            plan = plans.map { BenchmarkSelection(it.task, it.selected) },
            excluded = plans.flatMap { it.excluded },
            checkingProviders = checking,
            phase = phase,
        )
    }.map { it.copy(estimateUsd = runner.estimateUsd(it.plan)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BenchmarkUiState())

    /**
     * Looks up the providers of the likely candidates whenever the catalog, the privacy setting or the chosen
     * tasks change, so the plan above is already privacy-checked when the person sees it.
     */
    private fun trackProviders() = viewModelScope.launch {
        combine(catalog.state, benchmarkTasks, settingsRepository.settings) { c, tasks, settings -> Triple(c.models, tasks, settings) }
            .collect { (models, tasks, settings) ->
                if (models.isEmpty() || !settings.aiEnabled) return@collect
                // The benchmark's tasks, plus every task's likely models, so each suggestion below is privacy-checked.
                val ids = (
                    BenchmarkSuite.tasks.filter { it in tasks }
                        .flatMap { BenchmarkCandidates.lookahead(it, models, emptyList()) }.map { it.id } +
                        AiTask.entries.flatMap { ModelFit.lookupIds(it, models, emptyList()) }
                    ).distinct()
                checkingProviders.value = true
                try {
                    catalog.ensureEndpoints(ids, needZdr = settings.aiPrivacy != AiPrivacy.ANY)
                } finally {
                    checkingProviders.value = false
                }
            }
    }

    init {
        refreshCatalog(force = false)
        trackProviders()
    }

    private companion object {
        /** Ninety days of calls feed the measured averages; the meter itself only looks at this month. */
        const val USAGE_HISTORY_MS = 90L * 24 * 60 * 60 * 1000
    }

    /** The public model list needs no key, but with AI switched off no request leaves the phone at all. */
    fun refreshCatalog(force: Boolean = true) = viewModelScope.launch {
        if (settingsRepository.settings.first().aiEnabled) catalog.ensureLoaded(force)
    }

    fun saveKey(key: String?) {
        keys.setOpenRouterKey(key)
        keyCheck.value = KeyCheck.Idle
        if (!key.isNullOrBlank()) testKey()
    }

    fun testKey() {
        val key = keys.openRouterKey() ?: return
        keyCheck.value = KeyCheck.Checking
        viewModelScope.launch {
            keyCheck.value = runCatching { gateway.testKey(key) }.fold(
                onSuccess = { info ->
                    val credit = info.limitRemaining?.let { "\$%.2f credit left".format(it) }
                        ?: info.usage?.let { "\$%.2f used, no limit set".format(it) }
                    KeyCheck.Ok(listOfNotNull("Connected", credit, "free tier".takeIf { info.isFreeTier }).joinToString(" · "))
                },
                onFailure = { KeyCheck.Failed((it as? AiError)?.message ?: "Could not check the key.") },
            )
        }
    }

    fun setPrivacy(privacy: AiPrivacy) = viewModelScope.launch { settingsRepository.setAiPrivacy(privacy) }
    fun setAllowRetaining(allow: Boolean) = viewModelScope.launch { settingsRepository.setAiAllowRetaining(allow) }
    fun setDailyInsight(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDailyInsightEnabled(enabled) }
    fun setCoachReword(enabled: Boolean) = viewModelScope.launch { settingsRepository.setCoachAiReword(enabled) }

    /** The weekly menu refresh: registering the job and the setting move together, so off means no job. */
    fun setMenuAutoRefresh(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMenuAutoRefresh(enabled)
        MenuIngestionWorker.apply(WorkManager.getInstance(context), enabled)
    }

    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setAiEnabled(enabled)
        if (enabled) catalog.ensureLoaded(false)
    }

    /** Returns false (and changes nothing) when [text] is not a positive amount. */
    fun setBudget(text: String): Boolean {
        val cap = AiBudget.parseCap(text) ?: return false
        viewModelScope.launch { settingsRepository.setAiMonthlyBudget(cap) }
        return true
    }

    /** An invalid or negative number puts the feature back on its default. */
    fun setUsesPerDay(feature: AiFeature, text: String) {
        val uses = text.trim().toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
        viewModelScope.launch { settingsRepository.setAiUsesPerDay(feature, uses) }
    }

    /** A soft reset: the meter counts from now on, and every logged call stays on the phone. */
    fun resetCounters() = viewModelScope.launch { settingsRepository.setAiUsageResetAt(clock.nowMillis()) }
    fun pickModel(task: AiTask, modelId: String?) = viewModelScope.launch { settingsRepository.setAiModel(task, modelId) }

    fun toggleBenchmarkTask(task: AiTask) = benchmarkTasks.update { if (task in it) it - task else it + task }

    fun setBenchmarkCap(text: String) {
        benchmarkCapText.value = text
    }

    /** Runs the plan on the user's key. The screen has already shown the estimate and asked to confirm. */
    fun startBenchmark() {
        val state = benchmarkState.value
        val key = keys.openRouterKey()
        val cap = state.capUsd
        if (!state.canStart || key == null || cap == null) return
        benchmarkPhase.value = BenchmarkPhase.Running(null)
        benchmarkJob = viewModelScope.launch {
            benchmarkPhase.value = try {
                BenchmarkPhase.Finished(
                    runner.run(
                        selections = state.plan,
                        capUsd = cap,
                        privacy = settingsRepository.settings.first().aiPrivacy,
                        onProgress = { benchmarkPhase.value = BenchmarkPhase.Running(it) },
                    ),
                )
            } catch (_: CancellationException) {
                BenchmarkPhase.Cancelled
            } catch (_: Exception) {
                // Never the exception's text: this code handles the API key, and nothing it throws is worth the risk.
                BenchmarkPhase.Failed("The benchmark stopped unexpectedly. Models that finished are saved.")
            }
        }
    }

    fun cancelBenchmark() {
        benchmarkJob?.cancel()
    }

    fun dismissBenchmarkResult() {
        benchmarkPhase.value = BenchmarkPhase.Idle
    }
}
