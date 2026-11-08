package com.zeroindex.intake.domain.ai

/**
 * Turns a fetched menu page into plain text a model can read cheaply, and
 * decides whether what came back is a menu at all.
 *
 * Deliberately simple: drop scripts, styles and tags, decode the common
 * entities, collapse whitespace. A model reads the result; it does not need
 * a faithful DOM, only the words.
 */
object MenuText {

    /** Below this, a page is a shell rendered by JavaScript, or a block page. */
    const val MIN_USEFUL_CHARS = 400
    const val MAX_CHARS = 120_000

    private val dropBlocks = Regex("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>")
    private val breaks = Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>")
    private val tags = Regex("(?s)<[^>]+>")
    private val spaces = Regex("[ \\t\\u00A0]+")
    private val blankLines = Regex("\\n\\s*\\n+")

    fun fromHtml(html: String): String {
        val text = html
            .replace(dropBlocks, " ")
            .replace(breaks, "\n")
            .replace(tags, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(spaces, " ")
            .lines().joinToString("\n") { it.trim() }
            .replace(blankLines, "\n")
            .trim()
        return text.take(MAX_CHARS)
    }

    /**
     * Signs that a site refused an automated fetch rather than serving its
     * menu: a bot-check interstitial, or almost no text at all.
     */
    fun looksBlocked(text: String): Boolean {
        val lower = text.lowercase()
        val challenge = listOf(
            "verify you are human", "checking your browser", "enable javascript", "access denied",
            "are you a robot", "captcha", "request unsuccessful", "attention required",
        ).any { it in lower }
        return challenge || text.length < MIN_USEFUL_CHARS
    }

    /**
     * Sites whose terms forbid automated access. Their pages are fetched only
     * as an ordinary request would be; if that is refused, the import says
     * so and offers a PDF or pasted text instead of trying another route in.
     */
    private val noScrapeHosts = listOf("doordash.com", "ubereats.com", "grubhub.com")

    fun forbidsScraping(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        return noScrapeHosts.any { host == it || host.endsWith(".$it") }
    }
}
