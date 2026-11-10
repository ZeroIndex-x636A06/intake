package com.zeroindex.intake.ui.screens.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
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
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.timer.TimerService
import com.zeroindex.intake.ui.theme.IntakeShape
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
class SessionBarViewModel @Inject constructor(private val repository: TrainingRepository) : ViewModel() {
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
val SessionBarHeight: Dp = 64.dp

/**
 * The minimised session bar (IA v5 2.5): shown above the pill on the tab roots while a workout runs. Shows the day, the
 * clock and the rest countdown when one is running; tapping it goes back into the workout.
 */
@Composable
fun SessionBar(session: RunningSession, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val timer by TimerService.state.collectAsStateWithLifecycle()
    val elapsed by produceState(0L, session.startedAt) {
        while (true) {
            value = System.currentTimeMillis() - session.startedAt
            delay(1_000)
        }
    }
    val resting = timer.running && timer.remainingSeconds > 0
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = IntakeShape.Banner,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Go back into your workout", onClick = onResume),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    if (resting) "${TrainText.clock(elapsed)} · Rest ${TimerService.format(timer.remainingSeconds)}" else TrainText.clock(elapsed),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Resume", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
