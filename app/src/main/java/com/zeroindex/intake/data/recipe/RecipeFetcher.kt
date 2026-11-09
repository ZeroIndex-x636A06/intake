package com.zeroindex.intake.data.recipe

import com.zeroindex.intake.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface FetchResult {
    data class Page(val finalUrl: String, val html: String) : FetchResult
    data class Failed(val message: String) : FetchResult
}

/** Checks a pasted address before anything is sent. Pure, so it is tested without a network. */
object RecipeUrl {
    sealed interface Checked {
        data class Ok(val url: HttpUrl) : Checked
        data class Bad(val message: String) : Checked
    }

    /**
     * HTTPS only, except plainly local hosts (localhost, 127.x, 10.x, 192.168.x, 172.16-31.x, *.local,
     * *.lan, ::1), which may use http. "example.com/recipe" gets https:// added.
     */
    fun check(input: String): Checked {
        val raw = input.trim()
        if (raw.isEmpty()) return Checked.Bad("Paste the address of a recipe page.")
        if (raw.any { it.isWhitespace() }) return Checked.Bad("That does not look like one web address.")
        val withScheme = if (Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""").containsMatchIn(raw)) raw else "https://$raw"
        val url = withScheme.toHttpUrlOrNull()
            ?: return Checked.Bad("That does not look like a web address.")
        if (url.isHttps) return Checked.Ok(url)
        if (isLocalHost(url.host)) return Checked.Ok(url)
        return Checked.Bad("Only https:// addresses can be imported. Try the https:// version of the link.")
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h == "::1" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".localhost")) return true
        val m = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(h) ?: return false
        val (a, b) = m.groupValues[1].toInt() to m.groupValues[2].toInt()
        return a == 127 || a == 10 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
    }
}

/**
 * Downloads the page the user pasted, straight from that site. Runs only when the user taps Import.
 * HTTPS only (plain local hosts excepted), 5 MB cap, short timeouts, no cookies, and a User-Agent
 * that says what this is and carries nothing about the user.
 */
@Singleton
class RecipeFetcher @Inject constructor(client: OkHttpClient) {

    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        // A redirect between http and https is refused rather than followed.
        .followSslRedirects(false)
        .build()

    suspend fun fetch(input: String): FetchResult {
        val url = when (val c = RecipeUrl.check(input)) {
            is RecipeUrl.Checked.Bad -> return FetchResult.Failed(c.message)
            is RecipeUrl.Checked.Ok -> c.url
        }
        return withContext(Dispatchers.IO) { download(url) }
    }

    private fun download(url: HttpUrl): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            .get()
            .build()
        return try {
            http.newCall(request).execute().use { r ->
                if (r.isRedirect) {
                    return FetchResult.Failed("The site redirected to a different kind of address. Paste the final https:// link.")
                }
                if (!r.isSuccessful) return FetchResult.Failed("The site answered with an error (${r.code}).")
                val body = r.body
                val type = body.contentType()
                if (type != null && type.type != "text" && type.subtype !in setOf("xhtml+xml", "html")) {
                    return FetchResult.Failed("That link is not a web page.")
                }
                val declared = body.contentLength()
                if (declared > MAX_BYTES) return FetchResult.Failed("That page is larger than 5 MB, which is too big to import.")
                val bytes = readCapped(body.source())
                    ?: return FetchResult.Failed("That page is larger than 5 MB, which is too big to import.")
                FetchResult.Page(r.request.url.toString(), String(bytes, type?.charset(Charsets.UTF_8) ?: Charsets.UTF_8))
            }
        } catch (e: IOException) {
            FetchResult.Failed("Could not reach that page. Check the address and your connection.")
        } catch (e: IllegalArgumentException) {
            FetchResult.Failed("That does not look like a web address.")
        }
    }

    private fun readCapped(source: okio.BufferedSource): ByteArray? {
        val out = okio.Buffer()
        while (out.size <= MAX_BYTES) {
            if (source.read(out, 64L * 1024) == -1L) return out.readByteArray()
        }
        return null
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024

        /** Names the app and the purpose; the version is the build's own. Never any user data. */
        val USER_AGENT: String = "Intake/${BuildConfig.VERSION_NAME} (recipe import)"
    }
}
