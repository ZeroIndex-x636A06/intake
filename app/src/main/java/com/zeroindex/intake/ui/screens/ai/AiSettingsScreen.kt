@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.RankedModel
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.Flag
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape
import java.time.Duration

/**
 * Amendment §1: the model picker. Every AI feature is optional and runs on a
 * key the user supplies; this is where that key, the privacy level, and the
 * model for each task are set.
 */
@Composable
fun AiSettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<TaskRow?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("AI tools", "Optional. Logging, training and progress never need any of this.")
        }
        item(key = "key") { KeyCard(state, onSave = viewModel::saveKey, onTest = viewModel::testKey) }
        item(key = "privacy") {
            IntakeCard {
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
                        RadioButton(selected = state.settings.aiPrivacy == p, onClick = null)
                        Spacer(Modifier.padding(4.dp))
                        Column {
                            Text(p.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                p.explanation,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                SettingRow(
                    title = "Suggest free models",
                    subtitle = "Free models are often paid for with your data. Off keeps them out of suggestions.",
                    showDivider = false,
                ) {
                    Switch(checked = state.settings.aiAllowRetainingModels, onCheckedChange = viewModel::setAllowRetaining)
                }
            }
        }

        item(key = "tasks-head") {
            SectionLine(
                title = "Model per task",
                note = state.catalog.fetchedAt?.let { "list updated ${ago(it)}" }
                    ?: if (state.catalog.refreshing) "loading list…" else null,
            )
        }
        state.catalog.error?.let { error ->
            item(key = "catalog-error") {
                AccentNote(
                    lead = "Model list unavailable",
                    body = "$error Choices below use the last list saved on this phone.",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        items(state.rows, key = { it.task.name }) { row -> TaskCard(row, onClick = { picking = row }) }
        item(key = "refresh") {
            OutlinedButton(
                onClick = { viewModel.refreshCatalog() },
                enabled = !state.catalog.refreshing,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (state.catalog.refreshing) "Refreshing…" else "Refresh model list and prices") }
        }

        item(key = "insight") {
            IntakeCard {
                SettingRow(
                    title = "Daily insight",
                    subtitle = "One suggestion a day on Today. Sends a summary of the last two weeks of " +
                        "food, training and body data to the chosen model.",
                    showDivider = false,
                ) {
                    Switch(checked = state.settings.dailyInsightEnabled, onCheckedChange = viewModel::setDailyInsight)
                }
            }
        }

        item(key = "bench-head") { SectionLine("Benchmark results") }
        item(key = "bench") { BenchmarkTable(state) }
    }

    picking?.let { row ->
        ModelPickerSheet(
            row = row,
            onPick = { id ->
                viewModel.pickModel(row.task, id)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun KeyCard(state: AiSettingsUiState, onSave: (String?) -> Unit, onTest: () -> Unit) {
    var text by remember { mutableStateOf("") }
    IntakeCard {
        Eyebrow("OpenRouter key · optional")
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (state.hasKey) "A key is saved, encrypted on this phone." else "No key saved. AI features are off.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("sk-or-…", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            shape = IntakeShape.Field,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSave(text); text = "" },
                enabled = text.isNotBlank(),
                shape = IntakeShape.Chip,
            ) { Text("Save") }
            if (state.hasKey) {
                OutlinedButton(onClick = { onSave(null) }, shape = IntakeShape.Chip) { Text("Clear") }
                OutlinedButton(
                    onClick = onTest,
                    enabled = state.keyCheck !is KeyCheck.Checking,
                    shape = IntakeShape.Chip,
                ) { Text("Test connection") }
            }
        }
        when (val check = state.keyCheck) {
            KeyCheck.Idle -> Unit
            KeyCheck.Checking -> Text("Checking…", style = MaterialTheme.typography.bodySmall)
            is KeyCheck.Ok -> Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            is KeyCheck.Failed -> Text(check.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun TaskCard(row: TaskRow, onClick: () -> Unit) {
    val active = row.active
    IntakeCard(modifier = Modifier.clickable(onClickLabel = "Choose the model for ${row.task.label}", onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.task.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = active?.model?.name ?: "No capable model in the list yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            active?.let {
                Text(
                    text = "≈ ${usd(it.estimatedUsdPerUse)} / ${row.task.perUse}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            when {
                row.overrideMissing -> Flag("Your pick is gone from OpenRouter")
                row.isOverride -> Tag("Your choice")
                row.suggestion.basis != null -> Tag("Suggested")
            }
            if (active?.model?.isFree == true) Tag("Free")
            if (active?.model?.mayRetainPrompts == true) Flag("May keep your prompts")
        }
        if (!row.isOverride) {
            row.suggestion.basis?.let {
                Text(
                    it.explanation,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BenchmarkTable(state: AiSettingsUiState) {
    if (state.benchmarks.isEmpty()) {
        AccentNote(
            lead = "No benchmark results yet",
            body = "Suggestions marked \"not benchmarked\" are the cheapest capable models, not tested ones. " +
                "Run the benchmark harness with your key (see the README) and the results replace the guesses.",
            container = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        return
    }
    IntakeCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Eyebrow("Model", modifier = Modifier.weight(1f))
            Eyebrow("Accuracy", modifier = Modifier.weight(0.4f))
            Eyebrow("JSON", modifier = Modifier.weight(0.3f))
        }
        state.benchmarks.groupBy { it.task }.forEach { (task, scores) ->
            Text(task.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            scores.sortedByDescending { it.accuracy }.forEach { s ->
                Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.modelId, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("${(s.accuracy * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.4f))
                    Text("${(s.jsonValidity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.3f))
                }
            }
        }
    }
}

@Composable
private fun ModelPickerSheet(row: TaskRow, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val shown = row.suggestion.candidates.filter {
        query.isBlank() || it.model.id.contains(query, true) || it.model.name.contains(query, true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(row.task.label, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${row.suggestion.candidates.size} models can do this. Cheapest first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = IntakeShape.Field,
                    modifier = Modifier.weight(1f),
                )
                if (row.isOverride) {
                    Button(onClick = { onPick(null) }, shape = IntakeShape.Chip) { Text("Use suggested") }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(shown, key = { it.model.id }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        task = row.task,
                        isSuggested = candidate.model.id == row.suggestion.suggested?.model?.id,
                        isActive = candidate.model.id == row.active?.model?.id,
                        onClick = { onPick(candidate.model.id) },
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@Composable
private fun CandidateRow(candidate: RankedModel, task: AiTask, isSuggested: Boolean, isActive: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = isActive, onClick = null)
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(candidate.model.name, style = MaterialTheme.typography.titleSmall)
                Text(candidate.model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("≈ ${usd(candidate.estimatedUsdPerUse)} / ${task.perUse}", style = MaterialTheme.typography.labelMedium)
        }
        FlowRow(
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isSuggested) Tag("Suggested")
            if (candidate.model.isFree) Tag("Free")
            if (candidate.model.mayRetainPrompts) Flag("May keep your prompts")
            candidate.benchmark?.let { Tag("${(it.accuracy * 100).toInt()}% in benchmark") }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Money at the precision a per-request price needs: "$0.0004", "$0.03". */
internal fun usd(value: Double): String = when {
    value == 0.0 -> "free"
    value < 0.001 -> "\$%.4f".format(value)
    value < 0.1 -> "\$%.3f".format(value)
    else -> "\$%.2f".format(value)
}

private fun ago(millis: Long): String {
    val d = Duration.ofMillis((System.currentTimeMillis() - millis).coerceAtLeast(0))
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toHours() < 1 -> "${d.toMinutes()} min ago"
        d.toDays() < 1 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} d ago"
    }
}
