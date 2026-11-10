package com.zeroindex.intake.domain.ai

/** How big one request of a feature is, for pricing before there is any measured data. */
data class TokenProfile(
    val inputTokens: Int,
    val outputTokens: Int,
    val images: Int = 0,
    /** Server-side web searches per use, each billed separately by the provider. */
    val searches: Int = 0,
)

/**
 * Every place the app may call a model. One row per feature, so the usage meter and the cost estimate can
 * say "what did Recipe Hunter cost" rather than only "what did this model cost", and so the audit in
 * `docs/AI-COSTS.md` has a fixed list to be checked against.
 *
 * [task] is the model slot the feature runs on (the picker chooses one model per task). Two features share a
 * slot with another: the coach reword rides the daily-insight model and "Try with AI" on a recipe page rides
 * the Recipe Hunter model, so neither needs a picker of its own.
 *
 * [optInSwitch] features do nothing until the person turns them on, so the estimate counts them as zero
 * while the switch is off. [schedulable] features may also be called with [AiTrigger.OPT_IN_SCHEDULE].
 */
enum class AiFeature(
    val label: String,
    val task: AiTask?,
    val defaultUsesPerDay: Double,
    val schedulable: Boolean = false,
    val optInSwitch: Boolean = false,
    private val profileOverride: TokenProfile? = null,
) {
    PHOTO_ESTIMATE("Photo estimates", AiTask.VISION_TRACKER, 2.0),
    RECIPE_HUNTER("Recipe Hunter", AiTask.RECIPE_HUNTER, 0.3),
    PLAN_B("Plan B finder", AiTask.PLAN_B, 0.2),
    MENU_IMPORT("Menu import", AiTask.MENU_INGESTION, 0.1, schedulable = true),
    DAILY_INSIGHT("Daily insight", AiTask.DAILY_INSIGHT, 1.0, schedulable = true, optInSwitch = true),
    COACH_REWORD(
        "Coach reword", AiTask.DAILY_INSIGHT, 1.0 / 7.0, schedulable = true, optInSwitch = true,
        // A handful of one-line suggestions and their reasons, returned in the same shape.
        profileOverride = TokenProfile(inputTokens = 700, outputTokens = 500),
    ),
    RECIPE_TRY_AI(
        "Recipe: Try with AI", AiTask.RECIPE_HUNTER, 0.05,
        // Up to 20,000 characters of page text (about 6,700 tokens) and one recipe back; no web search.
        profileOverride = TokenProfile(inputTokens = 6_700, outputTokens = 1_500),
    ),

    /** The in-app benchmark: the person's own spend, counted in the meter but not in the estimate. */
    BENCHMARK("Benchmark", null, 0.0),
    ;

    /** Whether this feature is part of the daily and monthly estimate. */
    val inEstimate: Boolean get() = this != BENCHMARK

    /** What one use of this feature sends and receives, by default. */
    val profile: TokenProfile
        get() = profileOverride ?: task?.let {
            TokenProfile(it.typicalInputTokens, it.typicalOutputTokens, it.typicalImages, it.typicalSearches)
        } ?: TokenProfile(0, 0)

    /** True when the task's benchmark requests look like this feature's, so its measured cost applies. */
    val benchmarkRepresentative: Boolean get() = task != null && profileOverride == null

    /**
     * How long one request may take. Photos and the web-search features (Recipe Hunter, Plan B, menu import)
     * are slow, so they wait up to two minutes; text-only features fail after 45 seconds rather than leave the
     * person watching a spinner. The benchmark sits between, so one hung model cannot eat the run.
     */
    val timeout: AiTimeout
        get() = when (this) {
            PHOTO_ESTIMATE, RECIPE_HUNTER, PLAN_B, MENU_IMPORT -> AiTimeout.LONG
            RECIPE_TRY_AI, BENCHMARK -> AiTimeout.MEDIUM
            DAILY_INSIGHT, COACH_REWORD -> AiTimeout.TEXT
        }

    val allowedTriggers: Set<AiTrigger>
        get() = if (schedulable) setOf(AiTrigger.USER_ACTION, AiTrigger.OPT_IN_SCHEDULE) else setOf(AiTrigger.USER_ACTION)

    companion object {
        fun parse(name: String?): AiFeature? = entries.firstOrNull { it.name == name }
    }
}
