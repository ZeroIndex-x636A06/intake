package com.zeroindex.intake.domain.ai

/** The optional "Anything we can't see?" note sent with a photo. */
object VisionContext {
    const val MAX_CHARS = 300

    const val PROMPT = "Anything we can't see? (e.g. cooked in butter, large portion, sauce on the side)"

    /** Trimmed, on one line, capped; null when there is nothing left to send. */
    fun clean(text: String?): String? =
        text?.filterNot { it.isISOControl() && it != ' ' }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(MAX_CHARS)
            ?.takeIf { it.isNotEmpty() }
}
