package com.zeroindex.intake.data.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * "No provider for this model meets your privacy setting": the gateway tries the next compatible models from the
 * environment's list, with the same privacy setting, and a failure that is not about providers is never retried
 * on another model. A fake HTTP layer plays OpenRouter.
 */
class AiPrivacyFallbackTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Reply(val a: Int)

    private val now = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val clock = object : Clock {
        override fun nowMillis() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private val privacy404 = Wire(404, """{"error":{"code":404,"message":"No endpoints found matching your data policy"}}""")
    private val params404 = Wire(404, """{"error":{"code":404,"message":"No endpoints found that can handle the requested parameters."}}""")
    private val okReply = Wire(
        200,
        """{"model":"x","choices":[{"message":{"role":"assistant","content":"{\"a\":1}"}}],"usage":{"prompt_tokens":10,"completion_tokens":5,"cost":0.001}}""",
    )

    private class Wire(val code: Int, val body: String)

    private val sent = mutableListOf<JsonObject>()

    private fun gateway(env: FakeAiEnvironment, vararg wire: Wire): AiGateway {
        var i = 0
        val http = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                sent += json.parseToJsonElement(buffer.readUtf8()).jsonObject
                val w = wire[minOf(i++, wire.lastIndex)]
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(w.code).message("x")
                    .body(w.body.toResponseBody("application/json".toMediaType())).build()
            },
        ).build()
        return AiGateway(OpenRouterClient(http, json, "https://test.invalid/api/v1", pause = {}, jitter = { 0.5 }, nowMillis = { now }), env, json, clock)
    }

    private val schema = buildJsonObject { put("type", "object") }

    private suspend fun AiGateway.ask(feature: AiFeature = AiFeature.DAILY_INSIGHT) =
        structured(feature, AiTrigger.USER_ACTION, Reply.serializer(), schema, "sys", listOf(AiPart.Text("hi")))

    private fun modelsSent() = sent.map { it["model"]!!.jsonPrimitive.content }

    @Test
    fun `the first model has no provider, the second answers`() = runBlocking<Unit> {
        val env = FakeAiEnvironment(fallbacks = listOf("alt/one", "alt/two"))
        val outcome = gateway(env, privacy404, okReply).ask()
        assertThat(outcome.value).isEqualTo(Reply(1))
        assertThat(modelsSent()).containsExactly("test/daily_insight", "alt/one").inOrder()
        // Each attempt is logged: the failed one as a privacy failure, then the success.
        assertThat(env.records.map { it.failure }).containsExactly(AiFailureKind.PRIVACY, null).inOrder()
        assertThat(env.records.map { it.success }).containsExactly(false, true).inOrder()
        assertThat(env.records.map { it.model }.first()).isEqualTo("test/daily_insight")
    }

    @Test
    fun `the privacy setting is the same on every attempt`() = runBlocking<Unit> {
        val env = FakeAiEnvironment(privacy = AiPrivacy.ZERO_RETENTION, fallbacks = listOf("alt/one"))
        gateway(env, privacy404, okReply).ask()
        val providers = sent.map { it["provider"]!!.jsonObject }
        assertThat(providers).hasSize(2)
        providers.forEach {
            assertThat(it["zdr"]!!.jsonPrimitive.content).isEqualTo("true")
            assertThat(it["data_collection"]!!.jsonPrimitive.content).isEqualTo("deny")
        }
    }

    @Test
    fun `a request the providers cannot honour also moves on to the next model`() = runBlocking<Unit> {
        val env = FakeAiEnvironment(fallbacks = listOf("alt/one"))
        gateway(env, params404, okReply).ask()
        assertThat(modelsSent()).containsExactly("test/daily_insight", "alt/one").inOrder()
    }

    @Test
    fun `when every model fails the message names both causes and attempts are capped`() = runBlocking<Unit> {
        val env = FakeAiEnvironment(fallbacks = listOf("alt/one", "alt/two", "alt/three", "alt/four"))
        val gw = gateway(env, privacy404)
        val e = assertThrows(AiError.NoEndpointForPrivacy::class.java) { runBlocking { gw.ask() } }
        assertThat(sent).hasSize(AiGateway.MAX_MODEL_ATTEMPTS)
        assertThat(env.records).hasSize(AiGateway.MAX_MODEL_ATTEMPTS)
        assertThat(env.records.all { it.failure == AiFailureKind.PRIVACY }).isTrue()
        assertThat(e.message).contains("Settings")
        assertThat(e.message).contains("openrouter.ai/settings/privacy")
        assertThat(e.message).contains("Privacy for AI requests")
    }

    @Test
    fun `with no other compatible model the failure is reported after one attempt`() = runBlocking<Unit> {
        val gw = gateway(FakeAiEnvironment(), privacy404)
        assertThrows(AiError.NoEndpointForPrivacy::class.java) { runBlocking { gw.ask() } }
        assertThat(sent).hasSize(1)
    }

    @Test
    fun `a parameters failure is told apart from a privacy one and also names both places`() = runBlocking<Unit> {
        val gw = gateway(FakeAiEnvironment(), params404)
        val e = assertThrows(AiError.NoEndpointForParameters::class.java) { runBlocking { gw.ask() } }
        assertThat(e.message).contains("openrouter.ai/settings/privacy")
        assertThat(e.failureKind).isEqualTo(AiFailureKind.PRIVACY)
    }

    @Test
    fun `an error that is not about providers does not try another model`() = runBlocking<Unit> {
        val env = FakeAiEnvironment(fallbacks = listOf("alt/one"))
        val gw = gateway(env, Wire(403, """{"error":{"code":403,"message":"flagged"}}"""))
        assertThrows(AiError.Refused::class.java) { runBlocking { gw.ask() } }
        assertThat(sent).hasSize(1)

        val server = gateway(FakeAiEnvironment(fallbacks = listOf("alt/one")), Wire(402, """{"error":{"code":402,"message":"credits"}}"""))
        assertThrows(AiError.InsufficientCredits::class.java) { runBlocking { server.ask() } }
    }

    @Test
    fun `a repair turn goes to the model that answered`() = runBlocking<Unit> {
        val bad = Wire(200, """{"model":"x","choices":[{"message":{"role":"assistant","content":"nope"}}]}""")
        val env = FakeAiEnvironment(fallbacks = listOf("alt/one"))
        gateway(env, privacy404, bad, okReply).ask()
        assertThat(modelsSent()).containsExactly("test/daily_insight", "alt/one", "alt/one").inOrder()
    }

    private fun info(id: String, acceptsTemperature: Boolean) = com.zeroindex.intake.domain.ai.ModelInfo(
        id = id, name = id, description = "", contextLength = 100_000, promptPerMillion = 0.1, completionPerMillion = 0.1,
        perImage = 0.0, perRequest = 0.0, acceptsImages = false, acceptsFiles = false, outputsText = true,
        structuredOutputs = true, tools = true, acceptsTemperature = acceptsTemperature,
    )

    @Test
    fun `temperature is left out for a model whose providers do not list it, and sent otherwise`() = runBlocking<Unit> {
        // The root cause of the owner's failures: require_parameters plus a temperature no provider lists is a 404.
        val env = FakeAiEnvironment(
            models = mapOf("test/daily_insight" to info("test/daily_insight", acceptsTemperature = false), "alt/one" to info("alt/one", true)),
            fallbacks = listOf("alt/one"),
        )
        gateway(env, privacy404, okReply).ask()
        assertThat(sent[0].containsKey("temperature")).isFalse()
        assertThat(sent[0]["provider"]!!.jsonObject["require_parameters"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(sent[1].containsKey("temperature")).isTrue()

        sent.clear()
        gateway(FakeAiEnvironment(), okReply).ask()
        assertThat(sent.single().containsKey("temperature")).isTrue()
    }

    @Test
    fun `the failure text is plain and helpful`() {
        val text = AiError.NoEndpointForPrivacy.message
        assertThat(text).contains("app")
        assertThat(text).contains("OpenRouter account")
        assertThat(text).contains("pick another model")
    }
}
