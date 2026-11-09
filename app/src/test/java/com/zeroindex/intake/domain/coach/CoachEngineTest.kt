package com.zeroindex.intake.domain.coach

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.goals.Pace
import com.zeroindex.intake.domain.goals.Phase
import com.zeroindex.intake.domain.tdee.WeightPoint
import com.zeroindex.intake.domain.training.generator.Experience
import org.junit.Test

class CoachEngineTest {

    private val today = 20_000L

    /** A steady trend: [pctPerWeek] of 80 kg each week, one weigh-in a day for [days] days. */
    private fun weights(pctPerWeek: Double, days: Int = 60, startKg: Double = 80.0): List<WeightPoint> {
        val perDay = startKg * pctPerWeek / 100.0 / 7.0
        return (0 until days).map { WeightPoint(today - (days - 1) + it, startKg + perDay * (it - (days - 1))) }
    }

    private fun food(days: Int = 7, kcal: Double = 2_000.0, protein: Double = 160.0) =
        (0 until 14).map { FoodDayIn(today - it, if (it < days) kcal else 0.0, if (it < days) protein else 0.0, if (it < days) 4 else 0) }

    private fun inputs(
        phase: Phase = Phase.CUT,
        pctPerWeek: Double = -0.6,
        kcal: Int = 2_200,
        block: (WeekInputs) -> WeekInputs = { it },
    ) = block(
        WeekInputs(
            today = today,
            phase = phase,
            pace = Pace.STEADY,
            floorKcal = 1_500,
            maintenanceKcal = 2_700,
            currentKcal = kcal,
            targetProteinG = 170,
            currentCarbsG = 220,
            currentFatG = 60,
            weighIns = weights(pctPerWeek),
            foodDays = food(),
        ),
    )

    private fun CheckIn.cal() = suggestions.firstOrNull { it.kind == SuggestionKind.WEIGHT_TREND }

    // ---------------------------------------------------------------- weight trend

    @Test fun cutOnPaceSuggestsNothing() {
        val r = CoachEngine.review(inputs(pctPerWeek = -0.6))
        assertThat(r.cal()).isNull()
        assertThat(r.headline).isEqualTo("All on track")
    }

    @Test fun cutLosingTooSlowlyTakesALargeStepWhenBarelyMoving() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.05)).cal()!!
        assertThat(s.action).isEqualTo(CoachAction.SetCalories(2_200, 2_050))
        assertThat(s.why).contains("aim")
        assertThat(s.key).isEqualTo("cal:down")
    }

    @Test fun cutLosingSlowlyButMovingTakesTheSmallStep() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.25)).cal()!!
        assertThat(s.action).isEqualTo(CoachAction.SetCalories(2_200, 2_100))
    }

    @Test fun cutGainingWeightIsTooSlow() {
        val s = CoachEngine.review(inputs(pctPerWeek = 0.2)).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(2_050)
    }

    @Test fun cutNeverGoesBelowTheFloor() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.05, kcal = 1_560) { it.copy(floorKcal = 1_500) }).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(1_500)
    }

    @Test fun atTheFloorTheCoachSaysSoAndChangesNothing() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.05, kcal = 1_500) { it.copy(floorKcal = 1_500) }).cal()!!
        assertThat(s.action).isInstanceOf(CoachAction.Acknowledge::class.java)
        assertThat(s.details).contains("floor")
    }

    @Test fun anAdvancedOverrideWithNoFloorIsRespected() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.05, kcal = 1_400) { it.copy(floorKcal = null) }).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(1_250)
    }

    @Test fun nobodyUnder18GetsACut() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.05) { it.copy(canReduceCalories = false) }).cal()!!
        assertThat(s.action).isInstanceOf(CoachAction.Acknowledge::class.java)
        assertThat(s.details).contains("under 18")
    }

    @Test fun cutLosingTooFastAddsCaloriesAndComesFirst() {
        val r = CoachEngine.review(inputs(pctPerWeek = -1.3))
        val s = r.cal()!!
        assertThat(s.action).isEqualTo(CoachAction.SetCalories(2_200, 2_350))
        assertThat(s.details).contains("1%")
        assertThat(r.suggestions.first()).isEqualTo(s)
    }

    @Test fun cutLosingAboutOneAndAHalfTimesPaceAddsTheSmallStep() {
        val s = CoachEngine.review(inputs(pctPerWeek = -0.95)).cal()!!
        assertThat(s.action).isEqualTo(CoachAction.SetCalories(2_200, 2_300))
    }

    @Test fun bulkGainingTooSlowlyAddsCalories() {
        val s = CoachEngine.review(inputs(Phase.BULK, pctPerWeek = 0.05, kcal = 3_000)).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(3_150)
    }

    @Test fun bulkGainingTooFastTrimsCalories() {
        val s = CoachEngine.review(inputs(Phase.BULK, pctPerWeek = 0.9, kcal = 3_000)).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(2_850)
    }

    @Test fun bulkOnPaceIsLeftAlone() {
        assertThat(CoachEngine.review(inputs(Phase.BULK, pctPerWeek = 0.35, kcal = 3_000)).cal()).isNull()
    }

    @Test fun maintainStaysInsideAQuarterPercent() {
        assertThat(CoachEngine.review(inputs(Phase.MAINTAIN, pctPerWeek = 0.2)).cal()).isNull()
        assertThat(CoachEngine.review(inputs(Phase.MAINTAIN, pctPerWeek = -0.2)).cal()).isNull()
    }

    @Test fun maintainDriftingDownAddsAndDriftingUpTrims() {
        val down = CoachEngine.review(inputs(Phase.MAINTAIN, pctPerWeek = -0.4)).cal()!!
        assertThat((down.action as CoachAction.SetCalories).toKcal).isGreaterThan(2_200)
        val up = CoachEngine.review(inputs(Phase.MAINTAIN, pctPerWeek = 0.4)).cal()!!
        assertThat((up.action as CoachAction.SetCalories).toKcal).isLessThan(2_200)
    }

    @Test fun reverseDietAdvancesTheStepWhenWeightIsStable() {
        val s = CoachEngine.review(inputs(Phase.REVERSE_DIET, pctPerWeek = 0.0) { it.copy(reverseStepKcal = 75) }).cal()!!
        assertThat(s.action).isEqualTo(CoachAction.SetCalories(2_200, 2_275))
    }

    @Test fun reverseDietStepIsCappedAtMaintenance() {
        val s = CoachEngine.review(inputs(Phase.REVERSE_DIET, pctPerWeek = 0.0, kcal = 2_680) { it.copy(reverseStepKcal = 75, maintenanceKcal = 2_700) }).cal()!!
        assertThat((s.action as CoachAction.SetCalories).toKcal).isEqualTo(2_700)
        assertThat(CoachEngine.review(inputs(Phase.REVERSE_DIET, pctPerWeek = 0.0, kcal = 2_700) { it.copy(maintenanceKcal = 2_700) }).cal()).isNull()
    }

    @Test fun reverseDietHoldsWhenWeightClimbs() {
        val s = CoachEngine.review(inputs(Phase.REVERSE_DIET, pctPerWeek = 0.8)).cal()!!
        assertThat(s.action).isInstanceOf(CoachAction.Acknowledge::class.java)
        assertThat(s.title).contains("Hold")
    }

    @Test fun contestPrepUsesTighterBandsAndSmallerSteps() {
        // Aim is 0.75 a week. Losing 0.5 is 67%: fine for a normal cut (50% band), too slow for contest prep (80%).
        val normalCut = CoachEngine.review(inputs(Phase.CUT, pctPerWeek = -0.5) { it.copy(pace = Pace.FAST) }).cal()
        assertThat(normalCut).isNull()
        val contest = CoachEngine.review(inputs(Phase.CONTEST_PREP, pctPerWeek = -0.5)).cal()!!
        assertThat(contest.action).isEqualTo(CoachAction.SetCalories(2_200, 2_150))
    }

    @Test fun anAppliedCalorieChangeKeepsTheRuleQuietDuringTheCooldown() {
        val r = CoachEngine.review(inputs(pctPerWeek = -0.05) { it.copy(calorieCooldown = true) })
        assertThat(r.cal()).isNull()
    }

    @Test fun aDismissedSuggestionIsNotRepeated() {
        val r = CoachEngine.review(inputs(pctPerWeek = -0.05) { it.copy(suppressedKeys = setOf("cal:down")) })
        assertThat(r.cal()).isNull()
    }

    @Test fun noTargetMeansNoCalorieAdvice() {
        assertThat(CoachEngine.review(inputs(pctPerWeek = -0.05, kcal = 0)).cal()).isNull()
    }

    // ---------------------------------------------------------------- adherence gate

    @Test fun fewLoggedDaysBlockCalorieChangesAndSaySo() {
        val r = CoachEngine.review(inputs(pctPerWeek = -0.05) { it.copy(foodDays = food(days = 4)) })
        assertThat(r.cal()).isNull()
        val gap = r.suggestions.first()
        assertThat(gap.kind).isEqualTo(SuggestionKind.DATA_GAP)
        assertThat(gap.title).isEqualTo("Not enough data this week to adjust")
        assertThat(gap.why).contains("4 of 7")
        assertThat(gap.why).contains("Here's what to log")
        assertThat(gap.action).isInstanceOf(CoachAction.Acknowledge::class.java)
    }

    @Test fun fewWeighInsBlockCalorieChanges() {
        val few = (0 until 2).map { WeightPoint(today - it, 80.0) }
        val r = CoachEngine.review(inputs(pctPerWeek = -0.05) { it.copy(weighIns = few) })
        assertThat(r.cal()).isNull()
        assertThat(r.suggestions.first().kind).isEqualTo(SuggestionKind.DATA_GAP)
        assertThat(r.suggestions.first().why).contains("weighed in on 2")
    }

    @Test fun theGateOutranksEverythingElse() {
        val r = CoachEngine.review(inputs { it.copy(foodDays = food(days = 3, protein = 50.0)) })
        assertThat(r.suggestions.first().kind).isEqualTo(SuggestionKind.DATA_GAP)
    }

    @Test fun aLowCalorieDayDoesNotCountAsLogged() {
        val days = (0 until 14).map { FoodDayIn(today - it, if (it < 7) 300.0 else 0.0, 20.0, 1) }
        val r = CoachEngine.review(inputs { it.copy(foodDays = days) })
        assertThat(r.summary.daysLogged).isEqualTo(0)
    }

    // ---------------------------------------------------------------- protein

    @Test fun lowProteinIsPointedOutWithFoodIdeas() {
        val r = CoachEngine.review(
            inputs { it.copy(foodDays = food(protein = 120.0), proteinIdeas = listOf("Eggs (13 g protein in 100 g)", "Tuna (25 g protein in 100 g)")) },
        )
        val s = r.suggestions.single { it.kind == SuggestionKind.PROTEIN }
        assertThat(s.why).contains("120 g")
        assertThat(s.why).contains("170 g")
        assertThat(s.details).contains("Eggs")
        assertThat(s.action).isInstanceOf(CoachAction.Acknowledge::class.java)
    }

    @Test fun proteinAtEightyFivePercentIsFine() {
        val r = CoachEngine.review(inputs { it.copy(foodDays = food(protein = 150.0)) })
        assertThat(r.suggestions.none { it.kind == SuggestionKind.PROTEIN }).isTrue()
    }

    // ---------------------------------------------------------------- water

    @Test fun lowWaterIsPointedOutOnlyWhenWaterIsActuallyLogged() {
        val low = (0 until 7).map { WaterDayIn(today - it, 1_200.0, 2_500.0) }
        val r = CoachEngine.review(inputs { it.copy(waterDays = low) })
        val s = r.suggestions.single { it.kind == SuggestionKind.WATER }
        assertThat(s.why).contains("1,200")
        assertThat(s.why).contains("2,500")
        val never = (0 until 7).map { WaterDayIn(today - it, 0.0, 2_500.0) }
        assertThat(CoachEngine.review(inputs { it.copy(waterDays = never) }).suggestions.none { it.kind == SuggestionKind.WATER }).isTrue()
    }

    // ---------------------------------------------------------------- strength stalls

    private fun lift(id: String, name: String, vararg e1rm: Double, muscle: String = "chest", equipment: String = "barbell") =
        ExerciseHistoryIn(
            id, name, muscle, equipment, true,
            e1rm.mapIndexed { i, v -> ExerciseSession(today - 14 + i * 7L, v, v * 5, v * 0.8, 5) },
        )

    @Test fun threeFlatSessionsSuggestADeloadWhenNoneRecent() {
        val r = CoachEngine.review(inputs { it.copy(exercises = listOf(lift("bench", "Bench press", 100.0, 100.0, 99.0)), programId = "p", programName = "Upper") })
        val s = r.suggestions.single { it.kind == SuggestionKind.STRENGTH_STALL }
        assertThat(s.key).isEqualTo("stall:deload")
        assertThat(s.action).isEqualTo(CoachAction.Deload("p", "Upper", 10, 7))
        assertThat(s.why).contains("Bench press")
    }

    @Test fun aRecentDeloadMeansASwapForTheSameMuscleAndEquipment() {
        val pool = listOf(
            SwapCandidate("bench", "Bench press", "chest", "barbell"),
            SwapCandidate("incline", "Incline press", "chest", "barbell", timesTrained = 2),
            SwapCandidate("fly", "Cable fly", "chest", "cable"),
            SwapCandidate("row", "Row", "back", "barbell"),
        )
        val r = CoachEngine.review(
            inputs { it.copy(exercises = listOf(lift("bench", "Bench press", 100.0, 100.0, 99.0)), swapPool = pool, lastDeloadDay = today - 10) },
        )
        val s = r.suggestions.single { it.kind == SuggestionKind.STRENGTH_STALL }
        assertThat(s.action).isEqualTo(CoachAction.SwapExercise("bench", "Bench press", "incline", "Incline press"))
    }

    @Test fun noMatchingSwapMeansNoSuggestion() {
        val r = CoachEngine.review(
            inputs { it.copy(exercises = listOf(lift("bench", "Bench press", 100.0, 100.0, 99.0)), swapPool = emptyList(), lastDeloadDay = today - 10) },
        )
        assertThat(r.suggestions.none { it.kind == SuggestionKind.STRENGTH_STALL }).isTrue()
    }

    @Test fun progressingLiftsAreNotStalled() {
        val r = CoachEngine.review(inputs { it.copy(exercises = listOf(lift("bench", "Bench press", 100.0, 102.0, 104.0))) })
        assertThat(r.suggestions.none { it.kind == SuggestionKind.STRENGTH_STALL }).isTrue()
    }

    @Test fun moreVolumeAtTheSameMaxCountsAsProgress() {
        val ex = ExerciseHistoryIn(
            "squat", "Squat", "quads", "barbell", true,
            listOf(
                ExerciseSession(today - 14, 150.0, 3_000.0, 120.0, 5),
                ExerciseSession(today - 7, 150.0, 3_100.0, 120.0, 5),
                ExerciseSession(today - 1, 150.0, 3_300.0, 120.0, 5),
            ),
        )
        assertThat(CoachEngine.stalls(inputs { it.copy(exercises = listOf(ex)) }, CoachConfig())).isEmpty()
    }

    @Test fun twoSessionsAreNotEnoughToCallAStall() {
        val ex = lift("bench", "Bench press", 100.0, 99.0)
        assertThat(CoachEngine.stalls(inputs { it.copy(exercises = listOf(ex)) }, CoachConfig())).isEmpty()
    }

    @Test fun liftsOutsideTheActiveProgramAreIgnored() {
        val ex = lift("bench", "Bench press", 100.0, 100.0, 99.0).copy(inActiveProgram = false)
        assertThat(CoachEngine.stalls(inputs { it.copy(exercises = listOf(ex)) }, CoachConfig())).isEmpty()
    }

    // ---------------------------------------------------------------- recovery

    private fun health(sleep: Int?, hr: Double?, hrv: Double? = null, recentFrom: Long = 0, baseline: Boolean = true) =
        (0L until 35L).map { back ->
            val recent = back < 7
            HealthDayIn(
                today - back,
                if (recent) sleep else 450,
                if (recent) hr else 55.0,
                if (recent) hrv else 60.0,
            )
        }.let { if (baseline) it else it.filter { d -> today - d.dayIndex < 7 } }

    @Test fun oneBadSignalAloneDoesNotTriggerAnything() {
        val r = CoachEngine.review(inputs { it.copy(health = health(sleep = 320, hr = 55.0)) })
        assertThat(r.suggestions.none { it.kind == SuggestionKind.RECOVERY }).isTrue()
    }

    @Test fun poorSleepAndRaisedRestingHeartRateSuggestAnEasierDay() {
        val r = CoachEngine.review(inputs { it.copy(health = health(sleep = 320, hr = 63.0)) })
        val s = r.suggestions.single { it.kind == SuggestionKind.RECOVERY }
        assertThat(s.action).isEqualTo(CoachAction.EasierDay(1, 3))
        assertThat(s.why).contains("under 6 hours")
        assertThat(s.why).contains("resting heart rate")
        assertThat(s.details).contains("not medical advice")
    }

    @Test fun threeSignalsSuggestADeload() {
        val r = CoachEngine.review(inputs { it.copy(health = health(sleep = 320, hr = 63.0, hrv = 48.0)) })
        val s = r.suggestions.single { it.kind == SuggestionKind.RECOVERY }
        assertThat(s.action).isInstanceOf(CoachAction.Deload::class.java)
        assertThat(s.why).contains("HRV is down 20%")
    }

    @Test fun highEffortOnMostSetsCountsAsASignal() {
        val rpes = (0 until 12).map { SetRpe(today - 2, if (it < 8) 9.5 else 8.0) }
        val r = CoachEngine.review(inputs { it.copy(health = health(sleep = 320, hr = 55.0), setRpes = rpes) })
        val s = r.suggestions.single { it.kind == SuggestionKind.RECOVERY }
        assertThat(s.why).contains("8 of 12 sets")
    }

    @Test fun noHealthDataMeansNoRecoveryAdvice() {
        assertThat(CoachEngine.recoverySignals(inputs(), CoachConfig()).available).isEqualTo(0)
    }

    @Test fun anEasierDayAlreadyAppliedEscalatesToADeload() {
        val r = CoachEngine.review(inputs { it.copy(health = health(sleep = 320, hr = 63.0), easyDayApplied = true) })
        assertThat(r.suggestions.single { it.kind == SuggestionKind.RECOVERY }.action).isInstanceOf(CoachAction.Deload::class.java)
    }

    // ---------------------------------------------------------------- volume

    private fun sets(muscle: String, perWeek: Int, week: Int) =
        (0 until perWeek).map { (today - week * 7L - (it % 3)) to muscle }

    @Test fun twoWeeksWellBelowTheRangeSuggestsTwoMoreSets() {
        val inp = inputs {
            it.copy(
                experience = Experience.SOME,
                programMuscles = setOf("chest", "back"),
                setsByDay = sets("chest", 4, 0) + sets("chest", 5, 1) + sets("back", 12, 0) + sets("back", 12, 1),
                sessionDays = listOf(today - 1, today - 4, today - 8, today - 11),
            )
        }
        val s = CoachEngine.review(inp).suggestions.single { it.kind == SuggestionKind.VOLUME }
        assertThat(s.action).isEqualTo(CoachAction.MuscleSets("chest", 2))
        assertThat(s.why).contains("4 hard sets for chest")
    }

    @Test fun skippingWorkoutsIsNotAVolumeProblem() {
        val inp = inputs {
            it.copy(programMuscles = setOf("chest"), setsByDay = sets("chest", 2, 0) + sets("chest", 2, 1), sessionDays = listOf(today - 1))
        }
        assertThat(CoachEngine.review(inp).suggestions.none { it.kind == SuggestionKind.VOLUME }).isTrue()
    }

    @Test fun aboveTheRangeWithPoorRecoveryTrimsTwoSets() {
        val inp = inputs {
            it.copy(
                experience = Experience.SOME,
                programMuscles = setOf("chest"),
                setsByDay = sets("chest", 20, 0) + sets("chest", 20, 1),
                sessionDays = listOf(today - 1, today - 4, today - 8, today - 11),
                health = health(sleep = 320, hr = 55.0),
            )
        }
        val s = CoachEngine.review(inp).suggestions.single { it.kind == SuggestionKind.VOLUME }
        assertThat(s.action).isEqualTo(CoachAction.MuscleSets("chest", -2))
    }

    @Test fun aboveTheRangeWithGoodRecoveryIsLeftAlone() {
        val inp = inputs {
            it.copy(programMuscles = setOf("chest"), setsByDay = sets("chest", 20, 0), sessionDays = listOf(today - 1, today - 4))
        }
        assertThat(CoachEngine.review(inp).suggestions.none { it.kind == SuggestionKind.VOLUME }).isTrue()
    }

    // ---------------------------------------------------------------- diet break

    @Test fun aDietBreakIsRemindedTheWeekBeforeAndDuring() {
        val start = today - 7L * 9 // week 10 of the diet
        val soon = CoachEngine.review(inputs { it.copy(dietStartDay = start, dietBreakEveryWeeks = 10) })
        assertThat(soon.suggestions.single { it.kind == SuggestionKind.DIET_BREAK }.title).contains("due next week")
        val during = CoachEngine.review(inputs { it.copy(dietStartDay = today - 7L * 10, dietBreakEveryWeeks = 10) })
        assertThat(during.suggestions.single { it.kind == SuggestionKind.DIET_BREAK }.title).contains("diet break week")
    }

    @Test fun noDietBreakMidDietOrOutsideACut() {
        assertThat(CoachEngine.review(inputs { it.copy(dietStartDay = today - 21, dietBreakEveryWeeks = 10) }).suggestions.none { it.kind == SuggestionKind.DIET_BREAK }).isTrue()
        assertThat(CoachEngine.review(inputs(Phase.MAINTAIN, 0.0) { it.copy(dietStartDay = today - 63, dietBreakEveryWeeks = 10) }).suggestions.none { it.kind == SuggestionKind.DIET_BREAK }).isTrue()
    }

    // ---------------------------------------------------------------- output

    @Test fun atMostFourSuggestionsSortedByPriority() {
        val lowWater = (0 until 7).map { WaterDayIn(today - it, 800.0, 2_500.0) }
        val inp = inputs(pctPerWeek = -0.05) {
            it.copy(
                foodDays = food(protein = 100.0),
                waterDays = lowWater,
                health = health(sleep = 320, hr = 63.0),
                exercises = listOf(lift("bench", "Bench press", 100.0, 100.0, 99.0)),
                dietStartDay = today - 7L * 9,
                dietBreakEveryWeeks = 10,
            )
        }
        val r = CoachEngine.review(inp)
        assertThat(r.suggestions).hasSize(4)
        assertThat(r.suggestions.map { it.priority }).isInOrder()
        assertThat(r.suggestions.map { it.kind }).doesNotContain(SuggestionKind.WATER)
        assertThat(r.headline).isEqualTo("4 suggestions this week")
    }

    @Test fun theReviewIsDeterministic() {
        val inp = inputs(pctPerWeek = -0.05) { it.copy(foodDays = food(protein = 100.0)) }
        assertThat(CoachEngine.review(inp)).isEqualTo(CoachEngine.review(inp))
    }

    @Test fun theSummaryHasTheWeeksNumbers() {
        val r = CoachEngine.review(
            inputs {
                it.copy(
                    sessionDays = listOf(today - 1, today - 3, today - 20),
                    health = health(sleep = 420, hr = 55.0),
                )
            },
        )
        assertThat(r.summary.daysLogged).isEqualTo(7)
        assertThat(r.summary.avgKcal).isEqualTo(2_000)
        assertThat(r.summary.avgProteinG).isEqualTo(160)
        assertThat(r.summary.workouts).isEqualTo(2)
        assertThat(r.summary.avgSleepHours).isWithin(0.01).of(7.0)
        assertThat(r.summary.weighIns).isEqualTo(7)
    }

    // ---------------------------------------------------------------- macros

    @Test fun aCutTakesCarbsFirstAndKeepsProtein() {
        val (p, c, f) = CoachEngine.macrosFor(170, 220, 60, 2_200, 2_050)
        assertThat(p).isEqualTo(170)
        assertThat(c).isEqualTo(220 - 38) // 150 kcal / 4 g, rounded
        assertThat(f).isEqualTo(60)
    }

    @Test fun whenCarbsHitTheirFloorFatGivesNextButNotBelowItsFloor() {
        val (_, c, f) = CoachEngine.macrosFor(170, 105, 60, 2_000, 1_850, weightKg = 80.0)
        assertThat(f).isEqualTo(48) // fat gives next, down to 0.6 g/kg x 80 kg and no further
        assertThat(c).isLessThan(100) // the rest has to come from carbs again
        val (_, _, fatFloorHeld) = CoachEngine.macrosFor(170, 100, 49, 2_000, 1_000, weightKg = 80.0)
        assertThat(fatFloorHeld).isAtLeast(48)
    }

    @Test fun addingCaloriesGoesToCarbs() {
        val (p, c, f) = CoachEngine.macrosFor(170, 220, 60, 2_200, 2_350)
        assertThat(listOf(p, c, f)).containsExactly(170, 258, 60).inOrder()
    }
}
