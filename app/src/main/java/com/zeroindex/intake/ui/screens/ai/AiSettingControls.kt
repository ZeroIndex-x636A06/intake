package com.zeroindex.intake.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.theme.IntakeShape

/** The id suffix an inventory row uses for a task or a feature: its label with everything but letters and digits dropped. */
fun aiIdSuffix(label: String): String = label.filter { it.isLetterOrDigit() }

/**
 * One AI control of Settings > AI (optional), by inventory id. The Settings group page calls this for every AI id,
 * in inventory order. State comes from [AiSettingsViewModel]; nothing here is a use screen.
 */
@Composable
fun AiSettingControl(id: String, modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val benchmark by viewModel.benchmarkState.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<TaskRow?>(null) }
    val settings = state.settings
    Column(modifier) {
        when {
            id == "aiEnabled" -> AiMasterCard(enabled = settings.aiEnabled, onToggle = viewModel::setAiEnabled)
            id == "aiBudget" -> if (settings.aiEnabled) AiBudgetCard(meter = state.meter, onSetCap = viewModel::setBudget)
            id == "aiPrivacy" -> IntakeCard {
                Eyebrow("Privacy for AI requests")
                Spacer(Modifier.height(6.dp))
                AiPrivacy.entries.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.RadioButton) { viewModel.setPrivacy(p) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.aiPrivacy == p, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(p.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            id == "aiAllowRetaining" -> IntakeCard {
                SettingRow(
                    title = "Suggest free models",
                    subtitle = "Free models are often paid for with your data. Off keeps them out of suggestions.",
                    showDivider = false,
                ) { Switch(checked = settings.aiAllowRetainingModels, onCheckedChange = viewModel::setAllowRetaining) }
            }
            id == "dailyInsight" -> IntakeCard {
                SettingRow(
                    title = "Daily insight",
                    subtitle = "One suggestion a day on Today. Sends a summary of the last two weeks of food, training and body data to the chosen model.",
                    showDivider = false,
                ) { Switch(checked = settings.dailyInsightEnabled, onCheckedChange = viewModel::setDailyInsight, enabled = settings.aiEnabled) }
            }
            id == "coachAiReword" -> IntakeCard {
                SettingRow(
                    title = "Reword my check-in",
                    subtitle = "Rewrites the weekly check-in's wording. Only the suggestion texts go out; the numbers and actions never change.",
                    showDivider = false,
                ) { Switch(checked = settings.coachAiReword, onCheckedChange = viewModel::setCoachReword, enabled = settings.aiEnabled) }
            }
            id == "menuAutoRefresh" -> IntakeCard {
                SettingRow(
                    title = "Refresh saved menus weekly",
                    subtitle = "Re-reads your saved menu sources once a week. Pages with structured menu data need no AI; only the rest use the model.",
                    showDivider = false,
                ) { Switch(checked = settings.menuAutoRefresh, onCheckedChange = viewModel::setMenuAutoRefresh, enabled = settings.aiEnabled) }
            }
            id.startsWith("aiModel.") -> {
                val row = state.rows.firstOrNull { aiIdSuffix(it.task.label) == id.removePrefix("aiModel.") }
                if (row != null) {
                    IntakeCard { ModelRow(row, onChange = { picking = row }) }
                }
            }
            id.startsWith("aiUses.") -> {
                val feature = AiFeature.entries.firstOrNull { it.inEstimate && aiIdSuffix(it.label) == id.removePrefix("aiUses.") }
                if (feature != null) {
                    val uses = state.costRows.firstOrNull { it.cost.feature == feature }?.usesPerDay ?: feature.defaultUsesPerDay
                    UsesRow(feature, uses) { text -> viewModel.setUsesPerDay(feature, text) }
                }
            }
            id == "aiReset" -> {
                AiUsageMeterCard(meter = state.meter, onReset = viewModel::resetCounters)
                Spacer(Modifier.height(8.dp))
                AiCostCard(state, onUsesChange = viewModel::setUsesPerDay)
            }
            id == "aiBench" -> {
                BenchmarkRunCard(
                    hasKey = state.hasKey && settings.aiEnabled,
                    state = benchmark,
                    onToggleTask = viewModel::toggleBenchmarkTask,
                    onCapChange = viewModel::setBenchmarkCap,
                    onStart = viewModel::startBenchmark,
                    onCancel = viewModel::cancelBenchmark,
                    onDismiss = viewModel::dismissBenchmarkResult,
                )
                Spacer(Modifier.height(8.dp))
                BenchmarkTable(state)
            }
        }
    }
    picking?.let { row ->
        ModelPickerSheet(
            row = row,
            onPick = { choice ->
                viewModel.pickModel(row.task, choice)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun UsesRow(feature: AiFeature, value: Double, onChange: (String) -> Unit) {
    var text by remember(feature, value) { mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)) }
    Column {
        SettingRow(title = "Uses a day: ${feature.label}", subtitle = "Only for the cost estimate. It changes nothing else.") {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = IntakeShape.Field,
                modifier = Modifier.width(110.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The OpenRouter key field with Save, Test and Remove (Settings > API keys). */
@Composable
fun OpenRouterKeyControl(modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier) { KeyCard(state, onSave = viewModel::saveKey, onTest = viewModel::testKey) }
}

/** Refresh of the model list, shown under the model pickers. */
@Composable
fun AiModelListRefresh(modifier: Modifier = Modifier, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OutlinedButton(
        onClick = { viewModel.refreshCatalog() },
        enabled = !state.catalog.refreshing,
        shape = IntakeShape.Button,
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) { Text(if (state.catalog.refreshing) "Refreshing…" else "Refresh model list and prices") }
}
