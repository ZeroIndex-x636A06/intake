package com.zeroindex.intake.data.ai

import com.zeroindex.intake.domain.ai.cost.AiMoney
import java.io.IOException
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
) {
    companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        private val JSON_MEDIA = "application/json".toMediaType()
        // OpenRouter's optional app attribution. Names the app, nothing more.
        private const val APP_TITLE = "Intake"
    }

    /** Public list of models; needs no key. */
    internal suspend fun models(): List<OrModel> {
        val body = execute(Request.Builder().url("$baseUrl/models").get().build())
        return json.decodeFromString(ModelsResponse.serializer(), body).data
    }

    /** Checks a key and reports its remaining credit. */
    suspend fun keyInfo(apiKey: String): KeyData {
        val body = execute(
            Request.Builder().url("$baseUrl/key").header("Authorization", "Bearer $apiKey").get().build(),
        )
        return json.decodeFromString(KeyResponse.serializer(), body).data
    }

    /**
     * One chat completion. With [AiRequest.schema] set, the model is held to
     * that JSON Schema (strict structured output) and the provider is required
     * to support it, so a provider that would silently ignore the schema is
     * never picked.
     */
    suspend fun complete(apiKey: String, request: AiRequest): AiResponse {
        val started = System.nanoTime()
        val payload = buildPayload(request)
        val body = execute(
            Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("X-Title", APP_TITLE)
                .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                .build(),
        )
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw AiError.BadResponse("The reply was not JSON") }
        root["error"]?.let { throw errorFromBody(200, it) }

        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw AiError.BadResponse("The reply had no message")
        val content = message["content"].textContent()
        val usage = root["usage"]?.jsonObject
        return AiResponse(
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
            request.system?.let { add(buildJsonObject { put("role", "system"); put("content", it) }) }
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
            putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", request.schemaName)
                    put("strict", true)
                    put("schema", schema)
                }
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") { request.tools.forEach { add(it.toJson()) } }
        }
        if (request.parts.any { it is AiPart.Pdf }) {
            // Native file handling where the model has it, cheap text
            // extraction otherwise; Mistral OCR is the paid fallback and is
            // only worth it for scanned menus, which this does not assume.
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

    private suspend fun execute(request: Request): String {
        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw AiError.Network(e.message ?: "No connection")
        }
        response.use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) {
                val err = runCatching { json.parseToJsonElement(text).jsonObject["error"] }.getOrNull()
                throw errorFromBody(r.code, err)
            }
            return text
        }
    }

    private fun errorFromBody(code: Int, error: JsonElement?): AiError {
        val obj = error as? JsonObject
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        val status = obj?.get("code")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: code
        return when {
            status == 401 -> AiError.Unauthorized
            status == 402 -> AiError.InsufficientCredits
            status == 429 -> AiError.RateLimited
            // "No endpoints found matching your data policy" and its variants.
            status == 404 && (message.contains("policy", true) || message.contains("endpoint", true)) ->
                AiError.NoEndpointForPrivacy
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
)

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
    data object RateLimited : AiError("Too many requests just now. Try again in a minute.")
    data object NoEndpointForPrivacy : AiError(
        "No provider for this model meets your privacy setting. Pick another model, or relax the setting.",
    )
    data class Network(val detail: String) : AiError("Could not reach OpenRouter. Everything else still works offline.")
    data class Server(val code: Int, val detail: String) : AiError("OpenRouter returned an error ($code). Try again shortly.")
    /** [raw] is the model's reply, kept so the UI can show it (SPEC §6.5). */
    data class BadResponse(val detail: String, val raw: String? = null) :
        AiError("The model's answer could not be read: $detail.")
}
