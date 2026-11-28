package com.zeroindex.intake.ui.quicklog

import android.content.Context
import android.nfc.NfcAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One one-tap water size: a saved preset from Settings, shown in the user's volume unit. */
data class WaterSize(val presetId: String, val volumeMl: Double, val label: String)

data class QuickLogUiState(
    val context: QuickLogContext = QuickLogContext(),
    val waterSizes: List<WaterSize> = emptyList(),
)

/**
 * Feeds the Quick log sheet: which tiles are on, the saved water sizes, and the one-tap water log (with Undo, through the same
 * [HydrationRepository.log] Today's water card uses).
 */
@HiltViewModel
class QuickLogViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    settings: SettingsRepository,
    keys: ApiKeyStore,
    private val hydration: HydrationRepository,
    private val messages: AppMessages,
    private val clock: Clock,
) : ViewModel() {

    private val nfcAvailable: Boolean = runCatching { NfcAdapter.getDefaultAdapter(appContext) != null }.getOrDefault(false)

    val state: StateFlow<QuickLogUiState> = combine(
        settings.settings,
        keys.state,
        hydration.observePresets(),
    ) { s, k, presets ->
        QuickLogUiState(
            context = QuickLogContext(
                jumpTestsOn = s.jumpTests,
                // The photo estimate reads the picture with an AI model: it needs the master switch and the user's own key.
                aiReady = s.aiEnabled && k.hasOpenRouterKey,
                nfcAvailable = nfcAvailable,
            ),
            waterSizes = presets
                .filter { it.drinkType == DrinkType.WATER }
                .take(QuickLogPolicy.MAX_WATER_PILLS)
                .map { WaterSize(it.id, it.volumeMl, s.volumeUnit.format(it.volumeMl)) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuickLogUiState())

    /** Logs the size as water now. The snackbar's Undo takes it (and nothing else) back out. */
    fun logWater(size: WaterSize) {
        viewModelScope.launch {
            val log = hydration.log(size.volumeMl, clock.nowMillis(), presetId = size.presetId, drink = DrinkType.WATER)
            messages.post(AppMessage("Added ${size.label} of water", "Undo") { hydration.delete(log.id) })
        }
    }
}
