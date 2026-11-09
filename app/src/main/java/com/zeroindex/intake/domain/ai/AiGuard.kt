package com.zeroindex.intake.domain.ai

import com.zeroindex.intake.domain.ai.cost.BudgetStatus

/** Why the gateway refused to send a request. Nothing reaches the network when one of these applies. */
enum class GuardRefusal {
    /** The caller named no [AiTrigger]. A programming error, loud in debug builds. */
    NO_TRIGGER,

    /** The trigger is one this feature may not use, for example a schedule for photo estimates. */
    TRIGGER_NOT_ALLOWED,

    /** "Use AI features" is off. */
    SWITCH_OFF,

    /** A scheduled call whose own opt-in switch is off. */
    NOT_OPTED_IN,

    NO_KEY,

    /** This month's spend has reached the cap. */
    BUDGET,
}

/**
 * The single rule for whether a model call may go out (SPEC-v3 "AI policy"): it must say why it is
 * happening, be a reason that feature is allowed, be switched on, have a key, and fit in the month's budget.
 * Pure, so the rule is tested without a network or a phone.
 */
object AiGuard {
    fun check(
        feature: AiFeature,
        trigger: AiTrigger?,
        enabled: Boolean,
        hasKey: Boolean,
        optedInSchedules: Set<AiFeature>,
        budget: BudgetStatus,
    ): GuardRefusal? = when {
        trigger == null -> GuardRefusal.NO_TRIGGER
        trigger !in feature.allowedTriggers -> GuardRefusal.TRIGGER_NOT_ALLOWED
        !enabled -> GuardRefusal.SWITCH_OFF
        trigger == AiTrigger.OPT_IN_SCHEDULE && feature !in optedInSchedules -> GuardRefusal.NOT_OPTED_IN
        !hasKey -> GuardRefusal.NO_KEY
        budget.blocked -> GuardRefusal.BUDGET
        else -> null
    }
}
