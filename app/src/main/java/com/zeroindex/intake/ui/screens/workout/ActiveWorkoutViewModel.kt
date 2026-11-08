package com.zeroindex.intake.ui.screens.workout

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.SetLogEntity
import com.zeroindex.intake.data.db.entity.WorkoutSessionEntity
import com.zeroindex.intake.data.repository.ExercisePrescription
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.AppSettings
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.training.PlateCalculator
import com.zeroindex.intake.domain.training.PlateLoad
import com.zeroindex.intake.domain.training.WarmupGenerator
import com.zeroindex.intake.domain.training.WarmupSet
import com.zeroindex.intake.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveWorkoutUiState(
    val sessionId: String = "",
    val session: WorkoutSessionEntity? = null,
    val settings: AppSettings = AppSettings(),
    val prescriptions: List<ExercisePrescription> = emptyList(),
    val loggedSets: List<SetLogEntity> = emptyList(),
    val currentIndex: Int = 0,
    val finished: Boolean = false,
    /** True when the session has no program day: exercises are picked as you go. */
    val isFreestyle: Boolean = false,
) {
    /** At least one working set has been logged. */
    val hasWorkingSets: Boolean get() = loggedSets.any { !it.isWarmup }

    val current: ExercisePrescription? get() = prescriptions.getOrNull(currentIndex)

    fun setsFor(exerciseId: String): List<SetLogEntity> =
        loggedSets.filter { it.exerciseId == exerciseId }.sortedBy { it.setIndex }

    /** SPEC §10: which plates per side for the prescribed weight. */
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
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: String =
        savedStateHandle.toRoute<Route.ActiveWorkout>().sessionId

    private val prescriptions = MutableStateFlow<List<ExercisePrescription>>(emptyList())
    private val currentIndex = MutableStateFlow(0)
    private val finished = MutableStateFlow(false)
    private val freestyle = MutableStateFlow(false)

    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        repository.observeSets(sessionId),
        settingsRepository.settings,
        prescriptions,
        currentIndex,
        combine(finished, freestyle) { f, free -> f to free },
    ) { sets, settings, prescriptionList, index, finishedAndFree ->
        val (isFinished, isFreestyle) = finishedAndFree
        ActiveWorkoutUiState(
            sessionId = sessionId,
            settings = settings,
            prescriptions = prescriptionList,
            loggedSets = sets,
            currentIndex = index.coerceIn(0, maxOf(0, prescriptionList.lastIndex)),
            finished = isFinished,
            isFreestyle = isFreestyle,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(sessionId = sessionId),
    )

    init {
        loadPrescriptions()
    }

    private fun loadPrescriptions() = viewModelScope.launch {
        val session = repository.session(sessionId)
        freestyle.value = session?.programDayId == null
        prescriptions.value = repository.prescriptionsFor(
            programDayId = session?.programDayId,
            // The current session's own sets must not feed its prescriptions.
            excludeSessionId = sessionId,
        )
    }

    fun selectExercise(index: Int) { currentIndex.value = index }

    /** Adds an exercise to this workout (any workout, not just freestyle) and jumps to it. */
    fun addExercise(exerciseId: String) = viewModelScope.launch {
        val existing = prescriptions.value.indexOfFirst { it.exercise.id == exerciseId }
        if (existing >= 0) {
            currentIndex.value = existing
            return@launch
        }
        val added = repository.freestylePrescription(exerciseId, sessionId) ?: return@launch
        prescriptions.value = prescriptions.value + added
        currentIndex.value = prescriptions.value.lastIndex
    }

    /** "Save as a program day": returns the day name saved, or null if nothing could be saved. */
    fun saveAsProgramDay(name: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val day = repository.saveSessionAsProgramDay(sessionId, name)
        onDone(day != null)
    }

    fun nextExercise() {
        currentIndex.value = (currentIndex.value + 1)
            .coerceAtMost(maxOf(0, prescriptions.value.lastIndex))
    }

    fun previousExercise() {
        currentIndex.value = (currentIndex.value - 1).coerceAtLeast(0)
    }

    /** SPEC §10: reorder mid-session, because a rack might be taken. */
    fun moveExercise(from: Int, to: Int) {
        val list = prescriptions.value.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        prescriptions.value = list
        currentIndex.value = to
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

    fun finish(onFinished: () -> Unit) = viewModelScope.launch {
        repository.finishSession(sessionId)
        finished.value = true
        onFinished()
    }
}
