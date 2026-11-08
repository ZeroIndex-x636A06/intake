package com.zeroindex.intake

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.ui.IntakeApp
import com.zeroindex.intake.ui.theme.IntakeTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The only Activity in the app (SPEC §1: single-Activity, Navigation Compose
 * for routing, no Fragments and no XML layouts).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            IntakeTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                IntakeApp()
            }
        }
    }
}
