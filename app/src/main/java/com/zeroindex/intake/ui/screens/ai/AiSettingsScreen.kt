@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.PrivacyTag
import com.zeroindex.intake.ui.components.StatusPill
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.RankedModel
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.SettingRow
import com.zeroindex.intake.ui.components.SubHead
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
    val benchmark by viewModel.benchmarkState.collectAsStateWithLifecycle()
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
            SubHead("AI settings", "Everything AI is optional. It runs only when you tap something, or on a schedule you switch on.")
        }
        item(key = "master") { AiMasterCard(enabled = state.settings.aiEnabled, onToggle = viewModel::setAiEnabled) }
        if (state.settings.aiEnabled) {
            item(key = "budget") { AiBudgetCard(meter = state.meter, onSetCap = viewModel::setBudget) }
            item(key = "usage") { AiUsageMeterCard(meter = state.meter, onReset = viewModel::resetCounters) }
        }
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
                ErrorState(
                    title = "Model list unavailable",
                    body = "$error Choices below use the last list saved on this phone.",
                )
            }
        }
        item(key = "tasks") {
            IntakeCard {
                state.rows.forEachIndexed { i, row ->
                    ModelRow(row, onChange = { picking = row })
                    if (i != state.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item(key = "refresh") {
            OutlinedButton(
                onClick = { viewModel.refreshCatalog() },
                enabled = !state.catalog.refreshing,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(if (state.catalog.refreshing) "Refreshing…" else "Refresh model list and prices") }
        }

        item(key = "cost-head") { SectionLine("Estimated cost", note = "Not a bill") }
        item(key = "cost") { AiCostCard(state, onUsesChange = viewModel::setUsesPerDay) }

        item(key = "automatic") {
            AutomaticAiCard(
                settings = state.settings,
                onInsight = viewModel::setDailyInsight,
                onReword = viewModel::setCoachReword,
                onMenuRefresh = viewModel::setMenuAutoRefresh,
            )
        }

        item(key = "bench-run-head") { SectionLine("Run benchmark", note = "On your key · you set the cap") }
        item(key = "bench-run") {
            BenchmarkRunCard(
                hasKey = state.hasKey && state.settings.aiEnabled,
                state = benchmark,
                onToggleTask = viewModel::toggleBenchmarkTask,
                onCapChange = viewModel::setBenchmarkCap,
                onStart = viewModel::startBenchmark,
                onCancel = viewModel::cancelBenchmark,
                onDismiss = viewModel::dismissBenchmarkResult,
            )
        }
        item(key = "bench-head") { SectionLine("Benchmark results", note = "Local evaluation set") }
        item(key = "bench") { BenchmarkTable(state) }
        item(key = "key") { KeyCard(state, onSave = viewModel::saveKey, onTest = viewModel::testKey) }
        item(key = "filter-note") {
            Text(
                "Only models able to do the selected task are shown in each picker.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OpenRouter", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            when {
                state.keyCheck is KeyCheck.Ok -> StatusPill("● Connected")
                state.keyCheck is KeyCheck.Failed -> StatusPill("● Not connected", tone = PillTone.Bad)
                state.hasKey -> StatusPill("Key saved · not tested", tone = PillTone.Wait)
                else -> StatusPill("No key · AI off", tone = PillTone.Wait)
            }
        }
        Text(
            text = if (state.hasKey) "Your key is saved, encrypted on this phone, and sent only to OpenRouter." else
                "Optional. Without a key every AI feature stays off and everything else works.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        LabeledField(
            label = if (state.hasKey) "Replace key" else "API key",
            value = text,
            onValueChange = { text = it.trim() },
            placeholder = "sk-or-…",
            secret = true,
        )
        Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSave(text); text = "" },
                enabled = text.isNotBlank(),
                shape = IntakeShape.Chip,
                modifier = Modifier.heightIn(min = 44.dp),
            ) { Text("Save") }
            if (state.hasKey) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = state.keyCheck !is KeyCheck.Checking,
                    shape = IntakeShape.Chip,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) { Text(if (state.keyCheck is KeyCheck.Checking) "Testing…" else "Test connection", fontWeight = FontWeight.ExtraBold) }
                TextButton(onClick = { onSave(null) }) { Text("Remove key") }
            }
        }
        when (val check = state.keyCheck) {
            is KeyCheck.Ok -> Text(check.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            is KeyCheck.Failed -> ErrorState(body = check.message)
            else -> Unit
        }
    }
}

/** `.model-row`: task and per-use cost on the left, the model and a change button on the right. */
@Composable
private fun ModelRow(row: TaskRow, onChange: () -> Unit) {
    val active = row.active
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.task.label, style = MaterialTheme.typography.titleSmall)
            Text(
                active?.let { "≈ ${usd(it.estimatedUsdPerUse)} / ${row.task.perUse}" } ?: "No capable model yet",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.isOverride) {
                row.suggestion.basis?.let {
                    Text(it.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
            Text(
                active?.model?.let(::displayName) ?: "—",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 3.dp)) {
                when {
                    row.overrideMissing -> StatusPill("Your pick is gone", tone = PillTone.Bad)
                    row.isOverride -> StatusPill("Your choice", tone = PillTone.Wait)
                    row.suggestion.basis != null -> StatusPill("Suggested")
                }
                if (active?.model?.mayRetainPrompts == true) PrivacyTag("May keep prompts")
            }
            TextButton(onClick = onChange, modifier = Modifier.semantics { contentDescription = "Change model for ${row.task.label}" }) {
                Text("Change model")
            }
        }
    }
}

/** "OpenAI: GPT-6 Luna" reads as "OpenAI · GPT-6 Luna", matching the mockup. */
internal fun displayName(model: com.zeroindex.intake.domain.ai.ModelInfo): String =
    model.name.replaceFirst(": ", " · ")

private fun requirements(task: AiTask): String = buildList {
    if (task.needsImages) add("read photos")
    if (task.needsTools) add("search the web")
    add("return structured output")
    if (task.minContext >= 100_000) add("read ${task.minContext / 1000}k+ tokens")
}.joinToString(", ", prefix = "Filtered to models that can ", postfix = ".")

@Composable
private fun BenchmarkTable(state: AiSettingsUiState) {
    if (state.benchmarks.isEmpty()) {
        EmptyState(
            title = "No benchmark results yet",
            body = "Suggestions marked \"not benchmarked\" are the cheapest capable models, not tested ones. " +
                "Run the benchmark above with your key and the results replace the guesses.",
        )
        return
    }
    IntakeCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Eyebrow("Task / model", modifier = Modifier.weight(1f))
            listOf("Accuracy", "JSON", "Speed", "Cost").forEach {
                Eyebrow(it, modifier = Modifier.weight(0.42f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        state.benchmarks.sortedWith(compareBy({ it.task.ordinal }, { -it.accuracy })).forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${s.task.label.substringBefore(' ')} · ${s.modelId.substringAfter('/')}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                listOf(
                    "${(s.accuracy * 100).toInt()}%",
                    "${(s.jsonValidity * 100).toInt()}%",
                    s.medianLatencyMs?.let { "%.1f s".format(it / 1000.0) } ?: "—",
                    s.costUsdPerRequest?.let(::usd) ?: "—",
                ).forEach {
                    Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.42f))
                }
            }
        }
        AiTask.entries.filter { task -> state.benchmarks.none { it.task == task } }.forEach { task ->
            Text(
                "${task.label} · not benchmarked",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose for ${row.task.label}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Text(
                "${requirements(row.task)} ${row.suggestion.candidates.size} match, cheapest first.",
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
            Text(
                "Privacy varies by provider. Check the provider's current data-retention terms before sending a prompt.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun CandidateRow(candidate: RankedModel, task: AiTask, isSuggested: Boolean, isActive: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayName(candidate.model), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusPill(if (candidate.model.isFree) "Free" else "Paid", tone = if (candidate.model.isFree) PillTone.Ok else PillTone.Wait)
        }
        Text(
            listOfNotNull(
                "≈ ${usd(candidate.estimatedUsdPerUse)} / ${task.perUse}",
                candidate.benchmark?.let { "${(it.accuracy * 100).toInt()}% in benchmark" },
                candidate.model.id,
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isSuggested) StatusPill("Suggested")
            if (candidate.model.mayRetainPrompts) PrivacyTag("May keep your prompts")
            Spacer(Modifier.weight(1f))
            if (isActive) {
                StatusPill("In use")
            } else {
                OutlinedButton(onClick = onClick, shape = IntakeShape.Chip, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text("Select model")
                }
            }
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
