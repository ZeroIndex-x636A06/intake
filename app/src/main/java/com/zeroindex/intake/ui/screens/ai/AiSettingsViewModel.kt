package com.zeroindex.intake.ui.screens.ai

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
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelSuggester
import com.zeroindex.intake.domain.ai.RankedModel
import com.zeroindex.intake.domain.ai.Suggestion
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

data class AiSettingsUiState(
    val hasKey: Boolean = false,
    val keyCheck: KeyCheck = KeyCheck.Idle,
    val settings: AppSettings = AppSettings(),
    val catalog: CatalogState = CatalogState(),
    val rows: List<TaskRow> = emptyList(),
    val benchmarks: List<BenchmarkScore> = emptyList(),
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
    val estimateUsd: Double = 0.0,
    val phase: BenchmarkPhase = BenchmarkPhase.Idle,
) {
    val canStart: Boolean
        get() = phase !is BenchmarkPhase.Running && capUsd != null && plan.any { it.models.isNotEmpty() }
}

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val keys: ApiKeyStore,
    private val gateway: AiGateway,
    private val catalog: ModelCatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val runner: BenchmarkRunner,
) : ViewModel() {

    private val keyCheck = MutableStateFlow<KeyCheck>(KeyCheck.Idle)

    val uiState: StateFlow<AiSettingsUiState> = combine(
        keys.state,
        keyCheck,
        settingsRepository.settings,
        catalog.state,
        catalog.observeBenchmarks(),
    ) { keyState, check, settings, catalogState, benchmarks ->
        AiSettingsUiState(
            hasKey = keyState.hasOpenRouterKey,
            keyCheck = check,
            settings = settings,
            catalog = catalogState,
            benchmarks = benchmarks,
            rows = AiTask.entries.map { task ->
                val suggestion = ModelSuggester.suggest(
                    task, catalogState.models, benchmarks, settings.aiAllowRetainingModels,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUiState())

    private val benchmarkTasks = MutableStateFlow(BenchmarkSuite.tasks.toSet())
    private val benchmarkCapText = MutableStateFlow("%.2f".format(BenchmarkSuite.DEFAULT_CAP_USD))
    private val benchmarkPhase = MutableStateFlow<BenchmarkPhase>(BenchmarkPhase.Idle)
    private var benchmarkJob: Job? = null

    val benchmarkState: StateFlow<BenchmarkUiState> = combine(
        benchmarkTasks,
        benchmarkCapText,
        benchmarkPhase,
        catalog.state,
        combine(catalog.observeBenchmarks(), settingsRepository.settings, ::Pair),
    ) { tasks, capText, phase, catalogState, (benchmarks, settings) ->
        val plan = BenchmarkSuite.tasks.filter { it in tasks }.map { task ->
            BenchmarkSelection(
                task,
                BenchmarkSuite.defaultCandidates(task, catalogState.models, benchmarks, settings.aiAllowRetainingModels),
            )
        }
        BenchmarkUiState(tasks, capText, capText.toDoubleOrNull()?.takeIf { it > 0 }, plan, phase = phase)
    }.map { it.copy(estimateUsd = runner.estimateUsd(it.plan)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BenchmarkUiState())

    init {
        refreshCatalog(force = false)
    }

    fun refreshCatalog(force: Boolean = true) = viewModelScope.launch { catalog.ensureLoaded(force) }

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
                        apiKey = key,
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
