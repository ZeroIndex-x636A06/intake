package com.zeroindex.intake.data.sync

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable data class PairRequest(val code: String, val deviceName: String)
@Serializable data class PairResponse(val deviceId: String, val token: String)
@Serializable data class PairingCode(val code: String, val expiresAt: Long)
@Serializable data class SyncDevice(val id: String, val name: String, val createdAt: Long, val lastSeenAt: Long? = null, val current: Boolean = false)
@Serializable data class DevicesResponse(val devices: List<SyncDevice>)

@Serializable
data class WireRow(
    val table: String,
    val id: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val hash: String,
    val payload: JsonObject,
    val seq: Long? = null,
    val originDevice: String? = null,
)

@Serializable data class PushRequest(val rows: List<WireRow>)
@Serializable data class PushResponse(val accepted: Int, val ignored: Int, val seq: Long)
@Serializable data class PullResponse(val rows: List<WireRow>, val next: Long, val more: Boolean)
@Serializable private data class ErrorBody(val error: String)

/** A failure worded for the sync card; never includes the token. */
class SyncException(message: String, val unauthorized: Boolean = false, cause: Throwable? = null) : Exception(message, cause)

/** HTTP client for server/intake_sync.py. The token goes only in the Authorization header. */
class SyncApi(private val http: OkHttpClient, private val json: Json, serverUrl: String) {

    private val base: HttpUrl = serverUrl.trim().trimEnd('/').toHttpUrl()
    private val jsonType = "application/json".toMediaType()

    private fun url(path: String, query: Map<String, String> = emptyMap()) = base.newBuilder().apply {
        path.trim('/').split('/').forEach { addPathSegment(it) }
        query.forEach { (k, v) -> addQueryParameter(k, v) }
    }.build()

    private suspend fun <T> call(request: Request, out: KSerializer<T>): T = withContext(Dispatchers.IO) {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw SyncException("Could not reach ${base.host}.", cause = e)
        }
        response.use { r ->
            val body = r.body.string()
            if (!r.isSuccessful) {
                val reason = runCatching { json.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
                throw SyncException(
                    reason ?: "${base.host} returned HTTP ${r.code}.",
                    unauthorized = r.code == 401,
                )
            }
            try {
                json.decodeFromString(out, body)
            } catch (e: Exception) {
                throw SyncException("${base.host} sent a reply this app does not understand. Is it an Intake sync server?", cause = e)
            }
        }
    }

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    suspend fun pair(code: String, deviceName: String): PairResponse = call(
        Request.Builder().url(url("v1/pair"))
            .post(json.encodeToString(PairRequest.serializer(), PairRequest(code, deviceName)).toRequestBody(jsonType)).build(),
        PairResponse.serializer(),
    )

    suspend fun newPairingCode(token: String): PairingCode = call(
        Request.Builder().url(url("v1/pairing-codes")).auth(token).post("{}".toRequestBody(jsonType)).build(),
        PairingCode.serializer(),
    )

    suspend fun devices(token: String): List<SyncDevice> = call(
        Request.Builder().url(url("v1/devices")).auth(token).get().build(),
        DevicesResponse.serializer(),
    ).devices

    suspend fun revoke(token: String, deviceId: String) {
        call(Request.Builder().url(url("v1/devices/$deviceId")).auth(token).delete().build(), JsonObject.serializer())
    }

    suspend fun push(token: String, rows: List<WireRow>): PushResponse = call(
        Request.Builder().url(url("v1/push")).auth(token)
            .post(json.encodeToString(PushRequest.serializer(), PushRequest(rows)).toRequestBody(jsonType)).build(),
        PushResponse.serializer(),
    )

    suspend fun pull(token: String, since: Long, limit: Int = 500): PullResponse = call(
        Request.Builder().url(url("v1/pull", mapOf("since" to since.toString(), "limit" to limit.toString()))).auth(token).get().build(),
        PullResponse.serializer(),
    )
}
