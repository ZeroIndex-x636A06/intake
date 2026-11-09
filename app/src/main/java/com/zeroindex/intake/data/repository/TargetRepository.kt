package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.HealthSummaryDao
import com.zeroindex.intake.data.db.dao.NutritionTargetDao
import com.zeroindex.intake.data.db.entity.NutritionTargetEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.goals.WeekPattern
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class TargetRepository @Inject constructor(
    private val dao: NutritionTargetDao,
    private val healthDao: HealthSummaryDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /**
     * The target in force on [date], or null if none was ever set - plus that
     * day's net activity calories when the user has opted in (SPEC §12: off
     * by default and clearly labelled; amendment §2: BMR already taken out).
     * The extra goes to carbohydrate: protein and fat targets are set from
     * bodyweight and do not move with a workout.
     */
    fun observeTargetFor(date: LocalDate): Flow<MacroTarget?> = combine(
        dao.observeEffectiveAt(clock.rangeFor(date).endExclusive - 1),
        observeActivityBonus(date),
        settings.settings.map { it.weekPattern }.distinctUntilChanged(),
    ) { base, bonus, pattern -> base?.toDomain()?.withWeekPattern(pattern, date)?.plusActivity(bonus) }

    suspend fun targetFor(date: LocalDate): MacroTarget? {
        val base = dao.effectiveAt(clock.rangeFor(date).endExclusive - 1)?.toDomain() ?: return null
        val pattern = settings.settings.first().weekPattern
        return base.withWeekPattern(pattern, date).plusActivity(activityBonusFor(date))
    }

    /**
     * Advanced carb cycling and refeeds (goal builder): the day's own macros replace the base,
     * but only while the stored target is still the one the pattern was built from. If the target
     * has since been edited or recalculated, the plain target wins and nothing is applied silently.
     */
    private fun MacroTarget.withWeekPattern(pattern: WeekPattern?, date: LocalDate): MacroTarget {
        if (pattern == null || pattern.baseKcal != kcal) return this
        val day = pattern.days.firstOrNull { it.weekday == date.dayOfWeek.value } ?: return this
        return MacroTarget(kcal = day.kcal, proteinG = day.proteinG, carbsG = day.carbsG, fatG = day.fatG)
    }

    /** Net activity kcal added to [date]'s target, 0 when the option is off. */
    fun observeActivityBonus(date: LocalDate): Flow<Int> = combine(
        settings.settings,
        healthDao.observeForDay(clock.rangeFor(date).startInclusive),
    ) { prefs, day -> if (prefs.addActiveCaloriesToTarget) (day?.activeKcalNet ?: 0.0).roundToInt() else 0 }

    private suspend fun activityBonusFor(date: LocalDate): Int =
        if (settings.settings.first().addActiveCaloriesToTarget) {
            (healthDao.forDay(clock.rangeFor(date).startInclusive)?.activeKcalNet ?: 0.0).roundToInt()
        } else {
            0
        }

    private fun MacroTarget.plusActivity(kcal: Int): MacroTarget =
        if (kcal <= 0) this else copy(kcal = this.kcal + kcal, carbsG = carbsG + kcal / 4)

    fun observeHistory(): Flow<List<NutritionTargetEntity>> = dao.observeHistory()

    suspend fun latestEntity(): NutritionTargetEntity? = dao.effectiveAt(clock.nowMillis())

    /**
     * Appends a new dated target. SPEC §4 keeps these as a series so past days
     * keep the target they were actually judged against.
     */
    suspend fun setTarget(
        target: MacroTarget,
        effectiveFrom: Long = clock.nowMillis(),
        isAutoCalculated: Boolean,
    ) {
        val now = clock.nowMillis()
        dao.upsert(
            NutritionTargetEntity(
                id = newId(),
                effectiveFrom = effectiveFrom,
                kcal = target.kcal,
                proteinG = target.proteinG,
                carbsG = target.carbsG,
                fatG = target.fatG,
                isAutoCalculated = isAutoCalculated,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun NutritionTargetEntity.toDomain() =
        MacroTarget(kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG)
}
