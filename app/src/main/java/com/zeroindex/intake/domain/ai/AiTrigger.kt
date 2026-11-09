package com.zeroindex.intake.domain.ai

/**
 * Why a model call is happening. Every call through the AI gateway names one, and the gateway refuses a
 * call that names none (SPEC-v3 "AI policy": AI runs only on an explicit user action, or on a schedule the
 * user turned on, which is off by default).
 */
enum class AiTrigger {
    /** The person tapped something whose whole point is the AI result. */
    USER_ACTION,

    /** A schedule the person switched on in Settings → AI (or the coach screen). Never on by default. */
    OPT_IN_SCHEDULE,
}
