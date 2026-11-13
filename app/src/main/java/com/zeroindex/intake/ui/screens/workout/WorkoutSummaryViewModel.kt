package com.zeroindex.intake.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.settings.WeeklySets
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.training.NextTimeChange
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.RegionVolume
import com.zeroindex.intake.domain.training.SessionSummaries
import com.zeroindex.intake.domain.training.SessionSummary
import com.zeroindex.intake.domain.training.SummarySet
import com.zeroindex.intake.domain.training.WeeklyVolume
import com.zeroindex.intake.domain.training.muscles.BodyRegion
import com.zeroindex.intake.domain.units.WeightUnit
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** One exercise of the session on the summary: "3 × 8 @ 140", with its records. */
data class SummaryExercise(val name: String, val sets: Int, val detail: String, val records: List<PrKind>, val setLines: List<String> = emptyList())

data class WorkoutSummaryState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val title: String = "Workout",
    val summary: SessionSummary? = null,
    val unit: WeightUnit = WeightUnit.POUNDS,
    /** This calendar week, all regions, with the session already counted in. */
    val week: List<RegionVolume> = emptyList(),
    val exercises: List<SummaryExercise> = emptyList(),
    val nextTime: List<NextTimeChange> = emptyList(),
    /** True for a session with no program day and some working sets: "Keep this workout?" is offered. */
    val canKeep: Boolean = false,
    /** What was saved ("Saved as the workout \"Arm day\"."), once it was. */
    val kept: String? = null,
    val notes: String = "",
    val hint: String? = null,
    /** A session that was started from a program day can be started again on the same day. */
    val canRepeat: Boolean = false,
)

/**
 * The post-workout summary (IA v5 6f): records, totals, muscles worked against this week's targets, the exercises and the
 * next-time changes. It is also the read-only view of an older session opened from Recent sessions.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId: String = savedStateHandle.toRoute<Route.WorkoutSummary>().sessionId

    private val _state = MutableStateFlow(WorkoutSummaryState())
    val state: StateFlow<WorkoutSummaryState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val session = repository.session(sessionId)
        if (session == null) {
            _state.value = WorkoutSummaryState(loading = false)
            return
        }
        val settings = settingsRepository.settings.first()
        val title = session.programDayId?.let { repository.dayName(it) } ?: "Workout"
        val rows = repository.sessionSets(sessionId)
        val sets = rows.map {
            SummarySet(
                exerciseId = it.exerciseId,
                exerciseName = it.exerciseName,
                primaryMuscle = it.primaryMuscle,
                secondaryMuscles = it.secondaryMuscles.split(',').map { m -> m.trim() }.filter { m -> m.isNotEmpty() },
                weightKg = it.weightKg,
                reps = it.reps,
                isWarmup = it.isWarmup,
            )
        }
        val history = repository.historyBefore(sessionId, sets.map { it.exerciseId }.distinct())
        val summary = SessionSummaries.build(sets, history, session.startedAt, session.finishedAt ?: clock.nowMillis())

        val range = WeeklyVolume.weekRange(session.startedAt, clock.zone())
        val targets = WeeklySets.parse(settings.weeklySetsCsv).mapKeys { (k, _) -> BodyRegion.valueOf(k.name) }
        val week = WeeklyVolume.compute(repository.observeVolumeSets(range).first(), targets, range)

        val working = sets.filter { !it.isWarmup }
        val exercises = working.groupBy { it.exerciseId }.map { (_, list) ->
            val name = list.first().exerciseName
            val unit = settings.weightUnit
            val sameReps = list.map { it.reps }.distinct().size == 1
            val sameWeight = list.map { Math.round(it.weightKg * 100) }.distinct().size == 1
            val top = list.maxBy { it.weightKg }
            val detail = when {
                sameReps && sameWeight && top.weightKg > 0 -> "${list.size} × ${top.reps} @ ${fmt(unit.fromKg(top.weightKg))}"
                sameReps && sameWeight -> "${list.size} × ${top.reps}"
                top.weightKg > 0 -> "${list.size} sets · best ${fmt(unit.fromKg(top.weightKg))} × ${top.reps}"
                else -> "${list.size} sets · ${list.sumOf { it.reps }} reps"
            }
            val every = sets.filter { it.exerciseId == list.first().exerciseId }
            var n = 0
            val lines = every.map { s ->
                val weight = if (s.weightKg > 0) "${fmt(unit.fromKg(s.weightKg))} ${unit.label} × ${s.reps}" else "${s.reps} reps"
                if (s.isWarmup) "Warm-up: $weight" else "Set ${++n}: $weight"
            }
            SummaryExercise(name, list.size, detail, summary.records.filter { it.exerciseName == name }.map { it.kind }, lines)
        }

        val nextTime = nextTimeChanges(session, working)
        _state.value = WorkoutSummaryState(
            loading = false,
            session = session,
            title = title,
            summary = summary,
            unit = settings.weightUnit,
            week = week,
            exercises = exercises,
            nextTime = nextTime,
            canKeep = session.programDayId == null && working.isNotEmpty(),
            notes = session.notes.orEmpty(),
            hint = WeeklyVolume.hint(week),
            canRepeat = session.programDayId != null,
        )
    }

    /** What the program will ask for next time, for the exercises of this session. Only for the latest session of a day. */
    private suspend fun nextTimeChanges(session: WorkoutSessionEntity, working: List<SummarySet>): List<NextTimeChange> {
        val dayId = session.programDayId ?: return emptyList()
        val latest = repository.observeSessions().first().firstOrNull { it.finishedAt != null }
        if (latest?.id != session.id) return emptyList()
        val used = working.groupBy { it.exerciseId }.mapValues { (_, l) -> l.maxOf { it.weightKg } }
        return repository.prescriptionsFor(dayId, excludeSessionId = null).mapNotNull { p ->
            val usedKg = used[p.exercise.id] ?: return@mapNotNull null
            val next = p.prescription ?: return@mapNotNull null
            SessionSummaries.nextTime(p.exercise.name, usedKg, next.weightKg, next.repsMin, next.reason)
        }
    }

    /** Starts a new session on the same program day ("Repeat this workout"). */
    fun repeat(onStarted: (String) -> Unit) = viewModelScope.launch {
        val dayId = _state.value.session?.programDayId ?: return@launch
        onStarted(repository.startSession(dayId).id)
    }

    fun setNotes(text: String) { _state.value = _state.value.copy(notes = text) }

    /** Saves the notes if they changed. Called when the person taps Done. */
    fun saveNotes(then: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val session = s.session
        if (session != null && s.notes.trim() != session.notes.orEmpty().trim()) {
            repository.saveSessions(listOf(session.copy(notes = s.notes.trim().ifBlank { null }, updatedAt = clock.nowMillis())))
        }
        then()
    }

    fun keepAsWorkout(name: String) = viewModelScope.launch {
        val saved = repository.saveSessionAsWorkout(sessionId, name)
        _state.value = _state.value.copy(
            kept = if (saved != null) "Saved as the workout \"${saved.name}\". Find it in Programs > My workouts." else "Nothing to save.",
            canKeep = saved == null,
        )
    }

    fun keepAsProgramDay(name: String) = viewModelScope.launch {
        val day = repository.saveSessionAsProgramDay(sessionId, name)
        _state.value = _state.value.copy(
            kept = if (day != null) "Saved as the day \"${day.name}\" of your program." else "Nothing to save.",
            canKeep = day == null,
        )
    }
}
