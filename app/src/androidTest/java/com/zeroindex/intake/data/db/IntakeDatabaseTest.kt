package com.zeroindex.intake.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.db.entity.FoodEntity
import com.zeroindex.intake.data.db.entity.FoodLogEntity
import com.zeroindex.intake.data.db.entity.NutritionTargetEntity
import com.zeroindex.intake.domain.model.FoodSource
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.time.DayRange
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * SPEC §14: Room DAO tests against an in-memory database.
 *
 * Method names are camelCase rather than the backtick-with-spaces style used in
 * the JVM tests: these get dexed, and D8 rejects space characters in the
 * generated lambda class names below DEX version 040 (our minSdk 29).
 *
 * Instrumented rather than Robolectric: Robolectric's native SQLite artifact
 * could not be fetched from Maven Central in this environment, and an
 * unrunnable test suite is worse than one that needs a device. Run with
 * `./gradlew connectedDebugAndroidTest` with a phone or emulator attached.
 */
@RunWith(AndroidJUnit4::class)
class IntakeDatabaseTest {

    private lateinit var db: IntakeDatabase
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val now = 1_770_000_000_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IntakeDatabase::class.java,
        )
            .addCallback(IntakeDatabase.seedCallback())
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun allergenAliasesAreSeededAtFirstRun() = runTest {
        val aliases = db.allergenDao().aliases()
        assertThat(aliases).hasSize(AllergenSeed.DAIRY_ALIASES.size)
        assertThat(aliases.map { it.aliasTerm })
            .containsAtLeast("sodium caseinate", "buttermilk", "ghee", "lactalbumin", "whey")
        assertThat(aliases.map { it.allergen }.distinct()).containsExactly("dairy")
    }

    @Test
    fun allergenProfileSingletonExistsAfterSeeding() = runTest {
        val profile = db.allergenDao().profile()
        assertThat(profile).isNotNull()
        assertThat(profile!!.avoidedAllergens).contains("dairy")
    }

    @Test
    fun softDeletedFoodDisappearsFromReadsButStaysInTheTable() = runTest {
        val food = sampleFood("Chicken thigh")
        db.foodDao().upsert(food)
        assertThat(db.foodDao().findById(food.id)).isNotNull()

        db.foodDao().softDelete(food.id, now + 1)

        // SPEC §3.8: nothing is ever hard deleted.
        assertThat(db.foodDao().findById(food.id)).isNull()
        assertThat(db.foodDao().observeCount().first()).isEqualTo(0)
        db.openHelper.readableDatabase
            .query("SELECT deletedAt FROM food WHERE id = ?", arrayOf<Any?>(food.id))
            .use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getLong(0)).isEqualTo(now + 1)
            }
    }

    @Test
    fun logsAreReadBackByLocalDayRange() = runTest {
        val food = sampleFood()
        db.foodDao().upsert(food)
        val range = DayRange.of(LocalDate.of(2026, 2, 2), zone)

        db.foodLogDao().upsert(sampleLog(food, range.startInclusive, MealSlot.BREAKFAST, 300.0))
        db.foodLogDao().upsert(sampleLog(food, range.endExclusive - 60_000, MealSlot.SNACK, 200.0))
        db.foodLogDao().upsert(sampleLog(food, range.endExclusive, MealSlot.BREAKFAST, 999.0))

        val logs = db.foodLogDao().observeInRange(range.startInclusive, range.endExclusive).first()
        assertThat(logs.map { it.kcal }).containsExactly(300.0, 200.0).inOrder()
    }

    @Test
    fun dayTotalsSumOnlyThatDaysEntries() = runTest {
        val food = sampleFood()
        db.foodDao().upsert(food)
        val range = DayRange.of(LocalDate.of(2026, 2, 2), zone)
        db.foodLogDao().upsert(sampleLog(food, range.startInclusive, MealSlot.LUNCH, 620.0, 48.0))
        db.foodLogDao()
            .upsert(sampleLog(food, range.startInclusive + 3_600_000, MealSlot.DINNER, 700.0, 52.0))
        db.foodLogDao().upsert(sampleLog(food, range.endExclusive + 1, MealSlot.LUNCH, 400.0))

        val totals = db.foodLogDao()
            .observeTotalsInRange(range.startInclusive, range.endExclusive).first()
        assertThat(totals.kcalOrZero).isEqualTo(1320.0)
        assertThat(totals.proteinOrZero).isEqualTo(100.0)
    }

    @Test
    fun totalsOverAnEmptyDayAreZeroRatherThanACrash() = runTest {
        val range = DayRange.of(LocalDate.of(2026, 2, 3), zone)
        val totals = db.foodLogDao()
            .observeTotalsInRange(range.startInclusive, range.endExclusive).first()
        assertThat(totals.kcal).isNull()
        assertThat(totals.kcalOrZero).isEqualTo(0.0)
    }

    @Test
    fun slotTotalsGroupByMealSlot() = runTest {
        val food = sampleFood()
        db.foodDao().upsert(food)
        val range = DayRange.of(LocalDate.of(2026, 2, 2), zone)
        db.foodLogDao().upsert(sampleLog(food, range.startInclusive, MealSlot.BREAKFAST, 400.0, 30.0))
        db.foodLogDao()
            .upsert(sampleLog(food, range.startInclusive + 1, MealSlot.BREAKFAST, 100.0, 10.0))
        db.foodLogDao().upsert(sampleLog(food, range.startInclusive + 2, MealSlot.DINNER, 800.0, 60.0))

        val bySlot = db.foodLogDao()
            .observeSlotTotalsInRange(range.startInclusive, range.endExclusive).first()
            .associateBy { it.mealSlot }
        assertThat(bySlot[MealSlot.BREAKFAST]?.kcal).isEqualTo(500.0)
        assertThat(bySlot[MealSlot.BREAKFAST]?.protein).isEqualTo(40.0)
        assertThat(bySlot[MealSlot.DINNER]?.kcal).isEqualTo(800.0)
        assertThat(bySlot).doesNotContainKey(MealSlot.LUNCH)
    }

    @Test
    fun aQuickAddLogNeedsNoFoodRow() = runTest {
        // SPEC §5.4: quick add deliberately creates no food record.
        val log = FoodLogEntity(
            foodId = null,
            loggedAt = now,
            mealSlot = MealSlot.SNACK,
            quantityGrams = null,
            foodNameAtLog = "Quick add",
            kcal = 250.0,
            protein = 20.0,
            carbs = 30.0,
            fat = 8.0,
            createdAt = now,
            updatedAt = now,
        )
        db.foodLogDao().upsert(log)
        assertThat(db.foodLogDao().findById(log.id)?.foodId).isNull()
    }

    @Test
    fun editingAFoodDoesNotRewriteLoggedHistory() = runTest {
        val food = sampleFood("Greek yogurt", kcal = 59.0)
        db.foodDao().upsert(food)
        val log = sampleLog(food, now, MealSlot.BREAKFAST, 147.5, 25.0)
        db.foodLogDao().upsert(log)

        db.foodDao().upsert(food.copy(kcalPer100g = 97.0, updatedAt = now + 5))

        // SPEC §4: the log kept its own copy.
        assertThat(db.foodLogDao().findById(log.id)?.kcal).isEqualTo(147.5)
    }

    @Test
    fun mostLoggedFoodsComeBackInDescendingOrder() = runTest {
        val rare = sampleFood("Rare")
        val common = sampleFood("Common")
        val never = sampleFood("Never")
        db.foodDao().upsertAll(listOf(rare, common, never))
        repeat(2) { db.foodDao().incrementTimesLogged(rare.id, now) }
        repeat(9) { db.foodDao().incrementTimesLogged(common.id, now) }

        assertThat(db.foodDao().observeMostLogged(30).first().map { it.name })
            .containsExactly("Common", "Rare").inOrder()
    }

    @Test
    fun theEffectiveNutritionTargetIsTheNewestOneNotInTheFuture() = runTest {
        val dao = db.nutritionTargetDao()
        val january = DayRange.of(LocalDate.of(2026, 1, 1), zone).startInclusive
        val march = DayRange.of(LocalDate.of(2026, 3, 1), zone).startInclusive
        val june = DayRange.of(LocalDate.of(2026, 6, 1), zone).startInclusive

        dao.upsert(target(january, 2600))
        dao.upsert(target(march, 2400))
        dao.upsert(target(june, 2200))

        // SPEC §4: targets are a dated series so history stays accurate.
        val inApril = DayRange.of(LocalDate.of(2026, 4, 15), zone).startInclusive
        assertThat(dao.effectiveAt(inApril)?.kcal).isEqualTo(2400)
        assertThat(dao.effectiveAt(january)?.kcal).isEqualTo(2600)
        assertThat(dao.effectiveAt(june)?.kcal).isEqualTo(2200)
        assertThat(dao.effectiveAt(DayRange.of(LocalDate.of(2025, 12, 1), zone).startInclusive))
            .isNull()
    }

    private fun sampleFood(name: String = "Chicken breast", kcal: Double = 165.0) = FoodEntity(
        name = name,
        servingSizeGrams = 100.0,
        servingLabel = "100 g",
        kcalPer100g = kcal,
        proteinPer100g = 31.0,
        carbsPer100g = 0.0,
        fatPer100g = 3.6,
        source = FoodSource.MANUAL,
        createdAt = now,
        updatedAt = now,
    )

    private fun sampleLog(
        food: FoodEntity,
        at: Long,
        slot: MealSlot,
        kcal: Double = 165.0,
        protein: Double = 31.0,
    ) = FoodLogEntity(
        foodId = food.id,
        loggedAt = at,
        mealSlot = slot,
        quantityGrams = 100.0,
        foodNameAtLog = food.name,
        servingLabelAtLog = food.servingLabel,
        kcal = kcal,
        protein = protein,
        carbs = 0.0,
        fat = 3.6,
        createdAt = at,
        updatedAt = at,
    )

    private fun target(effectiveFrom: Long, kcal: Int) = NutritionTargetEntity(
        effectiveFrom = effectiveFrom,
        kcal = kcal,
        proteinG = 180,
        carbsG = 250,
        fatG = 70,
        isAutoCalculated = true,
        createdAt = effectiveFrom,
        updatedAt = effectiveFrom,
    )
}
