package com.zeroindex.intake.data.backup.remote

import com.zeroindex.intake.domain.backup.RemotePaths
import java.io.IOException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * WebDAV over OkHttp (RFC 4918): MKCOL to create the folder, PUT to a
 * `.partial` name, MOVE into place, PROPFIND (depth 1) to list, DELETE to prune.
 * Works with Nextcloud, ownCloud, Synology, QNAP, Apache mod_dav and rclone serve.
 *
 * The password goes only in the Authorization header. Errors are reported by
 * status code and host, never by echoing the request.
 */
class WebDavStore(
    private val http: OkHttpClient,
    baseUrl: String,
    private val folder: String,
    private val username: String,
    private val password: String,
) : RemoteStore {

    private val base: HttpUrl = baseUrl.trim().trimEnd('/').toHttpUrl()
    private val auth = Credentials.basic(username, password, Charsets.UTF_8)

    private fun url(vararg extra: String): HttpUrl = base.newBuilder().apply {
        (RemotePaths.segments(folder) + extra).forEach { addPathSegment(it) }
    }.build()

    private fun dirUrl(segments: List<String>): HttpUrl = base.newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        addPathSegment("") // trailing slash: collections are addressed with one
    }.build()

    private suspend fun call(request: Request): Response = withContext(Dispatchers.IO) {
        try {
            http.newCall(request.newBuilder().header("Authorization", auth).build()).execute()
        } catch (e: IOException) {
            throw RemoteStoreException("Could not reach ${base.host}: ${e.javaClass.simpleName}.", e)
        }
    }

    private fun fail(what: String, code: Int): Nothing = throw RemoteStoreException(
        when (code) {
            401 -> "${base.host} refused the username or password."
            403 -> "${base.host} does not allow writing to that folder."
            404 -> "${base.host} has no such path. Check the server address."
            405 -> "${base.host} does not accept WebDAV at that address."
            507 -> "${base.host} is out of space."
            else -> "$what failed on ${base.host} (HTTP $code)."
        },
    )

    private suspend fun ensureFolder() {
        val parts = RemotePaths.segments(folder)
        for (i in 1..parts.size) {
            val req = Request.Builder().url(dirUrl(parts.take(i))).method("MKCOL", null).build()
            call(req).use { r ->
                // 201 created; 405 already exists; 301/302 some servers redirect to the slash form.
                if (r.code !in setOf(201, 405, 301, 302) && !r.isSuccessful) fail("Creating the folder", r.code)
            }
        }
    }

    override suspend fun test(): String {
        ensureFolder()
        val probe = ".intake-write-test"
        put(probe, "ok".toByteArray())
        remove(probe)
        return "Connected to ${base.host}. The folder exists and is writable."
    }

    override suspend fun put(name: String, bytes: ByteArray) {
        ensureFolder()
        val partial = "$name.partial"
        call(
            Request.Builder().url(url(partial))
                .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
                .build(),
        ).use { r -> if (!r.isSuccessful) fail("Uploading", r.code) }
        call(
            Request.Builder().url(url(partial)).method("MOVE", null)
                .header("Destination", url(name).toString())
                .header("Overwrite", "T")
                .build(),
        ).use { r -> if (!r.isSuccessful) fail("Finishing the upload", r.code) }
    }

    override suspend fun list(): List<String> {
        val body = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val folderUrl = dirUrl(RemotePaths.segments(folder))
        val xml = call(
            Request.Builder().url(folderUrl)
                .method("PROPFIND", body.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "1")
                .build(),
        ).use { r ->
            if (r.code == 404) return emptyList()
            if (r.code != 207 && !r.isSuccessful) fail("Listing the folder", r.code)
            r.body.string()
        }
        return fileNames(hrefs(xml))
    }

    override suspend fun remove(name: String) {
        call(Request.Builder().url(url(name)).delete().build()).use { r ->
            if (!r.isSuccessful && r.code != 404) fail("Removing an old backup", r.code)
        }
    }

    internal companion object {
        /** Every `<href>` in a multistatus body, whatever namespace prefix the server picked. */
        fun hrefs(xml: String): List<String> =
            Regex("<(?:[A-Za-z0-9_-]+:)?href>([^<]+)</(?:[A-Za-z0-9_-]+:)?href>", RegexOption.IGNORE_CASE)
                .findAll(xml).map { it.groupValues[1].trim() }.toList()

        /**
         * The file names among [hrefs]. Collections (the folder itself, any
         * subfolder) end in a slash and are skipped. Hrefs may be absolute
         * URLs or absolute paths, percent-encoded either way.
         */
        fun fileNames(hrefs: List<String>): List<String> = hrefs.mapNotNull { href ->
            val path = if (href.contains("://")) href.substringAfter("://").substringAfter('/', "") else href
            if (path.endsWith("/")) return@mapNotNull null
            URLDecoder.decode(path.substringAfterLast('/').replace("+", "%2B"), "UTF-8").takeIf { it.isNotEmpty() }
        }
    }
}
