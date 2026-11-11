package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ProgramEntity
import com.zeroindex.intake.data.db.projection.SessionStat
import com.zeroindex.intake.data.db.projection.WorkoutSummary
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.settings.WeeklySets
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.SessionEstimate
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.TrainText
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A day of a program that can be started. */
data class DayPick(val dayId: String, val name: String, val weekday: Int?)

/** A program with its days, for "From a program". */
data class ProgramChoice(val programName: String, val active: Boolean, val days: List<DayPick>)

/** What today's card on Train says. */
sealed interface TodayCard {
    data object Loading : TodayCard

    /** No active program (or one with no days): training still works from an empty workout. */
    data object NoProgram : TodayCard

    data class Session(
        val programName: String,
        val week: Int,
        val dayNumber: Int,
        val dayCount: Int,
        val dayId: String,
        val dayName: String,
        val lines: List<String>,
        val moreCount: Int,
        val minutes: Int,
        val muscles: String,
    ) : TodayCard

    data class Rest(val programName: String, val line: String, val days: List<DayPick>) : TodayCard
}

data class RunningWorkout(val sessionId: String, val title: String, val startedAt: Long)

data class RepeatLast(val dayId: String, val label: String)

data class TrainHomeState(
    val today: TodayCard = TodayCard.Loading,
    val running: RunningWorkout? = null,
    val volume: List<RegionVolume> = emptyList(),
    val hint: String? = null,
    val weekSessions: Int = 0,
    val weekVolumeKg: Double = 0.0,
    val recent: List<SessionStat> = emptyList(),
    val repeatLast: RepeatLast? = null,
    val workouts: List<WorkoutSummary> = emptyList(),
    val activeProgram: ProgramEntity? = null,
    val unit: WeightUnit = WeightUnit.POUNDS,
    val busy: Boolean = false,
)

/**
 * Train > Workout (IA v5 6b): today's session, the other ways to start, this week's muscle volume, recent sessions. One
 * Start never starts two sessions: [TrainingRepository.startSession] hands back the one already running.
 */
@HiltViewModel
class TrainHomeViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val busy = MutableStateFlow(false)

    init {
        // The library must exist before a day's exercises can be named.
        viewModelScope.launch { repository.ensureSeedExercises() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val today: Flow<Pair<ProgramEntity?, TodayCard>> = repository.observeActiveProgram().flatMapLatest { program ->
        if (program == null) {
            flowOf(null to TodayCard.NoProgram)
        } else {
            combine(repository.observeDays(program.id), repository.observeSessionStats(1)) { d, last -> d to last }.mapLatest { (days, last) ->
                val finished = last.firstOrNull()?.finishedAt
                val plan = WorkoutSchedule.planFor(
                    days.map { ScheduleDay(it.dayIndex, it.name, it.weekday) },
                    program.currentDayIndex,
                    clock.today().dayOfWeek.value,
                    trainedToday = finished != null && com.zeroindex.intake.domain.time.localDateOf(finished, clock.zone()) == clock.today(),
                )
                program to when (plan) {
                    TodayPlan.NoDays -> TodayCard.NoProgram
                    is TodayPlan.Train -> {
                        val day = days.first { it.dayIndex == plan.day.dayIndex }
                        sessionCard(program, day.id, day.name, days.indexOf(day) + 1, days.size)
                    }
                    is TodayPlan.Rest -> TodayCard.Rest(
                        programName = program.name,
                        line = TrainText.restLine(plan.next.name, plan.next.weekday, plan.daysAway),
                        days = days.map { DayPick(it.id, it.name, it.weekday) },
                    )
                }
            }
        }
    }

    private suspend fun sessionCard(program: ProgramEntity, dayId: String, dayName: String, number: Int, count: Int): TodayCard {
        val exercises = repository.programExercises(dayId)
        val lines = exercises.mapNotNull { pe ->
            repository.exerciseAnyState(pe.exerciseId)?.let { e -> TrainText.line(e.name, pe.targetSets, pe.targetRepsMin, pe.targetRepsMax) }
        }
        val muscles = exercises.mapNotNull { repository.exerciseAnyState(it.exerciseId)?.primaryMuscle }
            .distinct().take(4).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } }
        val minutes = SessionEstimate.minutes(exercises.map { SessionEstimate.Slot(it.targetSets, it.restSec) })
        return TodayCard.Session(
            programName = program.name,
            week = program.currentWeek,
            dayNumber = number,
            dayCount = count,
            dayId = dayId,
            dayName = dayName,
            lines = lines.take(3),
            moreCount = (lines.size - 3).coerceAtLeast(0),
            minutes = minutes,
            muscles = muscles,
        )
    }

    private val running: Flow<RunningWorkout?> = repository.observeInProgress().mapLatest { s ->
        s?.let { RunningWorkout(it.id, it.programDayId?.let { d -> repository.dayName(d) } ?: "Workout", it.startedAt) }
    }

    private val volume: Flow<Pair<List<RegionVolume>, WeightUnit>> = settingsRepository.settings.flatMapLatest { app ->
        val range = WeeklyVolume.weekRange(clock.nowMillis(), clock.zone())
        val targets = WeeklySets.parse(app.weeklySetsCsv).mapKeys { (k, _) -> BodyRegion.valueOf(k.name) }
        repository.observeVolumeSets(range).mapLatest { sets -> WeeklyVolume.compute(sets, targets, range) to app.weightUnit }
    }

    private val repeatLast: Flow<RepeatLast?> = repository.observeSessions().mapLatest { sessions ->
        val last = sessions.firstOrNull { it.finishedAt != null && it.programDayId != null } ?: return@mapLatest null
        val dayId = last.programDayId ?: return@mapLatest null
        repository.dayName(dayId)?.let { RepeatLast(dayId, it) }
    }

    val state: StateFlow<TrainHomeState> = combine(
        today,
        running,
        volume,
        repository.observeSessionStats(40),
        combine(repeatLast, repository.observeWorkouts(), busy) { r, w, b -> Triple(r, w, b) },
    ) { (program, card), run, (vol, unit), stats, (repeat, workouts, isBusy) ->
        val week = WeeklyVolume.weekRange(clock.nowMillis(), clock.zone())
        val thisWeek = stats.filter { it.startedAt in week }
        TrainHomeState(
            today = card,
            running = run,
            volume = vol,
            hint = WeeklyVolume.hint(vol),
            weekSessions = thisWeek.size,
            weekVolumeKg = thisWeek.sumOf { it.volumeKg },
            recent = stats.take(4),
            repeatLast = repeat,
            workouts = workouts,
            activeProgram = program,
            unit = unit,
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainHomeState())

    /** Starts (or resumes) a session on a program day, or an empty one when [dayId] is null. */
    fun start(dayId: String?, onStarted: (String) -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                onStarted(repository.startSession(dayId).id)
            } finally {
                busy.value = false
            }
        }
    }

    /** Every program with its days, for "From a program". Loaded when the sheet opens. */
    suspend fun programChoices(): List<ProgramChoice> {
        val active = repository.activeProgram()?.id
        return repository.observePrograms().first()
            .sortedByDescending { it.id == active }
            .map { p -> ProgramChoice(p.name, p.id == active, repository.days(p.id).map { DayPick(it.id, it.name, it.weekday) }) }
            .filter { it.days.isNotEmpty() }
    }
}
