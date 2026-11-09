package com.zeroindex.intake.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.profile.BaselineSnapshot
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What the "Today's workout" card shows. */
sealed interface WorkoutCardState {
    data object Loading : WorkoutCardState

    /** No active program: offer to make one. */
    data object NoProgram : WorkoutCardState

    /** A session is open right now. */
    data class InProgress(val sessionId: String, val programName: String?) : WorkoutCardState

    data class Next(val programName: String, val dayName: String, val dayNumber: Int, val daysInProgram: Int) : WorkoutCardState
}

data class TodayExtras(
    val workout: WorkoutCardState = WorkoutCardState.Loading,
    val baseline: BaselineSnapshot? = null,
)

/**
 * Today's two small additions, kept apart from [TodayViewModel] so the nutrition screen's state
 * stays as it was: the baseline-week card and the next-workout card.
 */
@HiltViewModel
class TodayExtrasViewModel @Inject constructor(
    private val training: TrainingRepository,
    profile: ProfileRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workout = training.observeActiveProgram().flatMapLatest { program ->
        val days = if (program == null) flowOf(emptyList()) else training.observeDays(program.id)
        combine(days, training.observeInProgress()) { dayList, inProgress ->
            when {
                inProgress != null -> WorkoutCardState.InProgress(inProgress.id, program?.name)
                program == null -> WorkoutCardState.NoProgram
                dayList.isEmpty() -> WorkoutCardState.NoProgram
                else -> {
                    val index = program.currentDayIndex.coerceIn(0, dayList.lastIndex)
                    WorkoutCardState.Next(program.name, dayList[index].name, index + 1, dayList.size)
                }
            }
        }
    }

    val extras: StateFlow<TodayExtras> = combine(workout, profile.observeBaseline()) { w, b ->
        TodayExtras(workout = w, baseline = b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayExtras())
}
