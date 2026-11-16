package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.screens.videos.VideosSection

/** What the Train root can open. Everything is a route the Train graph (or the root) registers. */
class TrainActions(
    val onOpenSession: (sessionId: String) -> Unit,
    val onOpenSummary: (sessionId: String) -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenPrograms: () -> Unit,
    val onOpenGenerator: () -> Unit,
    val onOpenVideo: (videoId: String) -> Unit,
    val onMakeWorkout: (videoId: String) -> Unit,
    val onOpenKeySettings: () -> Unit,
)

/** The three sections of Train, in order. */
enum class TrainSection(val label: String) { WORKOUT("Workout"), EXERCISES("Exercises"), VIDEOS("Videos") }

/**
 * The Train tab root (IA v5 6b): a section switch under the top bar, then Workout, Exercises or Videos. The switch stays
 * in place while the section below it scrolls, and each section pads its own list for the pill.
 */
@Composable
fun TrainRootScreen(
    contentPadding: PaddingValues,
    actions: TrainActions,
    modifier: Modifier = Modifier,
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        SectionSwitch(
            selected = section,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (TrainSection.entries[section]) {
                TrainSection.WORKOUT -> WorkoutSection(
                    contentPadding = listPadding,
                    actions = actions,
                    onFromVideo = { section = TrainSection.VIDEOS.ordinal },
                )
                TrainSection.EXERCISES -> ExercisesSection(contentPadding = listPadding)
                TrainSection.VIDEOS -> VideosSection(
                    contentPadding = listPadding,
                    onOpenVideo = actions.onOpenVideo,
                    onMakeWorkout = actions.onMakeWorkout,
                    onOpenKeySettings = actions.onOpenKeySettings,
                )
            }
        }
    }
}

/** Workout · Exercises · Videos as one full-width pill, the selected one filled (the mockup's segmented control). */
@Composable
private fun SectionSwitch(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TrainSection.entries.forEachIndexed { index, s ->
                val on = index == selected
                Surface(
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { this.selected = on }
                        // Clip before the click so the press ripple follows the pill's round corners.
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab, onClick = { onSelect(index) }),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            s.label,
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
