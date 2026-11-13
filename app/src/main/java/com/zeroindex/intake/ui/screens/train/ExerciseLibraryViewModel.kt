package com.zeroindex.intake.ui.screens.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.zeroindex.intake.data.training.ExercisePhotoStore
import com.zeroindex.intake.data.db.entity.ExerciseEntity
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.training.ExerciseFilter
import com.zeroindex.intake.data.training.ExerciseHistory
import com.zeroindex.intake.data.training.ExerciseHistoryCalc
import com.zeroindex.intake.domain.training.SwapRanking
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
    /** The "My equipment" chip: only exercises you can do with what you own (Settings > Training). */
    val myEquipment: Boolean = false,
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
    private val photoStore: ExercisePhotoStore,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<String?>(null)
    private val equipment = MutableStateFlow<String?>(null)
    private val showHidden = MutableStateFlow(false)
    private val myEquipment = MutableStateFlow(false)

    private val source: Flow<List<ExerciseEntity>> = combine(
        repository.observeExercises(),
        repository.observeHiddenExercises(),
        showHidden,
    ) { visible, hidden, withHidden -> if (withHidden) hidden else visible }

    val state: StateFlow<ExerciseBrowseState> = combine(
        source,
        repository.observeExercises(),
        combine(query, muscle, equipment, showHidden, myEquipment) { q, m, e, h, mine -> arrayOf<Any?>(q, m, e, h, mine) },
        settingsRepository.settings,
    ) { list, visible, filters, settings ->
        val q = filters[0] as String
        val m = filters[1] as String?
        val e = filters[2] as String?
        val mine = filters[4] as Boolean
        val owned = settings.profile.equipment
        val ownedItems = settings.profile.effectiveEquipmentItems
        ExerciseBrowseState(
            shown = ExerciseFilter.apply(list, q, m, e).filter { !mine || SwapRanking.owns(it.name, it.equipment, owned, ownedItems) },
            myEquipment = mine,
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
    fun setMyEquipment(value: Boolean) { myEquipment.value = value }

    /** Adds a custom exercise from the form. */
    fun add(form: ExerciseForm, onAdded: (ExerciseEntity) -> Unit = {}) = viewModelScope.launch {
        onAdded(
            repository.createExercise(
                name = form.name.trim(),
                primaryMuscle = form.primaryMuscle.trim().lowercase(),
                equipment = form.equipment.trim().lowercase(),
                isBodyweight = form.isBodyweight,
                defaultRestSec = form.defaultRestSec,
                secondaryMuscles = form.secondaryMuscles.joinToString(","),
                howTo = form.howTo,
                photoPath = form.photoPath,
            ),
        )
    }

    /** Applies the form to an existing custom exercise. */
    fun update(existing: ExerciseEntity, form: ExerciseForm) = viewModelScope.launch {
        repository.updateExercise(
            existing.copy(
                name = form.name.trim(),
                primaryMuscle = form.primaryMuscle.trim().lowercase(),
                secondaryMuscles = form.secondaryMuscles.joinToString(","),
                equipment = form.equipment.trim().lowercase(),
                isBodyweight = form.isBodyweight,
                defaultRestSec = form.defaultRestSec,
                notes = form.howTo?.trim()?.ifBlank { null },
                photoPath = form.photoPath,
            ),
        )
    }

    /**
     * Stores a picked or captured photo as a small, EXIF-free JPEG in app storage and returns its file name,
     * or null if it could not be read. Photos are not part of backup or sync (docs/PRIVACY.md).
     */
    suspend fun importPhoto(uri: Uri): String? =
        photoStore.import(uri)

    /** A new empty file in the app cache for the camera to write to. */
    fun captureFile(): java.io.File = photoStore.newCaptureFile()

    fun save(exercise: ExerciseEntity) = viewModelScope.launch { repository.updateExercise(exercise) }
    fun hide(id: String) = viewModelScope.launch { repository.hideExercise(id) }
    fun restore(id: String) = viewModelScope.launch { repository.restoreExercise(id) }

    /** History and estimated max for one exercise; empty if it has never been lifted. */
    fun history(exerciseId: String): Flow<ExerciseHistory> =
        repository.observeExerciseSets(exerciseId).map { ExerciseHistoryCalc.summarize(it, java.util.TimeZone.getDefault().rawOffset.toLong()) }
}
