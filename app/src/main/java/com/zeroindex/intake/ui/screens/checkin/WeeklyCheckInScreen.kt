package com.zeroindex.intake.ui.screens.checkin

import com.zeroindex.intake.domain.time.DateText
import com.zeroindex.intake.domain.units.KcalFormat
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.entity.CoachSuggestionEntity
import com.zeroindex.intake.domain.coach.CheckInPeriod
import com.zeroindex.intake.domain.coach.CoachAction
import com.zeroindex.intake.domain.coach.CoachConfig
import com.zeroindex.intake.domain.coach.SuggestionStatus
import com.zeroindex.intake.domain.report.WeekLabel
import com.zeroindex.intake.domain.report.WeeklyReport
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.AccentButton
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.SoftCard
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.InfoTip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LoadingState
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.domain.coach.CoachEngine
import com.zeroindex.intake.domain.report.formatWeightChange
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.motion.MoreDetail
import com.zeroindex.intake.ui.screens.coach.CoachCheckInViewModel
import com.zeroindex.intake.ui.screens.report.WeeklyReportViewModel
import com.zeroindex.intake.ui.theme.IntakeShape
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    // Q6-4: the suggestions describe the days their check-in looked at, so open on the calendar week that holds most of
    // them (unless the person already picked a week), and the suggestions below say which days they are.
    var pickedWeek by rememberSaveable { mutableStateOf(false) }
    val coveredWeek = state.latest?.record?.day?.let { CheckInPeriod.weekCovering(it) }
    LaunchedEffect(coveredWeek) {
        if (!pickedWeek && coveredWeek != null && coveredWeek != reportState.weekStart &&
            !coveredWeek.isAfter(report.thisWeek) && !coveredWeek.isBefore(report.thisWeek.minusWeeks(5))
        ) {
            report.jumpTo(coveredWeek)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = IntakeSpacing.ScreenGutter,
            end = IntakeSpacing.ScreenGutter,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "weeks") {
            val chipScroll = rememberScrollState()
            // The newest chip is on the right; start there so the selected week is never half off screen.
            LaunchedEffect(chipScroll.maxValue) { chipScroll.scrollTo(chipScroll.maxValue) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(chipScroll).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                (5 downTo 0).map { report.thisWeek.minusWeeks(it.toLong()) }.forEach { week ->
                    ChoiceChip(label = WeekLabel.forWeek(week), selected = week == reportState.weekStart, onClick = { pickedWeek = true; report.jumpTo(week) })
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
            HeroCard {
                Eyebrow("Week of ${WeekLabel.forWeek(reportState.weekStart)}", color = heroMuted())
                Spacer(Modifier.height(14.dp))
                val r = reportState.report
                if (r == null) LoadingState(listOf("Adding up the week")) else Glance(r, reportState.weightUnit)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccentButton(
                        onClick = { save.launch("intake-week-${reportState.weekStart}.pdf") },
                        enabled = reportState.file != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(IntakeIcons.Download, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                    val on = MaterialTheme.intake.onHero
                    Button(
                        onClick = { reportState.file?.let { print(context, it) } },
                        enabled = reportState.file != null,
                        shape = IntakeShape.Button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = on.copy(alpha = 0.12f),
                            contentColor = on,
                            disabledContainerColor = on.copy(alpha = 0.06f),
                            disabledContentColor = on.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = IntakeSpacing.TouchTarget),
                    ) { Text("Print") }
                }
                reportState.message?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = heroMuted(), modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        reportState.report?.let { r -> item(key = "coach-summary") { WeekCard(r, state.weightUnit) } }

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
                    Button(
                        onClick = viewModel::checkInNow,
                        enabled = !state.working,
                        shape = IntakeShape.Button,
                        modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
                    ) { Text("Check in now") }
                }
            }
        } else {
            item(key = "latest-head") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Check-in of ${dayLabel(latest.record.day)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Based on the ${CoachConfig().windowDays} days ${CheckInPeriod.label(latest.record.day)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val noRows = latest.rows.isEmpty()
                    val onTrack = noRows && latest.record.headline == CoachEngine.HEADLINE_ON_TRACK
                    IntakeChip(
                        when {
                            !noRows -> "${latest.rows.count { it.status == SuggestionStatus.PENDING }} to review"
                            onTrack -> "All on track"
                            latest.record.headline == CoachEngine.HEADLINE_NOT_ENOUGH_DATA -> "Not enough data"
                            else -> "No suggestions"
                        },
                        tone = if (!noRows) {
                            if (latest.rows.any { it.status == SuggestionStatus.PENDING }) ChipTone.Warn else ChipTone.Ok
                        } else if (onTrack) ChipTone.Ok else ChipTone.Warn,
                    )
                }
            }
            if (latest.rows.isEmpty()) {
                item(key = "on-track") {
                    SandNote(
                        when (latest.record.headline) {
                            CoachEngine.HEADLINE_ON_TRACK ->
                                "Nothing to change this week. Your numbers are where they should be, so the best move is to keep going."
                            CoachEngine.HEADLINE_NOT_ENOUGH_DATA ->
                                "Not enough logged this week to judge. Log full days to get suggestions."
                            else ->
                                "No new suggestions. That does not mean everything is on track: check the protein and intake figures above, and log full days for a clearer read."
                        },
                    )
                }
            }
            items(latest.rows, key = { it.id }) { row ->
                SuggestionCard(row, state.weightUnit, state.volumeUnit, enabled = !state.working, onApply = { viewModel.apply(row) }, onDismiss = { viewModel.dismiss(row) })
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
                                    Text(if (past.record.headline == CoachEngine.HEADLINE_ON_TRACK) "All on track" else "No suggestions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                past.rows.forEach { row ->
                                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(row.aiTitle ?: row.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                        IntakeChip(
                                            when (row.status) {
                                                SuggestionStatus.APPLIED -> "Applied"
                                                SuggestionStatus.DISMISSED -> "Dismissed"
                                                SuggestionStatus.PENDING -> "Not decided"
                                            },
                                            tone = if (row.status == SuggestionStatus.APPLIED) ChipTone.Ok else ChipTone.Neutral,
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

/** The "Your week" list. Every figure is [WeeklyReport.figures], the same computation the top card and the PDF use. */
@Composable
private fun WeekCard(r: WeeklyReport, unit: WeightUnit) {
    val f = r.figures
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your week", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            InfoTip(
                text = "Weight uses your smoothed trend, not a single scale reading, so water and salt do not move it. " +
                    "Averages count only days with food logged, today included once something is logged.",
                label = "Your week",
            )
        }
        val change = f.weightChangeKg?.let { formatWeightChange(unit.fromKg(it), unit.label) } ?: "not enough weigh-ins"
        SummaryLine("Weight trend", change)
        SummaryLine("Calories (average)", f.avgKcal?.let { "${KcalFormat.format(it)} kcal" } ?: "no days logged")
        SummaryLine("Protein (average)", f.avgProteinG?.let { "$it g" } ?: "no days logged")
        SummaryLine("Days logged", "${f.daysLogged} of ${f.daysInWeek}")
        SummaryLine("Weigh-ins", "${f.weighIns}")
        SummaryLine("Workouts", "${f.workouts}")
        SummaryLine("Sleep (average)", f.avgSleepHours?.let { "%.1f h".format(Locale.US, it) } ?: "no data")
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
    weightUnit: WeightUnit,
    volumeUnit: com.zeroindex.intake.domain.units.VolumeUnit,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    var open by rememberSaveable(row.id) { mutableStateOf(false) }
    // The rules write their numbers in ml and kg; show them in the person's units (QA Q4-5).
    fun u(text: String) = com.zeroindex.intake.domain.units.UnitText.inUnits(text, weightUnit, volumeUnit)
    val action = runCatching { kotlinx.serialization.json.Json.decodeFromString(CoachAction.serializer(), row.actionJson) }.getOrNull()
    val reworded = row.aiTitle != null || row.aiWhy != null
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(row.kind.label, modifier = Modifier.weight(1f))
            if (row.status != SuggestionStatus.PENDING) {
                IntakeChip(
                    if (row.status == SuggestionStatus.APPLIED) "Applied" else "Dismissed",
                    tone = if (row.status == SuggestionStatus.APPLIED) ChipTone.Ok else ChipTone.Neutral,
                )
            }
        }
        Text(u(row.aiTitle ?: row.title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(u(row.aiWhy ?: row.why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        if (action != null && action !is CoachAction.Acknowledge) {
            Spacer(Modifier.height(4.dp))
            Text("Apply does: ${CoachCheckInViewModel.describe(action)}", style = MaterialTheme.typography.labelMedium)
        }
        GhostButton(onClick = { open = !open }) {
            Text(if (open) "Hide the why" else "Why this?")
        }
        if (open) {
            row.details?.let {
                SoftCard(contentPadding = PaddingValues(IntakeSpacing.CardPadCompact)) {
                    Text(u(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (reworded) {
                SandNote("Reworded with AI. The numbers and the action are the rule's own.\nOriginal: ${u(row.title)}. ${u(row.why)}")
            }
        }
        if (row.status == SuggestionStatus.PENDING) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApply,
                    enabled = enabled,
                    shape = IntakeShape.Button,
                    modifier = Modifier.weight(1f).heightIn(min = IntakeSpacing.TouchTarget),
                ) {
                    Text(if (action == null || action is CoachAction.Acknowledge) "Got it" else "Apply")
                }
                SoftButton(onClick = onDismiss, enabled = enabled, modifier = Modifier.weight(1f)) {
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
    val change = r.weightChangeKg?.let { formatWeightChange(unit.fromKg(it), unit.label) }
    val sleep = r.average { it.sleepMinutes }?.let { "${it.toInt() / 60}h ${it.toInt() % 60}m" }
    val f = r.figures
    val tiles = listOf(
        (weight ?: "—") to ("Avg weight" + (change?.let { " · $it" } ?: "")),
        (n(r.average { it.kcal })?.let { "$it kcal" } ?: "—") to ("Avg intake" + r.daysLogged.takeIf { it > 0 }?.let { " · $it day${if (it == 1) "" else "s"} logged" }.orEmpty()),
        (n(r.average { it.burned })?.let { "$it kcal" } ?: "—") to "Avg burn",
        (sleep ?: "—") to "Avg sleep",
        (n(r.average { it.restingHr })?.let { "$it bpm" } ?: "—") to "Resting HR",
        (n(r.average { it.steps })) .let { it ?: "—" } to "Avg steps",
        "${f.daysLogged}/${f.daysInWeek}" to "Days logged",
        "${f.weighIns}" to "Weigh-ins",
        "${f.workouts}" to "Workouts",
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (value, caption) ->
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.headlineSmall.merge(TextStyle(fontSize = 20.sp, lineHeight = 24.sp)))
                        Text(caption, style = MaterialTheme.typography.bodySmall, color = heroMuted())
                    }
                }
            }
        }
    }
    r.proteinHit()?.let { Text(it.label, style = MaterialTheme.typography.labelSmall, color = heroMuted(), modifier = Modifier.padding(top = 12.dp)) }
    if (r.intakeLooksUnderLogged()) {
        Text(
            "Logged days average far below your target. That usually means meals were not all logged, so it is not a real low intake.",
            style = MaterialTheme.typography.labelSmall,
            color = heroMuted(),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (r.daysLogged == 0) {
        Text(
            "Nothing logged this week. The PDF still prints whatever body and training data exists.",
            style = MaterialTheme.typography.bodySmall,
            color = heroMuted(),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun dayLabel(epochDay: Long): String =
    DateText.mediumWithWeekday(LocalDate.ofEpochDay(epochDay))

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
