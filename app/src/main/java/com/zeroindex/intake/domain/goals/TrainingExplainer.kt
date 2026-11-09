package com.zeroindex.intake.domain.goals

import com.zeroindex.intake.domain.training.WorkoutSchedule
import com.zeroindex.intake.domain.training.generator.Equipment
import com.zeroindex.intake.domain.training.generator.GeneratedProgram
import com.zeroindex.intake.domain.training.generator.GeneratorInputs
import com.zeroindex.intake.domain.training.generator.Muscles
import com.zeroindex.intake.domain.training.generator.VolumeGuide

/** "How this plan was built": which answers shaped the training plan and which did not, in plain words. */
data class HowBuilt(
    val used: List<String>,
    val notUsed: List<String>,
) {
    /** Everything as one block, for the preview and for tests. */
    val text: String
        get() = buildString {
            appendLine("Used:")
            used.forEach { appendLine("- $it") }
            appendLine("Not used:")
            notUsed.forEach { appendLine("- $it") }
        }.trimEnd()
}

object TrainingExplainer {

    /**
     * @param profile the person's profile when the plan came from it; null from the stand-alone wizard
     */
    fun howBuilt(
        inputs: GeneratorInputs,
        program: GeneratedProgram,
        profile: UserProfile? = null,
    ): HowBuilt {
        val withWeight = program.days.flatMap { it.exercises }.filter { it.startWeightKg != null }
        val startWeights = withWeight.size
        val fromHistory = withWeight.count { it.startNote?.contains("Based on your") == true }
        val used = mutableListOf<String>()
        val notUsed = mutableListOf<String>()

        val goalNote = if (profile != null && profile.trainingGoal == null) {
            " (matched to your ${profile.phase.phrase} phase, since you did not pick one)"
        } else ""
        used += "Training goal: ${inputs.goal.label}$goalNote. It sets the rep ranges, how hard each set is and how weight progresses."

        val r = VolumeGuide.range(inputs.experience)
        used += "Experience: ${inputs.experience.label.lowercase()}. About ${r.first} to ${r.last} hard sets per muscle per week."

        val chosenDays = profile?.trainingWeekdays?.filter { it in 1..7 }?.sorted().orEmpty()
        used += if (chosenDays.isNotEmpty()) {
            "Training days: " + chosenDays.joinToString(", ") { WorkoutSchedule.weekdayName(it, short = true) } +
                ". That is ${inputs.daysPerWeek} days, which picks the split, and each day of the plan is set to one of those weekdays."
        } else {
            "Days a week: ${inputs.daysPerWeek}. It picks the split." +
                (if (program.days.any { it.weekday != null }) " Days are spread across the week (" +
                    program.days.mapNotNull { it.weekday }.joinToString(", ") { WorkoutSchedule.weekdayName(it, short = true) } + ")." else "")
        }

        val trimmed = program.warnings.any { "sessions only fit" in it }
        used += "Session length: ${inputs.sessionMinutes} minutes. " +
            if (trimmed) "Some days were trimmed to fit." else "Every day fits."

        val kinds = Equipment.ALL.filter { it in inputs.equipment }
        used += "Equipment: ${kinds.joinToString(", ").ifBlank { "none" }}. Only lifts you can do with it are used."

        if (inputs.focusMuscles.isNotEmpty()) {
            used += "Focus muscles: ${inputs.focusMuscles.sortedBy { Muscles.PROGRAMMED.indexOf(it) }.joinToString(", ")}. About 25% more sets, trained early in the session."
        } else {
            notUsed += "Focus muscles: none chosen, so volume is even. Pick some in Profile & goals to give them more."
        }
        if (inputs.avoid.isNotEmpty()) {
            used += "Go easy on: ${inputs.avoid.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }}. Lifts that load those areas are left out."
        } else {
            notUsed += "Areas to go easy on: none chosen, so no lift is excluded."
        }
        if (inputs.hiddenExerciseIds.isNotEmpty()) {
            used += "Hidden exercises: ${inputs.hiddenExerciseIds.size} you hid are never picked."
        }
        used += when {
            startWeights == 0 ->
                "Starting weights: none were set (no weigh-in or history to go on), so you choose your first weight."
            fromHistory > 0 ->
                "Starting weights: $startWeights lifts start at about 85 to 90% of what you have lifted before ($fromHistory from your own history). Start light; adjusts after your first session."
            else ->
                "Starting weights: $startWeights main lifts have a careful guess from your body weight and experience. Start light; adjusts after your first session."
        }

        if (profile != null) {
            notUsed += "Your build and age do not change exercise choice; they affect calories."
            notUsed += "Sex, height and body fat affect calories and protein. Sex and body weight only shape the first-weight guess."
            notUsed += "Day-to-day activity affects calories only."
            if (profile.trainingGoal != null) {
                notUsed += "Phase (${profile.phase.label.lowercase()}) and pace set your calories. They did not pick the training goal, because you chose one."
            } else {
                notUsed += "Pace sets your calories only."
            }
        } else {
            notUsed += "Build, age, sex and body fat do not change exercise choice; they affect calories."
        }
        return HowBuilt(used, notUsed)
    }
}
