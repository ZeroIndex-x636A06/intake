package com.zeroindex.intake.domain.backup

import java.net.InetAddress
import java.net.URI

/**
 * Plain http to a sync server sends the device token in the clear, so it is allowed only to a host that is plainly on the local
 * network: a private or loopback IP literal, or a `.local` / `.lan` / `.home`
 * / `.internal` name. Everything else must be HTTPS.
 */
object SyncUrlPolicy {
    sealed interface Verdict {
        data object Ok : Verdict
        data object LocalPlainHttp : Verdict
        data class Rejected(val reason: String) : Verdict
    }

    private val localSuffixes = listOf(".local", ".lan", ".home", ".internal", ".home.arpa")

    fun check(url: String): Verdict {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return Verdict.Rejected("That is not a valid address.")
        val host = uri.host?.lowercase() ?: return Verdict.Rejected("The address needs a host name.")
        if (uri.userInfo != null) return Verdict.Rejected("Put the username in its own field, not in the address.")
        return when (uri.scheme?.lowercase()) {
            "https" -> Verdict.Ok
            "http" -> if (isLocal(host)) Verdict.LocalPlainHttp else
                Verdict.Rejected("Use https:// for a server outside your home network.")
            else -> Verdict.Rejected("The address must start with https://")
        }
    }

    internal fun isLocal(host: String): Boolean {
        if (localSuffixes.any { host.endsWith(it) } || host == "localhost") return true
        val literal = host.trim('[', ']')
        // Only literals: resolving a name here would hit DNS, and a name that
        // resolves privately today can resolve publicly tomorrow.
        if (!literal.matches(Regex("[0-9.]+")) && !literal.contains(':')) return false
        val addr = runCatching { InetAddress.getByName(literal) }.getOrNull() ?: return false
        return addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
            (addr.address.size == 16 && (addr.address[0].toInt() and 0xfe) == 0xfc)
    }
}
