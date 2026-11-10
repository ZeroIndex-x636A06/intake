@file:OptIn(ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.data.ai.benchmark.BenchmarkProgress
import com.zeroindex.intake.data.ai.benchmark.BenchmarkReport
import com.zeroindex.intake.data.ai.benchmark.ModelResult
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSummary
import com.zeroindex.intake.domain.ai.benchmark.ExcludedModel
import com.zeroindex.intake.domain.ai.benchmark.SkippedEntry
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.ErrorState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * Amendment §1: measure the candidate models on the user's own key. Cost is
 * shown and a spend cap is set before anything is sent, and the run can be
 * stopped at any point; the results table above this card fills in as models
 * finish.
 */
@Composable
internal fun BenchmarkRunCard(
    hasKey: Boolean,
    state: BenchmarkUiState,
    onToggleTask: (AiTask) -> Unit,
    onCapChange: (String) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    val running = state.phase is BenchmarkPhase.Running

    IntakeCard {
        Text(
            "Runs a few made-up menus, recipes and daily summaries through each model on your OpenRouter key and " +
                "scores the answers. Nothing from your own data is sent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasKey) {
            Spacer(Modifier.height(8.dp))
            StatusPill("No key · benchmark off", tone = PillTone.Wait)
            Text(
                "Save an OpenRouter key below to run it. The rest of the app does not need one.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@IntakeCard
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            BenchmarkSuite.tasks.forEach { task ->
                FilterChip(
                    selected = task in state.tasks,
                    onClick = { onToggleTask(task) },
                    enabled = !running,
                    label = { Text(task.label) },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
        }
        Text(
            "Photo estimates are not benchmarked: that needs photos with known answers.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        state.plan.forEach { selection ->
            Text(
                "${selection.task.label}: " +
                    selection.models.joinToString { it.id.substringAfter('/') }.ifEmpty {
                        if (state.checkingProviders) "checking providers…" else "no model fits your privacy setting yet"
                    },
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        LeftOut(state.excluded, state.checkingProviders)

        LabeledField(
            label = "Spend cap (USD)",
            value = state.capText,
            onValueChange = onCapChange,
            keyboardType = KeyboardType.Decimal,
            readOnly = running,
            isError = state.capUsd == null,
            supporting = if (state.capUsd == null) "Enter an amount above zero." else "The run stops as soon as spend reaches this.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Estimated cost at most ${usd(state.estimateUsd)}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.capUsd != null && state.estimateUsd > state.capUsd) {
                StatusPill("Above cap · will stop early", tone = PillTone.Wait)
            }
        }

        when (val phase = state.phase) {
            is BenchmarkPhase.Running -> {
                LoadingState(progressLines(phase.progress))
                phase.progress?.note?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                OutlinedButton(
                    onClick = onCancel,
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("Cancel") }
            }
            is BenchmarkPhase.Finished -> {
                ReportSummary(phase.report)
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            is BenchmarkPhase.Cancelled -> {
                EmptyState(body = "Cancelled. Models that had already finished are saved.")
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            is BenchmarkPhase.Failed -> {
                ErrorState(body = phase.message)
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            BenchmarkPhase.Idle -> Unit
        }

        if (!running) {
            Button(
                onClick = { confirming = true },
                enabled = state.canStart,
                shape = IntakeShape.Button,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(top = 9.dp),
            ) { Text("Run benchmark") }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Run the benchmark?") },
            text = {
                Text(
                    "This sends the fixtures to ${state.plan.sumOf { it.models.size }} model runs on your OpenRouter " +
                        "key. Estimated cost at most ${usd(state.estimateUsd)}; it stops at your cap of " +
                        "${state.capUsd?.let(::usd)}.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirming = false; onStart() }) { Text("Run", fontWeight = FontWeight.ExtraBold) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Not now") } },
        )
    }
}

private fun progressLines(progress: BenchmarkProgress?): List<String> =
    if (progress == null) {
        listOf("Starting…")
    } else {
        listOf(
            "${progress.task.label} · ${progress.modelId.substringAfter('/')}",
            "${progress.done} of ${progress.total} fixtures",
            "${usd(progress.spentUsd)} spent",
        )
    }

/** One line: "2 saved · 4 skipped (3 rate limited, 1 timed out)", with the reasons behind a tap. */
@Composable
private fun ReportSummary(report: BenchmarkReport) {
    val saved = report.results.count { it is ModelResult.Saved }
    val skipped = report.results.filterIsInstance<ModelResult.Skipped>().map {
        SkippedEntry(it.task.label, it.modelId, it.reason, it.kind)
    }
    var showWhy by remember { mutableStateOf(false) }
    Column(Modifier.padding(top = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                BenchmarkSummary.headline(saved, skipped),
                tone = if (saved > 0) PillTone.Ok else PillTone.Wait,
            )
        }
        Text(
            "${usd(report.spentUsd)} spent of ${usd(report.capUsd)} cap" + if (report.stoppedByCap) " · stopped at the cap" else "",
            style = MaterialTheme.typography.labelSmall,
        )
        if (skipped.isNotEmpty()) {
            TextButton(onClick = { showWhy = !showWhy }, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(if (showWhy) "Hide why" else "Why were they skipped?")
            }
            if (showWhy) {
                BenchmarkSummary.groups(skipped).forEach { (reason, models) ->
                    Text(
                        "$reason: ${models.joinToString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        report.abortReason?.let { ErrorState(title = "The run ended early", body = it) }
    }
}

/** The models left out of the plan, as one line with the detail behind a tap. */
@Composable
private fun LeftOut(excluded: List<ExcludedModel>, checking: Boolean) {
    if (excluded.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val counts = excluded.groupingBy { it.reason }.eachCount()
    val line = counts.entries.joinToString(", ") { "${it.value} ${it.key.label}" }
    TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 44.dp)) {
        Text(
            (if (open) "Hide" else "Left out") + ": " + line + if (checking) " · still checking" else "",
            style = MaterialTheme.typography.labelSmall,
        )
    }
    if (open) {
        excluded.groupBy { it.reason }.forEach { (reason, rows) ->
            Text(
                "${reason.label.replaceFirstChar { it.uppercase() }}: " +
                    rows.map { it.model.id.substringAfter('/') }.distinct().take(12).joinToString() +
                    if (rows.map { it.model.id }.distinct().size > 12) " and more" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
