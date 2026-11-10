package com.zeroindex.intake.ui.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.profile.BaselineSnapshot
import com.zeroindex.intake.domain.goals.ActivityLevel
import com.zeroindex.intake.domain.goals.BaselineReadiness
import com.zeroindex.intake.domain.goals.BuildType
import com.zeroindex.intake.domain.goals.DietBreakPlan
import com.zeroindex.intake.domain.goals.Pace
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.goals.PhasePlanner
import com.zeroindex.intake.domain.goals.ProfileMode
import com.zeroindex.intake.domain.goals.Sex
import com.zeroindex.intake.domain.goals.UserProfile
import com.zeroindex.intake.domain.training.generator.EquipmentPreset
import com.zeroindex.intake.domain.training.generator.Experience
import com.zeroindex.intake.domain.training.generator.TrainingGoal
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.UnitConverter
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.PillTone
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.components.StatusPill
import com.zeroindex.intake.ui.components.SubHead
import com.zeroindex.intake.ui.components.SwitchRow
import java.util.Locale
import kotlin.math.roundToInt

private val EQUIPMENT_CHOICES = listOf(
    EquipmentPreset.FULL_GYM,
    EquipmentPreset.HOME_DUMBBELLS,
    EquipmentPreset.BODYWEIGHT_ONLY,
)
private val WEEKDAY_NAMES = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val PACE_PHASES = setOf(Phase.CUT, Phase.LEAN_BULK, Phase.BULK, Phase.RECOMP)

/** Whole numbers without ".0", everything else to one decimal. */
internal fun num(value: Double): String {
    val rounded = UnitConverter.round(value, 1)
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else String.format(Locale.US, "%.1f", rounded)
}

/**
 * More → Profile & goals. Every choice saves as it is made. The screen is short by default
 * (Simple) and grows only when the person flips to Advanced.
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onBuildPlan: () -> Unit,
    onOpenTargetSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val settings = state.settings
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startBaseline() {
        viewModel.startBaseline()
        // Android 13+: ask now so the "plan is ready" note can arrive. Denied is fine; the Today card still says Ready.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "head") {
            SubHead("Profile & goals", "What your plan is built from. It stays on this phone.")
        }
        // ---------------------------------------------------------------- Basics
        item(key = "basics") {
            IntakeCard {
                Eyebrow("About you")
                Spacer(Modifier.height(8.dp))
                CommitField(
                    label = "Name (optional)",
                    saved = settings.displayName,
                    keyboardType = KeyboardType.Text,
                    placeholder = "Shown on You and the avatar",
                    onCommit = { viewModel.setDisplayName(it) },
                )
                Text("Sex (for the energy formulas)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                ChipGroup(Sex.entries, profile.sex, { it.label }, { s -> viewModel.update { it.copy(sex = s) } })
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommitField(
                        label = "Birth year",
                        saved = profile.birthYear?.toString() ?: "",
                        keyboardType = KeyboardType.Number,
                        placeholder = "1990",
                        modifier = Modifier.weight(1f),
                        onCommit = { text ->
                            val year = text.trim().toIntOrNull()
                            val now = viewModel.currentYear
                            if (year != null && year in (now - 100)..(now - 10)) viewModel.update { it.copy(birthYear = year) }
                        },
                    )
                    val lengthUnit = settings.lengthUnit
                    CommitField(
                        label = "Height (${lengthUnit.label})",
                        saved = profile.heightCm?.let { num(lengthUnit.fromCm(it)) } ?: "",
                        placeholder = if (lengthUnit == LengthUnit.INCHES) "70" else "178",
                        modifier = Modifier.weight(1f),
                        onCommit = { text ->
                            val cm = text.trim().toDoubleOrNull()?.let(lengthUnit::toCm)
                            if (cm != null && cm in 100.0..250.0) viewModel.update { it.copy(heightCm = cm) }
                        },
                    )
                }
                WeightRow(
                    weightKg = state.latestWeightKg,
                    unit = settings.weightUnit,
                    onLog = { kg -> viewModel.logWeighIn(kg, profile.bodyFatPercent ?: state.latestBodyFat) },
                )
                CommitField(
                    label = "Body fat % (optional)",
                    saved = profile.bodyFatPercent?.let(::num) ?: "",
                    placeholder = "Leave empty if unsure",
                    supporting = "With this, calories at rest use your lean mass (Katch-McArdle) and protein follows lean mass.",
                    onCommit = { text ->
                        val v = text.trim().toDoubleOrNull()
                        when {
                            text.isBlank() -> viewModel.update { it.copy(bodyFatPercent = null) }
                            v != null && v in 3.0..60.0 -> viewModel.update { it.copy(bodyFatPercent = v) }
                        }
                    },
                )
            }
        }

        // ---------------------------------------------------------------- Build
        item(key = "build") {
            Column {
                SectionLine(title = "Your build", trailing = {
                    com.zeroindex.intake.ui.components.InfoTip(GoalGlossary.BUILD, label = "Build")
                })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildType.entries.take(2).forEach { b ->
                        OptionTile(b.label, b.blurb, profile.build == b, { viewModel.update { it.copy(build = b) } },
                            Modifier.weight(1f), secondary = b.classicName)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildType.entries.drop(2).forEach { b ->
                        OptionTile(b.label, b.blurb, profile.build == b, { viewModel.update { it.copy(build = b) } },
                            Modifier.weight(1f), secondary = b.classicName)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    profile.build.startingTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.phase != profile.build.suggestedPhase) {
                    TextButton(onClick = { viewModel.update { it.copy(phase = it.build.suggestedPhase) } }) {
                        Text("Start with ${profile.build.suggestedPhase.label.lowercase()}?")
                    }
                }
            }
        }

        // ---------------------------------------------------------------- Phase
        item(key = "phase") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLine(title = "Your goal")
                val phases = Phase.entries.toList()
                phases.forEach { p ->
                    OptionTile(
                        title = p.label,
                        description = p.blurb,
                        selected = profile.phase == p,
                        onClick = { viewModel.update { it.copy(phase = p) } },
                        modifier = Modifier.fillMaxWidth(),
                        secondary = if (p.advanced) "Advanced" else null,
                    )
                }
            }
        }
        item(key = "goal-numbers") {
            IntakeCard {
                val wu = settings.weightUnit
                CommitField(
                    label = "Goal weight (${wu.label})",
                    saved = settings.goalWeightKg?.let { num(wu.fromKg(it)) } ?: "",
                    placeholder = "Optional",
                    onCommit = { t ->
                        if (t.isBlank()) viewModel.setGoalWeight(null)
                        else t.toDoubleOrNull()?.takeIf { it > 0 }?.let { viewModel.setGoalWeight(wu.toKg(it)) }
                    },
                )
                CommitField(
                    label = "Rate (${wu.label} per week, negative to lose)",
                    saved = if (settings.goalRateKgPerWeek == 0.0) "" else num(wu.fromKg(settings.goalRateKgPerWeek)),
                    placeholder = "0",
                    onCommit = { t -> viewModel.setGoalRate(wu.toKg(t.toDoubleOrNull() ?: 0.0)) },
                )
            }
        }
        if (profile.phase in PACE_PHASES) {
            item(key = "pace") {
                IntakeCard {
                    Text("How fast?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Segmented(
                        options = Pace.entries.map { it.label },
                        selectedIndex = profile.pace.ordinal,
                        onSelect = { i -> viewModel.update { it.copy(pace = Pace.entries[i]) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    val preview = state.preview
                    if (preview != null && state.latestWeightKg != null) {
                        val words = PhasePlanner.paceInWords(preview.calories.kgPerWeek, state.latestWeightKg!!, settings.weightUnit)
                        Text(words, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "About ${preview.calories.offsetKcal.let { if (it >= 0) "+$it" else "$it" }} kcal a day compared with your daily calorie burn.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Add your basics and a weigh-in to see this in kilograms or pounds.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        // ---------------------------------------------------------------- Activity
        item(key = "activity") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLine(title = "Day-to-day activity", note = "Outside training")
                ActivityLevel.entries.forEach { a ->
                    OptionTile(a.label, a.blurb, profile.activity == a, { viewModel.update { it.copy(activity = a) } }, Modifier.fillMaxWidth())
                }
                Text(
                    "Once your baseline has steps from Health Connect, they replace this choice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------------- Training
        item(key = "training") {
            IntakeCard {
                Eyebrow("Training you can do")
                Spacer(Modifier.height(8.dp))
                Text("Days a week", style = MaterialTheme.typography.labelMedium)
                ChipGroup((2..6).toList(), profile.trainingDaysPerWeek.coerceIn(2, 6), { "$it" },
                    { d -> viewModel.update { it.copy(trainingDaysPerWeek = d, trainingWeekdays = if (it.trainingWeekdays.size == d) it.trainingWeekdays else emptySet()) } }, Modifier.padding(top = 6.dp, bottom = 10.dp))
                Text("Which days? (optional)", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Pick the weekdays you train and your program's days are set to them, so Today and the coach follow your week. Leave empty and they are spread out for you.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiChipGroup(
                    options = (1..7).toList(),
                    selected = profile.trainingWeekdays,
                    label = { WEEKDAY_NAMES[it - 1] },
                    onToggle = { d ->
                        viewModel.update {
                            val next = if (d in it.trainingWeekdays) it.trainingWeekdays - d else it.trainingWeekdays + d
                            it.copy(
                                trainingWeekdays = next,
                                trainingDaysPerWeek = if (next.size in 2..6) next.size else it.trainingDaysPerWeek,
                            )
                        }
                    },
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                Text("Minutes per session", style = MaterialTheme.typography.labelMedium)
                ChipGroup(listOf(30, 45, 60, 75, 90), profile.sessionMinutes, { "$it" },
                    { m -> viewModel.update { it.copy(sessionMinutes = m) } }, Modifier.padding(top = 6.dp, bottom = 10.dp))
                Text("Experience", style = MaterialTheme.typography.labelMedium)
                ChipGroup(Experience.entries, profile.experience, { "${it.label} (${it.blurb.lowercase()})" },
                    { e -> viewModel.update { it.copy(experience = e) } }, Modifier.padding(top = 6.dp, bottom = 10.dp))
                Text("Training goal", style = MaterialTheme.typography.labelMedium)
                val goals: List<TrainingGoal?> = listOf(null) + TrainingGoal.entries
                ChipGroup(goals, profile.trainingGoal, { it?.label ?: "Match my goal above" },
                    { g -> viewModel.update { it.copy(trainingGoal = g) } }, Modifier.padding(top = 6.dp, bottom = 10.dp))
                Text("Focus muscles (optional)", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Tap the body or the chips. Chosen muscles get about 25% more weekly sets, early in each session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                com.zeroindex.intake.ui.components.FocusMusclePicker(
                    selected = profile.focusMuscles,
                    onToggle = { m ->
                        viewModel.update { it.copy(focusMuscles = if (m in it.focusMuscles) it.focusMuscles - m else it.focusMuscles + m) }
                    },
                    modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                )
                Text("Anything to go easy on? (optional)", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Lifts that load these areas are left out of a made plan.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MultiChipGroup(
                    options = com.zeroindex.intake.domain.training.generator.BodyArea.entries,
                    selected = profile.avoidAreas,
                    label = { it.label },
                    onToggle = { a ->
                        viewModel.update { it.copy(avoidAreas = if (a in it.avoidAreas) it.avoidAreas - a else it.avoidAreas + a) }
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // ---------------------------------------------------------------- Baseline
        item(key = "baseline") {
            BaselineSection(
                snapshot = state.baseline,
                hasPlanData = state.canBuildPlan,
                onStart = ::startBaseline,
                onStop = viewModel::cancelBaseline,
                onBuild = onBuildPlan,
            )
        }

        // ---------------------------------------------------------------- Build the plan
        item(key = "build-plan") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onBuildPlan,
                    enabled = state.canBuildPlan,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (state.baseline?.progress?.ready == true) "Build my plan from my baseline" else "Build my plan now") }
                if (!state.canBuildPlan) {
                    Text(
                        "Needs your sex, birth year, height and a weigh-in.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.baseline?.progress?.ready != true) {
                    Text(
                        "Without a baseline the plan starts from a formula and says so. You can rebuild it any time.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
            }
        }

        // ---------------------------------------------------------------- Safety and history
        item(key = "safety") {
            SandNote(
                title = "Good to know",
                body = "Intake's plans are for healthy adults. It does not plan for pregnancy, breastfeeding, " +
                    "eating disorders or medical conditions, and it never plans a calorie deficit under 18. " +
                    "If any of these apply, please talk to a doctor first. Everything here is calculated on " +
                    "your phone, with no AI.",
            )
        }
        if (settings.planHistory.isNotEmpty()) {
            item(key = "history") {
                IntakeCard {
                    Eyebrow("Plans you applied")
                    settings.planHistory.take(5).forEach { p ->
                        val date = java.time.Instant.ofEpochMilli(p.appliedAtMillis)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        Text(
                            "$date  ${p.phase.label}: ${p.kcal} kcal, P ${p.proteinG} · C ${p.carbsG} · F ${p.fatG}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightRow(weightKg: Double?, unit: WeightUnit, onLog: (Double) -> Unit) {
    val saved = weightKg?.let { num(unit.fromKg(it)) } ?: ""
    var text by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { text = saved }
    val parsed = text.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let(unit::toKg)
    val changed = parsed != null && (weightKg == null || kotlin.math.abs(parsed - weightKg) > 0.01)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledField(
            label = "Current weight (${unit.label})",
            value = text,
            onValueChange = { text = it },
            keyboardType = KeyboardType.Decimal,
            supporting = if (weightKg == null) "Saved as a weigh-in" else null,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { parsed?.let(onLog) },
            enabled = changed,
            modifier = Modifier.padding(bottom = if (weightKg == null) 22.dp else 0.dp).height(52.dp),
        ) { Text("Log it") }
    }
}

@Composable
private fun BaselineSection(
    snapshot: BaselineSnapshot?,
    hasPlanData: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBuild: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLine(title = "Baseline week")
        if (snapshot == null) {
            AccentNote(
                lead = "Let Intake learn your real numbers",
                body = "Log food and weigh in as normal for 7 days. Then your plan is built from what you " +
                    "actually eat and how your weight moves, not from a formula. We will tell you when it is ready.",
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start my baseline week") }
        } else {
            BaselineProgressCard(snapshot, onBuild = onBuild, canBuild = hasPlanData)
            TextButton(onClick = onStop) { Text("Stop the baseline") }
        }
    }
}

/** Shared by Profile and Today so both say the same things. */
@Composable
internal fun BaselineProgressCard(snapshot: BaselineSnapshot, onBuild: () -> Unit, canBuild: Boolean) {
    val p = snapshot.progress
    IntakeCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.headline, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusPill(if (p.ready) "Ready" else "Collecting", tone = if (p.ready) PillTone.Ok else PillTone.Wait)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Food logged on ${p.loggedDays} of ${BaselineReadiness.MIN_LOGGED_DAYS} needed days · " +
                "${p.weighInDays} of ${BaselineReadiness.MIN_WEIGH_IN_DAYS} needed weigh-ins",
            style = MaterialTheme.typography.bodySmall,
        )
        val extras = listOfNotNull(
            snapshot.averageSteps?.let { "${it.roundToInt()} steps a day" },
            snapshot.averageSleepMinutes?.let { "${(it / 60.0).let { h -> String.format(Locale.US, "%.1f", h) }} h sleep" },
            snapshot.averageRestingHr?.let { "resting heart rate ${it.roundToInt()}" },
            snapshot.workouts.takeIf { it > 0 }?.let { "$it workout${if (it == 1) "" else "s"}" },
        )
        if (extras.isNotEmpty()) {
            Text(
                extras.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        p.missing.forEach { m ->
            Spacer(Modifier.height(6.dp))
            Text("Still needed: ${m.what}", style = MaterialTheme.typography.labelMedium)
            Text(m.why, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BaselineReadiness.extensionExplanation(p)?.let { SandNote(it) }
        if (p.ready && canBuild) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBuild, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Build my plan") }
        }
    }
}
