package com.zeroindex.intake.domain.ai

/**
 * The AI workflows from amendment §1, with what each needs from a model and a
 * typical request size, so the picker can filter to capable models and quote
 * a price per use in money rather than per-million-token rates.
 *
 * Token figures are deliberately on the high side of what these prompts use:
 * a quoted "≈ $0.0004 a photo" that turns out to be $0.0003 is fine; the
 * other direction is not.
 */
enum class AiTask(
    val label: String,
    val perUse: String,
    val needsImages: Boolean = false,
    val needsTools: Boolean = false,
    val minContext: Long = 32_000,
    val typicalInputTokens: Int,
    val typicalOutputTokens: Int,
    val typicalImages: Int = 0,
    /** Server-side web searches per use, each billed separately. */
    val typicalSearches: Int = 0,
    /**
     * The amendment's suggested models, as live OpenRouter ids. An entry ending
     * in `*` is a family resolved to its newest member at pick time, which is
     * how "Gemini Flash (Latest)" stays latest without an app update.
     */
    val shortlist: List<String>,
) {
    VISION_TRACKER(
        label = "Photo estimates",
        perUse = "photo",
        needsImages = true,
        typicalInputTokens = 1_800,
        typicalOutputTokens = 600,
        typicalImages = 1,
        shortlist = listOf(
            "google/gemini-*-flash",
            "openai/gpt-6-luna",
            "openai/gpt-5.6-luna",
            "deepseek/deepseek-v4.1-flash",
        ),
    ),
    RECIPE_HUNTER(
        label = "Recipe Hunter",
        perUse = "search",
        needsTools = true,
        minContext = 100_000,
        typicalInputTokens = 18_000,
        typicalOutputTokens = 3_000,
        typicalSearches = 3,
        shortlist = listOf(
            "meta-llama/llama-3.3-70b-instruct",
            "google/gemini-*-flash",
            // The amendment names Claude 3.5 Haiku, which OpenRouter has
            // retired; Haiku 5.5 is its replacement and is cheaper than 3.5 was.
            "anthropic/claude-haiku-5.5",
        ),
    ),
    PLAN_B(
        label = "Plan B finder",
        perUse = "search",
        needsTools = true,
        minContext = 100_000,
        typicalInputTokens = 14_000,
        typicalOutputTokens = 2_000,
        typicalSearches = 3,
        shortlist = listOf(
            "meta-llama/llama-3.3-70b-instruct",
            "google/gemini-*-flash",
            "anthropic/claude-haiku-5.5",
        ),
    ),
    MENU_INGESTION(
        label = "Menu import",
        perUse = "menu",
        minContext = 200_000,
        typicalInputTokens = 40_000,
        typicalOutputTokens = 8_000,
        shortlist = listOf(
            "google/gemini-*-flash",
            "meta-llama/llama-3.3-70b-instruct",
            "anthropic/claude-haiku-5.5",
        ),
    ),
    DAILY_INSIGHT(
        label = "Daily insight",
        perUse = "day",
        typicalInputTokens = 4_000,
        typicalOutputTokens = 400,
        shortlist = listOf("anthropic/claude-haiku-5.5", "openai/gpt-6-luna"),
    ),
    ;

    fun canRun(model: ModelInfo): Boolean =
        model.isInteractive &&
            model.hasKnownPrice &&
            model.outputsText &&
            model.canReturnJson &&
            model.contextLength >= minContext &&
            (!needsImages || model.acceptsImages) &&
            (!needsTools || model.tools)

    companion object {
        /** Exa search in its default mode, the engine OpenRouter falls back to. */
        const val WEB_SEARCH_USD = 0.007
    }
}
