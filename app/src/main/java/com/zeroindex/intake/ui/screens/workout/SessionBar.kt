package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.training.WorkoutPauseStore
import com.zeroindex.intake.domain.training.WorkoutClock
import com.zeroindex.intake.domain.training.WorkoutPause
import androidx.hilt.navigation.compose.hiltViewModel
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.timer.TimerService
import com.zeroindex.intake.ui.theme.IntakeElevation
import com.zeroindex.intake.ui.theme.IntakeShape
import com.zeroindex.intake.ui.theme.intake
import com.zeroindex.intake.ui.theme.tabular
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** A workout that is running but not on screen. */
data class RunningSession(val id: String, val startedAt: Long, val title: String)

/** Feeds the minimised session bar: the unfinished session, if there is one. */
@HiltViewModel
class SessionBarViewModel @Inject constructor(
    private val repository: TrainingRepository,
    pauseStore: WorkoutPauseStore,
) : ViewModel() {
    /** The pause state of the running workout, so the bar's clock stops with the workout's. */
    val pause: StateFlow<WorkoutPause?> = pauseStore.state

    @OptIn(ExperimentalCoroutinesApi::class)
    val running: StateFlow<RunningSession?> = repository.observeInProgress()
        .flatMapLatest { s ->
            flow {
                emit(s?.let { RunningSession(it.id, it.startedAt, it.programDayId?.let { d -> repository.dayName(d) } ?: "Workout") })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Height the session bar takes above the pill, so tab roots can leave room for it. */
val SessionBarHeight: Dp = 72.dp

/**
 * The minimised session bar, the redesign's live pill (a 20 dp card with a lime pulse dot and an ink Resume pill): shown above the dock on the tab roots while a workout runs. Shows the day, the
 * clock and the rest countdown when one is running; tapping it goes back into the workout.
 */
@Composable
fun SessionBar(session: RunningSession, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val timer by TimerService.state.collectAsStateWithLifecycle()
    val pauseModel: SessionBarViewModel = hiltViewModel()
    val pauseState by pauseModel.pause.collectAsStateWithLifecycle()
    val pause = pauseState?.takeIf { it.sessionId == session.id }
    val elapsed by produceState(0L, session.startedAt, pause) {
        while (true) {
            value = WorkoutClock.trainedMs(session.startedAt, pause, System.currentTimeMillis())
            delay(1_000)
        }
    }
    val resting = timer.running && timer.remainingSeconds > 0
    val c = MaterialTheme.intake
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = IntakeShape.Live,
        shadowElevation = IntakeElevation.Live,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Go back into your workout", onClick = onResume),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 58.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // `.pulse`: a 10 dp lime dot with a 5 dp, 25% lime halo.
            Box(
                Modifier
                    .size(20.dp)
                    .background(c.accent.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).background(c.accent, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    when {
                        pause?.isPaused == true -> "${TrainText.clock(elapsed)} · Paused"
                        resting -> "${TrainText.clock(elapsed)} · Rest ${TimerService.format(timer.remainingSeconds)}"
                        else -> TrainText.clock(elapsed)
                    },
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The small ink "Resume" pill; the whole bar is the touch target.
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
            ) {
                Text(
                    "Resume",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}
