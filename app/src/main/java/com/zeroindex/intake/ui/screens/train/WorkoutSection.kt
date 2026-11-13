@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.db.projection.SessionStat
import com.zeroindex.intake.domain.training.MuscleVolume
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.muscles.MuscleRegion
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.domain.training.VolumeStatus
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.RegionBodyMap
import com.zeroindex.intake.ui.components.volumeColor
import com.zeroindex.intake.ui.onboarding.TabTip
import com.zeroindex.intake.ui.screens.hub.HubItem
import com.zeroindex.intake.ui.screens.hub.HubList
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.domain.onboarding.TipKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Train > Workout (the `a-train` frame): today's session with one Start, the other ways to start, this week's muscle volume
 * on a body map, and recent sessions. The program list and editor are one tap away in Programs, not here.
 */
@Composable
fun WorkoutSection(
    contentPadding: PaddingValues,
    actions: TrainActions,
    onFromVideo: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var programSheet by remember { mutableStateOf(false) }
    var workoutSheet by remember { mutableStateOf(false) }
    var openRegion by remember { mutableStateOf<BodyRegion?>(null) }
    var openMuscle by remember { mutableStateOf<MuscleRegion?>(null) }
    val running = state.running

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "tip") {
            TabTip(
                key = TipKey.TRAIN,
                body = "Start today's workout, or train without a plan. Programs, where you build or change one, is one tap away.",
                actionLabel = "Make me a program",
                onAction = actions.onOpenGenerator,
            )
        }

        item(key = "today") {
            when {
                running != null -> RunningCard(running, onResume = { actions.onOpenSession(running.sessionId) })
                else -> TodayCard(
                    card = state.today,
                    busy = state.busy,
                    onStart = { dayId -> viewModel.start(dayId, actions.onOpenSession) },
                    onOpenPrograms = actions.onOpenPrograms,
                )
            }
        }

        // One session at a time: while one runs, the only start is resuming it.
        if (running == null) {
            item(key = "other-head") { Eyebrow("Other ways to start", color = MaterialTheme.colorScheme.primary) }
            item(key = "other") {
                val items = buildList {
                    add(HubItem("Empty workout", "Add exercises as you go", Icons.Filled.Add, { viewModel.start(null, actions.onOpenSession) }))
                    add(
                        HubItem(
                            "From a program",
                            state.activeProgram?.let { "${it.name}. Pick any day." } ?: "Pick any day of any program",
                            Icons.AutoMirrored.Filled.ListAlt,
                            { programSheet = true },
                        ),
                    )
                    add(
                        HubItem(
                            "My workouts",
                            if (state.workouts.isEmpty()) "Saved routines you can start any time" else "${state.workouts.size} saved. Start any time.",
                            Icons.Filled.FitnessCenter,
                            { workoutSheet = true },
                        ),
                    )
                    add(HubItem("From a video", "Make a workout from a YouTube video", Icons.Filled.VideoLibrary, onFromVideo))
                    state.repeatLast?.let { last ->
                        add(HubItem("Repeat last", last.label, Icons.Filled.Replay, { viewModel.start(last.dayId, actions.onOpenSession) }))
                    }
                }
                HubList(items)
            }
        }

        item(key = "week-head") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("This week", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (state.weekSessions > 0) {
                    Text(
                        "${state.weekSessions} ${if (state.weekSessions == 1) "session" else "sessions"} · ${TrainText.thousands(state.unit.fromKg(state.weekVolumeKg))} ${state.unit.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "week") {
            WeekVolumeCard(
                volumes = state.volume,
                muscles = state.muscles,
                hint = state.hint,
                unit = state.unit,
                selected = openRegion,
                onSelect = { openRegion = if (openRegion == it) null else it },
                selectedMuscle = openMuscle,
                onSelectMuscle = { openMuscle = if (openMuscle == it) null else it },
            )
        }

        item(key = "recent-head") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Recent sessions", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onOpenHistory, modifier = Modifier.heightIn(min = 48.dp)) { Text("All sessions") }
            }
        }
        item(key = "recent") {
            if (state.recent.isEmpty()) {
                Text(
                    "Finished workouts show up here, with their volume and records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = IntakeShape.Card,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.recent.forEachIndexed { i, s ->
                            RecentRow(s, state.unit, onClick = { actions.onOpenSummary(s.sessionId) })
                            if (i != state.recent.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        item(key = "programs") {
            HubList(
                listOf(
                    HubItem(
                        "Programs",
                        "Your plan, My workouts, make or import one",
                        Icons.AutoMirrored.Filled.ListAlt,
                        actions.onOpenPrograms,
                    ),
                ),
            )
        }
    }

    if (programSheet) {
        ProgramPickerSheet(
            load = { viewModel.programChoices() },
            onStart = { dayId -> programSheet = false; viewModel.start(dayId, actions.onOpenSession) },
            onOpenPrograms = { programSheet = false; actions.onOpenPrograms() },
            onDismiss = { programSheet = false },
        )
    }
    if (workoutSheet) {
        WorkoutPickerSheet(
            workouts = state.workouts.filter { it.dayId != null && it.exerciseCount > 0 }.map { Triple(it.name, it.exerciseCount, it.dayId!!) },
            onStart = { dayId -> workoutSheet = false; viewModel.start(dayId, actions.onOpenSession) },
            onOpenPrograms = { workoutSheet = false; actions.onOpenPrograms() },
            onDismiss = { workoutSheet = false },
        )
    }
}

/** A session is running: say so, and one big button to go back into it. */
@Composable
private fun RunningCard(running: RunningWorkout, onResume: () -> Unit) {
    val elapsed by produceState(0L, running.startedAt) {
        while (true) {
            value = System.currentTimeMillis() - running.startedAt
            delay(1_000)
        }
    }
    IntakeCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Eyebrow("Workout in progress", color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(running.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(TrainText.clock(elapsed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onResume, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text("  Resume workout", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The hero: today's day with its exercises and Start; a rest day with "Train anyway"; or "No program yet". */
@Composable
private fun TodayCard(card: TodayCard, busy: Boolean, onStart: (dayId: String?) -> Unit, onOpenPrograms: () -> Unit) {
    var anyway by remember { mutableStateOf(false) }
    IntakeCard(color = MaterialTheme.colorScheme.primaryContainer) {
        val onCard = MaterialTheme.colorScheme.onPrimaryContainer
        when (card) {
            TodayCard.Loading -> {
                Eyebrow("Today", color = onCard)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(onCard.copy(alpha = 0.08f)))
            }
            TodayCard.NoProgram -> {
                Eyebrow("Today", color = onCard)
                Text("No program yet", style = MaterialTheme.typography.headlineMedium, color = onCard)
                Text(
                    "You can still train: start an empty workout and add exercises as you go. Everything is saved to your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onCard,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onStart(null) }, enabled = !busy, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Empty workout", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = onOpenPrograms, modifier = Modifier.heightIn(min = 48.dp)) { Text("Make a program in Programs") }
            }
            is TodayCard.Session -> {
                Eyebrow("Today · ${TrainText.weekAndDay(card.week, card.dayNumber)}", color = onCard)
                Text(card.dayName, style = MaterialTheme.typography.headlineMedium, color = onCard)
                val sub = buildString {
                    if (card.muscles.isNotEmpty()) append(card.muscles)
                    if (card.minutes > 0) append(if (isEmpty()) "" else " · ").append("about ${card.minutes} min")
                }
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = onCard.copy(alpha = 0.85f))
                Spacer(Modifier.height(10.dp))
                if (card.lines.isEmpty()) {
                    Text("No exercises in this day yet. Add some in Programs.", style = MaterialTheme.typography.bodyMedium, color = onCard)
                } else {
                    card.lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyLarge, color = onCard, modifier = Modifier.padding(vertical = 2.dp)) }
                    if (card.moreCount > 0) Text("+ ${card.moreCount} more", style = MaterialTheme.typography.bodyMedium, color = onCard.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { onStart(card.dayId) },
                    enabled = !busy && card.lines.isNotEmpty(),
                    shape = IntakeShape.Button,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("  Start workout", style = MaterialTheme.typography.titleMedium)
                }
            }
            is TodayCard.Rest -> {
                Eyebrow("Today · ${card.programName}", color = onCard)
                Text("Rest day", style = MaterialTheme.typography.headlineMedium, color = onCard)
                Text(card.line, style = MaterialTheme.typography.bodyMedium, color = onCard, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { anyway = !anyway }, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (anyway) "Hide days" else "Train anyway")
                }
                if (anyway) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        card.days.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { d -> ChoiceChip(d.name, selected = false, onClick = { onStart(d.dayId) }, modifier = Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** This week's sets per region against the targets: the body map, a legend in words, and tap-for-contributors rows. */
@Composable
private fun WeekVolumeCard(
    volumes: List<RegionVolume>,
    muscles: List<MuscleVolume>,
    hint: String?,
    unit: WeightUnit,
    selected: BodyRegion?,
    onSelect: (BodyRegion) -> Unit,
    selectedMuscle: MuscleRegion?,
    onSelectMuscle: (MuscleRegion) -> Unit,
) {
    IntakeCard {
        if (volumes.isEmpty()) {
            Text("Loading this week", style = MaterialTheme.typography.bodyMedium)
            return@IntakeCard
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RegionBodyMap(muscles = muscles, selected = selectedMuscle, onRegionTap = onSelectMuscle, modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth())
        }
        val pickedMuscle = muscles.firstOrNull { it.region == selectedMuscle }
        if (pickedMuscle != null) {
            Spacer(Modifier.height(8.dp))
            MuscleContributors(pickedMuscle)
        } else {
            Text(
                "Tap a muscle to see which exercises counted for it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Legend("Under", volumeColor(VolumeStatus.UNDER, true))
            Legend("In range", volumeColor(VolumeStatus.IN_RANGE, true))
            Legend("Over", volumeColor(VolumeStatus.OVER, true))
        }
        hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        volumes.forEachIndexed { i, v ->
            RegionRow(v, expanded = selected == v.region, onClick = { onSelect(v.region) })
            if (i != volumes.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (volumes.all { it.sets == 0.0 }) {
            Text(
                "Nothing logged this week yet. Sets you finish in a workout fill this in. Targets are in Settings > Training.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** What counted for one tapped muscle this week: its sets against its share of the target, and the exercises behind them. */
@Composable
private fun MuscleContributors(v: MuscleVolume) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(volumeColor(v.status, v.sets > 0.0)))
            Text("  ${v.region.label}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                "${v.summary()}, ${WeeklyVolume.words(v.status)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (v.contributors.isEmpty()) {
            Text("Nothing logged for ${v.region.label.lowercase()} this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            v.contributors.forEach { c ->
                Text(
                    "${c.exerciseName}: ${WeeklyVolume.formatSets(c.effectiveSets)} ${if (c.effectiveSets == 1.0) "set" else "sets"} counted (${c.loggedSets} logged)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "A set counts fully for its main muscle and by half for the helpers.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Legend(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RegionRow(v: RegionVolume, expanded: Boolean, onClick: () -> Unit) {
    val full = maxOf(v.target.toDouble(), v.sets, 1.0)
    val progress = (v.sets / full).toFloat().coerceIn(0f, 1f)
    val words = WeeklyVolume.words(v.status)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide what counted for ${v.region.label}" else "Show what counted for ${v.region.label}", onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "${v.region.label}: ${v.summary()}, $words" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.region.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(92.dp))
            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(volumeColor(v.status, v.sets > 0.0)))
            }
            Text(
                "${WeeklyVolume.formatSets(v.sets)} / ${v.target}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(76.dp),
            )
        }
        Text(
            words.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 92.dp, top = 2.dp),
        )
        if (expanded) {
            Column(Modifier.padding(top = 6.dp, start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (v.contributors.isEmpty()) {
                    Text("Nothing logged for ${v.region.label.lowercase()} this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    v.contributors.forEach { c ->
                        Text(
                            "${c.exerciseName}: ${WeeklyVolume.formatSets(c.effectiveSets)} ${if (c.effectiveSets == 1.0) "set" else "sets"} counted (${c.loggedSets} logged)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text("A set counts fully for its main muscle and by half for the helpers.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(s: SessionStat, unit: WeightUnit, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val date = DateTimeFormatter.ofPattern("EEE d MMM").format(Instant.ofEpochMilli(s.startedAt).atZone(zone))
    val minutes = s.finishedAt?.let { ((it - s.startedAt) / 60_000).coerceAtLeast(0) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open the ${s.dayName ?: "workout"} summary", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.dayName ?: "Workout", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                buildString {
                    append(date)
                    if (minutes != null) append(" · ").append(minutes).append(" min")
                    if (s.volumeKg > 0) append(" · ").append(TrainText.thousands(unit.fromKg(s.volumeKg))).append(' ').append(unit.label)
                    append(" · ").append(s.workingSets).append(if (s.workingSets == 1) " set" else " sets")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "From a program": every program with its days, each a Start. */
@Composable
private fun ProgramPickerSheet(
    load: suspend () -> List<ProgramChoice>,
    onStart: (dayId: String) -> Unit,
    onOpenPrograms: () -> Unit,
    onDismiss: () -> Unit,
) {
    var choices by remember { mutableStateOf<List<ProgramChoice>?>(null) }
    LaunchedEffect(Unit) { choices = load() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Start from a program", style = MaterialTheme.typography.headlineSmall)
            val list = choices
            when {
                list == null -> Text("Loading", style = MaterialTheme.typography.bodyMedium)
                list.isEmpty() -> {
                    Text("You have no program with days yet.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenPrograms, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Programs") }
                }
                else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    list.forEach { choice ->
                        item(key = "h-${choice.programName}") {
                            Eyebrow(choice.programName + if (choice.active) " · active" else "", modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                        }
                        items(choice.days.size, key = { "${choice.programName}-${choice.days[it].dayId}" }) { i ->
                            val d = choice.days[i]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable(role = Role.Button, onClickLabel = "Start ${d.name}", onClick = { onStart(d.dayId) }),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(d.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                d.weekday?.let { Text(WorkoutSchedule.weekdayName(it, short = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "My workouts": saved routines, each a Start. Managing them is in Programs. */
@Composable
private fun WorkoutPickerSheet(
    workouts: List<Triple<String, Int, String>>,
    onStart: (dayId: String) -> Unit,
    onOpenPrograms: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("My workouts", style = MaterialTheme.typography.headlineSmall)
            if (workouts.isEmpty()) {
                Text(
                    "Save a routine you repeat, like \"Arm day\" or \"Hotel gym\", and start it any time. Finish an empty workout and choose Save as workout, or make one in Programs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onOpenPrograms, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Programs") }
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(workouts.size, key = { workouts[it].third }) { i ->
                        val (name, count, dayId) = workouts[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .clickable(role = Role.Button, onClickLabel = "Start $name", onClick = { onStart(dayId) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text("$count ${if (count == 1) "exercise" else "exercises"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
