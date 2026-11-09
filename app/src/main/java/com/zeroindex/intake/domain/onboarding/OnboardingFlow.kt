package com.zeroindex.intake.domain.onboarding

import com.zeroindex.intake.domain.units.LengthUnit
import com.zeroindex.intake.domain.units.VolumeUnit
import com.zeroindex.intake.domain.units.WeightUnit

/**
 * The welcome flow as plain data and pure functions, so the step order, resume, skip and the
 * "does this install already have data" rule can be unit-tested on the JVM. No Android imports.
 */
enum class OnboardingStep {
    WELCOME,
    UNITS,
    ABOUT_YOU,
    GOAL,
    ALLERGENS,
    HOW_TO_START,
    PLANS,
    EXTRAS,
    DONE,
    ;

    /** The text stored in DataStore, so a renamed constant cannot silently change the saved step. */
    val key: String get() = name.lowercase()

    companion object {
        fun fromKey(key: String?): OnboardingStep? = entries.firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

object OnboardingFlow {
    val steps: List<OnboardingStep> = OnboardingStep.entries

    /** The step after [step]. The last step stays where it is. */
    fun next(step: OnboardingStep): OnboardingStep = steps.getOrElse(step.ordinal + 1) { step }

    /** The step before [step]. The first step stays where it is. */
    fun back(step: OnboardingStep): OnboardingStep = steps.getOrElse(step.ordinal - 1) { step }

    /** "Skip" on one step leaves its questions unanswered and moves on. */
    fun skip(step: OnboardingStep): OnboardingStep = if (canSkip(step)) next(step) else step

    /** The question steps can be skipped; the welcome and the closing screen have their own button. */
    fun canSkip(step: OnboardingStep): Boolean = step != OnboardingStep.WELCOME && step != OnboardingStep.DONE

    fun canGoBack(step: OnboardingStep): Boolean = step != OnboardingStep.WELCOME

    /** Where to pick up after the app was closed: the saved step, or the start when nothing valid is saved. */
    fun resume(savedKey: String?): OnboardingStep = OnboardingStep.fromKey(savedKey) ?: OnboardingStep.WELCOME

    /** 1-based position and total, for "Step 3 of 9" and the progress dots. */
    fun position(step: OnboardingStep): Pair<Int, Int> = (step.ordinal + 1) to steps.size

    fun isForward(from: OnboardingStep, to: OnboardingStep): Boolean = to.ordinal >= from.ordinal
}

/** What the database and profile already hold when the app starts. */
data class ExistingData(
    val foodLog: Boolean = false,
    val weighIn: Boolean = false,
    val workoutSession: Boolean = false,
    val profileBasics: Boolean = false,
) {
    val any: Boolean get() = foodLog || weighIn || workoutSession || profileBasics
}

enum class GateDecision {
    /** Show the welcome flow before the tabs. */
    SHOW_ONBOARDING,

    /** An install that already has data: set the done flag and go straight to the tabs. */
    AUTO_SKIP,

    /** Onboarding is finished: the tabs. */
    MAIN,
}

object OnboardingGate {
    /**
     * - Finished: the tabs.
     * - [resuming] (the person started, or asked to see the welcome again): the flow, even though
     *   the answers given so far now count as data, so quitting midway never skips the rest.
     * - Otherwise an install that already has any food log, weigh-in, workout session or profile
     *   basics skips the flow, so nobody with data is ever trapped in it.
     */
    fun decide(done: Boolean, resuming: Boolean, existing: ExistingData): GateDecision = when {
        done -> GateDecision.MAIN
        resuming -> GateDecision.SHOW_ONBOARDING
        existing.any -> GateDecision.AUTO_SKIP
        else -> GateDecision.SHOW_ONBOARDING
    }
}

data class UnitPrefs(val weight: WeightUnit, val length: LengthUnit, val volume: VolumeUnit) {
    companion object {
        val IMPERIAL = UnitPrefs(WeightUnit.POUNDS, LengthUnit.INCHES, VolumeUnit.US_FLUID_OUNCES)
        val METRIC = UnitPrefs(WeightUnit.KILOGRAMS, LengthUnit.CENTIMETRES, VolumeUnit.MILLILITRES)
    }
}

object UnitDefaults {
    /** The few countries that still use pounds and inches day to day. */
    private val IMPERIAL_COUNTRIES = setOf("US", "LR", "MM")

    /** lb / in / fl oz for a US locale, kg / cm / ml everywhere else (and when the country is unknown). */
    fun forCountry(isoCountry: String?): UnitPrefs =
        if (isoCountry?.trim()?.uppercase() in IMPERIAL_COUNTRIES) UnitPrefs.IMPERIAL else UnitPrefs.METRIC

    /**
     * The locale default is written once, on the first run. Replaying the welcome must never
     * overwrite units the person already chose.
     */
    fun shouldSeed(replay: Boolean, alreadySeeded: Boolean): Boolean = !replay && !alreadySeeded
}

/** A choice on the "Your plans" step; opened once the tabs are showing. */
enum class PendingDestination(val key: String, val label: String, val blurb: String) {
    MEAL_PLAN_IMPORT("meal_plan_import", "Import a meal plan file", "Opens Meal plans, where you pick the file."),
    MEAL_PLAN_BUILD("meal_plan_build", "Build a meal plan", "Opens Meal plans, where you start a new one."),
    PROGRAM_IMPORT("program_import", "Import a program file", "Opens Programs, where you pick the file."),
    PROGRAM_GENERATE("program_generate", "Make a program for me", "Answer a few questions and get a program."),
    PROGRAM_TEMPLATE("program_template", "Start from a template", "Pick a ready-made program to edit."),
    ;

    companion object {
        /** Null for nothing saved or a key from a version that no longer exists. */
        fun fromKey(key: String?): PendingDestination? = entries.firstOrNull { it.key == key }
    }
}

/** What opening the pending destination should do, decided from stored state alone. */
object PendingDestinations {
    /**
     * The destination to open now, or null. It waits until onboarding is done and the tour (which
     * runs on Today) has finished or was never due, so a person is never pulled off the tour.
     */
    fun readyToOpen(pending: PendingDestination?, onboardingDone: Boolean, tourPending: Boolean): PendingDestination? =
        pending?.takeIf { onboardingDone && !tourPending }
}

/** The four tabs that show a first-visit tip. Today has the tour instead. */
enum class TipKey(val key: String) {
    FOOD("food"),
    TRAIN("train"),
    PROGRESS("progress"),
    MORE("more"),
}

object TipsSeen {
    fun parse(stored: String?): Set<TipKey> =
        stored.orEmpty().split(',').mapNotNull { part -> TipKey.entries.firstOrNull { it.key == part.trim() } }.toSet()

    fun encode(seen: Set<TipKey>): String = seen.sortedBy { it.ordinal }.joinToString(",") { it.key }

    fun shouldShow(key: TipKey, seen: Set<TipKey>): Boolean = key !in seen
}
