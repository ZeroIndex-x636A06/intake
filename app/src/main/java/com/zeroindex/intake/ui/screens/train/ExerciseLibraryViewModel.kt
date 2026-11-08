package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.training.ExerciseFilter
import com.zeroindex.intake.data.training.ExerciseHistory
import com.zeroindex.intake.data.training.ExerciseHistoryCalc
import com.zeroindex.intake.domain.units.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseBrowseState(
    val shown: List<com.zeroindex.intake.data.db.entity.ExerciseEntity> = emptyList(),
    val total: Int = 0,
    val query: String = "",
    val muscle: String? = null,
    val equipment: String? = null,
    val showHidden: Boolean = false,
    val equipmentOptions: List<String> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KILOGRAMS,
)

/**
 * Shared by the exercise library and the exercise picker: search, muscle and equipment filters,
 * custom exercises (add, edit, hide, restore) and a per-exercise history. Hiding is a soft delete.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val repository: TrainingRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<String?>(null)
    private val equipment = MutableStateFlow<String?>(null)
    private val showHidden = MutableStateFlow(false)

    private val source: Flow<List<ExerciseEntity>> = combine(
        repository.observeExercises(),
        repository.observeHiddenExercises(),
        showHidden,
    ) { visible, hidden, withHidden -> if (withHidden) hidden else visible }

    val state: StateFlow<ExerciseBrowseState> = combine(
        source,
        repository.observeExercises(),
        combine(query, muscle, equipment, showHidden) { q, m, e, h -> arrayOf<Any?>(q, m, e, h) },
        settingsRepository.settings,
    ) { list, visible, filters, settings ->
        val q = filters[0] as String
        val m = filters[1] as String?
        val e = filters[2] as String?
        ExerciseBrowseState(
            shown = ExerciseFilter.apply(list, q, m, e),
            total = visible.size,
            query = q,
            muscle = m,
            equipment = e,
            showHidden = filters[3] as Boolean,
            equipmentOptions = ExerciseFilter.equipmentOf(visible),
            weightUnit = settings.weightUnit,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseBrowseState())

    init {
        // An empty or short library makes every builder useless, so top it up once.
        viewModelScope.launch { repository.ensureSeedExercises() }
    }

    fun setQuery(value: String) { query.value = value }
    fun setMuscle(value: String?) { muscle.value = value }
    fun setEquipment(value: String?) { equipment.value = value }
    fun setShowHidden(value: Boolean) { showHidden.value = value }

    fun add(
        name: String,
        primaryMuscle: String,
        equipment: String,
        isBodyweight: Boolean,
        defaultRestSec: Int,
        onAdded: (ExerciseEntity) -> Unit = {},
    ) = viewModelScope.launch {
        onAdded(
            repository.createExercise(
                name = name.trim(),
                primaryMuscle = primaryMuscle.trim().lowercase(),
                equipment = equipment.trim().lowercase(),
                isBodyweight = isBodyweight,
                defaultRestSec = defaultRestSec,
            ),
        )
    }

    fun save(exercise: ExerciseEntity) = viewModelScope.launch { repository.updateExercise(exercise) }
    fun hide(id: String) = viewModelScope.launch { repository.hideExercise(id) }
    fun restore(id: String) = viewModelScope.launch { repository.restoreExercise(id) }

    /** History and estimated max for one exercise; empty if it has never been lifted. */
    fun history(exerciseId: String): Flow<ExerciseHistory> =
        repository.observeExerciseSets(exerciseId).map { ExerciseHistoryCalc.summarize(it, java.util.TimeZone.getDefault().rawOffset.toLong()) }
}
