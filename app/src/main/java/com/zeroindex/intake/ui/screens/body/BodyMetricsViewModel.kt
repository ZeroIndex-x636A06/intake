package com.zeroindex.intake.ui.screens.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.TdeeRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyMetricsUiState(val settings: AppSettings = AppSettings())

@HiltViewModel
class BodyMetricsViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val tdeeRepository: TdeeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<BodyMetricsUiState> = settingsRepository.settings
        .map { BodyMetricsUiState(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BodyMetricsUiState(),
        )

    /**
     * Converts at the edge (SPEC §3.5) and then nudges the TDEE estimate, since
     * a new weigh-in is exactly what changes it.
     */
    fun save(
        displayWeight: Double,
        bodyFatPercent: Double?,
        displayWaist: Double?,
        displayChest: Double?,
        displayArm: Double?,
        displayThigh: Double?,
        displayHip: Double?,
        notes: String?,
        onSaved: () -> Unit,
    ) = viewModelScope.launch {
        val settings = uiState.value.settings
        bodyRepository.save(
            weightKg = settings.weightUnit.toKg(displayWeight),
            bodyFatPercent = bodyFatPercent,
            waistCm = displayWaist?.let { settings.lengthUnit.toCm(it) },
            chestCm = displayChest?.let { settings.lengthUnit.toCm(it) },
            armCm = displayArm?.let { settings.lengthUnit.toCm(it) },
            thighCm = displayThigh?.let { settings.lengthUnit.toCm(it) },
            hipCm = displayHip?.let { settings.lengthUnit.toCm(it) },
            notes = notes,
        )
        tdeeRepository.recalculate()?.let { tdeeRepository.refreshTargetFromTdee(it) }
        onSaved()
    }
}
