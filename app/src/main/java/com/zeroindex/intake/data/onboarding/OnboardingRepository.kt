package com.zeroindex.intake.data.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zeroindex.intake.data.db.dao.BodyMetricDao
import com.zeroindex.intake.data.db.dao.FoodLogDao
import com.zeroindex.intake.data.db.dao.WorkoutDao
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.onboarding.ExistingData
import com.zeroindex.intake.domain.onboarding.GateDecision
import com.zeroindex.intake.domain.onboarding.OnboardingFlow
import com.zeroindex.intake.domain.onboarding.OnboardingGate
import com.zeroindex.intake.domain.onboarding.OnboardingStep
import com.zeroindex.intake.domain.onboarding.PendingDestination
import com.zeroindex.intake.domain.onboarding.TipKey
import com.zeroindex.intake.domain.onboarding.TipsSeen
import com.zeroindex.intake.domain.onboarding.UnitDefaults
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A separate DataStore file, on purpose: these are "seen" flags and a resume point that belong to
 * this phone and this install. They are not settings, they must not travel in a backup or export,
 * and keeping them out of `intake_settings` keeps them clear of the settings-backup code.
 */
private val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore(name = "intake_onboarding")

/** Which way the person chose to start on the "How to start" step. */
enum class StartChoice(val key: String) {
    BASELINE("baseline"),
    ESTIMATE("estimate"),
    ;

    companion object {
        fun fromKey(key: String?): StartChoice? = entries.firstOrNull { it.key == key }
    }
}

data class OnboardingPrefs(
    val loaded: Boolean = false,
    val done: Boolean = false,
    /** The saved step, or null when the flow has not been opened yet. */
    val stepKey: String? = null,
    /** True while the welcome is being run again from Help, so the locale defaults are not rewritten. */
    val replay: Boolean = false,
    val unitsSeeded: Boolean = false,
    val startChoice: StartChoice? = null,
    val pending: PendingDestination? = null,
    /** The Today tour is due: set when the flow finishes or "Replay the tour" is tapped. */
    val tourPending: Boolean = false,
    val tipsSeen: Set<TipKey> = emptySet(),
) {
    val step: OnboardingStep get() = OnboardingFlow.resume(stepKey)
}

@Singleton
class OnboardingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val foodLogDao: FoodLogDao,
    private val bodyDao: BodyMetricDao,
    private val workoutDao: WorkoutDao,
) {
    private object Keys {
        val done = booleanPreferencesKey("onboarding_done")
        val step = stringPreferencesKey("onboarding_step")
        val replay = booleanPreferencesKey("onboarding_replay")
        val unitsSeeded = booleanPreferencesKey("onboarding_units_seeded")
        val startChoice = stringPreferencesKey("onboarding_start_choice")
        val pending = stringPreferencesKey("onboarding_pending_destination")
        val tourPending = booleanPreferencesKey("tour_pending")
        val tipsSeen = stringPreferencesKey("tips_seen")
    }

    val prefs: Flow<OnboardingPrefs> = context.onboardingStore.data.map { p ->
        OnboardingPrefs(
            loaded = true,
            done = p[Keys.done] ?: false,
            stepKey = p[Keys.step],
            replay = p[Keys.replay] ?: false,
            unitsSeeded = p[Keys.unitsSeeded] ?: false,
            startChoice = StartChoice.fromKey(p[Keys.startChoice]),
            pending = PendingDestination.fromKey(p[Keys.pending]),
            tourPending = p[Keys.tourPending] ?: false,
            tipsSeen = TipsSeen.parse(p[Keys.tipsSeen]),
        )
    }.distinctUntilChanged()

    suspend fun current(): OnboardingPrefs = prefs.first()

    /**
     * Decides, once per launch, whether the welcome flow comes first. An install that already has
     * data is marked done here so it is never asked again. The tour is not started for it, but the
     * per-tab tips stay unseen so the person still gets them once.
     */
    suspend fun resolveGate(): GateDecision {
        val state = current()
        if (state.done) return GateDecision.MAIN
        val resuming = state.replay || state.stepKey != null
        val decision = OnboardingGate.decide(state.done, resuming, existingData())
        if (decision == GateDecision.AUTO_SKIP) {
            context.onboardingStore.edit {
                it[Keys.done] = true
                it[Keys.unitsSeeded] = true
            }
        }
        return decision
    }

    private suspend fun existingData() = ExistingData(
        foodLog = foodLogDao.hasAnyRow(),
        weighIn = bodyDao.hasAnyRow(),
        workoutSession = workoutDao.hasAnySession(),
        profileBasics = settings.settings.first().profile.hasBasics,
    )

    /** Writes the step the screen is on, which is also the resume point. */
    suspend fun setStep(step: OnboardingStep) = context.onboardingStore.edit { it[Keys.step] = step.key }.let { }

    /** On the very first run only: write kg/cm/ml or lb/in/fl oz from the phone's region. */
    suspend fun seedUnitsIfFirstRun(isoCountry: String?) {
        val state = current()
        if (!UnitDefaults.shouldSeed(state.replay, state.unitsSeeded)) return
        val units = UnitDefaults.forCountry(isoCountry)
        settings.setWeightUnit(units.weight)
        settings.setLengthUnit(units.length)
        settings.setVolumeUnit(units.volume)
        context.onboardingStore.edit { it[Keys.unitsSeeded] = true }
    }

    suspend fun setStartChoice(choice: StartChoice?) = context.onboardingStore.edit {
        if (choice == null) it.remove(Keys.startChoice) else it[Keys.startChoice] = choice.key
    }.let { }

    suspend fun setPending(destination: PendingDestination?) = context.onboardingStore.edit {
        if (destination == null) it.remove(Keys.pending) else it[Keys.pending] = destination.key
    }.let { }

    /**
     * Ends the flow. A first run (not a replay) then starts the Today tour. The pending
     * destination is kept for the tabs to open.
     */
    suspend fun finish() {
        val replay = current().replay
        context.onboardingStore.edit {
            it[Keys.done] = true
            it[Keys.replay] = false
            it[Keys.unitsSeeded] = true
            it.remove(Keys.step)
            it.remove(Keys.startChoice)
            it[Keys.tourPending] = !replay
        }
    }

    /** Help, "Show the welcome again": runs the flow from the top without touching any data. */
    suspend fun restart() = context.onboardingStore.edit {
        it[Keys.done] = false
        it[Keys.replay] = true
        it[Keys.step] = OnboardingStep.WELCOME.key
        it.remove(Keys.startChoice)
        it.remove(Keys.pending)
    }.let { }

    suspend fun setTourPending(pending: Boolean) = context.onboardingStore.edit { it[Keys.tourPending] = pending }.let { }

    /** The pending destination was opened: forget it so it opens once. */
    suspend fun clearPending() = setPending(null)

    suspend fun dismissTip(key: TipKey) = context.onboardingStore.edit {
        it[Keys.tipsSeen] = TipsSeen.encode(TipsSeen.parse(it[Keys.tipsSeen]) + key)
    }.let { }

    /** Help, "Show screen tips again". */
    suspend fun resetTips() = context.onboardingStore.edit { it.remove(Keys.tipsSeen) }.let { }
}
