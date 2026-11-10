package com.zeroindex.intake.domain.ai

/**
 * Why an AI call did not give a usable answer. Logged on the usage row so Settings -> AI can show the reason a
 * feature keeps failing, in the user's words rather than a status code.
 */
enum class AiFailureKind(val label: String, val hint: String) {
    TIMEOUT("Timed out", "The model took too long. Try again, or pick a faster model."),
    INVALID_OUTPUT("Unreadable answer", "The model's reply did not match the format asked for. Try another model."),
    REFUSED("Provider refused", "The provider declined the request. Try another model."),
    RATE_LIMITED("Rate limited", "Too many requests. Wait a minute, or avoid free models."),
    NETWORK("No connection", "OpenRouter could not be reached."),
    AUTH("Key rejected", "OpenRouter did not accept the key. Check it in Settings -> AI."),
    CREDITS("Out of credit", "The OpenRouter account has no credit left."),
    PRIVACY("Privacy setting", "No provider for this model meets your privacy setting."),
    SERVER("Provider error", "The provider returned an error. Try again shortly."),
    CANCELLED("Cancelled", "You cancelled it."),
    ;

    companion object {
        fun parse(name: String?): AiFailureKind? = entries.firstOrNull { it.name == name }
    }
}

/** How patient a request should be. Vision and web-search features are slow; text-only ones should fail fast. */
enum class AiTimeout(val readSeconds: Long, val callSeconds: Long) {
    TEXT(45, 60),
    MEDIUM(90, 105),
    LONG(120, 150),
}
