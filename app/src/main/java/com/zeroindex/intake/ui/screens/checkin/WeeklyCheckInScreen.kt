package com.zeroindex.intake.ui.screens.checkin

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.SuggestionStatus
import com.zeroindex.intake.domain.coach.WeekSummary
import com.zeroindex.intake.domain.report.WeekLabel
import com.zeroindex.intake.domain.report.WeeklyReport
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.ConfidenceLine
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SummaryTile
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.screens.coach.CoachCheckInViewModel
import com.zeroindex.intake.ui.screens.report.WeeklyReportViewModel
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one weekly check-in (pushed from Progress): the week's summary with its range chips, the coach's suggestions
 * each with Apply / Dismiss, past check-ins, and Export / Print PDF for the chosen week. It replaces the separate
 * weekly report and check-in screens. Its settings (check-in day, notification, AI reword) are in Settings.
 */
@Composable
fun WeeklyCheckInScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: CoachCheckInViewModel = hiltViewModel(),
    report: WeeklyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(report::saveTo)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "weeks") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                (5 downTo 0).map { report.thisWeek.minusWeeks(it.toLong()) }.forEach { week ->
                    ChoiceChip(label = WeekLabel.forWeek(week), selected = week == reportState.weekStart, onClick = { report.jumpTo(week) })
                }
            }
        }
        state.message?.let { msg ->
            item(key = "message") {
                SandNote(msg)
                TextButton(onClick = viewModel::clearMessage) { Text("OK") }
            }
        }
        item(key = "summary") {
            IntakeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Week of ${WeekLabel.forWeek(reportState.weekStart)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    InfoTip(
                        text = "Weight uses your smoothed trend, not a single scale reading, so water and salt do not move it. Averages count only days with food logged.",
                        label = "Your week",
                    )
                }
                val r = reportState.report
                if (r == null) LoadingState(listOf("Adding up the week")) else Glance(r, reportState.weightUnit)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { save.launch("intake-week-${reportState.weekStart}.pdf") },
                        enabled = reportState.file != null,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Export PDF") }
                    OutlinedButton(
                        onClick = { reportState.file?.let { print(context, it) } },
                        enabled = reportState.file != null,
                        shape = IntakeShape.Button,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Print") }
                }
                reportState.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        if (reportState.isCurrentWeek) state.summary?.let { s -> item(key = "coach-summary") { WeekCard(s, state.weightUnit) } }

        item(key = "suggestions-head") { SectionLine("Suggestions", note = "You decide. Nothing changes until you tap Apply.") }
        val latest = state.latest
        if (latest == null) {
            item(key = "none") {
                IntakeCard {
                    Text("No check-in yet", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Log food and weigh in for a few days and your first check-in appears on its day. You can also run one now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = viewModel::checkInNow, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Check in now") }
                }
            }
        } else {
            item(key = "latest-head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check-in of ${dayLabel(latest.record.day)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusPill(
                        if (latest.rows.isEmpty()) "All on track" else "${latest.rows.count { it.status == SuggestionStatus.PENDING }} to review",
                        tone = if (latest.rows.any { it.status == SuggestionStatus.PENDING }) PillTone.Wait else PillTone.Ok,
                    )
                }
            }
            if (latest.rows.isEmpty()) {
                item(key = "on-track") { SandNote("Nothing to change this week. Your numbers are where they should be, so the best move is to keep going.") }
            }
            items(latest.rows, key = { it.id }) { row ->
                SuggestionCard(row, enabled = !state.working, onApply = { viewModel.apply(row) }, onDismiss = { viewModel.dismiss(row) })
            }
            item(key = "now") {
                TextButton(onClick = viewModel::checkInNow, enabled = !state.working, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Check in now") }
            }
        }
        if (state.past.isNotEmpty()) {
            item(key = "past") {
                MoreDetail(title = "Past check-ins (${state.past.size})") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        state.past.forEach { past ->
                            IntakeCard {
                                Text(dayLabel(past.record.day), style = MaterialTheme.typography.titleSmall)
                                if (past.rows.isEmpty()) {
                                    Text("All on track", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                past.rows.forEach { row ->
                                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.aiTitle ?: row.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        StatusPill(
                                            when (row.status) {
                                                SuggestionStatus.APPLIED -> "Applied"
                                                SuggestionStatus.DISMISSED -> "Dismissed"
                                                SuggestionStatus.PENDING -> "Not decided"
                                            },
                                            tone = if (row.status == SuggestionStatus.APPLIED) PillTone.Ok else PillTone.Wait,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "disclaimer") {
            Text(
                "This is not medical advice. If something feels wrong, rest and talk to a doctor.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekCard(s: WeekSummary, unit: WeightUnit) {
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            InfoTip(
                text = "Weight uses your smoothed trend, not a single scale reading, so water and salt do not move it. " +
                    "Averages count only days with food logged.",
                label = "Your week",
            )
        }
        val change = s.trendChangeKg?.let {
            val v = unit.fromKg(it)
            (if (v >= 0) "+" else "−") + "%.1f".format(Locale.US, abs(v)) + " ${unit.label}"
        } ?: "not enough weigh-ins"
        val pct = s.trendPctPerWeek?.let { " (${if (it >= 0) "+" else "−"}${"%.1f".format(Locale.US, abs(it))}% a week)" }.orEmpty()
        SummaryLine("Weight trend", change + pct)
        SummaryLine("Calories (average)", s.avgKcal?.let { "%,d kcal".format(Locale.US, it) } ?: "no days logged")
        SummaryLine("Protein (average)", s.avgProteinG?.let { "$it g" } ?: "no days logged")
        SummaryLine("Days logged", "${s.daysLogged} of ${s.daysInWindow}")
        SummaryLine("Weigh-ins", "${s.weighIns}")
        SummaryLine("Workouts", "${s.workouts}")
        SummaryLine("Sleep (average)", s.avgSleepHours?.let { "%.1f h".format(Locale.US, it) } ?: "no data")
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SuggestionCard(
    row: CoachSuggestionEntity,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    val action = runCatching { kotlinx.serialization.json.Json.decodeFromString(CoachAction.serializer(), row.actionJson) }.getOrNull()
    val reworded = row.aiTitle != null || row.aiWhy != null
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(row.kind.label, modifier = Modifier.weight(1f))
            if (row.status != SuggestionStatus.PENDING) {
                StatusPill(if (row.status == SuggestionStatus.APPLIED) "Applied" else "Dismissed", tone = PillTone.Wait)
            }
        }
        Text(row.aiTitle ?: row.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(row.aiWhy ?: row.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && action !is CoachAction.Acknowledge) {
            Spacer(Modifier.height(4.dp))
            Text("Apply does: ${CoachCheckInViewModel.describe(action)}", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (open) "Hide the why" else "Why this?")
        }
        if (open) {
            row.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (reworded) {
                SandNote("Reworded with AI. The numbers and the action are the rule's own.\nOriginal: ${row.title}. ${row.why}")
            }
        }
        if (row.status == SuggestionStatus.PENDING) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text(if (action == null || action is CoachAction.Acknowledge) "Got it" else "Apply")
                }
                OutlinedButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun Glance(r: WeeklyReport, unit: WeightUnit) {
    fun n(v: Double?) = v?.let { "%,d".format(it.roundToInt()) }
    val weight = r.average { it.weightKg }?.let { "%.1f %s".format(unit.fromKg(it), unit.label) }
    val change = r.weightChangeKg?.let { "%+.1f %s".format(unit.fromKg(it), unit.label) }
    val sleep = r.average { it.sleepMinutes }?.let { "${it.toInt() / 60}h ${it.toInt() % 60}m" }
    val tiles = listOf(
        (weight ?: "—") to ("Avg weight" + (change?.let { " · $it" } ?: "")),
        (n(r.average { it.kcal })?.let { "$it kcal" } ?: "—") to "Avg intake",
        (n(r.average { it.burned })?.let { "$it kcal" } ?: "—") to "Avg burn",
        (sleep ?: "—") to "Avg sleep",
        (n(r.average { it.restingHr })?.let { "$it bpm" } ?: "—") to "Resting HR",
        (n(r.average { it.steps })) .let { it ?: "—" } to "Avg steps",
    )
    tiles.chunked(2).forEach { pair ->
        Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            pair.forEach { (value, caption) -> SummaryTile(value, caption.uppercase(), Modifier.weight(1f)) }
        }
    }
    val target = r.proteinTarget
    val logged = r.days.filter { it.protein != null }
    if (target != null && logged.isNotEmpty()) {
        val hit = logged.count { (it.protein ?: 0.0) >= target }
        ConfidenceLine(
            "Protein target hit $hit of ${r.days.size} days · ${(hit * 100.0 / r.days.size).roundToInt()}%",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (r.daysLogged == 0) {
        EmptyState(body = "Nothing logged this week. The PDF still prints whatever body and training data exists.")
    }
}

private fun dayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

/** Hands the finished file to the system print dialog as-is. */
private fun print(context: Context, file: File) {
    val manager = context.getSystemService(PrintManager::class.java) ?: return
    manager.print(
        file.nameWithoutExtension,
        object : PrintDocumentAdapter() {
            override fun onLayout(
                old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal?,
                callback: LayoutResultCallback, extras: Bundle?,
            ) {
                if (cancel?.isCanceled == true) return callback.onLayoutCancelled()
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(file.name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                    true,
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                runCatching {
                    file.inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(destination).use { input.copyTo(it) }
                    }
                }.onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                    .onFailure { callback.onWriteFailed(it.message) }
            }
        },
        PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.NA_LETTER).build(),
    )
}
