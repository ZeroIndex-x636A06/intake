package com.zeroindex.intake

import com.zeroindex.intake.ui.components.ChoiceChip
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.zeroindex.intake.domain.template.TemplateLogMessage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.plan.PlannedMealSlot
import java.time.LocalTime
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

    /** A tapped tag waiting for the person's answer; [error] set means there is nothing to log. */
    private data class PendingTag(val id: String, val name: String, val slot: MealSlot, val warn: List<String>, val error: String? = null)

    private var pendingTag by mutableStateOf<PendingTag?>(null)

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
                    pendingTag?.let { TagConfirmDialog(it) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTagIntent(intent)
    }

    @Composable
    private fun TagConfirmDialog(tag: PendingTag) {
        if (tag.error != null) {
            AlertDialog(
                onDismissRequest = { pendingTag = null },
                title = { Text("Meal tag not found") },
                text = { Text(tag.error) },
                confirmButton = { TextButton(onClick = { pendingTag = null }) { Text("OK") } },
            )
            return
        }
        AlertDialog(
            onDismissRequest = { pendingTag = null },
            title = { Text("Log ${tag.name}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick the meal to log it to.")
                    // A FlowRow wraps the chips onto a second line rather than cutting the last one off at the edge.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MealSlot.BREAKFAST, MealSlot.LUNCH, MealSlot.DINNER, MealSlot.SNACK).forEach { slot ->
                            ChoiceChip(TemplateLogMessage.slotLabel(slot), tag.slot == slot, { pendingTag = tag.copy(slot = slot) })
                        }
                    }
                    if (tag.warn.isNotEmpty()) {
                        Text(
                            "Check before you log: ${tag.warn.joinToString()} may contain something you avoid.",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTag = null
                    logTemplate(tag.id, tag.slot)
                }) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { pendingTag = null }) { Text("Cancel") } },
        )
    }

    /**
     * Amendment §2: a tapped meal-prep tag asks first. It names the meal, offers the meal slots (the time of day
     * is only the starting choice) and logs on Log, with Undo. The allergen gate is unchanged: a food that now
     * matches the profile is never logged here, and the message says which. A tag whose meal is gone says so.
     */
    private fun handleTagIntent(intent: Intent?) {
        intent?.getStringExtra(com.zeroindex.intake.work.CoachNotifier.EXTRA_OPEN)?.let {
            messages.requestOpen(it)
            intent.removeExtra(com.zeroindex.intake.work.CoachNotifier.EXTRA_OPEN)
        }
        val action = intent?.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED && action != Intent.ACTION_VIEW) return
        val id = TemplateLink.parse(intent.dataString)
        if (id == null) {
            if (intent.dataString?.startsWith("${TemplateLink.SCHEME}://${TemplateLink.HOST}", ignoreCase = true) != true) return
            pendingTag = PendingTag("", "", MealSlot.LUNCH, emptyList(), "This tag does not hold a meal Intake can read.")
            return
        }
        lifecycleScope.launch {
            val name = templates.nameOf(id)
            if (name == null) {
                pendingTag = PendingTag(id, "", MealSlot.LUNCH, emptyList(), "That tag's meal no longer exists. Write the tag again from Food, Library, Meal templates.")
                return@launch
            }
            val warn = templates.checkOf(id)?.foods.orEmpty()
            pendingTag = PendingTag(id, name, PlannedMealSlot.forTime(LocalTime.now()), warn)
        }
    }

    private fun logTemplate(id: String, slot: MealSlot) {
        lifecycleScope.launch {
            when (val result = templates.log(id, slot)) {
                is TemplateLogResult.Logged -> messages.post(
                    AppMessage(
                        text = TemplateLogMessage.logged(
                            result.template.name, result.slot, result.allergenNote,
                        ),
                        actionLabel = "Undo",
                        onAction = { templates.undo(result.logs) },
                    ),
                )
                is TemplateLogResult.Blocked -> messages.post(
                    AppMessage("Not logged: ${result.foods.joinToString()} now matches your allergen profile"),
                )
                is TemplateLogResult.NeedsReview -> messages.post(
                    AppMessage("Not logged: ${result.foods.joinToString()} may contain something you avoid. Open Meal templates to review and log it."),
                )
                TemplateLogResult.NotFound -> messages.post(
                    AppMessage("That tag's meal no longer exists. Write the tag again from Food, Library, Meal templates."),
                )
            }
        }
    }
}
