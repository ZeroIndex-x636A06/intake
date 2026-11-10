package com.zeroindex.intake.data.remote.youtube

import com.zeroindex.intake.data.keys.ApiKeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Settings > API keys > YouTube > Test: one cheap `videos.list` call for a fixed public video id (1 quota unit). The
 * YouTube Data API expects the key as the `key` query parameter, so it is in the request URL (docs/FEEDBACK-R2.md,
 * "Videos use a key"); it is never logged and the URL is never put in an error message.
 */
@Singleton
class YoutubeKeyTester @Inject constructor(
    private val http: OkHttpClient,
    private val keys: ApiKeyStore,
) {
    /** Returns a plain-words result: (ok, text). */
    suspend fun test(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val key = keys.youtubeKey() ?: return@withContext false to "No key saved yet."
        val url = "https://www.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
            .addQueryParameter("part", "id")
            .addQueryParameter("id", "dQw4w9WgXcQ")
            .addQueryParameter("key", key)
            .build()
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                when {
                    r.isSuccessful -> true to "The key works."
                    r.code == 400 || r.code == 403 -> false to "YouTube did not accept the key (check that YouTube Data API v3 is enabled for it, or that today's quota is not used up)."
                    else -> false to "YouTube answered with an error (${r.code}). Try again later."
                }
            }
        }.getOrElse { false to "Could not reach YouTube. Check your connection." }
    }
}
