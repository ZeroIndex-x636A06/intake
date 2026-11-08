package com.zeroindex.intake.ui.photo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.vision.PhotoPreparer
import com.zeroindex.intake.data.ai.vision.VisionResult
import com.zeroindex.intake.data.ai.vision.VisionTracker
import com.zeroindex.intake.domain.ai.EstimateLine
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.MealSlot
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PhotoStage {
    data object NeedsKey : PhotoStage
    data object Capture : PhotoStage
    data class Working(val message: String) : PhotoStage
    data class Review(
        val result: VisionResult.Ready,
        /** Corrections at 1x; what is shown and logged is these times [scale]. */
        val base: List<EstimateLine>,
        val scale: Double = 1.0,
        val confirmingAllergens: Boolean = false,
        val saving: Boolean = false,
    ) : PhotoStage {
        val shown: List<EstimateLine> get() = base.map { it.scaled(scale) }
    }
    data class Unreadable(val raw: String?, val reason: String) : PhotoStage
    data object NotFood : PhotoStage
    data class Failed(val message: String) : PhotoStage
}

data class PhotoUiState(val stage: PhotoStage = PhotoStage.Capture, val biasNote: String? = null)

@HiltViewModel
class PhotoEstimateViewModel @Inject constructor(
    private val tracker: VisionTracker,
    private val preparer: PhotoPreparer,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PhotoUiState(stage = if (tracker.isConfigured()) PhotoStage.Capture else PhotoStage.NeedsKey),
    )
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(biasNote = tracker.biasNote()) } }
    }

    fun newCaptureFile(): File = preparer.newCaptureFile()

    fun onPhoto(uri: Uri) {
        setStage(PhotoStage.Working("Preparing the photo…"))
        viewModelScope.launch {
            val photo = runCatching { preparer.prepare(uri) }.getOrElse {
                setStage(PhotoStage.Failed(it.message ?: "That photo could not be read."))
                return@launch
            }
            setStage(PhotoStage.Working("Estimating… this usually takes a few seconds"))
            val result = runCatching { tracker.estimate(photo) }.getOrElse {
                setStage(PhotoStage.Failed((it as? AiError)?.message ?: "The estimate failed. Try again."))
                return@launch
            }
            setStage(
                when (result) {
                    is VisionResult.Ready -> PhotoStage.Review(result, result.lines)
                    is VisionResult.Unreadable -> PhotoStage.Unreadable(result.raw, result.reason)
                    is VisionResult.NotFood -> PhotoStage.NotFood
                },
            )
        }
    }

    fun setScale(scale: Double) = review { it.copy(scale = scale.coerceIn(VisionMath.MIN_SCALE, VisionMath.MAX_SCALE)) }

    fun useCautious() = review { it.copy(scale = VisionMath.cautiousScale(it.base, it.result.estimate.range.kcalHigh)) }

    /** [line] arrives as displayed, i.e. already scaled; stored back at 1x. */
    fun editLine(index: Int, line: EstimateLine) = review { r ->
        r.copy(base = r.base.toMutableList().also { it[index] = line.scaled(1.0 / r.scale) })
    }

    fun removeLine(index: Int) = review { r -> r.copy(base = r.base.filterIndexed { i, _ -> i != index }) }

    fun retake() = setStage(if (tracker.isConfigured()) PhotoStage.Capture else PhotoStage.NeedsKey)

    /** SPEC §7: anything flagged goes through the allergen sheet first. */
    fun confirm(slot: MealSlot, at: Long, onDone: () -> Unit) {
        val r = _state.value.stage as? PhotoStage.Review ?: return
        if (r.result.allergens.hasAnything && !r.confirmingAllergens) {
            review { it.copy(confirmingAllergens = true) }
            return
        }
        save(r, slot, at, onDone)
    }

    fun cancelAllergenConfirm() = review { it.copy(confirmingAllergens = false) }

    fun logAnyway(slot: MealSlot, at: Long, onDone: () -> Unit) {
        val r = _state.value.stage as? PhotoStage.Review ?: return
        save(r, slot, at, onDone)
    }

    private fun save(r: PhotoStage.Review, slot: MealSlot, at: Long, onDone: () -> Unit) {
        review { it.copy(saving = true, confirmingAllergens = false) }
        viewModelScope.launch {
            tracker.log(
                finalLines = r.shown,
                originalLines = r.result.lines,
                confidence = when (r.result.estimate.overallConfidence.lowercase()) {
                    "high" -> AiConfidence.HIGH
                    "medium" -> AiConfidence.MEDIUM
                    else -> AiConfidence.LOW
                },
                slot = slot,
                at = at,
                photoPath = r.result.photoPath,
            )
            onDone()
        }
    }

    private fun setStage(stage: PhotoStage) = _state.update { it.copy(stage = stage) }

    private fun review(block: (PhotoStage.Review) -> PhotoStage.Review) = _state.update { s ->
        (s.stage as? PhotoStage.Review)?.let { s.copy(stage = block(it)) } ?: s
    }
}
