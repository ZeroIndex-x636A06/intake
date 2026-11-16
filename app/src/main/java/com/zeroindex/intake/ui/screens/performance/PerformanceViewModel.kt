package com.zeroindex.intake.ui.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.PerformanceMetricEntity
import com.zeroindex.intake.data.repository.PerformanceRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PerformanceUiState(
    val settings: AppSettings = AppSettings(),
    val history: List<PerformanceMetricEntity> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val repository: PerformanceRepository,
    settingsRepository: SettingsRepository,
    private val messages: AppMessages,
) : ViewModel() {

    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PerformanceUiState> = combine(
        settingsRepository.settings,
        repository.observeAll(),
        error,
    ) { settings, history, err ->
        PerformanceUiState(settings = settings, history = history.reversed(), error = err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceUiState())

    /** Values arrive in the display unit and are converted to cm here (SPEC §3.5). */
    fun save(vertical: Double?, standing: Double?, total: Double?, notes: String?, onSaved: () -> Unit) {
        val unit = uiState.value.settings.lengthUnit
        viewModelScope.launch {
            runCatching {
                repository.save(
                    verticalJumpCm = vertical?.let(unit::toCm),
                    standingReachCm = standing?.let(unit::toCm),
                    totalReachCm = total?.let(unit::toCm),
                    notes = notes,
                )
            }.onSuccess {
                error.value = null
                onSaved()
            }.onFailure { error.value = it.message }
        }
    }

    /** Soft delete with an Undo snackbar: nothing is hard-deleted, and the person gets the row back on Undo. */
    fun delete(row: PerformanceMetricEntity) = viewModelScope.launch {
        repository.delete(row.id)
        messages.post(
            AppMessage(
                text = "Jump test deleted",
                actionLabel = "Undo",
                onAction = { repository.restore(row) },
            ),
        )
    }
}
