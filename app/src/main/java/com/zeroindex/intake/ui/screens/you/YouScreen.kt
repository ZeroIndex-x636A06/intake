package com.zeroindex.intake.ui.screens.you

import com.zeroindex.intake.domain.time.DateText
import com.zeroindex.intake.domain.units.KcalFormat
import com.zeroindex.intake.domain.units.format
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zeroindex.intake.ui.components.ListDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.photos.ProgressPhotoRepository
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.photos.PhotoDay
import com.zeroindex.intake.domain.photos.PhotoMeta
import com.zeroindex.intake.domain.photos.PhotoReminderRule
import com.zeroindex.intake.domain.photos.PhotoTimeline
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.ui.components.AvatarDisc
import com.zeroindex.intake.ui.components.ChipTone
import com.zeroindex.intake.ui.components.Eyebrow
import com.zeroindex.intake.ui.components.HeroCard
import com.zeroindex.intake.ui.components.IconTile
import com.zeroindex.intake.ui.components.IntakeCard
import com.zeroindex.intake.ui.components.IntakeChip
import com.zeroindex.intake.ui.components.ListCard
import com.zeroindex.intake.ui.components.MutedIcon
import com.zeroindex.intake.ui.components.RowChevron
import com.zeroindex.intake.ui.components.SoftButton
import com.zeroindex.intake.ui.components.heroMuted
import com.zeroindex.intake.ui.screens.photos.PhotoImage
import com.zeroindex.intake.ui.theme.IconSize
import com.zeroindex.intake.ui.theme.IntakeIcons
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.IntakeSpacing
import com.zeroindex.intake.ui.theme.intake
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class YouUiState(
    val settings: AppSettings = AppSettings(),
    val weightKg: Double? = null,
    val target: MacroTarget? = null,
    val waterMl: Double? = null,
    val recentDays: List<PhotoDay> = emptyList(),
    val photoCount: Int = 0,
    val photoDue: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class YouViewModel @Inject constructor(
    settings: SettingsRepository,
    body: BodyRepository,
    targets: TargetRepository,
    hydration: HydrationRepository,
    private val photos: ProgressPhotoRepository,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<YouUiState> = combine(
        settings.settings, body.observeLatest(), targets.observeTargetFor(clock.today()),
        hydration.observeDay(clock.today()), photos.observe(),
    ) { s, latest, target, water, all ->
        val days = PhotoTimeline.group(all, clock.zone())
        YouUiState(
            settings = s, weightKg = latest?.weightKg, target = target, waterMl = water.target.totalMl,
            recentDays = days.take(3).reversed(), photoCount = all.size,
            photoDue = PhotoReminderRule.isDue(s.photoReminderWeeks, all.maxOfOrNull { it.takenAt }, clock.nowMillis()),
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState())

    fun fileOf(meta: PhotoMeta): File? = photos.fileOf(meta)
}

/**
 * You (v6): who the plan is for. Header card, the Goals and plan hero (read-only, each figure opens the right
 * Settings page or the profile), Progress photos, then Help. The gear in the top bar opens Settings.
 */
@Composable
fun YouScreen(
    contentPadding: PaddingValues,
    onEditProfile: () -> Unit,
    onOpenPlan: () -> Unit,
    onChangeSetting: (group: String, focus: String?) -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenCompare: () -> Unit,
    onReplayTour: () -> Unit,
    onOpenScriptDocs: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val p = s.profile
    val year = LocalDate.now().year
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
            start = IntakeSpacing.ScreenGutter, end = IntakeSpacing.ScreenGutter,
        ),
        verticalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap),
    ) {
        item(key = "header") {
            IntakeCard(Modifier.clickable(onClickLabel = "Open Profile & goals", onClick = onEditProfile)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarDisc(s.displayName, large = true)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName.ifBlank { "You" }, style = MaterialTheme.typography.titleLarge)
                        val line = listOfNotNull(
                            p.birthYear?.let { "${year - it}" },
                            p.heightCm?.let { s.lengthUnit.format(it) },
                            state.weightKg?.let { s.weightUnit.format(it) },
                            s.goalWeightKg?.let { "goal ${s.weightUnit.format(it)}" },
                        ).joinToString(" · ").ifEmpty { "Add your basics to build a plan" }
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RowChevron()
                }
            }
        }

        // Say "worked out" only when it is true: the switch is on and the data it needs is there (QA Q4-12).
        val missing = com.zeroindex.intake.domain.goals.AutoTargetReadiness.sentence(p, state.weightKg != null)
        val worked = s.autoCalculateTargets && missing == null
        item(key = "goals") {
            val t = state.target
            HeroCard {
                // Stacked, not side by side: on a fresh install the plan chip is long and squeezed the eyebrow into a
                // 60 dp column (QA Q7-20).
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Eyebrow("Goals and plan", color = heroMuted())
                        Text(
                            when {
                                worked -> "worked out from your data"
                                s.autoCalculateTargets -> "waiting for your details"
                                else -> "set by hand"
                            },
                            style = MaterialTheme.typography.bodySmall, color = heroMuted(),
                        )
                    }
                    PlanChip(
                        if (p.hasBasics) "${p.phase.label} · ${p.pace.label.lowercase()}" else "Phase and pace: choose in your profile",
                        onEditProfile,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap)) {
                    PlanStat(
                        "Calories", t?.let { "${KcalFormat.format(it.kcal)} kcal" } ?: "No target yet",
                        when {
                            worked -> "from your weight trend and a ${p.pace.label.lowercase()} pace"
                            s.autoCalculateTargets -> missing ?: "not worked out yet"
                            else -> "set by hand"
                        },
                        Modifier.weight(1f),
                    ) { onChangeSetting("nutrition", "manualCalories") }
                    PlanStat(
                        "Protein · fat · carbs", t?.let { "${it.proteinG} · ${it.fatG} · ${it.carbsG} g" } ?: "—",
                        if (t == null && missing != null) "needs the same details" else "${s.proteinGramsPerKg} and ${s.fatGramsPerKg} g per kg",
                        Modifier.weight(1f),
                    ) { onChangeSetting("nutrition", "proteinPerKg") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.StackGap)) {
                    PlanStat(
                        "Water", state.waterMl?.let { s.volumeUnit.format(it) } ?: "—",
                        if (s.hydrationDynamic) "adjusts for the day" else "fixed target",
                        Modifier.weight(1f),
                    ) { onChangeSetting("water", "hydrationBase") }
                    PlanStat(
                        "Training", "${p.effectiveTrainingDays} days · ${p.sessionMinutes} min",
                        p.experience.label.lowercase() + " · " + p.effectiveTrainingGoal.label.lowercase(),
                        Modifier.weight(1f),
                    ) { onChangeSetting("training", null) }
                }
                Text(
                    "How this was worked out",
                    style = MaterialTheme.typography.labelLarge,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = IntakeSpacing.TouchTarget)
                        .clickable(role = Role.Button, onClick = onOpenPlan)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }

        item(key = "photos") {
            IntakeCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Progress photos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (state.photoDue) IntakeChip("A progress photo is due.", tone = ChipTone.Warn)
                }
                Text("private, on this phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (state.recentDays.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap), modifier = Modifier.fillMaxWidth()) {
                        repeat(3) {
                            Box(
                                Modifier.weight(1f).aspectRatio(0.82f).clip(IntakeShape.InnerCard).background(MaterialTheme.intake.soft),
                                contentAlignment = Alignment.Center,
                            ) { MutedIcon(IntakeIcons.User) }
                        }
                    }
                    Text(
                        "No photos yet. Front, side and back are all optional.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap), modifier = Modifier.fillMaxWidth()) {
                        state.recentDays.forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(0.82f).clip(IntakeShape.InnerCard)) {
                                PhotoImage(d.cover?.let(viewModel::fileOf), Modifier.matchParentSize(), targetPx = 400)
                                Text(
                                    DateText.medium(LocalDate.ofEpochDay(d.epochDay)),
                                    style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                )
                            }
                        }
                        repeat(3 - state.recentDays.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(IntakeSpacing.RowGap)) {
                    Button(onClick = onOpenPhotos, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = IntakeShape.Button) {
                        Icon(IntakeIcons.Camera, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        Spacer(Modifier.width(8.dp))
                        Text("Add photo")
                    }
                    SoftButton(onClick = onOpenCompare, enabled = state.photoCount >= 2, modifier = Modifier.weight(1f)) { Text("Compare") }
                }
                Text(
                    "Not in exports unless you switch it on in Settings > Sync and export.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        item(key = "help") {
            val helpFlow: com.zeroindex.intake.ui.onboarding.HelpFlowViewModel = hiltViewModel()
            var credits by remember { mutableStateOf(false) }
            ListCard {
                HelpRow(IntakeIcons.Help, "Replay the tour", "A quick look around Today. Nothing is changed or deleted.") { helpFlow.replayTour(then = onReplayTour) }
                ListDivider()
                HelpRow(IntakeIcons.Code, "Script docs", "Meal plan and program scripts, with the full reference. Works offline.", onOpenScriptDocs)
                ListDivider()
                HelpRow(IntakeIcons.ShieldCheck, "What leaves this phone", "Exactly what is sent, to whom, and when.", onOpenPrivacy)
                ListDivider()
                HelpRow(IntakeIcons.Book, "Credits and licences", "Where the food, exercise and weather data come from.") { credits = true }
            }
            if (credits) com.zeroindex.intake.ui.screens.settings.CreditsDialog(onDismiss = { credits = false })
        }
    }
}

@Composable
private fun HelpRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = IntakeSpacing.ListRowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = IntakeSpacing.ListRowPadH, vertical = IntakeSpacing.ListRowPadV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, small = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RowChevron()
    }
}

/** The phase-and-pace chip on the hero: a lime pill with ink text, so lime is never text on a dark card. */
@Composable
private fun PlanChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.intake.accent,
        contentColor = MaterialTheme.intake.onAccent,
        shape = IntakeShape.Chip,
        modifier = Modifier.heightIn(min = IntakeSpacing.TouchTarget).wrapContentHeight(Alignment.CenterVertically),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** One figure of the plan: the number big, its name and the reason under it. Tapping it opens the page that changes it. */
@Composable
private fun PlanStat(title: String, value: String, reason: String, modifier: Modifier = Modifier, onChange: () -> Unit) {
    Column(
        modifier
            .clip(IntakeShape.Tile)
            .clickable(onClickLabel = "Change $title", role = Role.Button, onClick = onChange)
            .heightIn(min = IntakeSpacing.TouchTarget)
            .padding(vertical = 6.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(title, style = MaterialTheme.typography.titleSmall, color = heroMuted())
        Text(reason, style = MaterialTheme.typography.bodySmall, color = heroMuted())
    }
}
