package com.zeroindex.intake.data.ai

import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiTimeout
import com.zeroindex.intake.domain.ai.RetryPolicy
import com.zeroindex.intake.domain.ai.cost.AiMoney
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Amendment §1: the one door to OpenRouter.
 *
 * Deliberately free of Android imports, so the same code path the app uses is
 * the one the benchmark harness measures on a laptop JVM.
 *
 * The key is passed per call and only ever placed in the Authorization
 * header - never logged, never in a URL, never in an error message.
 */
class OpenRouterClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = BASE_URL,
    /** How a retry waits. Injected so tests run without real pauses. */
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Double = { Math.random() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        private val JSON_MEDIA = "application/json".toMediaType()
        // OpenRouter's optional app attribution. Names the app, nothing more.
        private const val APP_TITLE = "Intake"
    }

    /** One derived client per patience level: same connection pool and interceptors, different timeouts. */
    private val clients = java.util.concurrent.ConcurrentHashMap<AiTimeout, OkHttpClient>()

    private fun clientFor(timeout: AiTimeout): OkHttpClient = clients.getOrPut(timeout) {
        http.newBuilder()
            .readTimeout(timeout.readSeconds, TimeUnit.SECONDS)
            .callTimeout(timeout.callSeconds, TimeUnit.SECONDS)
            .build()
    }

    /** Public list of models; needs no key. */
    internal suspend fun models(): List<OrModel> {
        val body = execute(Request.Builder().url("$baseUrl/models").get().build(), AiTimeout.MEDIUM)
        return json.decodeFromString(ModelsResponse.serializer(), body).data
    }

    /** The providers serving one model, with the parameters each accepts. Public; needs no key. */
    internal suspend fun endpointsOf(modelId: String): List<OrEndpoint> {
        val body = execute(Request.Builder().url("$baseUrl/models/$modelId/endpoints").get().build(), AiTimeout.TEXT)
        return json.decodeFromString(EndpointsResponse.serializer(), body).data.endpoints
    }

    /** Every endpoint that keeps nothing after answering (the zero-data-retention list). Public; needs no key. */
    internal suspend fun zdrEndpoints(): List<OrEndpoint> {
        val body = execute(Request.Builder().url("$baseUrl/endpoints/zdr").get().build(), AiTimeout.MEDIUM)
        return json.decodeFromString(ZdrResponse.serializer(), body).data
    }

    /** Checks a key and reports its remaining credit. */
    suspend fun keyInfo(apiKey: String): KeyData {
        val body = execute(
            Request.Builder().url("$baseUrl/key").header("Authorization", "Bearer $apiKey").get().build(),
            AiTimeout.TEXT,
        )
        return json.decodeFromString(KeyResponse.serializer(), body).data
    }

    /**
     * One chat completion. With [AiRequest.schema] set, the model is held to
     * that JSON Schema (strict structured output) and the provider is required
     * to support it, so a provider that would silently ignore the schema is
     * never picked.
     */
    suspend fun complete(apiKey: String, request: AiRequest): AiResponse = withContext(Dispatchers.Default) {
        // Building a request (base64 of a photo or PDF), and reading a large reply, never run on the main thread,
        // whichever scope the caller launched from.
        val started = System.nanoTime()
        val payload = buildPayload(request)
        val body = execute(
            Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("X-Title", APP_TITLE)
                .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                .build(),
            request.effectiveTimeout(),
        )
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw AiError.BadResponse("The reply was not JSON") }
        root["error"]?.let { throw errorFromBody(200, it) }

        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AiError.BadResponse("The reply had no message")
        val content = message["content"].textContent()
        val usage = root["usage"]?.jsonObject
        AiResponse(
            content = content,
            model = root["model"]?.jsonPrimitive?.contentOrNull ?: request.model,
            costUsd = usage?.get("cost")?.jsonPrimitive?.doubleOrNull,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
            latencyMs = (System.nanoTime() - started) / 1_000_000,
        )
    }

    internal fun buildPayload(request: AiRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        putJsonArray("messages") {
            request.systemText()?.let { add(buildJsonObject { put("role", "system"); put("content", it) }) }
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray { request.parts.forEach { add(it.toJson()) } })
                },
            )
        }
        request.maxTokens?.let { put("max_tokens", it) }
        request.temperature?.let { put("temperature", it) }
        request.schema?.let { schema ->
            if (request.strictSchema) {
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", request.schemaName)
                        put("strict", true)
                        put("schema", schema)
                    }
                }
            } else {
                // The model has no strict structured output: ask for a JSON object and give it the schema in words.
                putJsonObject("response_format") { put("type", "json_object") }
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") { request.tools.forEach { add(it.toJson()) } }
        }
        if (request.parts.any { it is AiPart.Pdf }) {
            // The free text engine, named explicitly. Without an engine OpenRouter uses the model's native file
            // handling (billed as input tokens) or Mistral OCR ($2 per 1,000 pages), neither of which a menu
            // table with a text layer needs. ("pdf-text" is the old name; OpenRouter now redirects it here.)
            putJsonArray("plugins") {
                add(buildJsonObject { put("id", "file-parser"); putJsonObject("pdf") { put("engine", "cloudflare-ai") } })
            }
        }
        putJsonObject("provider") {
            // Only route to a provider that honours the schema and tools.
            if (request.schema != null || request.tools.isNotEmpty()) put("require_parameters", true)
            when (request.privacy) {
                AiPrivacy.ZERO_RETENTION -> {
                    put("data_collection", "deny")
                    put("zdr", true)
                }
                AiPrivacy.NO_TRAINING -> put("data_collection", "deny")
                AiPrivacy.ANY -> Unit
            }
        }
        putJsonObject("usage") { put("include", true) }
    }

    private fun AiPart.toJson(): JsonObject = when (this) {
        is AiPart.Text -> buildJsonObject { put("type", "text"); put("text", text) }
        is AiPart.Image -> buildJsonObject {
            put("type", "image_url")
            putJsonObject("image_url") {
                put("url", "data:$mimeType;base64," + Base64.getEncoder().encodeToString(bytes))
            }
        }
        is AiPart.Pdf -> buildJsonObject {
            put("type", "file")
            putJsonObject("file") {
                put("filename", filename)
                put("file_data", "data:application/pdf;base64," + Base64.getEncoder().encodeToString(bytes))
            }
        }
    }

    private fun ServerTool.toJson(): JsonObject = when (this) {
        is ServerTool.WebSearch -> buildJsonObject {
            put("type", "openrouter:web_search")
            putJsonObject("parameters") {
                put("max_results", maxResults)
                maxUses?.let { put("max_uses", it) }
                excludedDomains.takeIf { it.isNotEmpty() }?.let { domains ->
                    putJsonArray("excluded_domains") { domains.forEach { add(JsonPrimitive(it)) } }
                }
            }
        }
        is ServerTool.WebFetch -> buildJsonObject {
            put("type", "openrouter:web_fetch")
            putJsonObject("parameters") {
                put("max_uses", maxUses)
                put("max_content_tokens", maxContentTokens)
            }
        }
    }

    /** Content may come back as a string or as an array of text parts. */
    private fun JsonElement?.textContent(): String = when (this) {
        null -> ""
        is JsonPrimitive -> contentOrNull.orEmpty()
        is JsonArray -> joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
        else -> ""
    }

    /**
     * Sends [request], retrying a 429 or 5xx with backoff that honours `Retry-After`. A timeout or a dropped
     * connection is reported at once, not retried: the provider may already have done (and billed) the work.
     * Cancelling the coroutine cancels the OkHttp call.
     */
    private suspend fun execute(request: Request, timeout: AiTimeout): String {
        val callClient = clientFor(timeout)
        var attempt = 1
        while (true) {
            val response = try {
                callClient.newCall(request).await()
            } catch (e: InterruptedIOException) {
                throw AiError.Timeout
            } catch (e: IOException) {
                throw AiError.Network(e.message ?: "No connection")
            }
            val (text, failure) = response.use { r ->
                val body = r.body.string()
                if (r.isSuccessful) return@use body to null
                val err = runCatching { json.parseToJsonElement(body).jsonObject["error"] }.getOrNull()
                body to errorFromBody(r.code, err, RetryPolicy.parseRetryAfter(r.header("Retry-After"), nowMillis()))
            }
            if (failure == null) return text
            val status = when (failure) {
                is AiError.RateLimited -> 429
                is AiError.Server -> failure.code
                else -> throw failure
            }
            val retryAfter = (failure as? AiError.RateLimited)?.retryAfterMs
            val wait = if (RetryPolicy.isRetryable(status)) RetryPolicy.waitMs(attempt, retryAfter, jitter()) else null
            if (wait == null) throw failure
            pause(wait)
            attempt++
        }
    }

    private fun errorFromBody(code: Int, error: JsonElement?, retryAfterMs: Long? = null): AiError {
        val obj = error as? JsonObject
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = obj?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: code
        return when {
            status == 401 -> AiError.Unauthorized
            status == 402 -> AiError.InsufficientCredits
            status == 408 -> AiError.Timeout
            status == 429 -> AiError.RateLimited(retryAfterMs)
            // "No endpoints found that can handle the requested parameters": a provider exists, but none accepts
            // everything the request sends (the schema, tools, sampling settings).
            (status == 404 || status == 400) &&
                (message.contains("requested parameters", true) || message.contains("support the requested", true)) ->
                AiError.NoEndpointForParameters
            // "No endpoints found matching your data policy" and its variants.
            (status == 404 || status == 400) &&
                (message.contains("data policy", true) || message.contains("endpoints found", true)) ->
                AiError.NoEndpointForPrivacy
            // Moderation or an unsupported request: the provider said no, and asking again will not change it.
            status == 403 || status == 400 || status == 422 -> AiError.Refused(status, message.take(200))
            status == 404 && message.contains("endpoint", true) -> AiError.NoEndpointForPrivacy
            else -> AiError.Server(status, message.take(200))
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) = cont.resume(response)
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** How much a request is allowed to be seen by whoever serves it. */
enum class AiPrivacy(val label: String, val explanation: String) {
    ZERO_RETENTION(
        "Zero retention",
        "Only providers that keep nothing after answering. Strictest; some models become unavailable.",
    ),
    NO_TRAINING(
        "No data collection",
        "Only providers that do not store or train on your prompts. Recommended.",
    ),
    ANY(
        "Any provider",
        "Allows providers that may store prompts or train on them. Cheapest, least private.",
    ),
}

sealed interface AiPart {
    data class Text(val text: String) : AiPart
    class Image(val bytes: ByteArray, val mimeType: String = "image/jpeg") : AiPart
    class Pdf(val bytes: ByteArray, val filename: String) : AiPart
}

/** Tools OpenRouter runs on its side, inside the one request. */
sealed interface ServerTool {
    data class WebSearch(
        val maxResults: Int = 5,
        val maxUses: Int? = 3,
        val excludedDomains: List<String> = emptyList(),
    ) : ServerTool

    data class WebFetch(val maxUses: Int = 6, val maxContentTokens: Int = 30_000) : ServerTool
}

data class AiRequest(
    val model: String,
    val parts: List<AiPart>,
    val system: String? = null,
    val schema: JsonObject? = null,
    val schemaName: String = "result",
    val tools: List<ServerTool> = emptyList(),
    val maxTokens: Int? = null,
    val temperature: Double? = 0.2,
    val privacy: AiPrivacy = AiPrivacy.NO_TRAINING,
    /** How long to wait for the answer; see [AiTimeout]. Photos, PDFs and web tools are raised to at least LONG. */
    val timeout: AiTimeout = AiTimeout.MEDIUM,
    /**
     * True when the model's catalog entry says it supports strict structured output. False sends JSON mode with
     * the schema spelled out in the system prompt instead, for models that only know `response_format`.
     */
    val strictSchema: Boolean = true,
) {
    /** The patience this request really needs, whatever the caller asked for. */
    fun effectiveTimeout(): AiTimeout {
        val slow = tools.isNotEmpty() || parts.any { it is AiPart.Image || it is AiPart.Pdf }
        return if (slow && timeout < AiTimeout.LONG) AiTimeout.LONG else timeout
    }

    /** The system prompt, with the schema appended in words when the model cannot be held to it strictly. */
    fun systemText(): String? {
        if (schema == null || strictSchema) return system
        val spelled = "Reply with a single JSON object that matches this JSON Schema exactly, and nothing else:\n$schema"
        return if (system.isNullOrBlank()) spelled else "$system\n\n$spelled"
    }
}

data class AiResponse(
    val content: String,
    val model: String,
    val costUsd: Double?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val latencyMs: Long,
)

/** Failures, each with a message fit to show the user as-is. */
sealed class AiError(override val message: String) : Exception(message) {
    data object NoKey : AiError("Add an OpenRouter key in Settings → AI to use this.")
    /** The master switch is off: nothing was sent. */
    data object Off : AiError("AI is off. Turn on “Use AI features” in Settings → AI to use this.")
    /** The month's budget is used up: nothing was sent. */
    data class BudgetReached(val capUsd: Double, val spentUsd: Double) : AiError(
        "This month's AI budget (${AiMoney.format(capUsd)}) is used up, so nothing was sent. " +
            "It starts again next month, or you can raise it in Settings → AI.",
    )
    /** The call did not say why it was happening, or that reason is not allowed for it: nothing was sent. */
    data class NotAllowed(val detail: String) : AiError("This action is not allowed to use AI, so nothing was sent.")
    data object Unauthorized : AiError("OpenRouter rejected the key. Check it in Settings → AI.")
    data object InsufficientCredits : AiError("The OpenRouter account is out of credit.")
    /** [retryAfterMs] is the provider's own `Retry-After`, when it gave one. */
    data class RateLimited(val retryAfterMs: Long? = null) : AiError("Too many requests just now. Try again in a minute.")
    /** The call waited as long as it is allowed to and got nothing back. */
    data object Timeout : AiError(
        "This is taking too long, so it was stopped. Try again, or pick a faster model in Settings → AI.",
    )
    /** The provider declined the request (moderation, or a request the model cannot take). Asking again will not help. */
    data class Refused(val code: Int, val detail: String) :
        AiError("The provider refused this request. Try another model in Settings → AI.")
    /** No model can do this task with the catalog and settings as they are. */
    data class NoModel(val task: String) : AiError("No suitable model is available for $task. Check Settings → AI.")
    /** No provider of the model could take the request under the privacy settings. Names both places that decide it. */
    data object NoEndpointForPrivacy : AiError(
        "No provider could take this request under your privacy settings. Two places decide that: the app's " +
            "“Privacy for AI requests” in Settings → AI, and your OpenRouter account's own privacy page at " +
            "openrouter.ai/settings/privacy, which can block providers too. Relax one of them, or pick another model in " +
            "Settings → AI.",
    )
    /** A provider exists but none accepts everything this task sends, together with the privacy settings. */
    data object NoEndpointForParameters : AiError(
        "No provider for this model can do everything this task needs under your privacy settings. Check the app's " +
            "“Privacy for AI requests” in Settings → AI and your OpenRouter account's privacy page at " +
            "openrouter.ai/settings/privacy, or pick another model in Settings → AI.",
    )
    data class Network(val detail: String) : AiError("Could not reach OpenRouter. Everything else still works offline.")
    data class Server(val code: Int, val detail: String) : AiError("OpenRouter returned an error ($code). Try again shortly.")
    /**
     * The model's reply could not be read even after one repair attempt. [detail] and [raw] are for tests and
     * debugging only: the message is fixed text, because model text is never shown to the user.
     */
    data class BadResponse(val detail: String, val raw: String? = null) :
        AiError("The model's answer could not be understood, even after a second try. Try again, or pick another model in Settings → AI.")

    /** What to record on the usage row when this is why a call failed. */
    val failureKind: AiFailureKind
        get() = when (this) {
            is Timeout -> AiFailureKind.TIMEOUT
            is BadResponse -> AiFailureKind.INVALID_OUTPUT
            is Refused -> AiFailureKind.REFUSED
            is RateLimited -> AiFailureKind.RATE_LIMITED
            is Network -> AiFailureKind.NETWORK
            is Unauthorized -> AiFailureKind.AUTH
            is InsufficientCredits -> AiFailureKind.CREDITS
            is NoEndpointForPrivacy, is NoEndpointForParameters -> AiFailureKind.PRIVACY
            is Server -> AiFailureKind.SERVER
            is NoKey, is Off, is BudgetReached, is NotAllowed, is NoModel -> AiFailureKind.REFUSED
        }
}
