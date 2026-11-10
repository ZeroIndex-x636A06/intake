@file:OptIn(ExperimentalLayoutApi::class)

package com.zeroindex.intake.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroindex.intake.data.onboarding.StartChoice
import com.zeroindex.intake.domain.goals.BuildType
import com.zeroindex.intake.domain.goals.Pace
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.goals.PhasePlanner
import com.zeroindex.intake.domain.goals.Sex
import com.zeroindex.intake.domain.onboarding.OnboardingFlow
import com.zeroindex.intake.domain.onboarding.OnboardingStep
import com.zeroindex.intake.domain.onboarding.PendingDestination
import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.components.AccentNote
import com.zeroindex.intake.ui.components.ChoiceChip
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.LabeledField
import com.zeroindex.intake.ui.components.SandNote
import com.zeroindex.intake.ui.motion.LocalReduceMotion
import com.zeroindex.intake.ui.motion.Motion
import com.zeroindex.intake.ui.screens.allergen.MyAllergensViewModel
import com.zeroindex.intake.ui.screens.profile.ChipGroup
import com.zeroindex.intake.ui.screens.profile.CommitField
import com.zeroindex.intake.ui.screens.profile.OptionTile
import com.zeroindex.intake.ui.screens.profile.ProfileViewModel
import com.zeroindex.intake.ui.screens.profile.num
import com.zeroindex.intake.ui.theme.IntakeShape
import kotlin.math.abs
import kotlinx.coroutines.delay

private val PACE_PHASES = setOf(Phase.CUT, Phase.LEAN_BULK, Phase.BULK, Phase.RECOMP)

/**
 * The welcome flow (`Route.Onboarding`): one calm question per screen. Every answer is saved as it
 * is given, and the step itself is saved, so closing the app and coming back resumes where the
 * person stopped. Nothing here needs a network, an account or AI.
 */
@Composable
fun OnboardingScreen(
    contentPadding: PaddingValues,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current
    val background = MaterialTheme.colorScheme.background
    if (!prefs.loaded) {
        Box(modifier.fillMaxSize().background(background))
        return
    }
    val step = prefs.step

    // Which way the page slides: forward when the new step is later in the list.
    var lastStep by remember { mutableStateOf(step) }
    val forward = OnboardingFlow.isForward(lastStep, step)
    SideEffect { lastStep = step }

    // Back goes one step; on the first step of a replay it leaves the flow (nothing is lost).
    BackHandler(enabled = OnboardingFlow.canGoBack(step) || prefs.replay) {
        if (OnboardingFlow.canGoBack(step)) viewModel.back(step) else viewModel.finish(onFinished)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressDots(step, Modifier.weight(1f))
            // Always visible: nobody is ever stuck in the flow.
            TextButton(onClick = { viewModel.finish(onFinished) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Skip setup")
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                Motion.sharedAxisEnter(forward, reduce) togetherWith Motion.sharedAxisExit(forward, reduce)
            },
            label = "onboarding-step",
        ) { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (s) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.UNITS -> UnitsStep(viewModel)
                    OnboardingStep.ABOUT_YOU -> AboutYouStep(profileViewModel)
                    OnboardingStep.GOAL -> GoalStep(profileViewModel) { viewModel.goTo(OnboardingStep.ABOUT_YOU) }
                    OnboardingStep.ALLERGENS -> AllergensStep()
                    OnboardingStep.HOW_TO_START -> HowToStartStep(viewModel) { viewModel.goTo(OnboardingStep.ABOUT_YOU) }
                    OnboardingStep.PLANS -> PlansStep(viewModel)
                    OnboardingStep.EXTRAS -> ExtrasStep()
                    OnboardingStep.DONE -> DoneStep(prefs.pending, prefs.replay)
                }
            }
        }

        BottomBar(
            step = step,
            replay = prefs.replay,
            onBack = { viewModel.back(step) },
            onSkip = { viewModel.skip(step) },
            onNext = {
                if (step == OnboardingStep.DONE) viewModel.finish(onFinished) else viewModel.next(step)
            },
        )
    }
}

@Composable
private fun ProgressDots(step: OnboardingStep, modifier: Modifier = Modifier) {
    val (position, total) = OnboardingFlow.position(step)
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Step $position of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i + 1 == position) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i < position) on else off),
            )
        }
    }
}

@Composable
private fun BottomBar(
    step: OnboardingStep,
    replay: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (OnboardingFlow.canGoBack(step)) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 52.dp)) { Text("Back") }
        }
        if (OnboardingFlow.canSkip(step)) {
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 52.dp)) { Text("Skip") }
        }
        Button(
            onClick = onNext,
            shape = IntakeShape.Button,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Text(
                when (step) {
                    OnboardingStep.WELCOME -> "Get started"
                    OnboardingStep.DONE -> if (replay) "Done" else "Go to Today"
                    else -> "Continue"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------ pieces

@Composable
private fun StepHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Reassurance(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

// ---------------------------------------------------------------------------------------- 1 Welcome

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(12.dp))
    Text(
        "Intake",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        "Food, training and progress in one calm place.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    IntakeCard {
        Reassurance(Icons.Filled.Lock, "Your log stays on this phone.")
        Reassurance(Icons.Filled.CloudOff, "Works with no account, no internet and no AI.")
        Reassurance(Icons.Filled.AutoAwesome, "AI is an optional shortcut you can add later.")
    }
    Text(
        "A few quick questions set things up. You can skip any of them.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ------------------------------------------------------------------------------------------ 2 Units

@Composable
private fun UnitsStep(viewModel: OnboardingViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    StepHeading("Which units do you like?", "Pick what you use every day. You can change this later in Settings.")
    IntakeCard {
        Text("Body weight", style = MaterialTheme.typography.titleSmall)
        ChipGroup(
            WeightUnit.entries.reversed(), settings.weightUnit, { it.label }, viewModel::setWeightUnit,
            Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        Text("Height", style = MaterialTheme.typography.titleSmall)
        ChipGroup(
            LengthUnit.entries.reversed(), settings.lengthUnit, { it.label }, viewModel::setLengthUnit,
            Modifier.padding(top = 8.dp, bottom = 14.dp),
        )
        Text("Drinks", style = MaterialTheme.typography.titleSmall)
        ChipGroup(
            VolumeUnit.entries.reversed(), settings.volumeUnit, { it.label }, viewModel::setVolumeUnit,
            Modifier.padding(top = 8.dp),
        )
    }
}

// --------------------------------------------------------------------------------------- 3 About you

@Composable
private fun AboutYouStep(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    val settings = state.settings
    StepHeading(
        "About you",
        "Used to estimate your starting calories. After a week of logging, Intake uses your real numbers instead.",
    )
    IntakeCard {
        Text("Sex (for the energy formulas)", style = MaterialTheme.typography.labelLarge)
        ChipGroup(
            Sex.entries, profile.sex, { it.label }, { s -> viewModel.update { it.copy(sex = s) } },
            Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        WeightField(
            weightKg = state.latestWeightKg,
            unit = settings.weightUnit,
            onLog = { kg -> viewModel.logWeighIn(kg, profile.bodyFatPercent ?: state.latestBodyFat) },
        )
        CommitField(
            label = "Body fat % (optional)",
            saved = profile.bodyFatPercent?.let(::num) ?: "",
            placeholder = "Leave empty if unsure",
            supporting = "With this, your calories at rest use your lean mass.",
            onCommit = { text ->
                val v = text.trim().toDoubleOrNull()
                when {
                    text.isBlank() -> viewModel.update { it.copy(bodyFatPercent = null) }
                    v != null && v in 3.0..60.0 -> viewModel.update { it.copy(bodyFatPercent = v) }
                }
            },
        )
    }
    Text(
        "It stays on this phone. Your weight is saved as your first weigh-in.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Weight saves as a weigh-in once the person stops typing (and when they leave the step), and only
 * when it differs from the last one, so editing never piles up duplicate entries.
 */
@Composable
private fun WeightField(weightKg: Double?, unit: WeightUnit, onLog: (Double) -> Unit) {
    val saved = weightKg?.let { num(unit.fromKg(it)) } ?: ""
    var text by rememberSaveable { mutableStateOf(saved) }
    var lastLoggedKg by remember { mutableStateOf<Double?>(null) }
    val currentKg by rememberUpdatedState(weightKg)
    val currentUnit by rememberUpdatedState(unit)
    val log by rememberUpdatedState(onLog)

    LaunchedEffect(saved) {
        val typed = text.trim().toDoubleOrNull()
        val savedValue = saved.toDoubleOrNull()
        if (savedValue != null && (typed == null || abs(typed - savedValue) > 0.06)) text = saved
    }

    fun flush() {
        val kg = text.trim().toDoubleOrNull()?.takeIf { it > 0 }?.let(currentUnit::toKg) ?: return
        if (kg !in 20.0..450.0) return
        val known = currentKg
        if (known != null && abs(kg - known) <= 0.01) return
        val last = lastLoggedKg
        if (last != null && abs(kg - last) <= 0.01) return
        lastLoggedKg = kg
        log(kg)
    }
    val flushNow by rememberUpdatedState(::flush)

    LaunchedEffect(text) {
        delay(800)
        flushNow()
    }
    DisposableEffect(Unit) { onDispose { flushNow() } }

    LabeledField(
        label = "Current weight (${unit.label})",
        value = text,
        onValueChange = { text = it },
        keyboardType = KeyboardType.Decimal,
        placeholder = if (unit == WeightUnit.POUNDS) "170" else "75",
    )
}

// ----------------------------------------------------------------------------------------- 4 Goal

@Composable
private fun GoalStep(viewModel: ProfileViewModel, onNeedAbout: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = state.profile
    StepHeading("Your goal", "Pick the closest fit. Intake works out the numbers.")

    Text("Your build", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BuildType.entries.take(2).forEach { b ->
            OptionTile(b.label, b.blurb, profile.build == b, { viewModel.update { it.copy(build = b) } },
                Modifier.weight(1f), secondary = b.classicName)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BuildType.entries.drop(2).forEach { b ->
            OptionTile(b.label, b.blurb, profile.build == b, { viewModel.update { it.copy(build = b) } },
                Modifier.weight(1f), secondary = b.classicName)
        }
    }
    Text(
        profile.build.startingTip,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (profile.phase != profile.build.suggestedPhase) {
        TextButton(
            onClick = { viewModel.update { it.copy(phase = it.build.suggestedPhase) } },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Start with ${profile.build.suggestedPhase.label.lowercase()}?") }
    }

    Text("What do you want to do?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    Phase.entries.filter { profile.isAdvanced || !it.advanced || it == profile.phase }.forEach { p ->
        OptionTile(
            title = p.label,
            description = p.blurb,
            selected = profile.phase == p,
            onClick = { viewModel.update { it.copy(phase = p) } },
            modifier = Modifier.fillMaxWidth(),
            secondary = if (p.advanced) "Advanced" else null,
        )
    }

    if (profile.phase in PACE_PHASES) {
        Text("How fast?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
        ChipGroup(Pace.entries, profile.pace, { it.label }, { p -> viewModel.update { it.copy(pace = p) } })
    }

    val preview = state.preview
    if (preview != null && state.latestWeightKg != null) {
        val pace = if (profile.phase in PACE_PHASES) {
            PhasePlanner.paceInWords(preview.calories.kgPerWeek, state.latestWeightKg!!, state.settings.weightUnit)
        } else {
            null
        }
        AccentNote(
            lead = "About ${"%,d".format(preview.calories.kcal)} kcal and ${preview.macros.proteinG} g protein a day",
            body = (pace?.let { "$it. " } ?: "") +
                "This is a day-one estimate. After a week of logging, Intake uses your real numbers instead.",
        )
    } else {
        SandNote(
            "Add your sex, birth year, height and weight on the About you step to see your daily calories and protein here.",
        )
        TextButton(onClick = onNeedAbout, modifier = Modifier.heightIn(min = 48.dp)) { Text("Go to About you") }
    }
}

// ------------------------------------------------------------------------------------- 5 Allergens

@Composable
private fun AllergensStep(viewModel: MyAllergensViewModel = hiltViewModel()) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    StepHeading(
        "Foods you avoid",
        "Intake checks every food you add against this list, on your phone. It can warn you; it never hides a warning.",
    )
    val items = overview?.items.orEmpty()
    val avoided = items.count { it.avoided }
    Text(
        if (avoided == 0) "Nothing selected yet. Tap the ones you avoid." else "You avoid $avoided.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            ChoiceChip(
                label = item.displayName,
                selected = item.avoided,
                onClick = { viewModel.setAvoided(item.key, !item.avoided) },
                modifier = Modifier.semantics { selected = item.avoided },
            )
        }
        ChoiceChip(label = "+ Add your own", selected = false, onClick = { adding = true })
    }
    if (adding) {
        AddOwnDialog(
            onDismiss = { adding = false },
            onAdd = { name ->
                adding = false
                viewModel.createAllergen(name)
            },
        )
    }
}

@Composable
private fun AddOwnDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add your own") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "For example Nightshades or Red dye 40. You can add the words that should flag it later in Food, Allergens.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabeledField(label = "Name", value = name, onValueChange = { name = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Add", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
    )
}

// ---------------------------------------------------------------------------------- 6 How to start

@Composable
private fun HowToStartStep(viewModel: OnboardingViewModel, onNeedAbout: () -> Unit) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val estimate by viewModel.estimate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun startBaseline() {
        viewModel.chooseBaseline()
        // Android 13+: ask now so the "plan is ready" note can arrive. Denied is fine; Today still says Ready.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    StepHeading("How do you want to start?", "Either way you can change your plan any time.")
    OptionTile(
        title = "Learn my baseline first (recommended)",
        description = "Log normally for a week; Intake then builds your calories, water and training plan.",
        selected = prefs.startChoice == StartChoice.BASELINE,
        onClick = ::startBaseline,
        modifier = Modifier.fillMaxWidth(),
    )
    OptionTile(
        title = "Use the estimate now",
        description = "Start today with calories and protein worked out from your answers. No waiting.",
        selected = prefs.startChoice == StartChoice.ESTIMATE,
        onClick = viewModel::chooseEstimate,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val result = estimate) {
        is EstimateResult.Applied -> AccentNote(
            lead = "Plan applied",
            body = "${"%,d".format(result.kcal)} kcal and ${result.proteinG} g protein a day. See it any time on You, the circle at the top right.",
        )
        EstimateResult.NeedsBasics -> {
            SandNote("An estimate needs your sex, birth year, height and weight. You can add them on the About you step.")
            TextButton(onClick = onNeedAbout, modifier = Modifier.heightIn(min = 48.dp)) { Text("Go to About you") }
        }
        null -> Unit
    }
}

// ----------------------------------------------------------------------------------------- 7 Plans

@Composable
private fun PlansStep(viewModel: OnboardingViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    StepHeading(
        "Your plans",
        "Optional. Pick one to open when you finish, or skip. You can do all of these later.",
    )
    PendingDestination.entries.forEach { d ->
        OptionTile(
            title = d.label,
            description = d.blurb,
            selected = prefs.pending == d,
            onClick = { viewModel.choosePending(if (prefs.pending == d) null else d) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    OptionTile(
        title = "Skip",
        description = "I'll set up plans later.",
        selected = prefs.pending == null,
        onClick = { viewModel.choosePending(null) },
        modifier = Modifier.fillMaxWidth(),
    )
}

// --------------------------------------------------------------------------------------- 8 Extras

@Composable
private fun ExtrasStep() {
    StepHeading("Optional extras", "None of these are needed. Intake works fully without them.")
    ExtraRow(
        Icons.Filled.FavoriteBorder,
        "Health Connect",
        "Steps, sleep and weight from your watch or scale.",
    )
    ExtraRow(
        Icons.Filled.CloudUpload,
        "Backups",
        "A copy of your data in a file, or on a server you run yourself.",
    )
    ExtraRow(
        Icons.Filled.AutoAwesome,
        "AI shortcuts",
        "Photo estimates and recipe ideas. Off until you add your own key.",
    )
}

@Composable
private fun ExtraRow(icon: ImageVector, title: String, description: String) {
    IntakeCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "You can set this up later on You, the circle at the top right.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------------------- 9 Done

@Composable
private fun DoneStep(pending: PendingDestination?, replay: Boolean) {
    Spacer(Modifier.height(24.dp))
    Text(
        "You're set",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        if (replay) "Your answers are saved. Nothing was deleted."
        else "Your answers are saved. Next is a quick look around Today, and you can skip it.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (pending != null) {
        SandNote("After that: ${pending.label.replaceFirstChar { it.lowercase() }}.")
    }
}
