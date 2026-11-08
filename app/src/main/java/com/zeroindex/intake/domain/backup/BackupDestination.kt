package com.zeroindex.intake.domain.backup

import java.net.InetAddress
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

/** Amendment §2: where a nightly backup goes. Secrets are never part of this. */
enum class BackupProtocol(val label: String) { WEBDAV("WebDAV"), SFTP("SFTP"), SMB("SMB") }

enum class BackupSchedule { NIGHTLY, MANUAL }

/**
 * The non-secret half of a backup destination, stored as JSON in settings.
 * The password or private key lives in the encrypted credential store and is
 * looked up by protocol, so exporting settings never carries it.
 *
 * - WebDAV: [host] is the full base URL (`https://nas.example.net/remote.php/dav/files/me`).
 * - SFTP: [host] is a hostname, [port] defaults to 22; [hostKeySha256] is pinned on first use.
 * - SMB: [host] is a hostname, [share] the share name; [domain] is optional.
 */
@Serializable
data class BackupDestination(
    val protocol: BackupProtocol = BackupProtocol.WEBDAV,
    val host: String = "",
    val port: Int? = null,
    val path: String = "/intake/backups",
    val username: String = "",
    val share: String = "",
    val domain: String = "",
    val hostKeySha256: String? = null,
) {
    val isComplete: Boolean get() = host.isNotBlank() && username.isNotBlank() &&
        (protocol != BackupProtocol.SMB || share.isNotBlank())

    /** What a run records as its destination: never a password, never a full URL with credentials. */
    fun describe(): String = when (protocol) {
        BackupProtocol.WEBDAV -> "WebDAV · ${runCatching { URI(host).host }.getOrNull() ?: host}${RemotePaths.normalize(path)}"
        BackupProtocol.SFTP -> "SFTP · $host${RemotePaths.normalize(path)}"
        BackupProtocol.SMB -> "SMB · $host/$share${RemotePaths.normalize(path)}"
    }
}

object RemotePaths {
    /** "/a//b/" → "/a/b"; "" → "". Backslashes become slashes so SMB paths can be typed either way. */
    fun normalize(path: String): String {
        val parts = segments(path)
        return if (parts.isEmpty()) "" else parts.joinToString("/", prefix = "/")
    }

    fun segments(path: String): List<String> =
        path.replace('\\', '/').split('/').map { it.trim() }.filter { it.isNotEmpty() && it != "." }
            .also { parts -> require(parts.none { it == ".." }) { "A backup path cannot contain '..'" } }

    fun join(dir: String, name: String): String = normalize(dir) + "/" + name
}

/** File names a backup run writes, and which old ones a run may prune. */
object BackupNames {
    const val PREFIX = "intake-backup-"
    const val SUFFIX = ".intake"
    /** Thirty nights: a month of history without filling a small NAS. */
    const val KEEP = 30

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /** UTC in the name so a phone changing time zone never reorders backups. */
    fun forTime(millis: Long): String = PREFIX + stamp.format(Instant.ofEpochMilli(millis)) + SUFFIX

    fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && name.endsWith(SUFFIX) &&
            name.removePrefix(PREFIX).removeSuffix(SUFFIX).matches(Regex("\\d{8}-\\d{6}"))

    /**
     * Oldest first, everything beyond the newest [keep] of *our* files.
     * Anything else in the folder is never touched.
     */
    fun toPrune(names: Collection<String>, keep: Int = KEEP): List<String> {
        require(keep >= 1) { "keep at least one backup" }
        val ours = names.filter(::isOurs).sortedDescending()
        return ours.drop(keep).sorted()
    }
}

/**
 * Plain-HTTP WebDAV sends the password and the (already encrypted) file in
 * the clear, so it is allowed only to a host that is plainly on the local
 * network: a private or loopback IP literal, or a `.local` / `.lan` / `.home`
 * / `.internal` name. Everything else must be HTTPS.
 */
object WebDavUrlPolicy {
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
