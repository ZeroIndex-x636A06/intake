package com.zeroindex.intake.data.ai.vision

import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.db.dao.AiCorrectionDao
import com.zeroindex.intake.data.db.entity.AiCorrectionEntity
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.EstimateLine
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VisionResult {
    val photoPath: String

    data class Ready(
        val estimate: VisionEstimate,
        val lines: List<EstimateLine>,
        /** One scan for the whole plate, for the allergen sheet. */
        val allergens: AllergenScanResult,
        /** Flagged lines by index, with the worst kind found on each. */
        val flaggedLines: Map<Int, AllergenMatchKind>,
        val model: String,
        val costUsd: Double?,
        override val photoPath: String,
    ) : VisionResult

    /** SPEC §6.5: show what came back and offer manual logging, never a toast. */
    data class Unreadable(val raw: String?, val reason: String, override val photoPath: String) : VisionResult

    data class NotFood(override val photoPath: String) : VisionResult
}

/**
 * Amendment §1 Vision Tracker on top of SPEC §6. Allergens follow
 * [ModelAllergenPolicy]: the model can raise a warning but never clear one.
 */
@Singleton
class VisionTracker @Inject constructor(
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val foodLog: FoodLogRepository,
    private val corrections: AiCorrectionDao,
    private val clock: Clock,
) {
    suspend fun estimate(photo: PreparedPhoto): VisionResult {
        val avoided = allergens.avoidedAllergens()
        val outcome = try {
            gateway.structured(
                feature = AiFeature.PHOTO_ESTIMATE,
                trigger = AiTrigger.USER_ACTION,
                serializer = VisionEstimate.serializer(),
                schema = VisionSchema.schema,
                system = VisionSchema.systemPrompt(avoided),
                parts = listOf(
                    AiPart.Image(photo.jpeg),
                    AiPart.Text("Estimate this meal."),
                ),
            )
        } catch (e: AiError.BadResponse) {
            return VisionResult.Unreadable(e.raw, e.message, photo.savedPath)
        }

        val estimate = outcome.value
        if (estimate.notFood || estimate.items.isEmpty()) return VisionResult.NotFood(photo.savedPath)

        val lines = estimate.items.map {
            EstimateLine(it.name, it.estimatedGrams, it.kcal, it.protein, it.carbs, it.fat)
        }
        val (scan, flagged) = scanPlate(estimate, avoided)
        return VisionResult.Ready(
            estimate = estimate,
            lines = lines,
            allergens = scan,
            flaggedLines = flagged,
            model = outcome.response.model,
            costUsd = outcome.response.costUsd,
            photoPath = photo.savedPath,
        )
    }

    private suspend fun scanPlate(
        estimate: VisionEstimate,
        avoided: Set<String>,
    ): Pair<AllergenScanResult, Map<Int, AllergenMatchKind>> {
        val verdict = ModelAllergenPolicy.merge(
            local = estimate.items.map { allergens.scan(name = it.name) },
            claims = estimate.items.map { ModelAllergenClaim(it.name, it.containsAllergens, it.allergenReasoning) },
            avoided = avoided,
            // Hidden fats and sauces are where dairy hides on a plate.
            extraLocal = estimate.hiddenCalorieSources.map { allergens.scan(name = null, ingredients = it.source) },
            source = "The photo model",
        )
        return verdict.scan to verdict.perItem
    }

    /**
     * Logs the reviewed lines, one entry each, and records a correction for
     * every line the user moved by more than 1% - including through the
     * slider, which is a correction too (SPEC §6.9).
     */
    suspend fun log(
        finalLines: List<EstimateLine>,
        originalLines: List<EstimateLine>,
        confidence: AiConfidence,
        slot: MealSlot,
        at: Long,
        photoPath: String,
    ) {
        val now = clock.nowMillis()
        finalLines.forEachIndexed { index, line ->
            if (line.kcal <= 0 && line.protein <= 0) return@forEachIndexed
            foodLog.logPrepared(
                name = line.name,
                macros = Macros(line.kcal, line.protein, line.carbs, line.fat),
                slot = slot,
                servingLabel = "${line.grams.toInt()} g · photo estimate",
                grams = line.grams.takeIf { it > 0 },
                photoUri = photoPath,
                aiConfidence = confidence,
                at = at,
            )
            val original = originalLines.getOrNull(index) ?: return@forEachIndexed
            if (VisionMath.isCorrection(original.kcal, line.kcal)) {
                corrections.upsert(
                    AiCorrectionEntity(
                        correctedAt = now,
                        estimatedKcal = original.kcal,
                        correctedKcal = line.kcal,
                        estimatedGrams = original.grams,
                        correctedGrams = line.grams,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    suspend fun biasNote(): String? =
        VisionMath.biasNote(corrections.all().map { it.estimatedKcal to it.correctedKcal })

    fun isConfigured() = gateway.isConfigured()
}
