package com.zeroindex.intake.ui.screens.you

import com.zeroindex.intake.domain.units.format
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.zeroindex.intake.ui.components.SectionLine
import com.zeroindex.intake.ui.screens.photos.PhotoImage
import com.zeroindex.intake.ui.theme.IntakeShape
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
 * You (Option A): who the plan is for. Header, Goals and plan (read-only, each with a Change that opens the right
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
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = state.settings
    val p = s.profile
    val year = LocalDate.now().year
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding() + 4.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(s.displayName.take(1).uppercase().ifEmpty { "Y" }, style = MaterialTheme.typography.headlineSmall) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.displayName.ifBlank { "You" }, style = MaterialTheme.typography.titleLarge)
                        val line = listOfNotNull(
                            p.birthYear?.let { "${year - it}" },
                            p.heightCm?.let { s.lengthUnit.format(it) },
                            state.weightKg?.let { s.weightUnit.format(it) },
                            s.goalWeightKg?.let { "goal ${s.weightUnit.format(it)}" },
                        ).joinToString(" · ").ifEmpty { "Add your basics to build a plan" }
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEditProfile) { Icon(Icons.Filled.Edit, contentDescription = "Edit your profile") }
                }
            }
        }

        item(key = "goals-head") { SectionLine("Goals and plan", note = if (s.autoCalculateTargets) "worked out from your data" else "set by hand") }
        item(key = "goals") {
            Card {
                val t = state.target
                PlanRow("Calories", if (s.autoCalculateTargets) "from your weight trend and a ${p.pace.label.lowercase()} pace" else "set by hand",
                    t?.let { "%,d kcal".format(it.kcal) } ?: "No target yet") { onChangeSetting("nutrition", "manualCalories") }
                PlanRow("Protein · fat · carbs", "${s.proteinGramsPerKg} and ${s.fatGramsPerKg} g per kg",
                    t?.let { "${it.proteinG} · ${it.fatG} · ${it.carbsG} g" } ?: "—") { onChangeSetting("nutrition", "proteinPerKg") }
                PlanRow("Water", if (s.hydrationDynamic) "adjusts for the day" else "fixed target",
                    state.waterMl?.let { s.volumeUnit.format(it) } ?: "—") { onChangeSetting("water", "hydrationBase") }
                PlanRow("Training", p.experience.label.lowercase() + " · " + p.effectiveTrainingGoal.label.lowercase(),
                    "${p.effectiveTrainingDays} days · ${p.sessionMinutes} min") { onChangeSetting("training", null) }
                PlanRow("Phase and pace", "${p.phase.label} · ${p.pace.label.lowercase()}", null, last = true) { onEditProfile() }
                TextButton(onClick = onOpenPlan, modifier = Modifier.heightIn(min = 48.dp)) { Text("How this was worked out") }
            }
        }

        item(key = "photos-head") { SectionLine("Progress photos", note = "private, on this phone") }
        item(key = "photos") {
            Card {
                if (state.recentDays.isEmpty()) {
                    Text("No photos yet. Front, side and back are all optional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        state.recentDays.forEach { d ->
                            Box(Modifier.weight(1f).aspectRatio(0.72f)) {
                                PhotoImage(d.cover?.let(viewModel::fileOf), Modifier.matchParentSize(), targetPx = 400)
                                Text(
                                    LocalDate.ofEpochDay(d.epochDay).format(DateTimeFormatter.ofPattern("MMM d")),
                                    style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                                )
                            }
                        }
                        repeat(3 - state.recentDays.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (state.photoDue) {
                    Text("A progress photo is due.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenPhotos, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Add photo") }
                    OutlinedButton(onClick = onOpenCompare, enabled = state.photoCount >= 2, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Compare") }
                }
                Text(
                    "Not in exports unless you switch it on in Settings > Sync and export.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        item(key = "help-head") { SectionLine("Help") }
        item(key = "help") {
            Card {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tour, scripts help and about", style = MaterialTheme.typography.titleSmall)
                        Text("Replay the tour, read the script docs, privacy and credits.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onOpenHelp, shape = IntakeShape.Chip) { Text("Open") }
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = IntakeShape.Card,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
private fun PlanRow(title: String, reason: String, value: String?, last: Boolean = false, onChange: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
        TextButton(onClick = onChange) { Text("Change") }
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
