package com.zeroindex.intake.wear

import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.FoodRepository
import com.zeroindex.intake.data.repository.HydrationRepository
import com.zeroindex.intake.data.repository.MealPlanRepository
import com.zeroindex.intake.data.repository.MealTemplateRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.repository.TrainingRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.drinks.DrinkSizes
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.time.localDateOf
import com.zeroindex.intake.domain.training.ScheduleDay
import com.zeroindex.intake.domain.training.TodayPlan
import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.wearprotocol.PlannedMeal
import com.zeroindex.intake.wearprotocol.RemoteProtocol
import com.zeroindex.intake.wearprotocol.RemoteState
import com.zeroindex.intake.wearprotocol.TodayWorkout
import com.zeroindex.intake.wearprotocol.WatchFood
import com.zeroindex.intake.wearprotocol.WatchTemplate
import com.zeroindex.intake.wearprotocol.WaterSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The phone's day as the watch's quick remote shows it: eaten against target, water, the sizes and short food lists the
 * watch offers, today's planned meal and workout. Everything is read from the same repositories the phone's screens
 * use, in the phone's units. No AI, no network.
 */
@Singleton
class RemoteStateBuilder @Inject constructor(
    private val foodLog: FoodLogRepository,
    private val foods: FoodRepository,
    private val hydration: HydrationRepository,
    private val targets: TargetRepository,
    private val templates: MealTemplateRepository,
    private val mealPlan: MealPlanRepository,
    private val training: TrainingRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** The date, re-read every ten minutes so the numbers roll over at midnight even when nothing is logged. */
    private fun today(): Flow<java.time.LocalDate> = flow {
        while (true) {
            emit(clock.today())
            delay(10 * 60_000L)
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<RemoteState> = today().flatMapLatest { date ->
        val day = combine(
            foodLog.observeDayTotals(date),
            targets.observeTargetFor(date),
            hydration.observeDay(date),
            settings.settings.map { it.volumeUnit }.distinctUntilChanged(),
        ) { eaten, target, water, unit ->
            RemoteState(
                dateKey = date.toString(),
                kcal = eaten.kcal.roundToInt(),
                kcalTarget = target?.kcal,
                proteinG = eaten.protein.roundToInt(),
                proteinTargetG = target?.proteinG,
                waterMl = water.consumedMl.roundToInt(),
                waterTargetMl = water.target.totalMl.roundToInt().takeIf { it > 0 },
                volumeUnit = unit.wire(),
                updatedAt = clock.nowMillis(),
            )
        }
        val offers = combine(
            sizes(),
            foods.observeRecents(30).map { list -> list.toWatchFoods() },
            foods.observeFavorites().map { list -> list.toWatchFoods() },
            templates.observeSummaries().map { list ->
                list.sortedByDescending { it.template.lastLoggedAt ?: 0L }
                    .take(RemoteState.MAX_LIST)
                    .map { WatchTemplate(it.template.id, it.template.name, it.kcal.roundToInt()) }
            },
        ) { sizes, recents, favourites, tpl -> Offers(sizes, recents, favourites, tpl) }
        combine(day, offers, planned(), todayWorkout()) { d, o, p, w ->
            d.copy(waterSizes = o.sizes, recents = o.recents, favourites = o.favourites, templates = o.templates, planned = p, todayWorkout = w)
        }
    }

    private data class Offers(val sizes: List<WaterSize>, val recents: List<WatchFood>, val favourites: List<WatchFood>, val templates: List<WatchTemplate>)

    /** The person's own quick sizes (Settings > Water and drinks), water ones only; the unit's defaults when there are none. */
    private fun sizes(): Flow<List<WaterSize>> = combine(hydration.observePresets(), settings.settings.map { it.volumeUnit }) { presets, unit ->
        val water = presets.filter { it.drinkType == DrinkType.WATER && it.volumeMl > 0 }
        if (water.isNotEmpty()) {
            water.take(RemoteState.MAX_LIST).map { WaterSize(it.name, it.volumeMl.roundToInt(), it.id) }
        } else {
            DrinkSizes.defaults(unit).map { WaterSize(it.label, it.ml.roundToInt()) }
        }
    }

    private fun List<FoodEntity>.toWatchFoods(): List<WatchFood> = asSequence()
        .filter { it.drinkCategory == null }
        .take(RemoteState.MAX_LIST)
        .map { f ->
            WatchFood(f.id, f.name.take(40), f.servingSizeGrams, f.servingLabel.take(24), (f.kcalPer100g * f.servingSizeGrams / 100).roundToInt())
        }
        .toList()

    /** Today's planned meal: the active plan's main dish for the current week, with the phone's allergen verdict. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun planned(): Flow<PlannedMeal?> = mealPlan.observePosition().flatMapLatest { position ->
        mealPlan.observeWeekContent(position?.week ?: 1).map { week ->
            week?.main?.let { m ->
                PlannedMeal(
                    name = m.recipe.name.take(40),
                    kcal = m.macros.perServing.kcal.roundToInt().takeIf { m.macros.knownCount > 0 },
                    proteinG = m.macros.perServing.protein.roundToInt().takeIf { m.macros.knownCount > 0 },
                    allergen = when (m.allergenStatus) {
                        com.zeroindex.intake.data.db.entity.AllergenStatus.CONTAINS -> "CONTAINS"
                        com.zeroindex.intake.data.db.entity.AllergenStatus.MAY_CONTAIN -> "MAY_CONTAIN"
                        else -> "NONE"
                    },
                )
            }
        }
    }

    /** The program day Train would offer today, null on a rest day or with no program. Same rule as Train. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun todayWorkout(): Flow<TodayWorkout?> = training.observeActiveProgram().flatMapLatest { program ->
        if (program == null) {
            flowOf(null)
        } else {
            combine(training.observeDays(program.id), training.observeSessionStats(1)) { days, last ->
                val finished = last.firstOrNull()?.finishedAt
                val plan = WorkoutSchedule.planFor(
                    days.map { ScheduleDay(it.dayIndex, it.name, it.weekday) },
                    program.currentDayIndex,
                    clock.today().dayOfWeek.value,
                    trainedToday = finished != null && localDateOf(finished, clock.zone()) == clock.today(),
                )
                (plan as? TodayPlan.Train)?.let { t -> days.firstOrNull { it.dayIndex == t.day.dayIndex }?.let { TodayWorkout(it.id, it.name) } }
            }
        }
    }

    private fun VolumeUnit.wire() = if (this == VolumeUnit.US_FLUID_OUNCES) RemoteProtocol.UNIT_OZ else RemoteProtocol.UNIT_ML
}
