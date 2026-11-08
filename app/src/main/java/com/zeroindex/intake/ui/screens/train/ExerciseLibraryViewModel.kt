package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.seed.ProgramSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val programSeed: ProgramSeed,
) : ViewModel() {

    val exercises: StateFlow<List<ExerciseEntity>> = repository.observeExercises()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    init {
        // An empty library makes the builder useless, so fill it once.
        viewModelScope.launch { programSeed.ensureExerciseLibrary() }
    }

    fun add(name: String, primaryMuscle: String, equipment: String) = viewModelScope.launch {
        repository.createExercise(name = name, primaryMuscle = primaryMuscle, equipment = equipment)
    }
}
