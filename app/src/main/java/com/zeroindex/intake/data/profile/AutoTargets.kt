package com.zeroindex.intake.data.profile

import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.goals.AutoTargetReadiness
import com.zeroindex.intake.domain.goals.GoalBuilder
import com.zeroindex.intake.domain.nutrition.MacroTarget
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** What one pass of "work my targets out from my data" did. */
sealed interface AutoTargetOutcome {
    /** A new dated target was written. */
    data class Updated(val target: MacroTarget, val previousKcal: Int?) : AutoTargetOutcome

    /** The worked-out numbers are already the active target. */
    data class Unchanged(val target: MacroTarget) : AutoTargetOutcome

    /** Targets cannot be worked out yet; [reason] says what is missing. */
    data class Missing(val reason: String) : AutoTargetOutcome

    /** The switch is off, so nothing was touched. */
    data object Off : AutoTargetOutcome
}

/**
 * The one place the automatic targets are computed and stored. It runs the same goal builder the plan screen shows,
 * with the person's Settings protein and fat, and writes the result as the dated target that You, Today and the plan
 * screen all read. Nightly, after a weigh-in, on "Recalculate now" and on switching the setting on all come here,
 * so the numbers cannot disagree (QA Q4-6, Q4-7, Q4-12). No network, no AI.
 */
@Singleton
class AutoTargets @Inject constructor(
    private val profiles: ProfileRepository,
    private val targets: TargetRepository,
    private val settings: SettingsRepository,
) {
    suspend fun refresh(): AutoTargetOutcome {
        val app = settings.settings.first()
        if (!app.autoCalculateTargets) return AutoTargetOutcome.Off
        val inputs = profiles.goalInputs()
        if (inputs == null) {
            val hasWeighIn = profiles.observeLatestWeigh().first() != null
            return AutoTargetOutcome.Missing(
                AutoTargetReadiness.sentence(app.profile, hasWeighIn) ?: "There is not enough data yet.",
            )
        }
        val plan = GoalBuilder.build(inputs)
        val m = plan.macros
        val target = MacroTarget(kcal = plan.calories.kcal, proteinG = m.proteinG, carbsG = m.carbsG, fatG = m.fatG)
        val previous = targets.latestEntity()
        if (previous != null && previous.kcal == target.kcal && previous.proteinG == target.proteinG &&
            previous.carbsG == target.carbsG && previous.fatG == target.fatG
        ) {
            return AutoTargetOutcome.Unchanged(target)
        }
        targets.setTarget(target, isAutoCalculated = true)
        return AutoTargetOutcome.Updated(target, previous?.kcal)
    }

    companion object {
        /** The confirmation or the explanation the person sees after a recalculation. */
        fun message(outcome: AutoTargetOutcome): String = when (outcome) {
            is AutoTargetOutcome.Updated ->
                "Targets updated: ${outcome.target.kcal} kcal, ${outcome.target.proteinG} g protein, " +
                    "${outcome.target.carbsG} g carbs, ${outcome.target.fatG} g fat."
            is AutoTargetOutcome.Unchanged ->
                "Targets are already up to date: ${outcome.target.kcal} kcal, ${outcome.target.proteinG} g protein."
            is AutoTargetOutcome.Missing -> "Cannot work your targets out yet. ${outcome.reason}"
            AutoTargetOutcome.Off -> "Working targets out from your data is off. Turn it on first."
        }
    }
}
