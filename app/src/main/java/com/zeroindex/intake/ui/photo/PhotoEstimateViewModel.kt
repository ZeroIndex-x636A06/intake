package com.zeroindex.intake.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.vision.PhotoPreparer
import com.zeroindex.intake.data.ai.vision.VisionResult
import com.zeroindex.intake.data.ai.vision.VisionTracker
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.EstimateLine
import com.zeroindex.intake.domain.ai.PhotoItem
import com.zeroindex.intake.domain.ai.PhotoItems
import com.zeroindex.intake.domain.ai.VisionContext
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.PortionUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PhotoStage {
    data object NeedsKey : PhotoStage
    data object Capture : PhotoStage

    /** [startedAt] lets the screen show how long it has been waiting. */
    data class Working(val message: String, val startedAt: Long) : PhotoStage

    data class Review(
        val result: VisionResult.Ready,
        /** Every food found, at 1x; what is shown and logged is the ticked ones times [scale]. */
        val items: List<PhotoItem>,
        val scale: Double = 1.0,
        /** Set when the final allergen check found something, to open the warning sheet. */
        val confirmingAllergens: AllergenScanResult? = null,
        val saving: Boolean = false,
    ) : PhotoStage {
        val shown: List<PhotoItem> get() = PhotoItems.shown(items, scale)
        val ticked: Int get() = items.count { it.included }
    }

    data class Unreadable(val reason: String) : PhotoStage
    data object NotFood : PhotoStage
    data class Failed(val message: String) : PhotoStage
}

data class PhotoUiState(
    val stage: PhotoStage = PhotoStage.Capture,
    val biasNote: String? = null,
    /** The "Anything we can't see?" note, kept while the person retakes. */
    val extraContext: String = "",
    /** Portions are shown and typed in this unit; they are stored and logged in grams. */
    val unit: PortionUnit = PortionUnit.GRAMS,
)

@HiltViewModel
class PhotoEstimateViewModel @Inject constructor(
    private val tracker: VisionTracker,
    private val preparer: PhotoPreparer,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PhotoUiState(stage = if (tracker.isConfigured()) PhotoStage.Capture else PhotoStage.NeedsKey),
    )
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch { _state.update { it.copy(biasNote = tracker.biasNote()) } }
        // Portions follow the weight unit in Settings: pounds show ounces, kilograms show grams.
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.update { it.copy(unit = PortionUnit.forWeightUnit(s.weightUnit)) }
            }
        }
    }

    fun newCaptureFile(): File = preparer.newCaptureFile()

    fun setContext(text: String) = _state.update { it.copy(extraContext = text.take(VisionContext.MAX_CHARS)) }

    fun onPhoto(uri: Uri) {
        setStage(PhotoStage.Working("Preparing the photo…", clock.nowMillis()))
        val note = _state.value.extraContext
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val photo = try {
                    preparer.prepare(uri)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    setStage(PhotoStage.Failed(e.message ?: "That photo could not be read."))
                    return@launch
                }
                setStage(PhotoStage.Working("Estimating… this can take up to two minutes", clock.nowMillis()))
                val result = try {
                    tracker.estimate(photo, note)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiError) {
                    setStage(PhotoStage.Failed(e.message))
                    return@launch
                } catch (e: Exception) {
                    setStage(PhotoStage.Failed("The estimate failed. Try again."))
                    return@launch
                }
                setStage(
                    when (result) {
                        is VisionResult.Ready -> PhotoStage.Review(result, result.items)
                        is VisionResult.Unreadable -> PhotoStage.Unreadable(result.reason)
                        is VisionResult.NotFood -> PhotoStage.NotFood
                    },
                )
            } catch (_: CancellationException) {
                // Cancel was pressed: retake() already moved the screen back to the camera.
            }
        }
    }

    /** Stops a request in flight (the OkHttp call is cancelled with the coroutine) and returns to the camera. */
    fun cancel() {
        job?.cancel()
        retake()
    }

    fun setScale(scale: Double) = review { it.copy(scale = scale.coerceIn(VisionMath.MIN_SCALE, VisionMath.MAX_SCALE)) }

    fun useCautious() = review { r ->
        val ticked = r.items.filter { it.included }.map { it.line }
        r.copy(scale = VisionMath.cautiousScale(ticked, r.result.estimate.range.kcalHigh))
    }

    fun toggle(id: Int) = review { it.copy(items = PhotoItems.toggle(it.items, id)) }

    fun rename(id: Int, name: String) = review { it.copy(items = PhotoItems.rename(it.items, id, name)) }

    /** [text] is what was typed, in the person's unit. */
    fun setPortion(id: Int, text: String) = review { r ->
        val grams = _state.value.unit.parseToGrams(text) ?: return@review r
        r.copy(items = PhotoItems.setGrams(r.items, id, grams, r.scale))
    }

    fun setMacro(id: Int, change: (EstimateLine) -> EstimateLine) = review { r ->
        r.copy(items = PhotoItems.setMacro(r.items, id, r.scale, change))
    }

    fun retake() = setStage(if (tracker.isConfigured()) PhotoStage.Capture else PhotoStage.NeedsKey)

    /** SPEC §7: anything flagged goes through the allergen sheet first, checked again on the foods as edited. */
    fun confirm(slot: MealSlot, at: Long, unit: PortionUnit, onDone: () -> Unit) {
        val r = _state.value.stage as? PhotoStage.Review ?: return
        if (r.saving) return
        review { it.copy(saving = true) }
        viewModelScope.launch {
            val scan = tracker.recheck(r.result.estimate, r.shown)
            if (scan.hasAnything) {
                review { it.copy(confirmingAllergens = scan, saving = false) }
            } else {
                save(r, slot, at, unit, onDone)
            }
        }
    }

    fun cancelAllergenConfirm() = review { it.copy(confirmingAllergens = null, saving = false) }

    fun logAnyway(slot: MealSlot, at: Long, unit: PortionUnit, onDone: () -> Unit) {
        val r = _state.value.stage as? PhotoStage.Review ?: return
        review { it.copy(confirmingAllergens = null, saving = true) }
        viewModelScope.launch { save(r, slot, at, unit, onDone) }
    }

    private suspend fun save(r: PhotoStage.Review, slot: MealSlot, at: Long, unit: PortionUnit, onDone: () -> Unit) {
        tracker.log(
            shown = r.shown,
            originals = r.result.lines.mapIndexed { i, l -> i to l }.toMap(),
            confidence = when (r.result.estimate.overallConfidence.lowercase()) {
                "high" -> AiConfidence.HIGH
                "medium" -> AiConfidence.MEDIUM
                else -> AiConfidence.LOW
            },
            slot = slot,
            at = at,
            photoPath = r.result.photoPath,
            unit = unit,
        )
        onDone()
    }

    private fun setStage(stage: PhotoStage) = _state.update { it.copy(stage = stage) }

    private fun review(block: (PhotoStage.Review) -> PhotoStage.Review) = _state.update { s ->
        (s.stage as? PhotoStage.Review)?.let { s.copy(stage = block(it)) } ?: s
    }
}
