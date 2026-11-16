package com.zeroindex.intake.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.training.PlateCalculator
import com.zeroindex.intake.domain.training.PlateLoad
import com.zeroindex.intake.domain.training.PrDetector
import com.zeroindex.intake.domain.training.PrKind
import com.zeroindex.intake.domain.training.Prescription
import com.zeroindex.intake.domain.training.RankedSwap
import com.zeroindex.intake.domain.training.SessionProgress
import com.zeroindex.intake.domain.training.SetResult
import com.zeroindex.intake.domain.training.SwapCandidate
import com.zeroindex.intake.domain.training.SwapRanking
import com.zeroindex.intake.domain.training.SwapWeight
import com.zeroindex.intake.domain.training.WarmupGenerator
import com.zeroindex.intake.domain.training.WarmupSet
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveWorkoutUiState(
    val sessionId: String = "",
    val session: WorkoutSessionEntity? = null,
    val title: String = "Workout",
    val settings: AppSettings = AppSettings(),
    val prescriptions: List<ExercisePrescription> = emptyList(),
    val loggedSets: List<SetLogEntity> = emptyList(),
    /** Working sets of earlier sessions per exercise: what the live PR flag compares with. */
    val history: Map<String, List<SetResult>> = emptyMap(),
    /** Set id to the record it set (only sets that are one). */
    val records: Map<String, PrKind> = emptyMap(),
    /** "+ Add set" taps per exercise this session. */
    val extraRows: Map<String, Int> = emptyMap(),
    /** The exercise the person picked from Up next, or null to follow the first one with sets left. */
    val picked: Int? = null,
    val loading: Boolean = true,
    /** True when the session has no program day: exercises are picked as you go. */
    val isFreestyle: Boolean = false,
) {
    /** At least one working set has been logged. */
    val hasWorkingSets: Boolean get() = loggedSets.any { !it.isWarmup }

    private val slots: List<SessionProgress.Slot>
        get() = prescriptions.map { p ->
            SessionProgress.Slot(
                plannedSets = (p.prescription?.sets ?: 0) + (extraRows[p.exercise.id] ?: 0),
                loggedWorkingSets = setsFor(p.exercise.id).count { !it.isWarmup },
            )
        }

    val totalSets: Int get() = SessionProgress.total(slots)
    val doneSets: Int get() = SessionProgress.done(slots)

    /** Index of the exercise shown expanded. */
    val currentIndex: Int
        get() = (picked ?: SessionProgress.currentIndex(slots)).coerceIn(0, maxOf(0, prescriptions.lastIndex))

    val current: ExercisePrescription? get() = prescriptions.getOrNull(currentIndex)

    fun setsFor(exerciseId: String): List<SetLogEntity> =
        loggedSets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }

    /** SPEC §10: which plates per side for a weight. */
    fun plateLoad(weightKg: Double): PlateLoad = PlateCalculator.calculate(
        targetKg = weightKg,
        barWeightKg = settings.barWeightKg,
        availablePlatesKg = settings.platesKgList,
    )

    fun warmups(weightKg: Double): List<WarmupSet> = WarmupGenerator.generate(
        workingWeightKg = weightKg,
        barWeightKg = settings.barWeightKg,
        incrementKg = settings.platesKgList.minOrNull()?.times(2) ?: 2.5,
    )
}

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        savedStateHandle.toRoute<Route.ActiveWorkout>().sessionId

    private val prescriptions = MutableStateFlow<List<ExercisePrescription>>(emptyList())
    private val history = MutableStateFlow<Map<String, List<SetResult>>>(emptyMap())
    private val extraRows = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val picked = MutableStateFlow<Int?>(null)
    private val meta = MutableStateFlow(Triple<WorkoutSessionEntity?, String, Boolean>(null, "Workout", false))
    private val loading = MutableStateFlow(true)

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        repository.observeSets(sessionId),
        settingsRepository.settings,
        prescriptions,
        combine(history, extraRows, picked) { h, e, p -> Triple(h, e, p) },
        combine(meta, loading) { m, l -> m to l },
    ) { sets, settings, list, (hist, extra, pick), (m, isLoading) ->
        ActiveWorkoutUiState(
            sessionId = sessionId,
            session = m.first,
            title = m.second,
            settings = settings,
            prescriptions = list,
            loggedSets = sets,
            history = hist,
            records = records(sets, hist),
            extraRows = extra,
            picked = pick,
            loading = isLoading,
            isFreestyle = m.third,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(sessionId = sessionId),
    )

    init {
        load()
    }

    /** Which of the logged sets set a record, in the order they were done: earlier sets of the day count as history too. */
    private fun records(sets: List<SetLogEntity>, hist: Map<String, List<SetResult>>): Map<String, PrKind> {
        val out = mutableMapOf<String, PrKind>()
        sets.filter { !it.isWarmup }.groupBy { it.exerciseId }.forEach { (exerciseId, rows) ->
            val past = hist[exerciseId].orEmpty().toMutableList()
            rows.sortedBy { it.completedAt }.forEach { s ->
                val kind = PrDetector.check(s.weightKg, s.reps, past)
                if (kind != PrKind.NONE) out[s.id] = kind
                past += SetResult(s.weightKg, s.reps)
            }
        }
        return out
    }

    private fun load() = viewModelScope.launch {
        val session = repository.session(sessionId)
        val dayId = session?.programDayId
        val title = dayId?.let { repository.dayName(it) } ?: "Workout"
        meta.value = Triple(session, title, dayId == null)
        val base = repository.prescriptionsFor(
            programDayId = dayId,
            // The current session's own sets must not feed its prescriptions.
            excludeSessionId = sessionId,
        )
        // Exercises that were added or swapped in and already have sets (this is a resumed session) stay in the list.
        val have = base.map { it.exercise.id }.toSet()
        val extra = repository.sets(sessionId).sortedBy { it.completedAt }.map { it.exerciseId }.distinct()
            .filter { it !in have }
            .mapNotNull { repository.freestylePrescription(it, sessionId) }
        val all = base + extra
        prescriptions.value = all
        history.value = repository.historyBefore(sessionId, all.map { it.exercise.id })
        loading.value = false
    }

    fun pick(index: Int) { picked.value = index }

    /** Adds an exercise to this workout (any workout, not just freestyle) and jumps to it. */
    fun addExercise(exerciseId: String) = viewModelScope.launch {
        val existing = prescriptions.value.indexOfFirst { it.exercise.id == exerciseId }
        if (existing >= 0) {
            picked.value = existing
            return@launch
        }
        val added = repository.freestylePrescription(exerciseId, sessionId) ?: return@launch
        prescriptions.value = prescriptions.value + added
        history.value = history.value + repository.historyBefore(sessionId, listOf(exerciseId))
        picked.value = prescriptions.value.lastIndex
    }

    /** "+ Add set": one more row for this exercise. */
    fun addSet(exerciseId: String) {
        extraRows.value = extraRows.value + (exerciseId to ((extraRows.value[exerciseId] ?: 0) + 1))
    }

    /** Move an exercise one place earlier or later (a rack might be taken). */
    fun move(index: Int, delta: Int) {
        val list = prescriptions.value.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(index))
        prescriptions.value = list
        picked.value = to
    }

    /** The exercises that could stand in for [exerciseId], best first: same muscles, then what the person owns. */
    suspend fun swapOptions(exerciseId: String): List<RankedSwap> {
        val current = prescriptions.value.firstOrNull { it.exercise.id == exerciseId }?.exercise ?: return emptyList()
        val profile = settingsRepository.settings.first().profile
        val owned = profile.equipment
        val inSession = prescriptions.value.map { it.exercise.id }.toSet()
        return SwapRanking.rank(
            current = current.toCandidate(),
            candidates = repository.exercises().map { it.toCandidate() },
            owned = owned,
            excludeIds = inSession,
            ownedItems = profile.effectiveEquipmentItems,
        )
    }

    /**
     * Replaces [exerciseId] with [newId] for this session only. The new exercise keeps the plan's sets and rep range; the
     * weight is its own last working weight, or a conservative estimate from the weight of the one it replaces.
     */
    fun swap(exerciseId: String, newId: String) = viewModelScope.launch {
        val index = prescriptions.value.indexOfFirst { it.exercise.id == exerciseId }
        if (index < 0) return@launch
        val old = prescriptions.value[index]
        val fresh = repository.freestylePrescription(newId, sessionId) ?: return@launch
        val step = settingsRepository.settings.first().platesKgList.minOrNull()?.times(2) ?: 2.5
        val ownLast = fresh.lastSession.maxOfOrNull { it.weightKg }?.takeIf { it > 0 }
        val oldWeight = old.prescription?.weightKg ?: old.lastSession.maxOfOrNull { it.weightKg }
        val weight = SwapWeight.carryOver(ownLast, oldWeight, old.exercise.equipment, fresh.exercise.equipment, step)
        val plan = old.prescription
        val prescription = Prescription(
            weightKg = weight ?: 0.0,
            sets = plan?.sets ?: fresh.prescription?.sets ?: 3,
            repsMin = plan?.repsMin ?: fresh.prescription?.repsMin ?: 8,
            repsMax = plan?.repsMax ?: fresh.prescription?.repsMax ?: 12,
            targetRpe = plan?.targetRpe,
            reason = when {
                ownLast != null -> "Swapped in for ${old.exercise.name}. Your last weight on this one."
                weight != null -> "Swapped in for ${old.exercise.name}. A cautious estimate from that weight: change it as you like."
                else -> "Swapped in for ${old.exercise.name}. Pick a starting weight."
            },
        )
        val replaced = fresh.copy(programExercise = null, prescription = prescription)
        prescriptions.value = prescriptions.value.toMutableList().also { it[index] = replaced }
        history.value = history.value + repository.historyBefore(sessionId, listOf(newId))
        picked.value = index
    }

    /** "Save as workout": keeps this session as a saved single-session workout in My workouts. */
    fun saveAsWorkout(name: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(repository.saveSessionAsWorkout(sessionId, name) != null)
    }

    fun logSet(
        exerciseId: String,
        setIndex: Int,
        weightKg: Double,
        reps: Int,
        rpe: Double?,
        isWarmup: Boolean,
        isFailure: Boolean,
        restTakenSec: Int?,
    ) = viewModelScope.launch {
        repository.logSet(
            sessionId = sessionId,
            exerciseId = exerciseId,
            setIndex = setIndex,
            weightKg = weightKg,
            reps = reps,
            rpe = rpe,
            isWarmup = isWarmup,
            isFailure = isFailure,
            restTakenSec = restTakenSec,
        )
    }

    fun deleteSet(id: String) = viewModelScope.launch { repository.deleteSet(id) }

    /** Finishes the session, then reports its id so the screen can open the summary. */
    fun finish(onFinished: (sessionId: String) -> Unit) = viewModelScope.launch {
        repository.finishSession(sessionId)
        onFinished(sessionId)
    }

    /** "Discard": the session and its sets are hidden (a soft delete). */
    fun discard(onDone: () -> Unit) = viewModelScope.launch {
        repository.discardSession(sessionId)
        onDone()
    }

    private fun ExerciseEntity.toCandidate() = SwapCandidate(
        id = id,
        name = name,
        primaryMuscle = primaryMuscle,
        secondaryMuscles = secondaryMuscles.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        equipment = equipment,
    )
}
