package com.zeroindex.intake.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.settings.HydrationPrefs
import com.zeroindex.intake.data.settings.HydrationSettings
import com.zeroindex.intake.domain.hydration.DrinkOverride
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.SweatMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Advanced half of the water sheet: drink values and the measured sweat rate. */
@HiltViewModel
class HydrationExtrasViewModel @Inject constructor(
    private val prefs: HydrationPrefs,
) : ViewModel() {
    val settings: StateFlow<HydrationSettings> = prefs.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HydrationSettings())

    fun setOverride(type: DrinkType, override: DrinkOverride?) =
        viewModelScope.launch { prefs.setDrinkOverride(type, override) }

    fun saveSweat(measurement: SweatMeasurement?, use: Boolean) =
        viewModelScope.launch { prefs.setSweat(measurement, use) }
}
