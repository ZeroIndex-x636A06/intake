package com.zeroindex.intake

import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.zeroindex.intake.data.health.HealthSync
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TemplateLogResult
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.domain.nfc.TemplateLink
import com.zeroindex.intake.ui.AppMessage
import com.zeroindex.intake.ui.AppMessages
import com.zeroindex.intake.ui.IntakeApp
import com.zeroindex.intake.ui.adaptive.LocalWindowSizeClass
import com.zeroindex.intake.ui.theme.IntakeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * The only Activity in the app (SPEC §1: single-Activity, Navigation Compose
 * for routing, no Fragments and no XML layouts).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var templates: MealTemplateRepository
    @Inject lateinit var messages: AppMessages
    @Inject lateinit var healthSync: HealthSync

    override fun onResume() {
        super.onResume()
        // SPEC §12: sync on resume (rate-limited inside), never on a timer.
        healthSync.onResume()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // A recreation (rotation) replays the launch intent; only act on it once.
        if (savedInstanceState == null) handleTagIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val windowSize = calculateWindowSizeClass(this)
            IntakeTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                CompositionLocalProvider(LocalWindowSizeClass provides windowSize) {
                    IntakeApp(messages = messages)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTagIntent(intent)
    }

    /**
     * Amendment §2: a tapped meal-prep tag logs its template to the current
     * slot and says so, with Undo. If a food in it now matches the allergen
     * profile, nothing is logged and the message says which food.
     */
    private fun handleTagIntent(intent: Intent?) {
        val action = intent?.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED && action != Intent.ACTION_VIEW) return
        val id = TemplateLink.parse(intent.dataString) ?: return
        lifecycleScope.launch {
            when (val result = templates.log(id)) {
                is TemplateLogResult.Logged -> messages.post(
                    AppMessage(
                        text = "Logged ${result.template.name} to ${result.slot.name.lowercase()}",
                        actionLabel = "Undo",
                        onAction = { templates.undo(result.logs) },
                    ),
                )
                is TemplateLogResult.Blocked -> messages.post(
                    AppMessage("Not logged: ${result.foods.joinToString()} now matches your allergen profile"),
                )
                TemplateLogResult.NotFound -> messages.post(
                    AppMessage("That tag's meal no longer exists. Write the tag again from More → Meal prep tags."),
                )
            }
        }
    }
}
