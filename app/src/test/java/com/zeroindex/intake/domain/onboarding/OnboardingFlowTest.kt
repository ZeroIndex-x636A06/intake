package com.zeroindex.intake.domain.onboarding

import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun stepsRunInTheBriefsOrder() {
        assertEquals(
            listOf("welcome", "units", "about_you", "goal", "equipment", "allergens", "how_to_start", "plans", "extras", "done"),
            OnboardingFlow.steps.map { it.key },
        )
    }

    @Test
    fun nextAndBackWalkTheListAndStopAtTheEnds() {
        assertEquals(OnboardingStep.UNITS, OnboardingFlow.next(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DONE, OnboardingFlow.next(OnboardingStep.EXTRAS))
        assertEquals(OnboardingStep.DONE, OnboardingFlow.next(OnboardingStep.DONE))
        assertEquals(OnboardingStep.ABOUT_YOU, OnboardingFlow.back(OnboardingStep.GOAL))
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.back(OnboardingStep.WELCOME))
    }

    @Test
    fun skipMovesOnOnlyFromQuestionSteps() {
        assertEquals(OnboardingStep.GOAL, OnboardingFlow.skip(OnboardingStep.ABOUT_YOU))
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.skip(OnboardingStep.WELCOME))
        assertEquals(OnboardingStep.DONE, OnboardingFlow.skip(OnboardingStep.DONE))
        assertTrue(OnboardingFlow.canSkip(OnboardingStep.ALLERGENS))
        assertFalse(OnboardingFlow.canGoBack(OnboardingStep.WELCOME))
        assertTrue(OnboardingFlow.canGoBack(OnboardingStep.UNITS))
    }

    @Test
    fun resumeReturnsTheSavedStepAndFallsBackToTheStart() {
        assertEquals(OnboardingStep.GOAL, OnboardingFlow.resume("goal"))
        assertEquals(OnboardingStep.GOAL, OnboardingFlow.resume(" GOAL "))
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.resume(null))
        assertEquals(OnboardingStep.WELCOME, OnboardingFlow.resume("a_step_from_the_future"))
    }

    @Test
    fun everyStepSurvivesAKeyRoundTrip() {
        OnboardingStep.entries.forEach { assertEquals(it, OnboardingFlow.resume(it.key)) }
    }

    @Test
    fun positionIsOneBased() {
        assertEquals(1 to 10, OnboardingFlow.position(OnboardingStep.WELCOME))
        assertEquals(10 to 10, OnboardingFlow.position(OnboardingStep.DONE))
    }

    @Test
    fun directionForTheTransition() {
        assertTrue(OnboardingFlow.isForward(OnboardingStep.UNITS, OnboardingStep.ABOUT_YOU))
        assertFalse(OnboardingFlow.isForward(OnboardingStep.GOAL, OnboardingStep.UNITS))
    }

    // --- The auto-skip rule -----------------------------------------------------------------

    @Test
    fun aFreshInstallShowsTheFlow() {
        assertEquals(GateDecision.SHOW_ONBOARDING, OnboardingGate.decide(false, false, ExistingData()))
    }

    @Test
    fun anyExistingDataSkipsTheFlow() {
        listOf(
            ExistingData(foodLog = true),
            ExistingData(weighIn = true),
            ExistingData(workoutSession = true),
            ExistingData(profileBasics = true),
        ).forEach { data ->
            assertTrue(data.any)
            assertEquals(GateDecision.AUTO_SKIP, OnboardingGate.decide(false, false, data))
        }
    }

    @Test
    fun finishedOnboardingGoesToTheTabsWhateverTheData() {
        assertEquals(GateDecision.MAIN, OnboardingGate.decide(true, false, ExistingData()))
        assertEquals(GateDecision.MAIN, OnboardingGate.decide(true, true, ExistingData(foodLog = true)))
    }

    @Test
    fun aFlowThatWasStartedResumesEvenThoughItsOwnAnswersNowCountAsData() {
        // Weight and basics from step 3 are saved; relaunching must not auto-skip the rest.
        val answered = ExistingData(weighIn = true, profileBasics = true)
        assertEquals(GateDecision.SHOW_ONBOARDING, OnboardingGate.decide(false, true, answered))
    }

    // --- Locale default units ---------------------------------------------------------------

    @Test
    fun usLocaleDefaultsToPoundsAndInches() {
        val prefs = UnitDefaults.forCountry("US")
        assertEquals(WeightUnit.POUNDS, prefs.weight)
        assertEquals(LengthUnit.INCHES, prefs.length)
        assertEquals(VolumeUnit.US_FLUID_OUNCES, prefs.volume)
        assertEquals(UnitPrefs.IMPERIAL, UnitDefaults.forCountry("us"))
    }

    @Test
    fun otherLocalesDefaultToMetric() {
        listOf("GB", "DE", "CA", "AU", "IN", "", null).forEach {
            assertEquals("country $it", UnitPrefs.METRIC, UnitDefaults.forCountry(it))
        }
        assertEquals(WeightUnit.KILOGRAMS, UnitDefaults.forCountry("FR").weight)
        assertEquals(LengthUnit.CENTIMETRES, UnitDefaults.forCountry("FR").length)
        assertEquals(VolumeUnit.MILLILITRES, UnitDefaults.forCountry("FR").volume)
    }

    @Test
    fun theLocaleDefaultIsWrittenOnceAndNeverOnAReplay() {
        assertTrue(UnitDefaults.shouldSeed(replay = false, alreadySeeded = false))
        assertFalse(UnitDefaults.shouldSeed(replay = false, alreadySeeded = true))
        assertFalse(UnitDefaults.shouldSeed(replay = true, alreadySeeded = false))
    }

    // --- Pending destination ----------------------------------------------------------------

    @Test
    fun pendingDestinationKeysRoundTripAndUnknownKeysAreIgnored() {
        PendingDestination.entries.forEach { assertEquals(it, PendingDestination.fromKey(it.key)) }
        assertNull(PendingDestination.fromKey(null))
        assertNull(PendingDestination.fromKey("removed_in_a_later_version"))
    }

    @Test
    fun pendingDestinationWaitsForOnboardingAndTheTour() {
        val pending = PendingDestination.PROGRAM_GENERATE
        assertNull(PendingDestinations.readyToOpen(pending, onboardingDone = false, tourPending = false))
        assertNull(PendingDestinations.readyToOpen(pending, onboardingDone = true, tourPending = true))
        assertEquals(pending, PendingDestinations.readyToOpen(pending, onboardingDone = true, tourPending = false))
        assertNull(PendingDestinations.readyToOpen(null, onboardingDone = true, tourPending = false))
    }

    // --- First-visit tips -------------------------------------------------------------------

    @Test
    fun tipsAreStoredPerTab() {
        val seen = setOf(TipKey.TRAIN, TipKey.FOOD)
        assertEquals("food,train", TipsSeen.encode(seen))
        assertEquals(seen, TipsSeen.parse("food,train"))
        assertEquals(emptySet<TipKey>(), TipsSeen.parse(null))
        assertEquals(setOf(TipKey.MORE), TipsSeen.parse("more,nonsense,"))
        assertFalse(TipsSeen.shouldShow(TipKey.FOOD, seen))
        assertTrue(TipsSeen.shouldShow(TipKey.PROGRESS, seen))
    }
}
