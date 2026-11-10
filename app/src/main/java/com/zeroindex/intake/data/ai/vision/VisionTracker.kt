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
import com.zeroindex.intake.domain.ai.PhotoItem
import com.zeroindex.intake.domain.ai.PhotoItemInput
import com.zeroindex.intake.domain.ai.PhotoItems
import com.zeroindex.intake.domain.ai.VisionContext
import com.zeroindex.intake.domain.ai.VisionMath
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.model.AiConfidence
import com.zeroindex.intake.domain.model.AllergenMatchKind
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.PortionUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VisionResult {
    val photoPath: String

    data class Ready(
        val estimate: VisionEstimate,
        val lines: List<EstimateLine>,
        /** The foods found, at 1x, ready for the review list (untick, rename, resize). */
        val items: List<PhotoItem>,
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
    /**
     * [extraContext] is the person's own note ("cooked in butter, large portion"). It is sent to OpenRouter with
     * the photo, so the privacy page says so, and it is capped so a pasted essay cannot become a large request.
     */
    suspend fun estimate(photo: PreparedPhoto, extraContext: String? = null): VisionResult {
        val avoided = allergens.avoidedAllergens()
        val outcome = try {
            gateway.structured(
                feature = AiFeature.PHOTO_ESTIMATE,
                trigger = AiTrigger.USER_ACTION,
                serializer = VisionEstimate.serializer(),
                schema = VisionSchema.schema,
                system = VisionSchema.systemPrompt(avoided),
                parts = buildList {
                    add(AiPart.Image(photo.jpeg))
                    add(AiPart.Text("Estimate this meal."))
                    VisionContext.clean(extraContext)?.let { add(AiPart.Text("Note from the user about this photo: $it")) }
                },
            )
        } catch (e: AiError.BadResponse) {
            // The model's text is never shown: only the plain message.
            return VisionResult.Unreadable(null, e.message, photo.savedPath)
        }

        val estimate = outcome.value
        if (estimate.notFood || estimate.items.isEmpty()) return VisionResult.NotFood(photo.savedPath)

        val lines = estimate.items.map {
            EstimateLine(it.name, it.estimatedGrams, it.kcal, it.protein, it.carbs, it.fat)
        }
        val items = PhotoItems.of(
            estimate.items.mapIndexed { i, it -> PhotoItemInput(lines[i], it.gramsLow, it.gramsHigh, it.confidence) },
        )
        val (scan, flagged) = scanPlate(estimate, avoided)
        return VisionResult.Ready(
            estimate = estimate,
            lines = lines,
            items = items,
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
     * The allergen check at confirm time, over the foods as they stand after the person's edits: the local
     * engine reads each current name (so a renamed food is re-checked), and the model's claims stay tied to the
     * item they were made about by id. A rename cannot clear a claim; unticking the item is how it goes away.
     */
    suspend fun recheck(estimate: VisionEstimate, shown: List<PhotoItem>): AllergenScanResult {
        val avoided = allergens.avoidedAllergens()
        return ModelAllergenPolicy.merge(
            local = shown.map { allergens.scan(name = it.line.name) },
            claims = shown.map {
                val original = estimate.items.getOrNull(it.id)
                ModelAllergenClaim(it.line.name, original?.containsAllergens.orEmpty(), original?.allergenReasoning.orEmpty())
            },
            avoided = avoided,
            extraLocal = estimate.hiddenCalorieSources.map { allergens.scan(name = null, ingredients = it.source) },
            source = "The photo model",
        ).scan
    }

    /**
     * Logs every ticked food, one entry each, and records a correction for every one the user moved by more than
     * 1% - including through the slider, which is a correction too (SPEC 6.9). [shown] is already scaled;
     * [originals] are the model's own lines by item id.
     */
    suspend fun log(
        shown: List<PhotoItem>,
        originals: Map<Int, EstimateLine>,
        confidence: AiConfidence,
        slot: MealSlot,
        at: Long,
        photoPath: String,
        unit: PortionUnit,
    ) {
        val now = clock.nowMillis()
        PhotoItems.loggable(shown).forEach { item ->
            val line = item.line
            foodLog.logPrepared(
                name = line.name,
                macros = Macros(line.kcal, line.protein, line.carbs, line.fat),
                slot = slot,
                servingLabel = "${unit.format(line.grams)} \u00b7 photo estimate",
                grams = line.grams.takeIf { it > 0 },
                photoUri = photoPath,
                aiConfidence = confidence,
                at = at,
            )
            val original = originals[item.id] ?: return@forEach
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
