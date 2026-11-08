package com.zeroindex.intake.ui.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.data.ai.CatalogState
import com.zeroindex.intake.data.ai.ModelCatalogRepository
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelSuggester
import com.zeroindex.intake.domain.ai.RankedModel
import com.zeroindex.intake.domain.ai.Suggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val keys: ApiKeyStore,
    private val gateway: AiGateway,
    private val catalog: ModelCatalogRepository,
    private val settingsRepository: SettingsRepository,
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
}
