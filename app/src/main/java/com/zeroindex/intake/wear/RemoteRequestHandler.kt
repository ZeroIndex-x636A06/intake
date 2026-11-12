package com.zeroindex.intake.wear

import android.content.Context
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.MealLogResult
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TemplateLogResult
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.nutrition.QuickAddField
import com.zeroindex.intake.domain.nutrition.QuickAddLimits
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.wearprotocol.RemoteReplies
import com.zeroindex.intake.wearprotocol.RemoteRequest
import com.zeroindex.intake.wearprotocol.RemoteResponse
import com.zeroindex.intake.wearprotocol.RequestKind
import com.zeroindex.intake.wearprotocol.RequestLedger
import com.zeroindex.intake.wearprotocol.WatchSlot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Applies one tap from the watch (docs/WEAR.md). Every branch goes through the repository call the phone's own screens
 * use, so the phone stays the source of truth and its allergen engine decides:
 *
 * - a food the engine says CONTAINS is refused and the watch is told why;
 * - a food it says MAY CONTAIN is not logged either, because the phone's answer to that is a sheet a person must read
 *   and confirm, and a watch cannot do that. The watch is told to finish on the phone;
 * - nothing here ever clears or downgrades a verdict.
 *
 * A request id is applied once: the answer is remembered ([RequestLedger]) and given again if the same id arrives again.
 * AI is never involved.
 */
@Singleton
class RemoteRequestHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hydration: HydrationRepository,
    private val foodLog: FoodLogRepository,
    private val foods: FoodRepository,
    private val templates: MealTemplateRepository,
    private val mealPlan: MealPlanRepository,
    private val training: TrainingRepository,
    private val allergens: AllergenRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    suspend fun handle(request: RemoteRequest): RemoteResponse = mutex.withLock {
        val ledger = RequestLedger.decode(prefs.getString(KEY_LEDGER, null))
        ledger[request.id]?.let { return it }
        val response = runCatching { apply(request) }
            .getOrElse { RemoteReplies.failed(request.id, "The phone could not log that. Try again.") }
        ledger.record(response)
        // commit(), not apply(): the answer must be on disk before the request item is deleted.
        prefs.edit().putString(KEY_LEDGER, ledger.encode()).commit()
        response
    }

    private suspend fun apply(request: RemoteRequest): RemoteResponse {
        request.validate()?.let { return RemoteReplies.failed(request.id, it) }
        val at = request.effectiveTime(clock.nowMillis()) ?: return RemoteReplies.expired(request.id)
        val slot = (request.slot ?: WatchSlot.forHour(Instant.ofEpochMilli(at).atZone(clock.zone()).hour)).toMealSlot()
        return when (request.kind) {
            RequestKind.WATER -> {
                val ml = request.volumeMl ?: return RemoteReplies.failed(request.id, "Bad water amount")
                hydration.log(volumeMl = ml, loggedAt = at, presetId = request.presetId)
                RemoteReplies.done(request.id, "Logged ${ml.toInt()} ml")
            }
            RequestKind.LOG_FOOD -> logFood(request, slot, at)
            RequestKind.LOG_TEMPLATE -> {
                when (val r = templates.log(request.templateId.orEmpty(), slot, at)) {
                    is TemplateLogResult.Logged -> RemoteReplies.done(request.id, "Logged ${r.template.name} to ${r.slot.name.lowercase()}")
                    is TemplateLogResult.Blocked -> RemoteReplies.blocked(request.id, "a food in that meal", r.foods)
                    TemplateLogResult.NotFound -> RemoteReplies.notFound(request.id, "That meal")
                }
            }
            RequestKind.LOG_PLANNED -> logPlanned(request, slot)
            RequestKind.QUICK_ADD -> {
                val kcal = request.kcal ?: return RemoteReplies.failed(request.id, "Bad calories")
                QuickAddLimits.problem(QuickAddField.KCAL, kcal.toDouble())?.let {
                    return RemoteReplies.failed(request.id, "Not logged. ${QuickAddField.KCAL.label}: $it")
                }
                foodLog.quickAdd("Quick add", Macros(kcal = kcal.toDouble()), slot, at)
                RemoteReplies.done(request.id, "Logged $kcal kcal to ${slot.name.lowercase()}")
            }
            RequestKind.START_WORKOUT -> startWorkout(request)
        }
    }

    private suspend fun logFood(request: RemoteRequest, slot: MealSlot, at: Long): RemoteResponse {
        val food = foods.findById(request.foodId.orEmpty()) ?: return RemoteReplies.notFound(request.id, "That food")
        if (food.drinkCategory != null) {
            return RemoteReplies.checkOnPhone(request.id, food.name, "Log drinks from the water screen on the phone.")
        }
        val scan = allergens.scan(food)
        if (scan.blocks) return RemoteReplies.blocked(request.id, food.name, scan.contains.map { it.matchedTerm })
        if (scan.hasAnything) {
            return RemoteReplies.checkOnPhone(
                request.id, food.name, "It may contain something you avoid. Open Intake on your phone to decide.",
                scan.mayContain.map { it.matchedTerm },
            )
        }
        val grams = request.grams ?: food.servingSizeGrams
        foodLog.logFood(food, grams, slot, at)
        val note = if (scan.hasCheckLabel) listOf("Check the label for allergens.") else emptyList()
        return RemoteReplies.done(
            request.id,
            "Logged ${food.name} (${grams.toInt()} g) to ${slot.name.lowercase()}" + note.joinToString(prefix = ". "),
            scan.checkLabel.map { it.matchedTerm },
        )
    }

    private suspend fun logPlanned(request: RemoteRequest, slot: MealSlot): RemoteResponse {
        val week = mealPlan.observePosition().first()?.week ?: 1
        val content = mealPlan.observeWeekContent(week).first()?.main ?: return RemoteReplies.notFound(request.id, "Today's planned meal")
        val name = content.recipe.name
        if (!content.macros.isComplete) {
            return RemoteReplies.checkOnPhone(request.id, name, "Its numbers are incomplete. Confirm them on the phone.")
        }
        return when (val r = mealPlan.logMeal(content, slot)) {
            is MealLogResult.Blocked -> RemoteReplies.blocked(request.id, name, r.terms)
            is MealLogResult.Logged -> RemoteReplies.done(
                request.id,
                "Logged $name to ${slot.name.lowercase()}" + if (r.status == AllergenStatus.MAY_CONTAIN) ". Check the labels for allergens." else "",
                content.allergenTerms,
            )
        }
    }

    private suspend fun startWorkout(request: RemoteRequest): RemoteResponse {
        val dayId = request.programDayId
        if (dayId != null && training.day(dayId) == null) return RemoteReplies.notFound(request.id, "That workout")
        if (training.observeInProgress().first() != null) return RemoteReplies.done(request.id, "A workout is already running.")
        training.ensureSeedExercises()
        training.startSession(dayId)
        return RemoteReplies.done(request.id, "Workout started.")
    }

    private fun WatchSlot.toMealSlot() = MealSlot.valueOf(name)

    private companion object {
        const val PREFS = "wear_remote"
        const val KEY_LEDGER = "ledger"
    }
}
