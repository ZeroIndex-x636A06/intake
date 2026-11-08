package com.zeroindex.intake.domain.ai

/**
 * A model as the picker sees it, independent of OpenRouter's wire format.
 * Prices are USD per million tokens, the unit people actually compare.
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val contextLength: Long,
    val promptPerMillion: Double,
    val completionPerMillion: Double,
    /** Some vision models charge per image on top of tokens. */
    val perImage: Double,
    val perRequest: Double,
    val acceptsImages: Boolean,
    val acceptsFiles: Boolean,
    val outputsText: Boolean,
    val structuredOutputs: Boolean,
    val tools: Boolean,
    /** Unix seconds; used to resolve "latest" in a shortlist. */
    val createdAt: Long = 0,
) {
    val isFree: Boolean get() = id.endsWith(":free") || (promptPerMillion == 0.0 && completionPerMillion == 0.0)

    /**
     * Async batch variants and floating "~latest" aliases are not usable for
     * an interactive request whose model must stay put once chosen.
     */
    val isInteractive: Boolean get() = !id.endsWith(":batch") && !id.startsWith("~")

    /**
     * Free endpoints are commonly paid for with your data, and some listings
     * say so outright ("prompts and outputs may be retained and used"). The
     * picker warns on either. With the "no data collection" privacy setting
     * on, OpenRouter will refuse to route to such providers anyway; this
     * makes the trade visible before it happens rather than as an error.
     */
    val mayRetainPrompts: Boolean
        get() = isFree || RETENTION_HINTS.any { description.contains(it, ignoreCase = true) }

    private companion object {
        val RETENTION_HINTS = listOf("may be retained", "may be logged", "used for training", "may be used")
    }
}
