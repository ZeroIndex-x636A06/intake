package com.zeroindex.intake.ui.screens.train

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeroindex.intake.ui.components.Segmented
import com.zeroindex.intake.ui.screens.videos.VideosSection
import com.zeroindex.intake.ui.theme.IntakeSpacing

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
    val listPadding = trainPadding(bottom = contentPadding.calculateBottomPadding() + 24.dp)

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Segmented(
            options = TrainSection.entries.map { it.label },
            selectedIndex = section,
            onSelect = { section = it },
            role = androidx.compose.ui.semantics.Role.Tab,
            modifier = Modifier.fillMaxWidth().padding(horizontal = IntakeSpacing.ScreenGutter, vertical = 8.dp),
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
