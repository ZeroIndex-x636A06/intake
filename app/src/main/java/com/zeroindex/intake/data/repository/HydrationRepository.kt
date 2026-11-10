package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.HydrationDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.db.entity.HydrationLogEntity
import com.zeroindex.intake.data.db.entity.HydrationPresetEntity
import com.zeroindex.intake.data.db.entity.SEED_TIMESTAMP
import com.zeroindex.intake.data.db.entity.seedId
import com.zeroindex.intake.data.settings.HydrationPrefs
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.data.weather.WeatherRepository
import com.zeroindex.intake.domain.hydration.CaffeineAlcohol
import com.zeroindex.intake.domain.hydration.DayDrinkTotals
import com.zeroindex.intake.domain.hydration.DrinkEntry
import com.zeroindex.intake.domain.hydration.DrinkType
import com.zeroindex.intake.domain.hydration.Drinks
import com.zeroindex.intake.domain.hydration.HydrationPlan
import com.zeroindex.intake.domain.hydration.HydrationTarget
import com.zeroindex.intake.domain.hydration.SessionLoad
import com.zeroindex.intake.domain.hydration.SweatLoss
import com.zeroindex.intake.domain.hydration.TargetBreakdown
import com.zeroindex.intake.domain.hydration.TargetLine
import com.zeroindex.intake.domain.hydration.WeatherStatus
import com.zeroindex.intake.domain.time.Clock
import com.zeroindex.intake.domain.units.VolumeUnit
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** One day of water: what was drunk, against what target, and why. */
data class HydrationDay(
    val logs: List<HydrationLogEntity>,
    /** Kept in its older shape for the card and the daily insight; built from [breakdown]. */
    val target: HydrationTarget.Target,
    val breakdown: TargetBreakdown = TargetBreakdown(
        TargetLine(TargetLine.Id.BASE, "Base", target.baseMl, "base", ""),
        emptyList(),
    ),
    val totals: DayDrinkTotals = DayDrinkTotals(
        rawMl = logs.sumOf { it.volumeMl },
        effectiveMl = logs.sumOf { it.effectiveMl }.coerceAtLeast(0.0),
        caffeineMg = logs.sumOf { it.caffeineMg },
        standardDrinks = logs.sumOf { Drinks.standardDrinks(it.alcoholMl) },
        largestCaffeineServingMg = logs.maxOfOrNull { it.caffeineMg } ?: 0.0,
    ),
    val weather: WeatherStatus = WeatherStatus.Off,
    val sweatRateMeasured: Boolean = false,
) {
    /** What counts toward the target: volume x each drink's factor, never below zero. */
    val consumedMl: Double get() = totals.effectiveMl

    /** The plain total poured, before factors. Shown in the details. */
    val rawMl: Double get() = totals.rawMl
    val notes: List<String> get() = CaffeineAlcohol.notes(totals)
    val progress: Float get() = if (target.totalMl <= 0) 0f else (consumedMl / target.totalMl).toFloat()
}

@Singleton
class HydrationRepository @Inject constructor(
    private val dao: HydrationDao,
    private val healthDao: HealthSummaryDao,
    private val workoutDao: WorkoutDao,
    private val bodyRepository: BodyRepository,
    private val settingsRepository: SettingsRepository,
    private val hydrationPrefs: HydrationPrefs,
    private val weatherRepository: WeatherRepository,
    private val drinks: DrinkRepository,
    private val clock: Clock,
) {
    fun observePresets(): Flow<List<HydrationPresetEntity>> = dao.observePresets()

    /** Opt-in weather: a no-op unless the user turned it on. Call when Today opens. */
    suspend fun refreshWeather() = weatherRepository.refreshIfStale()

    fun observeDay(date: LocalDate): Flow<HydrationDay> {
        val range = clock.rangeFor(date)
        val sessions = workoutDao.observeSessions().map { all ->
            all.filter { it.startedAt >= range.startInclusive && it.startedAt < range.endExclusive }
                .map { s ->
                    val end = s.finishedAt ?: min(clock.nowMillis(), s.startedAt + MAX_SESSION_MS)
                    val rpes = workoutDao.setsOf(s.id).filter { !it.isWarmup }.mapNotNull { it.rpe }
                    SessionLoad(
                        minutes = (min(end - s.startedAt, MAX_SESSION_MS) / 60_000.0).coerceAtLeast(0.0),
                        avgRpe = rpes.takeIf { it.isNotEmpty() }?.average(),
                    )
                }
        }
        return combine(
            dao.observeLogsInRange(range.startInclusive, range.endExclusive),
            settingsRepository.settings,
            bodyRepository.observeLatest(),
            sessions,
            combine(
                healthDao.observeForDay(range.startInclusive),
                hydrationPrefs.settings,
                weatherRepository.status(date),
            ) { health, prefs, weather -> Triple(health, prefs, weather) },
        ) { logs, settings, latest, loads, extras ->
            val (health, prefs, weather) = extras
            val totals = CaffeineAlcohol.totals(
                logs.map { DrinkEntry(it.volumeMl, it.hydrationFactor, it.caffeineMg, it.alcoholMl) },
            )
            val breakdown = HydrationPlan.compose(
                manualBaseMl = settings.hydrationBaseMl,
                bodyweightKg = latest?.weightKg,
                dynamic = settings.hydrationDynamic,
                // A session started but not yet timed still makes it a training day.
                plannedTrainingDay = loads.isNotEmpty(),
                trainingDayBonusMl = settings.hydrationTrainingBonusMl,
                sessions = loads,
                measuredSweatLitresPerHour = prefs.measuredSweatLitresPerHour,
                activeKcalNet = health?.activeKcalNet,
                weather = (weather as? WeatherStatus.Ready)?.day,
                drinks = totals,
            )
            HydrationDay(
                logs = logs,
                target = breakdown.toTarget(),
                breakdown = breakdown,
                totals = totals,
                weather = weather,
                sweatRateMeasured = prefs.measuredSweatLitresPerHour != null && SweatLoss.totalMinutes(loads) > 0,
            )
        }
    }

    /**
     * Logs one drink through the one drink flow ([DrinkRepository]): the hydration row, and the food-log row for a drink
     * that has calories, so Today's water card and the Drinks page write the same thing. With no [drink] given, a tapped
     * preset decides the type, else it is water. The factor, caffeine and alcohol are worked out now from the user's
     * current Advanced values and stored on the row, so changing a factor later never rewrites history.
     */
    suspend fun log(
        volumeMl: Double,
        loggedAt: Long,
        presetId: String? = null,
        drink: DrinkType? = null,
    ): HydrationLogEntity {
        require(volumeMl > 0) { "volume must be positive" }
        val type = drink ?: presetId?.let { dao.presetById(it)?.drinkType } ?: DrinkType.WATER
        return drinks.logOfType(type, volumeMl, loggedAt, presetId)
    }

    /** Soft-deletes the entry and, for a drink with calories, its food-log row too. */
    suspend fun delete(id: String) = drinks.delete(id)

    /** Brings a soft-deleted entry back (and its food-log row), for the snackbar's Undo. */
    suspend fun restore(log: HydrationLogEntity) = drinks.restore(log.id)

    suspend fun savePreset(
        name: String,
        volumeMl: Double,
        existing: HydrationPresetEntity? = null,
        drinkType: DrinkType = existing?.drinkType ?: DrinkType.WATER,
    ) {
        val now = clock.nowMillis()
        val order = existing?.displayOrder ?: (dao.presets().maxOfOrNull { it.displayOrder }?.plus(1) ?: 0)
        dao.upsertPreset(
            existing?.copy(name = name, volumeMl = volumeMl, drinkType = drinkType, updatedAt = now)
                ?: HydrationPresetEntity(
                    name = name,
                    volumeMl = volumeMl,
                    displayOrder = order,
                    drinkType = drinkType,
                    createdAt = now,
                    updatedAt = now,
                ),
        )
    }

    suspend fun deletePreset(id: String) = dao.softDeletePreset(id, clock.nowMillis())

    /**
     * Seeds the starter presets: Glass, Bottle, Shaker (water), then Tea 250 ml and Coffee 250 ml. A fresh install
     * gets all five. An install that already has presets only gets Tea and Coffee added, once: they are matched by
     * their fixed ids and deleted rows count, so removing them is permanent.
     */
    suspend fun ensureDefaultPresets() {
        // The generic drinks (water, coffee, cola ...) are food rows too; seed them with the presets so Drinks is never empty.
        drinks.ensureSeeded()
        val oz = VolumeUnit.US_FLUID_OUNCES
        val now = SEED_TIMESTAMP
        fun seed(name: String, ml: Double, order: Int, type: DrinkType = DrinkType.WATER) = HydrationPresetEntity(
            id = seedId("hydration-preset", name),
            name = name,
            volumeMl = ml,
            displayOrder = order,
            drinkType = type,
            createdAt = now,
            updatedAt = now,
        )
        if (dao.presetRowCount() == 0) {
            dao.upsertPresets(
                listOf(
                    seed("Glass", oz.toMl(8.0), 0),
                    seed("Bottle", 500.0, 1),
                    seed("Shaker", oz.toMl(24.0), 2),
                    seed("Tea", 250.0, 3, DrinkType.TEA),
                    seed("Coffee", 250.0, 4, DrinkType.COFFEE),
                ),
            )
            return
        }
        var next = (dao.presets().maxOfOrNull { it.displayOrder } ?: -1) + 1
        listOf(Triple("Tea", 250.0, DrinkType.TEA), Triple("Coffee", 250.0, DrinkType.COFFEE)).forEach { (name, ml, type) ->
            val preset = seed(name, ml, next, type)
            if (dao.presetIdCount(preset.id) == 0) {
                dao.upsertPreset(preset)
                next++
            }
        }
    }

    private companion object {
        /** A session left open counts no more than four hours of sweat. */
        const val MAX_SESSION_MS = 4 * 60 * 60 * 1000L
    }
}
