package com.zeroindex.intake.domain.food

import java.security.MessageDigest

/**
 * The rules for pictures, kept pure so they are tested.
 *
 * A product picture ([Source.OPEN_FOOD_FACTS]) is only ever a thumbnail from Open Food Facts' own image host, over
 * https. With Open Food Facts search switched off nothing is loaded at all (the switch means "do not talk to Open
 * Food Facts"); offline, only what is already on the phone is shown.
 *
 * A recipe photo ([Source.RECIPE_PAGE]) is the image address of a page the user imported. It may live on any public
 * https host (recipe sites keep photos on their own CDNs), is never fetched from an address that points inside the
 * phone's network, has a larger size cap, and does not depend on the Open Food Facts switch: it talks to the recipe's
 * own site, not to Open Food Facts. Both share the same disk cache and offline behaviour.
 */
object ProductImagePolicy {

    enum class Decision { SKIP, CACHE_ONLY, FETCH_OK }

    enum class Source { OPEN_FOOD_FACTS, RECIPE_PAGE }

    fun decide(url: String?, offEnabled: Boolean, online: Boolean, source: Source = Source.OPEN_FOOD_FACTS): Decision = when {
        url == null || !isAllowedUrl(url, source) -> Decision.SKIP
        source == Source.OPEN_FOOD_FACTS && !offEnabled -> Decision.SKIP
        !online -> Decision.CACHE_ONLY
        else -> Decision.FETCH_OK
    }

    fun isAllowedUrl(url: String, source: Source): Boolean = when (source) {
        Source.OPEN_FOOD_FACTS -> isAllowedUrl(url)
        Source.RECIPE_PAGE -> isAllowedRecipeUrl(url)
    }

    /**
     * https, a public host name, no credentials in the address, and the default port. IP addresses, single-label
     * names and local suffixes are refused, so an imported page cannot make the phone fetch from its own network.
     */
    fun isAllowedRecipeUrl(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://") || u.any { it.isWhitespace() }) return false
        val uri = runCatching { java.net.URI(u) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        if (uri.userInfo != null || (uri.port != -1 && uri.port != 443)) return false
        if (!host.contains('.')) return false
        if (host.all { it.isDigit() || it == '.' }) return false // an IPv4 address
        if (host.contains(':') || host.startsWith("[")) return false // an IPv6 address
        val local = listOf(".local", ".lan", ".localhost", ".internal", ".home", ".corp", ".intranet")
        return local.none { host.endsWith(it) } && host != "localhost"
    }

    /** The size cap for [source], so a wrong or hostile response cannot fill memory. */
    fun maxBytes(source: Source): Int = if (source == Source.RECIPE_PAGE) RECIPE_MAX_BYTES else MAX_BYTES

    fun isAllowedUrl(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://")) return false
        val host = u.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase()
        return host == "openfoodfacts.org" || host.endsWith(".openfoodfacts.org")
    }

    /** The power-of-two `inSampleSize` that keeps the decoded bitmap at least [targetPx] on its shorter side. */
    fun sampleSize(width: Int, height: Int, targetPx: Int): Int {
        if (width <= 0 || height <= 0 || targetPx <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) sample *= 2
        return sample
    }

    /** A file name for the disk cache: the address itself is never used as a path. */
    fun cacheName(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Largest Open Food Facts picture accepted from the network. */
    const val MAX_BYTES = 400 * 1024

    /** Largest recipe photo accepted: blog photos are bigger than product thumbnails, and are decoded down to the screen. */
    const val RECIPE_MAX_BYTES = 3 * 1024 * 1024
}
