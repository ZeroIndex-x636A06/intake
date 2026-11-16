package com.zeroindex.intake.data.remote.youtube

import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.domain.videos.VideoMeta
import com.zeroindex.intake.domain.videos.YoutubeIds
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Why a video call did not work, in words the screen can show. Messages never carry the key or the URL. */
sealed class VideoError(message: String) : Exception(message) {
    data object NoKey : VideoError("No YouTube key saved yet.")
    data object Offline : VideoError("Videos need a connection.")
    data object QuotaUsed : VideoError("YouTube's daily limit for your key is used. Try again tomorrow.")
    data object KeyRejected : VideoError("YouTube did not accept the key. Check it in Settings > API keys.")
    data class Failed(val code: Int) : VideoError("YouTube answered with an error ($code). Try again later.")
}

/** One page of `search.list`. */
data class SearchPage(val ids: List<String>, val nextPageToken: String?)

/** Reading the Data API's JSON. Pure, so it is tested against fixtures. */
object YoutubeJson {
    private val json = Json { ignoreUnknownKeys = true }

    /** The video ids of a `search.list` answer, in order. */
    fun searchIds(body: String): List<String> {
        val items = json.parseToJsonElement(body).jsonObject["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { it.jsonObject["id"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull }
    }

    /** The `nextPageToken` of a `search.list` answer, or null on the last page. */
    fun nextPageToken(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["nextPageToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** The videos of a `videos.list` answer (part snippet, contentDetails, statistics). */
    fun videos(body: String): List<VideoMeta> {
        val items = json.parseToJsonElement(body).jsonObject["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val o = element.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val snippet = o["snippet"] as? JsonObject ?: return@mapNotNull null
            val thumbs = snippet["thumbnails"] as? JsonObject
            val thumb = listOf("medium", "high", "default", "standard").firstNotNullOfOrNull { k ->
                (thumbs?.get(k) as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            }
            VideoMeta(
                id = id,
                title = snippet["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = snippet["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                channelTitle = snippet["channelTitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                categoryId = snippet["categoryId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                durationSec = YoutubeIds.durationSeconds((o["contentDetails"] as? JsonObject)?.get("duration")?.jsonPrimitive?.contentOrNull),
                thumbnailUrl = thumb,
                publishedAt = snippet["publishedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                viewCount = (o["statistics"] as? JsonObject)?.get("viewCount")?.jsonPrimitive?.longOrNull,
                embedWidth = (o["player"] as? JsonObject)?.get("embedWidth")?.jsonPrimitive?.longOrNull?.toInt(),
                embedHeight = (o["player"] as? JsonObject)?.get("embedHeight")?.jsonPrimitive?.longOrNull?.toInt(),
            )
        }
    }

    /** The first error reason of an error answer ("quotaExceeded", "keyInvalid"), or null. */
    fun errorReason(body: String): String? = runCatching {
        val err = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return null
        val first = (err["errors"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        first ?: err["status"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Title, channel and thumbnail of an oEmbed answer. */
    fun oembed(id: String, body: String): VideoMeta? = runCatching {
        val o = json.parseToJsonElement(body).jsonObject
        VideoMeta(
            id = id,
            title = o["title"]?.jsonPrimitive?.contentOrNull ?: return null,
            channelTitle = o["author_name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            thumbnailUrl = o["thumbnail_url"]?.jsonPrimitive?.contentOrNull,
        )
    }.getOrNull()

    fun mapError(code: Int, body: String): VideoError {
        val reason = errorReason(body).orEmpty()
        return when {
            reason in setOf("quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded") -> VideoError.QuotaUsed
            reason in setOf("keyInvalid", "API_KEY_INVALID", "badRequest", "INVALID_ARGUMENT", "accessNotConfigured", "forbidden", "PERMISSION_DENIED") -> VideoError.KeyRejected
            code == 400 || code == 401 || code == 403 -> VideoError.KeyRejected
            else -> VideoError.Failed(code)
        }
    }
}

/**
 * The only place the app talks to YouTube's Data API (and the no-key oEmbed address). The key comes from [ApiKeyStore] and goes
 * only in the `key` query parameter the API requires (docs/FEEDBACK-R2.md, "Videos use a key"). It is never logged, never put
 * in an error message and never stored with a result. Playback is separate (the embedded player in a WebView).
 */
@Singleton
class YoutubeApi @Inject constructor(
    private val http: OkHttpClient,
    private val keys: ApiKeyStore,
) {
    fun hasKey(): Boolean = keys.youtubeKey() != null

    /** `search.list`: embeddable videos, moderate safe search. About 100 quota units. Returns the video ids. */
    suspend fun search(query: String, maxResults: Int = 25): List<String> = searchPage(query, null, maxResults).ids

    /** One page of `search.list`: the video ids and the token for the next page (null at the end). The token is not secret. */
    suspend fun searchPage(query: String, pageToken: String?, maxResults: Int = 25): SearchPage {
        val key = keys.youtubeKey() ?: throw VideoError.NoKey
        val builder = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video")
            .addQueryParameter("videoEmbeddable", "true")
            .addQueryParameter("safeSearch", "moderate")
            .addQueryParameter("maxResults", maxResults.coerceIn(1, 50).toString())
            .addQueryParameter("q", query)
        if (!pageToken.isNullOrBlank()) builder.addQueryParameter("pageToken", pageToken)
        val url = builder.addQueryParameter("key", key).build()
        val body = get(url.toString())
        return SearchPage(YoutubeJson.searchIds(body), YoutubeJson.nextPageToken(body))
    }

    /**
     * `videos.list` with snippet, contentDetails, statistics and player for up to 50 ids. About 1 quota unit: the cost of a
     * list call is per call, not per part, so `player` adds nothing. `maxWidth` and `maxHeight` make `player.embedWidth` and
     * `embedHeight` follow the video's aspect ratio, which is how Hide Shorts tells a vertical video from a horizontal one.
     */
    suspend fun videos(ids: List<String>): List<VideoMeta> {
        if (ids.isEmpty()) return emptyList()
        val key = keys.youtubeKey() ?: throw VideoError.NoKey
        val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet,contentDetails,statistics,player")
            .addQueryParameter("maxWidth", "720")
            .addQueryParameter("maxHeight", "720")
            .addQueryParameter("id", ids.take(50).joinToString(","))
            .addQueryParameter("key", key)
            .build()
        val byId = YoutubeJson.videos(get(url.toString())).associateBy { it.id }
        // Keep the order of the search.
        return ids.mapNotNull { byId[it] }
    }

    /** The no-key way to get a title and channel for one pasted link. Null when YouTube does not know the video. */
    suspend fun oembed(id: String): VideoMeta? = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/oembed".toHttpUrl().newBuilder()
            .addQueryParameter("url", YoutubeIds.watchUrl(id))
            .addQueryParameter("format", "json")
            .build()
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else YoutubeJson.oembed(id, r.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw VideoError.Offline
        }
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (r.isSuccessful) body else throw YoutubeJson.mapError(r.code, body)
            }
        } catch (e: VideoError) {
            throw e
        } catch (e: IOException) {
            throw VideoError.Offline
        }
    }
}
