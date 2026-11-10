package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.domain.training.NextTimeChange
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.RecordLine
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.SessionSummary
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.EmptyState
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.MuscleMapView
import com.zeroindex.intake.ui.components.Tag
import com.zeroindex.intake.ui.theme.IntakeShape

/**
 * The post-workout summary (the `a-summary` frame): new records, the four totals, muscles worked against this week's
 * targets with the body map, the exercise list, what changes next time, and (for an empty workout) "Keep this workout?".
 */
@Composable
fun WorkoutSummaryScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var keepName by remember { mutableStateOf("Workout " + java.time.format.DateTimeFormatter.ofPattern("d MMM").format(java.time.LocalDate.now())) }

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.summary == null -> Box(Modifier.fillMaxWidth().weight(1f).padding(16.dp)) {
                EmptyState(
                    title = "This workout is not here any more",
                    body = "It may have been discarded. Your other sessions are in Train > Recent sessions.",
                )
            }
            else -> {
                val summary = state.summary!!
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item(key = "head") {
                        Column {
                            Text(state.title, style = MaterialTheme.typography.headlineSmall)
                            val started = state.session?.startedAt
                            if (started != null) {
                                Text(
                                    java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm")
                                        .format(java.time.Instant.ofEpochMilli(started).atZone(java.time.ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (summary.records.isNotEmpty()) {
                        item(key = "records") { RecordsCard(summary.records, state.unit) }
                    }
                    item(key = "stats") { StatsRow(summary, state.unit) }
                    if (summary.regionSets.isNotEmpty()) {
                        item(key = "muscles") { MusclesCard(summary, state.week, state.hint) }
                    }
                    item(key = "exercises-head") { Text("Exercises", style = MaterialTheme.typography.titleLarge) }
                    item(key = "exercises") {
                        if (state.exercises.isEmpty()) {
                            EmptyState(body = "No working sets were logged in this workout.")
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = IntakeShape.Card,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    state.exercises.forEachIndexed { i, e ->
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(e.name, style = MaterialTheme.typography.titleMedium)
                                                Text(e.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (e.records.isNotEmpty()) Tag("PR")
                                        }
                                        if (i != state.exercises.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                    if (state.nextTime.isNotEmpty()) {
                        item(key = "next") { NextTimeCard(state.nextTime, state.unit) }
                    }
                    if (state.canKeep) {
                        item(key = "keep") {
                            IntakeCard {
                                Text("Keep this workout?", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Save it to repeat it: as a workout you can start any time, or as a day of your program. Optional.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                                )
                                OutlinedTextField(
                                    value = keepName,
                                    onValueChange = { keepName = it },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { viewModel.keepAsWorkout(keepName.trim()) },
                                        enabled = keepName.isNotBlank(),
                                        shape = IntakeShape.Button,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text("Save as workout") }
                                    OutlinedButton(
                                        onClick = { viewModel.keepAsProgramDay(keepName.trim()) },
                                        enabled = keepName.isNotBlank(),
                                        shape = IntakeShape.Button,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text("Add to program") }
                                }
                            }
                        }
                    }
                    state.kept?.let { message ->
                        item(key = "kept") {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = IntakeShape.Banner, modifier = Modifier.fillMaxWidth()) {
                                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                    item(key = "notes") {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::setNotes,
                            label = { Text("Notes (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = { viewModel.saveNotes(onDone) },
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp),
                ) { Text("Done") }
            }
        }
    }
}

@Composable
private fun RecordsCard(records: List<RecordLine>, unit: WeightUnit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = IntakeShape.Card, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(34.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(TrainText.recordsTitle(records.size), style = MaterialTheme.typography.titleLarge)
                records.forEach { r ->
                    Text(recordText(r, unit), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** "Bench press: estimated 1-rep max 104 lb, up 4 lb" and "Overhead press: 5 reps at 95 lb (was 4)". */
internal fun recordText(r: RecordLine, unit: WeightUnit): String = when (r.kind) {
    PrKind.ESTIMATED_MAX -> {
        val now = fmt(unit.fromKg(r.value))
        val up = r.previous?.let { ", up ${fmt(unit.fromKg(r.value - it))} ${unit.label}" }.orEmpty()
        "${r.exerciseName}: estimated 1-rep max $now ${unit.label}$up"
    }
    PrKind.REP_RECORD -> {
        val was = r.previous?.let { " (was ${it.toInt()})" }.orEmpty()
        "${r.exerciseName}: ${r.value.toInt()} reps at ${fmt(unit.fromKg(r.weightKg))} ${unit.label}$was"
    }
    PrKind.NONE -> r.exerciseName
}

@Composable
private fun StatsRow(summary: SessionSummary, unit: WeightUnit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTileBox(Modifier.weight(1f), "${summary.durationMin}", "minutes")
        StatTileBox(Modifier.weight(1f), TrainText.thousands(unit.fromKg(summary.volumeKg)), "${unit.label} volume")
        StatTileBox(Modifier.weight(1f), "${summary.workingSets}", "sets")
        StatTileBox(Modifier.weight(1f), "${summary.records.size}", "PRs")
    }
}

@Composable
private fun StatTileBox(modifier: Modifier, value: String, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.InnerCard,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.semantics { contentDescription = "$value $label" },
    ) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Muscles worked: the body map lit by this session, and this week's sets per region with today's share highlighted. */
@Composable
private fun MusclesCard(summary: SessionSummary, week: List<RegionVolume>, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Muscles worked", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("sets this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IntakeCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MuscleMapView(highlight = summary.highlight, detail = false, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            val worked = week.filter { w -> (summary.regionSets[w.region] ?: 0.0) > 0.0 }.ifEmpty { week.filter { it.sets > 0.0 } }
            worked.forEach { v ->
                val today = summary.regionSets[v.region] ?: 0.0
                RegionBar(v, today)
                Spacer(Modifier.height(8.dp))
            }
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** A thin bar: this week's sets against the target, with the part this session added in the stronger colour. */
@Composable
private fun RegionBar(v: RegionVolume, today: Double) {
    val full = maxOf(v.target.toDouble(), v.sets, 1.0)
    val total = (v.sets / full).toFloat().coerceIn(0f, 1f)
    val share = (today.coerceAtMost(v.sets) / full).toFloat().coerceIn(0f, 1f)
    val text = "${v.region.label}: ${WeeklyVolume.formatSets(v.sets)} of ${v.target} sets this week, ${WeeklyVolume.formatSets(today)} today, ${WeeklyVolume.words(v.status)}"
    Row(Modifier.fillMaxWidth().semantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        Text(v.region.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(92.dp))
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(total).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)))
            Box(Modifier.fillMaxHeight().fillMaxWidth(share).background(MaterialTheme.colorScheme.primary))
        }
        Text(
            "${WeeklyVolume.formatSets(v.sets)} / ${v.target}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(76.dp),
        )
    }
}

@Composable
private fun NextTimeCard(changes: List<NextTimeChange>, unit: WeightUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next time", style = MaterialTheme.typography.titleLarge)
        IntakeCard {
            changes.forEachIndexed { i, c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        "${c.exerciseName}: ${fmt(unit.fromKg(c.fromKg))} → ${fmt(unit.fromKg(c.toKg))} ${unit.label} × ${c.reps}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(c.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (i != changes.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
