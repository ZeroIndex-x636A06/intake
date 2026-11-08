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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zeroindex.intake.domain.model.AllergenSeverity
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ScreenHeading

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") { ScreenHeading("Settings") }

        state.message?.let { message ->
            item(key = "message") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = viewModel::clearMessage) { Text("Dismiss") }
                    }
                }
            }
        }

        item(key = "units") {
            Section("Units") {
                Text(
                    text = "Stored in kilograms and centimetres whatever you pick here, so " +
                        "switching never rewrites your history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                ChipRow(
                    options = WeightUnit.entries.map { it to it.label },
                    selected = settings.weightUnit,
                    onSelect = viewModel::setWeightUnit,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = LengthUnit.entries.map { it to it.label },
                    selected = settings.lengthUnit,
                    onSelect = viewModel::setLengthUnit,
                )
            }
        }

        item(key = "theme") {
            Section("Appearance") {
                ChipRow(
                    options = ThemeMode.entries.map {
                        it to it.name.lowercase().replaceFirstChar(Char::uppercase)
                    },
                    selected = settings.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
                SwitchRow(
                    label = "Use wallpaper colours",
                    checked = settings.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
            }
        }

        item(key = "keys") {
            Section("API keys") {
                Text(
                    text = "Yours, entered here, stored encrypted on this device. The app works " +
                        "without them - you lose remote search and photo estimates, nothing else.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.keys.storeAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This device's keystore is unavailable, so keys cannot be saved. " +
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
                KeyField(
                    label = "Anthropic (photo estimates)",
                    hasKey = state.keys.hasAnthropicKey,
                    onSave = viewModel::setAnthropicKey,
                    onTest = null,
                    testResult = KeyTestResult.Untested,
                )
            }
        }

        item(key = "allergens") {
            Section("Allergens") {
                AllergenEditor(
                    avoided = state.avoidedAllergens,
                    severity = state.allergenSeverity,
                    onChange = viewModel::setAvoidedAllergens,
                    onAddAlias = viewModel::addAlias,
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
                    text = "With this on, the target comes from measured maintenance and your " +
                        "rate goal, recalculated nightly - never from a formula.",
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
                NumberSetting(
                    label = "Protein g per kg of goal weight (1.6 to 2.2)",
                    value = settings.proteinGramsPerKg,
                    onSet = { it?.let(viewModel::setProteinPerKg) },
                )
                NumberSetting(
                    label = "Fat g per kg (0.6 minimum)",
                    value = settings.fatGramsPerKg,
                    onSet = { it?.let(viewModel::setFatPerKg) },
                )

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::recalculateTargetFromTdee,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Recalculate from my data") }

                ManualTargetRow(
                    latestWeightKg = state.latestWeightKg ?: settings.goalWeightKg,
                    onSet = viewModel::setManualTarget,
                )
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
                )
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
            }
        }

        item(key = "health") {
            Section("Health Connect") {
                SwitchRow(
                    label = "Add active calories to my target",
                    checked = settings.addActiveCaloriesToTarget,
                    onChange = viewModel::setAddActiveCalories,
                )
                Text(
                    text = "Off by default on purpose: wearable burn estimates are unreliable, " +
                        "and double-counting them is the most common way a cut quietly stalls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = if (hasKey) "A key is saved." else "No key saved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Paste key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onSave(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
            ) { Text("Save") }
            if (hasKey) {
                OutlinedButton(onClick = { onSave(null) }) { Text("Clear") }
            }
            if (onTest != null && hasKey) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = testResult !is KeyTestResult.Testing,
                ) { Text("Test") }
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

@Composable
private fun AllergenEditor(
    avoided: Set<String>,
    severity: AllergenSeverity,
    onChange: (Set<String>, AllergenSeverity) -> Unit,
    onAddAlias: (String, String) -> Unit,
) {
    val common = listOf("dairy", "gluten", "nuts", "peanuts", "soy", "egg", "shellfish", "fish")
    var aliasAllergen by remember { mutableStateOf("") }
    var aliasTerm by remember { mutableStateOf("") }

    Column {
        Text(
            text = "Every food entering the app is checked against these, from any path.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        common.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { name ->
                    FilterChip(
                        selected = name in avoided,
                        onClick = {
                            val next = if (name in avoided) avoided - name else avoided + name
                            onChange(next, severity)
                        },
                        label = { Text(name) },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        ChipRow(
            options = AllergenSeverity.entries.map {
                it to it.name.lowercase().replaceFirstChar(Char::uppercase)
            },
            selected = severity,
            onSelect = { onChange(avoided, it) },
        )

        Spacer(Modifier.height(12.dp))
        Text("Add a term the engine should catch", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = aliasAllergen,
                onValueChange = { aliasAllergen = it },
                label = { Text("Allergen") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = aliasTerm,
                onValueChange = { aliasTerm = it },
                label = { Text("Term") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = {
                onAddAlias(aliasAllergen, aliasTerm)
                aliasAllergen = ""
                aliasTerm = ""
            },
            enabled = aliasAllergen.isNotBlank() && aliasTerm.isNotBlank(),
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("Add term") }
    }
}
