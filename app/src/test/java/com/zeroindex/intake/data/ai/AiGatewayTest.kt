package com.zeroindex.intake.data.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.cost.AiUsageRecord
import com.zeroindex.intake.domain.time.Clock
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The gateway's rules with no network and no key: an interceptor stands in for OpenRouter and counts the
 * requests that reach it, so "rejected" means "nothing was sent".
 */
class AiGatewayTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Reply(val a: Int)

    private var now = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val clock = object : Clock {
        override fun nowMillis() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private var sent = 0
    private var bodies = mutableListOf<String>()

    private fun reply(content: String, cost: String? = "0.0042", tokens: Boolean = true) = """
        {"model":"test/model","choices":[{"message":{"role":"assistant","content":${json.encodeToString(String.serializer(), content)}}}],
         "usage":{${if (tokens) "\"prompt_tokens\":1200,\"completion_tokens\":300," else ""}${if (cost != null) "\"cost\":$cost" else "\"x\":0"}}}
    """.trimIndent()

    private fun gateway(env: FakeAiEnvironment, vararg bodies: String, fail: Boolean = false): AiGateway {
        var i = 0
        val http = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                sent++
                if (fail) throw IOException("offline")
                val body = bodies[minOf(i++, bodies.lastIndex)]
                this.bodies += body
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            },
        ).build()
        return AiGateway(OpenRouterClient(http, json, "https://test.invalid/api/v1", pause = {}), env, json, clock)
    }

    private val schema = buildJsonObject { put("type", "object") }

    private suspend fun AiGateway.ask(
        feature: AiFeature = AiFeature.PLAN_B,
        trigger: AiTrigger? = AiTrigger.USER_ACTION,
    ) = structured(feature, trigger, Reply.serializer(), schema, "sys", listOf(AiPart.Text("PROMPT-TEXT-XYZ")))

    // --- the three refusals the policy names --------------------------------------------------------------

    @Test
    fun `with the master switch off a call is refused and nothing is sent or logged`() = runTest {
        val env = FakeAiEnvironment(enabled = false)
        val gw = gateway(env, reply("""{"a":1}"""))
        val e = assertThrows(AiError::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        assertThat(e).isEqualTo(AiError.Off)
        assertThat(sent).isEqualTo(0)
        assertThat(env.records).isEmpty()
        assertThat(gw.isConfigured()).isFalse()
        assertThat(gw.unavailableReason()).isEqualTo(AiError.Off)
    }

    @Test
    fun `once the monthly budget is used up calls are refused and nothing is sent`() = runTest {
        val env = FakeAiEnvironment(budgetUsd = 2.0, priorSpendUsd = 2.0)
        val gw = gateway(env, reply("""{"a":1}"""))
        val e = assertThrows(AiError.BudgetReached::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        assertThat(e.capUsd).isEqualTo(2.0)
        assertThat(e.message).contains("budget")
        assertThat(sent).isEqualTo(0)
        assertThat(env.records).isEmpty()
    }

    @Test
    fun `spend that crosses the cap during a session stops the next call`() = runTest {
        val env = FakeAiEnvironment(budgetUsd = 0.005)
        val gw = gateway(env, reply("""{"a":1}""", cost = "0.0042"))
        gw.ask() // 0.0042 of 0.005: allowed, and logged
        assertThat(sent).isEqualTo(1)
        gw.ask() // 0.0084 would pass the cap, but the check is on what is already spent: still under, allowed
        assertThat(sent).isEqualTo(2)
        assertThrows(AiError.BudgetReached::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        assertThat(sent).isEqualTo(2)
    }

    @Test
    fun `the budget starts again when the month changes`() = runTest {
        val env = FakeAiEnvironment(budgetUsd = 0.004)
        val gw = gateway(env, reply("""{"a":1}""", cost = "0.0042"))
        gw.ask()
        assertThrows(AiError.BudgetReached::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }

        now = LocalDateTime.of(2026, 12, 1, 0, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        gw.ask() // a new month: last month's spend no longer counts
        assertThat(env.records).hasSize(2)
    }

    @Test
    fun `raising the cap lets calls through again`() = runTest {
        val env = FakeAiEnvironment(budgetUsd = 1.0, priorSpendUsd = 1.5)
        val gw = gateway(env, reply("""{"a":1}"""))
        assertThrows(AiError.BudgetReached::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        env.budgetUsd = 5.0
        gw.ask()
        assertThat(sent).isEqualTo(1)
    }

    @Test
    fun `a call with no trigger fails loudly in debug builds and is refused in release`() = runTest {
        val debug = FakeAiEnvironment(strict = true)
        val gwDebug = gateway(debug, reply("""{"a":1}"""))
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { gwDebug.ask(trigger = null) } }

        val release = FakeAiEnvironment(strict = false)
        val gwRelease = gateway(release, reply("""{"a":1}"""))
        assertThrows(AiError.NotAllowed::class.java) { kotlinx.coroutines.runBlocking { gwRelease.ask(trigger = null) } }
        assertThat(sent).isEqualTo(0)
        assertThat(debug.records).isEmpty()
        assertThat(release.records).isEmpty()
    }

    // --- the rest of the policy ---------------------------------------------------------------------------

    @Test
    fun `a schedule is refused for features that are never scheduled`() = runTest {
        val env = FakeAiEnvironment(strict = false, optIns = AiFeature.entries.toSet())
        val gw = gateway(env, reply("""{"a":1}"""))
        listOf(AiFeature.PHOTO_ESTIMATE, AiFeature.RECIPE_HUNTER, AiFeature.PLAN_B, AiFeature.RECIPE_TRY_AI).forEach { f ->
            assertThrows(AiError.NotAllowed::class.java) { kotlinx.coroutines.runBlocking { gw.ask(f, AiTrigger.OPT_IN_SCHEDULE) } }
        }
        assertThat(sent).isEqualTo(0)
    }

    @Test
    fun `a scheduled call needs the feature's own switch to be on`() = runTest {
        val env = FakeAiEnvironment()
        val gw = gateway(env, reply("""{"a":1}"""))
        assertThrows(AiError.NotAllowed::class.java) {
            kotlinx.coroutines.runBlocking { gw.ask(AiFeature.DAILY_INSIGHT, AiTrigger.OPT_IN_SCHEDULE) }
        }
        assertThat(sent).isEqualTo(0)

        env.optIns = setOf(AiFeature.DAILY_INSIGHT)
        gw.ask(AiFeature.DAILY_INSIGHT, AiTrigger.OPT_IN_SCHEDULE)
        assertThat(sent).isEqualTo(1)
        assertThat(env.records.single().trigger).isEqualTo(AiTrigger.OPT_IN_SCHEDULE)
    }

    @Test
    fun `without a key nothing is sent`() = runTest {
        val env = FakeAiEnvironment(key = null)
        val gw = gateway(env, reply("""{"a":1}"""))
        assertThrows(AiError.NoKey::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        assertThat(sent).isEqualTo(0)
        assertThat(gw.isConfigured()).isFalse()
    }

    // --- usage recording ----------------------------------------------------------------------------------

    @Test
    fun `a successful call is logged with the cost OpenRouter reported`() = runTest {
        val env = FakeAiEnvironment()
        val gw = gateway(env, reply("""{"a":1}"""))
        val out = gw.ask(AiFeature.RECIPE_HUNTER)
        assertThat(out.value.a).isEqualTo(1)
        val r = env.records.single()
        assertThat(r.feature).isEqualTo(AiFeature.RECIPE_HUNTER)
        assertThat(r.trigger).isEqualTo(AiTrigger.USER_ACTION)
        assertThat(r.model).isEqualTo("test/model")
        assertThat(r.promptTokens).isEqualTo(1200)
        assertThat(r.completionTokens).isEqualTo(300)
        assertThat(r.costUsd).isEqualTo(0.0042)
        assertThat(r.costReported).isTrue()
        assertThat(r.success).isTrue()
        assertThat(r.at).isEqualTo(now)
    }

    @Test
    fun `a missing cost is worked out from catalog prices and marked estimated`() = runTest {
        val model = ModelInfo(
            id = "test/model", name = "T", description = "", contextLength = 1_000_000,
            promptPerMillion = 1.0, completionPerMillion = 2.0, perImage = 0.0, perRequest = 0.0,
            acceptsImages = false, acceptsFiles = false, outputsText = true, structuredOutputs = true, tools = false,
        )
        val env = FakeAiEnvironment(models = mapOf(model.id to model))
        val gw = gateway(env, reply("""{"a":1}""", cost = null))
        gw.ask(AiFeature.DAILY_INSIGHT)
        val r = env.records.single()
        assertThat(r.costReported).isFalse()
        assertThat(r.costUsd).isEqualTo(0.0018) // 1,200 x $1/M + 300 x $2/M
    }

    @Test
    fun `an unparseable first reply is retried and both requests are logged`() = runTest {
        val env = FakeAiEnvironment()
        val gw = gateway(env, reply("not json at all"), reply("""{"a":2}"""))
        val out = gw.ask()
        assertThat(out.value.a).isEqualTo(2)
        assertThat(sent).isEqualTo(2)
        assertThat(env.records.map { it.success }).containsExactly(false, true).inOrder()
        assertThat(out.response.costUsd).isWithin(1e-9).of(0.0084)
    }

    @Test
    fun `a network failure is logged as a failed call with no cost`() = runTest {
        val env = FakeAiEnvironment()
        val gw = gateway(env, fail = true)
        assertThrows(AiError.Network::class.java) { kotlinx.coroutines.runBlocking { gw.ask() } }
        val r = env.records.single()
        assertThat(r.success).isFalse()
        assertThat(r.costUsd).isEqualTo(0.0)
        assertThat(r.promptTokens).isNull()
    }

    @Test
    fun `a failing usage log never breaks the feature`() = runTest {
        val env = FakeAiEnvironment().apply { failRecording = true }
        val gw = gateway(env, reply("""{"a":3}"""))
        assertThat(gw.ask().value.a).isEqualTo(3)
    }

    @Test
    fun `the log holds no prompt, reply or key`() = runTest {
        val env = FakeAiEnvironment(key = "sk-or-v1-SECRET-KEY-123")
        val gw = gateway(env, reply("""{"a":1}"""))
        gw.ask()
        val text = env.records.toString()
        assertThat(text).doesNotContain("SECRET")
        assertThat(text).doesNotContain("PROMPT-TEXT-XYZ")
        assertThat(text).doesNotContain("\"a\"")
        assertThat(AiUsageRecord::class.java.declaredFields.map { it.name })
            .containsNoneOf("prompt", "content", "reply", "key", "apiKey")
    }

    @Test
    fun `the benchmark goes through the same guard and log`() = runTest {
        val env = FakeAiEnvironment(enabled = false)
        val gw = gateway(env, reply("""{"a":1}"""))
        val request = AiRequest("some/model", listOf(AiPart.Text("x")))
        assertThrows(AiError.Off::class.java) {
            kotlinx.coroutines.runBlocking { gw.complete(AiFeature.BENCHMARK, AiTrigger.USER_ACTION, request) }
        }
        env.enabled = true
        gw.complete(AiFeature.BENCHMARK, AiTrigger.USER_ACTION, request)
        assertThat(env.records.single().feature).isEqualTo(AiFeature.BENCHMARK)
    }
}
