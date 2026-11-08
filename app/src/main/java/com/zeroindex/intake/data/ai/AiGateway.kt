package com.zeroindex.intake.data.ai

import com.zeroindex.intake.data.keys.ApiKeyStore
import com.zeroindex.intake.data.settings.SettingsRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.ModelSuggester
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A parsed answer plus what it cost and which model gave it. */
data class AiOutcome<T>(val value: T, val response: AiResponse)

/**
 * The only way a feature talks to a model. It owns three decisions so no
 * feature can get them wrong on its own:
 *
 * - **Whether**: no key, no request - every AI feature is opt-in by having
 *   supplied one, and the rest of the app never needs it (SPEC §3.3).
 * - **Which model**: the user's choice for that task, else the suggestion.
 * - **How private**: the privacy setting is applied to every request, not
 *   left to each caller to remember.
 */
@Singleton
class AiGateway @Inject constructor(
    private val client: OpenRouterClient,
    private val keys: ApiKeyStore,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalogRepository,
    private val json: Json,
) {
    fun isConfigured(): Boolean = keys.openRouterKey() != null

    suspend fun modelFor(task: AiTask): String {
        val prefs = settings.settings.first()
        prefs.aiModelOverrides[task]?.let { return it }
        val state = catalog.ensureLoaded()
        val suggestion = ModelSuggester.suggest(task, state.models, catalog.benchmarks(), prefs.aiAllowRetainingModels)
        return suggestion.suggested?.model?.id
            // Offline with no cached catalog: fall back to the first concrete
            // shortlist entry rather than refusing outright.
            ?: task.shortlist.firstOrNull { '*' !in it }
            ?: throw AiError.BadResponse("no model available for ${task.label}")
    }

    /**
     * Runs [task] and decodes the reply into [T]. A reply that does not parse
     * is retried once with the parse error fed back - models occasionally wrap
     * JSON in prose even under a schema - and a second failure is reported
     * rather than guessed at.
     */
    suspend fun <T> structured(
        task: AiTask,
        serializer: KSerializer<T>,
        schema: JsonObject,
        system: String,
        parts: List<AiPart>,
        tools: List<ServerTool> = emptyList(),
        maxTokens: Int = task.typicalOutputTokens * 2,
    ): AiOutcome<T> {
        val key = keys.openRouterKey() ?: throw AiError.NoKey
        val prefs = settings.settings.first()
        val request = AiRequest(
            model = modelFor(task),
            parts = parts,
            system = system,
            schema = schema,
            schemaName = task.name.lowercase(),
            tools = tools,
            maxTokens = maxTokens,
            privacy = prefs.aiPrivacy,
        )
        val first = client.complete(key, request)
        decodeReply(json, serializer, first.content)?.let { return AiOutcome(it, first) }

        val retry = client.complete(
            key,
            request.copy(
                parts = parts + AiPart.Text(
                    "Your previous reply could not be parsed. Reply with only a JSON object matching the schema.",
                ),
                tools = emptyList(),
            ),
        )
        val value = decodeReply(json, serializer, retry.content)
            ?: throw AiError.BadResponse("it did not match the expected format", raw = retry.content)
        return AiOutcome(value, retry.copy(costUsd = sum(first.costUsd, retry.costUsd)))
    }

    suspend fun testKey(key: String): KeyData = client.keyInfo(key)

    private fun sum(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

    companion object {
        /**
         * Decodes a reply into [T], or null if it does not match. Shared with
         * the benchmark so "parsed" means the same thing in both.
         */
        fun <T> decodeReply(json: Json, serializer: KSerializer<T>, content: String): T? {
            val text = extractJson(content) ?: return null
            return try {
                json.decodeFromString(serializer, text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** Pulls the JSON object out of a reply that may be fenced or prefixed with prose. */
        fun extractJson(content: String): String? {
            val trimmed = content.trim()
            if (trimmed.startsWith("{")) return trimmed
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            return if (start >= 0 && end > start) trimmed.substring(start, end + 1) else null
        }
    }
}
