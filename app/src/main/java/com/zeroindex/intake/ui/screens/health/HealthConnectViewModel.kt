package com.zeroindex.intake.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.entity.HealthDailySummaryEntity
import com.zeroindex.intake.data.health.HealthConnectManager
import com.zeroindex.intake.data.health.HealthSyncState
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HealthUiState(
    val sync: HealthSyncState = HealthSyncState(),
    val settings: AppSettings = AppSettings(),
    val today: HealthDailySummaryEntity? = null,
)

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val manager: HealthConnectManager,
    private val settingsRepository: SettingsRepository,
    summaryDao: HealthSummaryDao,
    clock: Clock,
) : ViewModel() {

    val state: StateFlow<HealthUiState> = combine(
        manager.state,
        settingsRepository.settings,
        summaryDao.observeForDay(clock.todayRange().startInclusive),
    ) { sync, settings, today -> HealthUiState(sync, settings, today) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthUiState())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch { manager.refreshPermissions() }
    fun syncNow() = viewModelScope.launch { manager.sync() }
}
