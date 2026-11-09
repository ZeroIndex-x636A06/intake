package com.zeroindex.intake.domain.food

import java.security.MessageDigest

/**
 * The rules for product pictures, kept pure so they are tested. A picture is only ever a thumbnail from Open Food
 * Facts' own image host, over https. With Open Food Facts search switched off nothing is loaded at all (the switch
 * means "do not talk to Open Food Facts"); offline, only what is already on the phone is shown.
 */
object ProductImagePolicy {

    enum class Decision { SKIP, CACHE_ONLY, FETCH_OK }

    fun decide(url: String?, offEnabled: Boolean, online: Boolean): Decision = when {
        url == null || !isAllowedUrl(url) -> Decision.SKIP
        !offEnabled -> Decision.SKIP
        !online -> Decision.CACHE_ONLY
        else -> Decision.FETCH_OK
    }

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

    /** Largest picture accepted from the network, so a wrong or hostile response cannot fill memory. */
    const val MAX_BYTES = 400 * 1024
}
