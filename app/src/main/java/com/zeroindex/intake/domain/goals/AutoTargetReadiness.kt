package com.zeroindex.intake.domain.goals

/**
 * What the app still needs before it can work targets out from the person's data. The You card, the plan screen and
 * Settings all ask here, so none of them claims "worked out from your data" while something is missing (QA Q4-12).
 */
object AutoTargetReadiness {

    /** The missing pieces, in plain words; empty when targets can be worked out. */
    fun missing(profile: UserProfile, hasWeighIn: Boolean): List<String> = buildList {
        if (profile.sex == null) add("your sex")
        if (profile.birthYear == null) add("your birth year")
        if (profile.heightCm == null) add("your height")
        if (!hasWeighIn) add("a weigh-in")
    }

    /** "Add your sex, your height and a weigh-in first." or null when nothing is missing. */
    fun sentence(profile: UserProfile, hasWeighIn: Boolean): String? {
        val m = missing(profile, hasWeighIn)
        if (m.isEmpty()) return null
        val list = if (m.size == 1) m[0] else m.dropLast(1).joinToString(", ") + " and " + m.last()
        return "Add $list first."
    }
}
