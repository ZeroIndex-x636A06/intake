package com.zeroindex.intake.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.coach.CoachRepository
import com.zeroindex.intake.data.profile.BaselineSnapshot
import com.zeroindex.intake.data.repository.BodyRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.domain.coach.DailyFocus
import com.zeroindex.intake.domain.time.Clock
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import com.zeroindex.intake.data.profile.ProfileRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.WorkoutSchedule
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

    /** [weekday] is the ISO weekday the day is scheduled on, or null when the program runs as a rotation. */
    data class Next(
        val programName: String,
        val dayName: String,
        val dayNumber: Int,
        val daysInProgram: Int,
        val weekday: Int? = null,
    ) : WorkoutCardState

    /** A weekday-scheduled program with nothing scheduled today: say what is next and when. */
    data class Rest(
        val programName: String,
        val nextDayName: String,
        val nextWeekday: Int,
        val daysAway: Int,
    ) : WorkoutCardState
}

data class TodayExtras(
    val workout: WorkoutCardState = WorkoutCardState.Loading,
    val baseline: BaselineSnapshot? = null,
    /** "One thing to do today"; null until the first calculation. */
    val focus: DailyFocus? = null,
)

/**
 * Today's two small additions, kept apart from [TodayViewModel] so the nutrition screen's state
 * stays as it was: the baseline-week card and the next-workout card.
 */
@HiltViewModel
class TodayExtrasViewModel @Inject constructor(
    private val training: TrainingRepository,
    profile: ProfileRepository,
    private val coach: CoachRepository,
    foodLog: FoodLogRepository,
    body: BodyRepository,
    hydration: HydrationRepository,
    private val clock: Clock,
) : ViewModel() {

    init {
        // The nightly job may not have run yet (a phone that was off); make the check-in if it is due.
        viewModelScope.launch { runCatching { coach.generateIfDue() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val workout = training.observeActiveProgram().flatMapLatest { program ->
        val days = if (program == null) flowOf(emptyList()) else training.observeDays(program.id)
        combine(days, training.observeInProgress()) { dayList, inProgress ->
            when {
                inProgress != null -> WorkoutCardState.InProgress(inProgress.id, program?.name)
                program == null -> WorkoutCardState.NoProgram
                dayList.isEmpty() -> WorkoutCardState.NoProgram
                else -> {
                    val plan = WorkoutSchedule.planFor(
                        dayList.map { ScheduleDay(it.dayIndex, it.name, it.weekday) },
                        program.currentDayIndex,
                        clock.today().dayOfWeek.value,
                    )
                    when (plan) {
                        is TodayPlan.Train -> WorkoutCardState.Next(
                            program.name, plan.day.name, dayList.indexOfFirst { it.dayIndex == plan.day.dayIndex } + 1,
                            dayList.size, plan.day.weekday,
                        )
                        is TodayPlan.Rest -> WorkoutCardState.Rest(program.name, plan.next.name, plan.next.weekday ?: 1, plan.daysAway)
                        TodayPlan.NoDays -> WorkoutCardState.NoProgram
                    }
                }
            }
        }
    }

    /** Recomputed whenever anything it reads changes: food, weigh-ins, water, workouts, the baseline or a pending check-in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val focus = listOf<kotlinx.coroutines.flow.Flow<Any?>>(
        coach.observePendingCount(),
        foodLog.observeDayTotals(clock.today()),
        body.observeLatest(),
        hydration.observeDay(clock.today()),
        training.observeSessions(),
        profile.observeBaseline(),
    ).let { fs -> combine(fs) { Unit } }.mapLatest { runCatching { coach.dailyFocus() }.getOrNull() }

    val extras: StateFlow<TodayExtras> = combine(workout, profile.observeBaseline(), focus) { w, b, f ->
        TodayExtras(workout = w, baseline = b, focus = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayExtras())
}
