package com.zeroindex.intake.domain.coach

import com.zeroindex.intake.domain.goals.Pace
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.training.generator.Experience
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Every threshold the coach uses, in one place (docs/COACH.md lists them with their sources).
 * Pure data: the rules never read a constant from anywhere else.
 */
data class CoachConfig(
    // --- Data gate ---
    val windowDays: Int = 7,
    val minLoggedDays: Int = 5,
    val minWeighIns: Int = 3,
    /** A day counts as logged with at least this many kcal, the same rule as the baseline week. */
    val loggedDayMinKcal: Double = 800.0,

    // --- Weight trend ---
    /** Losing/gaining slower than this share of the target pace is "too slow". */
    val slowShare: Double = 0.5,
    val fastShare: Double = 1.5,
    /** A loss above this percent of body weight per week is always too fast. */
    val fastLossPctPerWeek: Double = 1.0,
    val smallStepKcal: Int = 100,
    val largeStepKcal: Int = 150,
    /** Maintain and recomp: stay within this many percent per week of the target rate. */
    val maintainBandPct: Double = 0.25,
    /** Contest prep uses tighter bands and smaller steps. */
    val contestSlowShare: Double = 0.8,
    val contestFastShare: Double = 1.2,
    val contestSmallStepKcal: Int = 50,
    val contestLargeStepKcal: Int = 100,
    val minTrendSpanDays: Int = 10,
    val minWeighInsForTrend: Int = 5,
    /** After an applied calorie change, wait this long so the trend can show its effect. */
    val calorieCooldownDays: Int = 14,
    /** A dismissed or applied suggestion is not repeated for this long. */
    val repeatSuppressDays: Int = 7,

    // --- Protein and water ---
    val proteinShare: Double = 0.85,
    val proteinMinLoggedDays: Int = 3,
    val waterShare: Double = 0.8,
    val waterMinDaysWithLogs: Int = 3,

    // --- Strength stall ---
    val stallSessions: Int = 3,
    val stallWindowDays: Int = 42,
    /** The newer sessions must beat the oldest of the three by this fraction to count as progress. */
    val stallMinGain: Double = 0.005,
    val stallVolumeGain: Double = 0.05,
    val deloadRecentDays: Int = 28,
    val deloadPercent: Int = 10,
    val deloadDays: Int = 7,

    // --- Recovery ---
    val sleepLowMinutes: Int = 360,
    val sleepLowNights: Int = 3,
    val sleepMinNights: Int = 4,
    val restingHrRiseBpm: Double = 5.0,
    val hrvDropPct: Double = 10.0,
    val baselineDays: Int = 28,
    val minBaselineDays: Int = 7,
    val minRecentDays: Int = 3,
    val rpeHigh: Double = 9.0,
    val rpeHighShare: Double = 0.5,
    val rpeMinSets: Int = 10,
    val signalsForEasierDay: Int = 2,
    val signalsForDeload: Int = 3,
    val easierDaySetsLess: Int = 1,
    val easierDayDays: Int = 3,

    // --- Volume ---
    val volumeBelowShare: Double = 0.75,
    val volumeMinSessionsPerWeek: Int = 2,
    val volumeStepSets: Int = 2,

    // --- Output ---
    val maxSuggestions: Int = 4,
    val dietBreakLeadWeeks: Int = 1,
)

@Serializable
enum class SuggestionKind(val label: String, val priority: Int) {
    DATA_GAP("Log more", 1),
    WEIGHT_TREND("Calories", 10),
    DIET_BREAK("Diet break", 15),
    RECOVERY("Recovery", 20),
    STRENGTH_STALL("Strength", 30),
    PROTEIN("Protein", 40),
    VOLUME("Training volume", 50),
    WATER("Water", 60),
}

/** What Apply does. The values are exact; the screen shows them and the repository runs them. */
@Serializable
sealed interface CoachAction {
    /** Move the calorie target. Protein stays, carbs give first, then fat down to its floor. */
    @Serializable @SerialName("set_calories")
    data class SetCalories(val fromKcal: Int, val toKcal: Int) : CoachAction

    /** A deload week for the active program: [percent] less weight for [days] days. */
    @Serializable @SerialName("deload")
    data class Deload(val programId: String?, val programName: String?, val percent: Int, val days: Int) : CoachAction

    /** Swap one exercise in the active program for another that trains the same muscle with the same kit. */
    @Serializable @SerialName("swap_exercise")
    data class SwapExercise(
        val fromExerciseId: String,
        val fromName: String,
        val toExerciseId: String,
        val toName: String,
    ) : CoachAction

    /** Next session: [setsLess] fewer sets on every exercise, for the next [days] days. */
    @Serializable @SerialName("easier_day")
    data class EasierDay(val setsLess: Int, val days: Int) : CoachAction

    /** Add or take away weekly sets for one muscle in the active program. */
    @Serializable @SerialName("muscle_sets")
    data class MuscleSets(val muscle: String, val deltaSets: Int) : CoachAction

    /** Nothing to change: the card only points something out. The button reads "Got it". */
    @Serializable @SerialName("acknowledge")
    data class Acknowledge(val note: String = "") : CoachAction
}

@Serializable
data class Suggestion(
    /** Stable id for "the same suggestion", used to avoid repeating a dismissed one. */
    val key: String,
    val kind: SuggestionKind,
    val title: String,
    /** One line with the numbers behind it. */
    val why: String,
    val details: String? = null,
    val action: CoachAction,
    val priority: Int,
)

@Serializable
enum class SuggestionStatus { PENDING, APPLIED, DISMISSED }

/** Small coach state kept with the settings (and so in backups). */
@Serializable
data class CoachState(
    /** Epoch day of the latest check-in. */
    val lastCheckInDay: Long? = null,
    /** Newest first, capped. Check-ins with no suggestions are listed here too. */
    val checkIns: List<CheckInRecord> = emptyList(),
    /** Prescriptions are lighter until this moment (UTC millis). */
    val deloadUntilMillis: Long? = null,
    val deloadPercent: Int = 10,
    /** The next sessions have fewer sets until this moment (UTC millis). */
    val easyUntilMillis: Long? = null,
    val easySetsLess: Int = 1,
    /** Epoch day a deload was last applied or started. */
    val lastDeloadDay: Long? = null,
)

@Serializable
data class CheckInRecord(val day: Long, val suggestionCount: Int, val headline: String)

// ---------------------------------------------------------------------------------------------
// Inputs. The repository fills these from the tables; the rules never see a database.
// ---------------------------------------------------------------------------------------------

data class FoodDayIn(val dayIndex: Long, val kcal: Double, val proteinG: Double, val entries: Int)

data class WaterDayIn(val dayIndex: Long, val consumedMl: Double, val targetMl: Double)

data class HealthDayIn(
    val dayIndex: Long,
    val sleepMinutes: Int? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
)

/** One session's best numbers for an exercise, working sets only. */
data class ExerciseSession(
    val dayIndex: Long,
    val bestE1rmKg: Double,
    val volumeKg: Double,
    val topWeightKg: Double,
    val topReps: Int,
)

data class ExerciseHistoryIn(
    val exerciseId: String,
    val name: String,
    val muscle: String,
    val equipment: String,
    val inActiveProgram: Boolean,
    /** Oldest first. */
    val sessions: List<ExerciseSession>,
)

/** A library exercise the coach may swap in. */
data class SwapCandidate(
    val exerciseId: String,
    val name: String,
    val muscle: String,
    val equipment: String,
    /** How often the person has trained it; known lifts win ties. */
    val timesTrained: Int = 0,
)

data class SetRpe(val dayIndex: Long, val rpe: Double?)

data class WeekInputs(
    /** Epoch day the check-in is for (local). */
    val today: Long,
    val phase: Phase,
    val pace: Pace,
    val contestWeeklyLossPct: Double = 0.75,
    val reverseStepKcal: Int = 75,
    /** Calories at rest x 1.1, or null when the person turned the floor off in Advanced. */
    val floorKcal: Int?,
    /** False under 18: the coach never suggests eating less. */
    val canReduceCalories: Boolean = true,
    /** Burn to climb to in a reverse diet, if known. */
    val maintenanceKcal: Int? = null,
    val currentKcal: Int,
    val targetProteinG: Int,
    val currentCarbsG: Int = 0,
    val currentFatG: Int = 0,
    val weighIns: List<WeightPoint>,
    val foodDays: List<FoodDayIn>,
    val waterDays: List<WaterDayIn> = emptyList(),
    val health: List<HealthDayIn> = emptyList(),
    val experience: Experience = Experience.SOME,
    val programId: String? = null,
    val programName: String? = null,
    /** Muscles with at least one exercise in the active program. */
    val programMuscles: Set<String> = emptySet(),
    val exercises: List<ExerciseHistoryIn> = emptyList(),
    val swapPool: List<SwapCandidate> = emptyList(),
    /** Working sets of the last 14 days, one entry each: (dayIndex, muscle). */
    val setsByDay: List<Pair<Long, String>> = emptyList(),
    /** Finished sessions in the last 14 days (epoch days). */
    val sessionDays: List<Long> = emptyList(),
    val setRpes: List<SetRpe> = emptyList(),
    val lastDeloadDay: Long? = null,
    val easyDayApplied: Boolean = false,
    /** First day of the current stretch of dieting, from the plan history. */
    val dietStartDay: Long? = null,
    val dietBreakEveryWeeks: Int? = null,
    /** Keys that must not be suggested right now (decided recently). */
    val suppressedKeys: Set<String> = emptySet(),
    /** True while a recent applied calorie change is still working through the trend. */
    val calorieCooldown: Boolean = false,
    /** High-protein food ideas, already filtered by the allergen engine. */
    val proteinIdeas: List<String> = emptyList(),
)

/** The numbers shown above the suggestions. */
data class WeekSummary(
    val trendChangeKg: Double?,
    val trendPctPerWeek: Double?,
    val avgKcal: Int?,
    val avgProteinG: Int?,
    val daysLogged: Int,
    val weighIns: Int,
    val workouts: Int,
    val avgSleepHours: Double?,
    val daysInWindow: Int,
)

data class CheckIn(
    val summary: WeekSummary,
    val suggestions: List<Suggestion>,
    /** "All on track" when there is nothing to suggest. */
    val headline: String,
)
