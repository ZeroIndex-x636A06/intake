@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeroindex.intake.ui.screens.train

import com.zeroindex.intake.domain.time.DateText
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.navigation.LocalSessionBarSpace
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
import com.zeroindex.intake.ui.components.AccentButton
import com.zeroindex.intake.ui.components.AccentCard
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.GhostButton
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.LineButton
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.ListDivider
import com.zeroindex.intake.ui.components.RegionBodyMap
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.components.volumeColor
import com.zeroindex.intake.ui.onboarding.TabTip
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.domain.onboarding.TipKey
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Train > Workout (the `Train` mockup): today's session with one Start, the other ways to start as tiles, this week's
 * coverage per muscle group on the body map, and recent sessions. The program list and editor are one tap away in Programs.
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
    val hasHistory by viewModel.hasHistory.collectAsStateWithLifecycle()
    var programSheet by remember { mutableStateOf(false) }
    var workoutSheet by remember { mutableStateOf(false) }
    var openRegion by remember { mutableStateOf<BodyRegion?>(null) }
    var openMuscle by remember { mutableStateOf<MuscleRegion?>(null) }
    val running = state.running

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "tip") {
            TabTip(
                key = TipKey.TRAIN,
                body = "Start today's workout, or train without a plan. Programs, where you build or change one, is one tap away.",
                actionLabel = "Make me a program",
                onAction = actions.onOpenGenerator,
                hasHistory = hasHistory,
            )
        }

        item(key = "today") {
            when {
                // The session bar above the pill is the way back in while it shows; the big card is for when it does not.
                running != null && LocalSessionBarSpace.current > 0.dp -> Text(
                    "Your workout is running. Tap the bar above the menu to go back into it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            item(key = "other-head") { SectionLine("Other ways to start") }
            item(key = "other") {
                val tiles = buildList<@Composable (Modifier) -> Unit> {
                    add { m -> TrainTile(IntakeIcons.Plus, "Empty workout", "Add exercises as you go", { viewModel.start(null, actions.onOpenSession) }, m) }
                    add { m ->
                        TrainTile(
                            IntakeIcons.CalendarPlain,
                            "From a program",
                            state.activeProgram?.let { "${it.name}. Pick any day." } ?: "Pick any day of any program",
                            { programSheet = true },
                            m,
                        )
                    }
                    add { m ->
                        TrainTile(
                            IntakeIcons.Bookmark,
                            "My workouts",
                            if (state.workouts.isEmpty()) "Saved routines you can start any time" else "${state.workouts.size} saved. Start any time.",
                            { workoutSheet = true },
                            m,
                        )
                    }
                    add { m -> TrainTile(IntakeIcons.Video, "From a video", "Make a workout from a YouTube video", onFromVideo, m) }
                }
                TileGrid(tiles)
            }
            state.repeatLast?.let { last ->
                item(key = "repeat") {
                    LineButton(onClick = { viewModel.start(last.dayId, actions.onOpenSession) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(IntakeIcons.History, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        Spacer(Modifier.width(8.dp))
                        Text("Repeat last · ${last.label}", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "generator") {
                AccentCard(modifier = Modifier.clip(IntakeShape.Card).clickable(role = Role.Button, onClick = actions.onOpenGenerator)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(IntakeIcons.Sliders, contentDescription = null, modifier = Modifier.size(IconSize.Default))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Make me a program", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "A few questions, a plan that fits your days and gear",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }

        item(key = "week-head") {
            SectionLine(
                "This week",
                trailing = {
                    if (state.weekSessions > 0) {
                        Text(
                            "${state.weekSessions} ${if (state.weekSessions == 1) "session" else "sessions"} · ${TrainText.thousands(state.unit.fromKg(state.weekVolumeKg))} ${state.unit.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
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
            SectionLine(
                "Recent sessions",
                trailing = { GhostButton(onClick = actions.onOpenHistory) { Text("All sessions", style = MaterialTheme.typography.titleSmall) } },
            )
        }
        item(key = "recent") {
            if (state.recent.isEmpty()) {
                Text(
                    "Finished workouts show up here, with their volume and records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ListCard {
                    state.recent.forEachIndexed { i, s ->
                        if (i > 0) ListDivider()
                        RecentRow(s, state.unit, onClick = { actions.onOpenSummary(s.sessionId) })
                    }
                }
            }
        }

        item(key = "programs") {
            ListCard {
                TrainRow(
                    title = "Programs",
                    subtitle = "Your plan, My workouts, make or import one",
                    icon = IntakeIcons.Notes,
                    onClick = actions.onOpenPrograms,
                    trailing = { RowChevronIcon() },
                )
            }
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

/** A session is running: say so, and one big button to go back into it. The lime card: the one thing that needs attention. */
@Composable
private fun RunningCard(running: RunningWorkout, onResume: () -> Unit) {
    val elapsed by produceState(0L, running.startedAt) {
        while (true) {
            value = System.currentTimeMillis() - running.startedAt
            delay(1_000)
        }
    }
    AccentCard {
        Eyebrow("Workout in progress", color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(running.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(TrainText.clock(elapsed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onResume, shape = IntakeShape.Button, modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget)) {
            Icon(IntakeIcons.Play, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(8.dp))
            Text("Resume workout", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A secondary button on the dark hero card: translucent `onHero` fill (b.3). */
@Composable
private fun heroButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.intake.onHero.copy(alpha = 0.12f),
    contentColor = MaterialTheme.intake.onHero,
)

/** The hero: today's day with its exercises and Start; a rest day with "Train anyway"; or "No program yet". */
@Composable
private fun TodayCard(card: TodayCard, busy: Boolean, onStart: (dayId: String?) -> Unit, onOpenPrograms: () -> Unit) {
    var anyway by remember { mutableStateOf(false) }
    val onCard = MaterialTheme.intake.onHero
    HeroCard {
        when (card) {
            TodayCard.Loading -> {
                Eyebrow("Today", color = heroMuted())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(96.dp).clip(IntakeShape.Tile).background(onCard.copy(alpha = 0.08f)))
            }
            TodayCard.NoProgram -> {
                Eyebrow("Today", color = heroMuted())
                Text("No program yet", style = MaterialTheme.typography.headlineSmall, color = onCard)
                Text(
                    "You can still train: start an empty workout and add exercises as you go. Everything is saved to your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroMuted(),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(14.dp))
                AccentButton(onClick = { onStart(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(IntakeIcons.Plus, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(8.dp))
                    Text("Empty workout", style = MaterialTheme.typography.labelLarge)
                }
                GhostButton(onClick = onOpenPrograms) { Text("Make a program in Programs", color = onCard) }
            }
            is TodayCard.Session -> {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Today · ${TrainText.weekAndDay(card.week, card.dayNumber)}", color = heroMuted())
                        Text(card.dayName, style = MaterialTheme.typography.headlineSmall, color = onCard)
                        if (card.muscles.isNotEmpty()) Text(card.muscles, style = MaterialTheme.typography.bodyMedium, color = heroMuted())
                    }
                    if (card.minutes > 0) {
                        Spacer(Modifier.width(10.dp))
                        Surface(color = onCard.copy(alpha = 0.12f), contentColor = onCard, shape = IntakeShape.Chip) {
                            Text(
                                "about ${card.minutes} min",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (card.lines.isEmpty()) {
                    Text("No exercises in this day yet. Add some in Programs.", style = MaterialTheme.typography.bodyMedium, color = onCard)
                } else {
                    card.lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyLarge, color = onCard, modifier = Modifier.padding(vertical = 3.dp)) }
                    if (card.moreCount > 0) Text("+ ${card.moreCount} more", style = MaterialTheme.typography.bodyMedium, color = heroMuted(), modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(16.dp))
                AccentButton(
                    onClick = { onStart(card.dayId) },
                    enabled = !busy && card.lines.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(IntakeIcons.Play, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(8.dp))
                    Text("Start workout", style = MaterialTheme.typography.labelLarge)
                }
            }
            is TodayCard.Rest -> {
                Eyebrow("Today · ${card.programName}", color = heroMuted())
                Text("Rest day", style = MaterialTheme.typography.headlineSmall, color = onCard)
                Text(card.line, style = MaterialTheme.typography.bodyMedium, color = heroMuted(), modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { anyway = !anyway },
                    shape = IntakeShape.Button,
                    colors = heroButtonColors(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = IntakeSpacing.TouchTarget),
                ) {
                    Text(if (anyway) "Hide days" else "Train anyway", style = MaterialTheme.typography.labelLarge)
                }
                if (anyway) {
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap)) {
                        card.days.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap)) {
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
        volumes.forEach { v ->
            RegionRow(v, expanded = selected == v.region, onClick = { onSelect(v.region) })
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
            .clip(IntakeShape.Tile)
            .background(MaterialTheme.intake.soft)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(IntakeShape.Heat).background(volumeColor(v.status, v.sets > 0.0)))
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
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(IntakeShape.Heat).background(color))
        Text("  $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One muscle group as a coverage track (`.track`): label and sets, a 10 dp track with an ink fill, and the status as a chip. */
@Composable
private fun RegionRow(v: RegionVolume, expanded: Boolean, onClick: () -> Unit) {
    val full = maxOf(v.target.toDouble(), v.sets, 1.0)
    val progress = (v.sets / full).toFloat().coerceIn(0f, 1f)
    val words = WeeklyVolume.words(v.status)
    val tone = if (v.status == VolumeStatus.IN_RANGE) ChipTone.Ok else ChipTone.Warn
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide what counted for ${v.region.label}" else "Show what counted for ${v.region.label}", onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = "${v.region.label}: ${v.summary()}, $words" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(88.dp)) {
                Text(v.region.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${WeeklyVolume.formatSets(v.sets)} / ${v.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.weight(1f).height(10.dp).clip(IntakeShape.Pill).background(MaterialTheme.intake.soft)) {
                Box(
                    Modifier
                        .padding(2.dp)
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(IntakeShape.Pill)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
            IntakeChip(words.replaceFirstChar { it.uppercase() }, tone = tone)
        }
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
    val date = DateText.mediumWithWeekday(s.startedAt, zone)
    val minutes = s.finishedAt?.let { ((it - s.startedAt) / 60_000).coerceAtLeast(0) }
    TrainRow(
        title = s.dayName ?: "Workout",
        subtitle = buildString {
            append(date)
            if (minutes != null) append(" · ").append(minutes).append(" min")
            if (s.volumeKg > 0) append(" · ").append(TrainText.thousands(unit.fromKg(s.volumeKg))).append(' ').append(unit.label)
            append(" · ").append(s.workingSets).append(if (s.workingSets == 1) " set" else " sets")
        },
        onClick = onClick,
        onClickLabel = "Open the ${s.dayName ?: "workout"} summary",
        trailing = {
            if (s.finishedAt == null) IntakeChip("Unfinished", tone = ChipTone.Warn) else RowChevronIcon()
        },
    )
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
        Column(Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Start from a program", style = MaterialTheme.typography.headlineSmall)
            val list = choices
            when {
                list == null -> Text("Loading", style = MaterialTheme.typography.bodyMedium)
                list.isEmpty() -> {
                    Text("You have no program with days yet.", style = MaterialTheme.typography.bodyMedium)
                    GhostButton(onClick = onOpenPrograms) { Text("Open Programs") }
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
                                Icon(IntakeIcons.Play, contentDescription = null, modifier = Modifier.size(IconSize.Small))
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
        Column(Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("My workouts", style = MaterialTheme.typography.headlineSmall)
            if (workouts.isEmpty()) {
                Text(
                    "Save a routine you repeat, like \"Arm day\" or \"Hotel gym\", and start it any time. Finish an empty workout and choose Save as workout, or make one in Programs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GhostButton(onClick = onOpenPrograms) { Text("Open Programs") }
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
                            Icon(IntakeIcons.Play, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        }
                        ListDivider()
                    }
                }
            }
        }
    }
}
