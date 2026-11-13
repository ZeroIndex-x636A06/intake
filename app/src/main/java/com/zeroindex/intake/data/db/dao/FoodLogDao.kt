package com.zeroindex.intake.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.projection.DailyIntake
import com.zeroindex.intake.data.db.projection.MacroTotals
import com.zeroindex.intake.data.db.projection.RecentDrinkLog
import com.zeroindex.intake.data.db.projection.RecentPrepared
import com.zeroindex.intake.data.db.projection.SlotTotals
import com.zeroindex.intake.domain.model.MealSlot
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {

    @Upsert suspend fun upsert(log: FoodLogEntity)

    /** True when the table holds any row at all, deleted or not. Used once, to spot an install with data. */
    @Query("SELECT EXISTS(SELECT 1 FROM food_log)")
    suspend fun hasAnyRow(): Boolean
    @Upsert suspend fun upsertAll(logs: List<FoodLogEntity>)

    @Query("SELECT * FROM food_log WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): FoodLogEntity?

    @Query("SELECT * FROM food_log WHERE deletedAt IS NULL ORDER BY loggedAt")
    suspend fun all(): List<FoodLogEntity>

    /**
     * Everything logged in one local day. The caller passes a half-open UTC
     * millis range from `DayRange` - SPEC §3.4 forbids storing local dates.
     */
    @Query(
        """
        SELECT * FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        ORDER BY loggedAt
        """,
    )
    fun observeInRange(startInclusive: Long, endExclusive: Long): Flow<List<FoodLogEntity>>

    @Query(
        """
        SELECT * FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        ORDER BY loggedAt
        """,
    )
    suspend fun inRange(startInclusive: Long, endExclusive: Long): List<FoodLogEntity>

    @Query(
        """
        SELECT SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat
        FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        """,
    )
    fun observeTotalsInRange(startInclusive: Long, endExclusive: Long): Flow<MacroTotals>

    @Query(
        """
        SELECT mealSlot AS mealSlot, SUM(kcal) AS kcal, SUM(protein) AS protein
        FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        GROUP BY mealSlot
        """,
    )
    fun observeSlotTotalsInRange(startInclusive: Long, endExclusive: Long): Flow<List<SlotTotals>>

    /**
     * Calories per local day across a range, bucketed in SQL. The zone offset
     * comes from the caller, which knows the zone, rather than being guessed.
     */
    @Query(
        """
        SELECT ((loggedAt + :zoneOffsetMillis) / :dayLengthMillis) * :dayLengthMillis - :zoneOffsetMillis
                   AS dayStartMillis,
               SUM(kcal) AS kcal
        FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        GROUP BY dayStartMillis
        ORDER BY dayStartMillis
        """,
    )
    fun observeDailyIntake(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetMillis: Long,
        dayLengthMillis: Long = 86_400_000L,
    ): Flow<List<DailyIntake>>

    @Query(
        """
        SELECT ((loggedAt + :zoneOffsetMillis) / :dayLengthMillis) * :dayLengthMillis - :zoneOffsetMillis
                   AS dayStartMillis,
               SUM(kcal) AS kcal
        FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        GROUP BY dayStartMillis
        ORDER BY dayStartMillis
        """,
    )
    suspend fun dailyIntake(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetMillis: Long,
        dayLengthMillis: Long = 86_400_000L,
    ): List<DailyIntake>

    /** Each drink logged to a slot (Drinks), latest first, with the size it was last logged at: the Drinks page's Recent row. */
    @Query(
        """
        SELECT foodId AS foodId, quantityGrams AS ml, MAX(loggedAt) AS at FROM food_log
        WHERE foodId IS NOT NULL AND quantityGrams IS NOT NULL AND mealSlot = :slot AND deletedAt IS NULL
        GROUP BY foodId ORDER BY at DESC LIMIT :limit
        """,
    )
    fun observeRecentDrinkLogs(slot: MealSlot, limit: Int): Flow<List<RecentDrinkLog>>

    /** Names of rows logged without a food (recipe servings among them), latest first: Log food's recent recipes. */
    @Query(
        """
        SELECT foodNameAtLog AS name, MAX(loggedAt) AS at FROM food_log
        WHERE foodId IS NULL AND deletedAt IS NULL
        GROUP BY foodNameAtLog ORDER BY at DESC LIMIT :limit
        """,
    )
    fun observeRecentPrepared(limit: Int): Flow<List<RecentPrepared>>

    @Query("UPDATE food_log SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    /** The row whether or not it was deleted: Undo on a drink brings its food row back too. */
    @Query("SELECT * FROM food_log WHERE id = :id")
    suspend fun findAny(id: String): FoodLogEntity?

    /** How many distinct days have any entry, for "18 of the last 21 days" (SPEC §11). */
    @Query(
        """
        SELECT COUNT(DISTINCT ((loggedAt + :zoneOffsetMillis) / :dayLengthMillis))
        FROM food_log
        WHERE deletedAt IS NULL AND loggedAt >= :startInclusive AND loggedAt < :endExclusive
        """,
    )
    suspend fun countDaysLogged(
        startInclusive: Long,
        endExclusive: Long,
        zoneOffsetMillis: Long,
        dayLengthMillis: Long = 86_400_000L,
    ): Int

    /** Every row touched since [since], deleted rows included, for Health Connect sync. */
    @Query("SELECT * FROM food_log WHERE updatedAt > :since ORDER BY updatedAt")
    suspend fun changedSince(since: Long): List<FoodLogEntity>

    /** Emits whenever the table changes; the sync debounces on it. */
    @Query("SELECT MAX(updatedAt) FROM food_log")
    fun observeLastChange(): Flow<Long?>
}
