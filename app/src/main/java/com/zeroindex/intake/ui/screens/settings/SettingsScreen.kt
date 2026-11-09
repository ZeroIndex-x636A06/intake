package com.zeroindex.intake.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.settings.ThemeMode
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionCard
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.motion.pressScale
import com.zeroindex.intake.ui.onboarding.HelpFlowViewModel
import com.zeroindex.intake.ui.theme.IntakeShape

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenAi: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    section: String? = null,
    /** Opens Food database settings: Open Food Facts search, contributing, credits. */
    onOpenFoodData: (() -> Unit)? = null,
    /** My allergens replaced the old Allergens section here; this is its link row. */
    onOpenMyAllergens: (() -> Unit)? = null,
    /** Replay the Today tour, then land on Today. */
    onGoToToday: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    helpFlow: HelpFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    // Profile & goals and Allergens open this screen at their own section. The items above a
    // section are the heading plus the two optional notes, so the position is worked out here.
    val listState = rememberLazyListState()
    val lastExportAt = settings.lastExportAtMillis
    val showExportPrompt = lastExportAt == null || System.currentTimeMillis() - lastExportAt > 30L * 86_400_000L
    val leadingItems = 1 + (if (state.message != null) 1 else 0) + (if (showExportPrompt) 1 else 0)
    LaunchedEffect(section) {
        // Items after the leading ones: Look and units (0), Targets (1).
        if (section == "targets") listState.scrollToItem(leadingItems + 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") {
            SubHead("Settings", "Everything here stays on this phone.")
        }

        state.message?.let { message ->
            item(key = "message") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = IntakeShape.Banner,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = viewModel::clearMessage,
                            shape = IntakeShape.Chip,
                        ) { Text("Dismiss") }
                    }
                }
            }
        }

        // SPEC §13: a prompt when nothing has been exported or backed up in 30 days.
        if (showExportPrompt) {
            item(key = "export-prompt") {
                SandNote(
                    title = if (lastExportAt == null) "No backup yet" else "Last backup was over 30 days ago",
                    body = "Everything lives only on this phone. Export a file or set up a nightly backup to your own server.",
                )
                OutlinedButton(
                    onClick = onOpenBackup,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                ) { Text("Backup & export →") }
            }
        }

        // The mockup puts units and theme in segmented controls: with two or
        // three options each, showing them all is one tap and no guessing.
        item(key = "preferences") {
            Section("Look and units") {
                SettingRow(title = "Weight", subtitle = "Display unit") {
                    Segmented(
                        options = WeightUnit.entries.map { it.label },
                        selectedIndex = WeightUnit.entries.indexOf(settings.weightUnit),
                        onSelect = { viewModel.setWeightUnit(WeightUnit.entries[it]) },
                    )
                }
                SettingRow(title = "Length", subtitle = "Display unit") {
                    Segmented(
                        options = LengthUnit.entries.map { it.label },
                        selectedIndex = LengthUnit.entries.indexOf(settings.lengthUnit),
                        onSelect = { viewModel.setLengthUnit(LengthUnit.entries[it]) },
                    )
                }
                SettingRow(title = "Theme", subtitle = "App appearance") {
                    Segmented(
                        options = ThemeMode.entries.map {
                            it.name.lowercase().replaceFirstChar(Char::uppercase)
                        },
                        selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                    )
                }
                SwitchRow(
                    label = "Reduce motion",
                    subtitle = "Turns off sweeps, count-ups and bursts. Also on when your phone has animations off.",
                    checked = settings.reduceMotion,
                    onChange = viewModel::setReduceMotion,
                )
                SwitchRow(
                    label = "Use wallpaper colours",
                    subtitle = "Overrides the designed palette",
                    checked = settings.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                    showDivider = false,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Weights are stored in kilograms and lengths in centimetres whatever " +
                        "you pick here, so switching never rewrites your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "targets") {
            Section("Targets") {
                SwitchRow(
                    label = "Work the target out from my own data",
                    checked = settings.autoCalculateTargets,
                    onChange = viewModel::setAutoTargets,
                )
                Text(
                    text = "With this on, the target comes from what you actually eat and weigh, " +
                        "recalculated nightly - never from a formula.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                val unit = settings.weightUnit
                NumberSetting(
                    label = "Goal weight (${unit.label})",
                    value = settings.goalWeightKg?.let { unit.fromKg(it) },
                    onSet = { viewModel.setGoalWeightKg(it?.let(unit::toKg)) },
                )
                NumberSetting(
                    label = "Rate (${unit.label} per week, negative to lose)",
                    value = unit.fromKg(settings.goalRateKgPerWeek),
                    allowNegative = true,
                    onSet = { viewModel.setGoalRate(unit.toKg(it ?: 0.0)) },
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::recalculateTargetFromTdee,
                    modifier = Modifier.fillMaxWidth().height(50.dp).pressScale(),
                ) { Text("Recalculate from my data") }

                MoreDetail(title = "Fine-tune protein, fat and calories") {
                    NumberSetting(
                        label = "Protein (g per kg of goal weight, 1.6 to 2.2)",
                        value = settings.proteinGramsPerKg,
                        onSet = { it?.let(viewModel::setProteinPerKg) },
                    )
                    NumberSetting(
                        label = "Fat (g per kg, 0.6 minimum)",
                        value = settings.fatGramsPerKg,
                        onSet = { it?.let(viewModel::setFatPerKg) },
                    )
                    ManualTargetRow(
                        latestWeightKg = state.latestWeightKg ?: settings.goalWeightKg,
                        onSet = viewModel::setManualTarget,
                    )
                }
            }
        }

        item(key = "training") {
            Section("Training") {
                NumberSetting(
                    label = "Default rest (seconds)",
                    value = settings.defaultRestSec.toDouble(),
                    onSet = { it?.let { v -> viewModel.setRestSeconds(v.toInt()) } },
                )
                SwitchRow(
                    label = "Vibrate when rest ends",
                    checked = settings.restTimerVibrate,
                    onChange = viewModel::setRestVibrate,
                    showDivider = false,
                )
            }
        }

        item(key = "water-weather") {
            Section("Water") { WeatherSettingsSection() }
        }

        // My allergens replaced the old editor that lived here; this row is the way in.
        if (onOpenMyAllergens != null) {
            item(key = "allergens") {
                Section("Allergens") {
                    Text(
                        "Choose what to avoid, add your own allergens and keywords. Every food is checked against them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenMyAllergens,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                    ) { Text("My allergens →") }
                }
            }
        }

        if (onOpenFoodData != null) {
            item(key = "food-data") {
                Section("Food database") {
                    Text(
                        "Offline USDA foods, Open Food Facts search and contributing, and data credits.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenFoodData,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                    ) { Text("Food database & credits →") }
                }
            }
        }

        item(key = "help") {
            Section("Help") {
                Text(
                    "A quick look around Today, one step at a time. Nothing is changed or deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { helpFlow.replayTour(then = onGoToToday) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp),
                ) { Text("Replay the tour") }
            }
        }

        // Rarely used, so folded away: keys, AI, wearable calories, barbell maths.
        item(key = "advanced") {
            Section("Advanced") {
                MoreDetail(title = "Show advanced settings") {
                    val unit = settings.weightUnit
                    NumberSetting(
                        label = "Bar weight (${unit.label})",
                        value = unit.fromKg(settings.barWeightKg),
                        onSet = { it?.let { v -> viewModel.setBarWeightKg(unit.toKg(v)) } },
                    )
                    TextSetting(
                        label = "Plates available per side (kg, comma separated)",
                        value = settings.availablePlatesKg,
                        onSet = viewModel::setPlates,
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        label = "Add active calories to my target",
                        checked = settings.addActiveCaloriesToTarget,
                        onChange = viewModel::setAddActiveCalories,
                        showDivider = false,
                    )
                    Text(
                        text = "Off by default on purpose: wearable burn estimates are unreliable, " +
                            "and double-counting them is the most common way a cut quietly stalls.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Eyebrow("API keys")
                    Text(
                        text = "Optional. Entered here, stored encrypted on this device. The app works " +
                            "without them: you only lose remote search and photo estimates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!state.keys.storeAvailable) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "This phone's keystore is unavailable, so keys cannot be saved. " +
                                "Everything else still works.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    KeyField(
                        label = "USDA FoodData Central",
                        hasKey = state.keys.hasUsdaKey,
                        onSave = viewModel::setUsdaKey,
                        onTest = viewModel::testUsdaKey,
                        testResult = state.usdaTest,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onOpenAi,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("AI tools and models (OpenRouter) →") }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SectionCard(title = title) { content() }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    showDivider: Boolean = true,
) {
    SettingRow(title = label, subtitle = subtitle, showDivider = showDivider) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberSetting(
    label: String,
    value: Double?,
    allowNegative: Boolean = false,
    onSet: (Double?) -> Unit,
) {
    var text by remember(value) {
        mutableStateOf(value?.let { UnitConverter.round(it, 2).toString() } ?: "")
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { next ->
                text = next.filter { it.isDigit() || it == '.' || (allowNegative && it == '-') }
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onSet(text.toDoubleOrNull()) }) { Text("Set") }
    }
}

@Composable
private fun TextSetting(label: String, value: String, onSet: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onSet(text) }) { Text("Set") }
    }
}

@Composable
private fun ManualTargetRow(latestWeightKg: Double?, onSet: (Int, Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { next -> text = next.filter(Char::isDigit) },
            label = { Text("Or set calories by hand") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = {
                val kcal = text.toIntOrNull() ?: return@OutlinedButton
                onSet(kcal, latestWeightKg ?: 80.0)
            },
            enabled = text.toIntOrNull()?.let { it > 0 } == true && latestWeightKg != null,
        ) { Text("Set") }
    }
    if (latestWeightKg == null) {
        Text(
            text = "A manual target needs a goal weight or one weigh-in, to split the macros.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeyField(
    label: String,
    hasKey: Boolean,
    onSave: (String?) -> Unit,
    onTest: (() -> Unit)?,
    testResult: KeyTestResult,
) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("$label · optional")
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hasKey) "A key is saved on this device." else "No key saved.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Paste key", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = IntakeShape.Field,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onSave(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                shape = IntakeShape.Chip,
            ) { Text("Save") }
            if (hasKey) {
                OutlinedButton(
                    onClick = { onSave(null) },
                    shape = IntakeShape.Chip,
                ) { Text("Clear") }
            }
            if (onTest != null && hasKey) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = testResult !is KeyTestResult.Testing,
                    shape = IntakeShape.Chip,
                ) { Text("Test connection") }
            }
        }
        when (testResult) {
            KeyTestResult.Untested -> Unit
            KeyTestResult.Testing -> Text(
                "Testing...",
                style = MaterialTheme.typography.bodySmall,
            )
            is KeyTestResult.Ok -> Text(
                text = testResult.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            is KeyTestResult.Failed -> Text(
                text = testResult.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
