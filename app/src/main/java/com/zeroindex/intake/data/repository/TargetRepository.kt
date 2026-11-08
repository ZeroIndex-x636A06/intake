package com.zeroindex.intake.data.repository

import com.zeroindex.intake.data.db.dao.NutritionTargetDao
import com.zeroindex.intake.data.db.entity.NutritionTargetEntity
import com.zeroindex.intake.data.db.entity.newId
import com.zeroindex.intake.domain.nutrition.MacroTarget
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TargetRepository @Inject constructor(
    private val dao: NutritionTargetDao,
    private val clock: Clock,
) {
    /** The target in force on [date], or null if none was ever set. */
    fun observeTargetFor(date: LocalDate): Flow<MacroTarget?> =
        dao.observeEffectiveAt(clock.rangeFor(date).endExclusive - 1).map { it?.toDomain() }

    suspend fun targetFor(date: LocalDate): MacroTarget? =
        dao.effectiveAt(clock.rangeFor(date).endExclusive - 1)?.toDomain()

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
