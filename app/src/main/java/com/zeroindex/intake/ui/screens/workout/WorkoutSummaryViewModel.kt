package com.zeroindex.intake.ui.screens.workout

import com.zeroindex.intake.domain.units.WeightText
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
import com.zeroindex.intake.domain.training.SetEdit
import com.zeroindex.intake.domain.training.history.ReopenOutcome
import com.zeroindex.intake.domain.training.history.SessionEditor
import com.zeroindex.intake.domain.training.history.TimeOutcome
import java.time.LocalDate
import java.time.LocalTime
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
data class SummaryExercise(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val detail: String,
    val records: List<PrKind>,
    val setLines: List<String> = emptyList(),
    /** The same sets as [setLines], one per line, with what is needed to change or remove them. */
    val setRows: List<SummarySetRow> = emptyList(),
)

/** One set line of the summary that can be tapped to edit it. */
data class SummarySetRow(val setId: String, val label: String, val weightKg: Double, val reps: Int, val isWarmup: Boolean)

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
    /** Any past workout can be started again: a program day again, or a blank workout with the same exercises. */
    val canRepeat: Boolean = false,
    /** Whether Undo Finish is possible, and if not why (see ReopenRules). Null while loading. */
    val reopen: ReopenOutcome? = null,
    /** The time of the last logged set, so a new length cannot cut it off. */
    val lastSetAt: Long? = null,
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
    private val pauseStore: com.zeroindex.intake.data.training.WorkoutPauseStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val sessionId: String = savedStateHandle.toRoute<Route.WorkoutSummary>().sessionId

    private val _state = MutableStateFlow(WorkoutSummaryState())
    val state: StateFlow<WorkoutSummaryState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    /** Reads everything again: after a set is changed, the records, volume and muscle totals follow from the sets. */
    private suspend fun load() {
        val session = repository.session(sessionId)
        if (session == null) {
            _state.value = WorkoutSummaryState(loading = false)
            return
        }
        val settings = settingsRepository.settings.first()
        val title = session.programDayId?.let { repository.dayName(it) } ?: "Workout"
        val rows = repository.sessionSets(sessionId)
        val setIdByIndex = java.util.IdentityHashMap<SummarySet, String>()
        val sets = rows.map {
            SummarySet(
                exerciseId = it.exerciseId,
                exerciseName = it.exerciseName,
                primaryMuscle = it.primaryMuscle,
                secondaryMuscles = it.secondaryMuscles.split(',').map { m -> m.trim() }.filter { m -> m.isNotEmpty() },
                weightKg = it.weightKg,
                reps = it.reps,
                isWarmup = it.isWarmup,
            ).also { s -> setIdByIndex[s] = it.setId }
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
                sameReps && sameWeight && top.weightKg > 0 -> "${list.size} × ${top.reps} @ ${WeightText.format(top.weightKg, unit)}"
                sameReps && sameWeight -> "${list.size} × ${top.reps}"
                top.weightKg > 0 -> "${list.size} sets · best ${WeightText.format(top.weightKg, unit)} × ${top.reps}"
                else -> "${list.size} sets · ${list.sumOf { it.reps }} reps"
            }
            val every = sets.filter { it.exerciseId == list.first().exerciseId }
            var n = 0
            val setRows = every.map { s ->
                val weight = if (s.weightKg > 0) "${WeightText.format(s.weightKg, unit)} × ${s.reps}" else "${s.reps} reps"
                val label = if (s.isWarmup) "Warm-up: $weight" else "Set ${++n}: $weight"
                SummarySetRow(setIdByIndex[s]!!, label, s.weightKg, s.reps, s.isWarmup)
            }
            SummaryExercise(
                exerciseId = list.first().exerciseId,
                name = name,
                sets = list.size,
                detail = detail,
                records = summary.records.filter { it.exerciseName == name }.map { it.kind },
                setLines = setRows.map { it.label },
                setRows = setRows,
            )
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
            // A reload after an edit keeps what was typed in the notes and the "saved as" message.
            notes = if (_state.value.loading) session.notes.orEmpty() else _state.value.notes,
            kept = _state.value.kept,
            hint = WeeklyVolume.hint(week),
            canRepeat = true,
            reopen = repository.reopenCheck(sessionId),
            lastSetAt = rows.maxOfOrNull { it.completedAt },
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

    /** Starts a new session that repeats this one. The id is null when another workout is already running. */
    fun repeat(onResult: (String?) -> Unit) = viewModelScope.launch {
        onResult(repository.repeatSession(sessionId)?.id)
    }

    /** The reason a new date, start or length cannot be used, in plain words, or null when it can. */
    fun timeProblem(date: LocalDate, time: LocalTime, minutes: Int?): String? {
        val session = _state.value.session ?: return null
        if (minutes == null) return "Type the length in minutes."
        val start = SessionEditor.combine(date, time, clock.zone())
        val out = SessionEditor.retime(session.startedAt, session.finishedAt, _state.value.lastSetAt, start, minutes, clock.nowMillis())
        return (out as? TimeOutcome.Refused)?.let { SessionEditor.words(it.problem) }
    }

    /** Saves a new date, start time and length; the sets move with the start. */
    fun retime(date: LocalDate, time: LocalTime, minutes: Int) = viewModelScope.launch {
        repository.retimeSession(sessionId, SessionEditor.combine(date, time, clock.zone()), minutes)
        load()
    }

    /** The name of an exercise just picked, for the "Add a set" dialog. */
    fun askAddSet(exerciseId: String, onName: (String) -> Unit) = viewModelScope.launch {
        onName(repository.exerciseAnyState(exerciseId)?.name ?: "this exercise")
    }

    /** Adds a set (typed in the person's unit) to an exercise of this finished workout. */
    fun addSet(exerciseId: String, weightText: String, repsText: String, onAdded: (setId: String) -> Unit = {}) = viewModelScope.launch {
        val values = SetEdit.parse(weightText, repsText, _state.value.unit) ?: return@launch
        val added = repository.addSetToSession(sessionId, exerciseId, values.weightKg, values.reps)
        load()
        if (added != null) onAdded(added.id)
    }

    /** Moves a logged set to another exercise. */
    fun changeExercise(setId: String, newExerciseId: String) = viewModelScope.launch {
        repository.changeSetExercise(setId, newExerciseId)
        load()
    }

    /**
     * Undo Finish. [onOpened] gets the session id to open as the active workout. When another workout is running and
     * [finishTheOther] is false, nothing changes and [onConflict] is called so the person can choose.
     */
    fun reopen(finishTheOther: Boolean, onOpened: (String) -> Unit, onConflict: () -> Unit) = viewModelScope.launch {
        val finishedAt = repository.session(sessionId)?.finishedAt
        val now = clock.nowMillis()
        when (repository.reopenSession(sessionId, finishTheOther)) {
            ReopenOutcome.Allowed -> {
                // The idle time since Finish is not trained time: the clock and the next Finish leave it out, and the start the
                // person set stays as it is (Q8-6).
                if (finishedAt != null) {
                    pauseStore.reopen(sessionId, com.zeroindex.intake.domain.training.history.ReopenRules.idleGap(finishedAt, now), now)
                }
                onOpened(sessionId)
            }
            is ReopenOutcome.AnotherActive -> onConflict()
            else -> load()
        }
    }

    /** Changes a logged set's weight and reps (typed in the person's unit), then recomputes records and totals. */
    fun editSet(setId: String, weightText: String, repsText: String) = viewModelScope.launch {
        val values = SetEdit.parse(weightText, repsText, _state.value.unit) ?: return@launch
        repository.editSet(setId, values.weightKg, values.reps)
        load()
    }

    /** Takes a set out of the session (a soft delete); [restoreSet] is the Undo. */
    fun removeSet(setId: String) = viewModelScope.launch {
        repository.deleteSet(setId)
        load()
    }

    fun restoreSet(setId: String) = viewModelScope.launch {
        repository.restoreSet(setId)
        load()
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
