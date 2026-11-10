package com.zeroindex.intake.data.ai

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.ai.vision.VisionEstimate
import com.zeroindex.intake.data.ai.vision.VisionSchema
import com.zeroindex.intake.domain.ai.AiFailureKind
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTimeout
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.cost.UsageSummarizer
import com.zeroindex.intake.domain.time.Clock
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
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
 * B1: timeouts per feature, strict versus JSON-mode schemas, the one repair retry, Retry-After backoff, a Cancel
 * that cancels the OkHttp call, and the failure kind on every logged row. No network and no key: an interceptor
 * plays OpenRouter.
 */
class AiReliabilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Reply(val a: Int)

    private val now = LocalDateTime.of(2026, 11, 10, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val clock = object : Clock {
        override fun nowMillis() = now
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    private fun ok(content: String, cost: String = "0.001") = """
        {"model":"test/model","choices":[{"message":{"role":"assistant","content":${json.encodeToString(String.serializer(), content)}}}],
         "usage":{"prompt_tokens":100,"completion_tokens":50,"cost":$cost}}
    """.trimIndent()

    private class Wire(val code: Int = 200, val body: String, val headers: Map<String, String> = emptyMap())

    private val sentBodies = mutableListOf<JsonObject>()
    private val readTimeouts = mutableListOf<Int>()
    private val pauses = mutableListOf<Long>()

    private fun gateway(
        env: FakeAiEnvironment,
        vararg wire: Wire,
        onCall: (Interceptor.Chain) -> Unit = {},
        throwing: (() -> IOException)? = null,
    ): AiGateway {
        var i = 0
        val http = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                onCall(chain)
                readTimeouts += chain.readTimeoutMillis()
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                sentBodies += json.parseToJsonElement(buffer.readUtf8()).jsonObject
                throwing?.let { throw it() }
                val w = wire[minOf(i++, wire.lastIndex)]
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(w.code).message("x")
                    .apply { w.headers.forEach { (k, v) -> header(k, v) } }
                    .body(w.body.toResponseBody("application/json".toMediaType())).build()
            },
        ).build()
        val client = OpenRouterClient(
            http, json, "https://test.invalid/api/v1",
            pause = { pauses += it }, jitter = { 0.5 }, nowMillis = { now },
        )
        return AiGateway(client, env, json, clock)
    }

    private val schema = buildJsonObject { put("type", "object") }

    private suspend fun AiGateway.ask(feature: AiFeature = AiFeature.DAILY_INSIGHT, parts: List<AiPart> = listOf(AiPart.Text("hi"))) =
        structured(feature, AiTrigger.USER_ACTION, Reply.serializer(), schema, "sys", parts)

    private fun userText(body: JsonObject): String =
        body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.joinToString("\n") {
            it.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
        }

    // --- timeouts -------------------------------------------------------------------------------------

    @Test
    fun `vision and web search features are patient and text features fail fast`() {
        assertThat(AiFeature.PHOTO_ESTIMATE.timeout).isEqualTo(AiTimeout.LONG)
        assertThat(AiFeature.RECIPE_HUNTER.timeout).isEqualTo(AiTimeout.LONG)
        assertThat(AiFeature.PLAN_B.timeout).isEqualTo(AiTimeout.LONG)
        assertThat(AiFeature.MENU_IMPORT.timeout).isEqualTo(AiTimeout.LONG)
        assertThat(AiFeature.DAILY_INSIGHT.timeout).isEqualTo(AiTimeout.TEXT)
        assertThat(AiFeature.COACH_REWORD.timeout).isEqualTo(AiTimeout.TEXT)
        assertThat(AiTimeout.LONG.readSeconds).isAtLeast(90)
        assertThat(AiTimeout.LONG.readSeconds).isAtMost(120)
        assertThat(AiTimeout.TEXT.readSeconds).isEqualTo(45)
    }

    @Test
    fun `each call uses its feature's read timeout on the wire`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = ok("""{"a":1}""")))
        gw.ask(AiFeature.DAILY_INSIGHT)
        gw.ask(AiFeature.PLAN_B)
        gw.ask(AiFeature.PHOTO_ESTIMATE, parts = listOf(AiPart.Image(byteArrayOf(1, 2, 3))))
        assertThat(readTimeouts).containsExactly(45_000, 120_000, 120_000).inOrder()
    }

    @Test
    fun `an image, a pdf or a web tool raises a short timeout to the long one`() {
        val text = AiRequest("m", listOf(AiPart.Text("x")), timeout = AiTimeout.TEXT)
        assertThat(text.effectiveTimeout()).isEqualTo(AiTimeout.TEXT)
        assertThat(text.copy(parts = listOf(AiPart.Pdf(byteArrayOf(1), "a.pdf"))).effectiveTimeout()).isEqualTo(AiTimeout.LONG)
        assertThat(text.copy(tools = listOf(ServerTool.WebSearch())).effectiveTimeout()).isEqualTo(AiTimeout.LONG)
    }

    @Test
    fun `a timeout is reported plainly, logged as a timeout and not retried`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = "{}"), throwing = { SocketTimeoutException("timeout") })
        val e = assertThrows(AiError.Timeout::class.java) { runBlocking { gw.ask() } }
        assertThat(e.message).contains("taking too long")
        assertThat(sentBodies).hasSize(1)
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.TIMEOUT)
        assertThat(pauses).isEmpty()
    }

    // --- structured output ----------------------------------------------------------------------------

    private fun env(strict: Boolean, jsonMode: Boolean = true) = FakeAiEnvironment(
        models = mapOf(
            "test/plan_b" to ModelInfo(
                id = "test/plan_b", name = "n", description = "", contextLength = 100_000, promptPerMillion = 1.0,
                completionPerMillion = 2.0, perImage = 0.0, perRequest = 0.0, acceptsImages = false, acceptsFiles = false,
                outputsText = true, structuredOutputs = strict, tools = true, responseFormat = jsonMode,
            ),
        ),
    )

    @Test
    fun `a model with strict structured output is held to the schema`() = runBlocking<Unit> {
        val gw = gateway(env(strict = true), Wire(body = ok("""{"a":1}""")))
        gw.ask(AiFeature.PLAN_B)
        val format = sentBodies.single()["response_format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        assertThat(format["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `a model with only json mode gets json mode and the schema in the prompt`() = runBlocking<Unit> {
        val gw = gateway(env(strict = false), Wire(body = ok("""{"a":1}""")))
        gw.ask(AiFeature.PLAN_B)
        val body = sentBodies.single()
        assertThat(body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("json_object")
        val system = body["messages"]!!.jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
        assertThat(system).contains("JSON Schema")
        assertThat(system).contains("\"type\"")
    }

    // --- validate, repair, one retry ------------------------------------------------------------------

    @Test
    fun `fenced json and a trailing comma are accepted without a retry`() = runBlocking<Unit> {
        val gw = gateway(FakeAiEnvironment(), Wire(body = ok("```json\n{\"a\":4,}\n```")))
        assertThat(gw.ask().value.a).isEqualTo(4)
        assertThat(sentBodies).hasSize(1)
    }

    @Test
    fun `a reply that does not fit gets one repair retry that quotes the error and the previous reply`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = ok("""{"a":"not a number"}""")), Wire(body = ok("""{"a":2}""")))
        val out = gw.ask()
        assertThat(out.value.a).isEqualTo(2)
        assertThat(sentBodies).hasSize(2)
        val repair = userText(sentBodies[1])
        assertThat(repair).contains("Your previous reply did not match the schema:")
        assertThat(repair).contains("Reply with JSON only.")
        assertThat(repair).contains("not a number")
        // The first attempt was logged as an unreadable answer; the repair as a success.
        assertThat(env.records.map { it.failure }).containsExactly(AiFailureKind.INVALID_OUTPUT, null).inOrder()
        assertThat(env.records.map { it.success }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `two bad replies end in a plain error that never contains the model's text`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = ok("RAW-MODEL-TEXT-ONE")), Wire(body = ok("RAW-MODEL-TEXT-TWO")))
        val e = assertThrows(AiError.BadResponse::class.java) { runBlocking { gw.ask() } }
        assertThat(sentBodies).hasSize(2) // one try and exactly one repair
        assertThat(e.message).doesNotContain("RAW-MODEL-TEXT")
        assertThat(e.message).contains("could not be understood")
        assertThat(env.records.map { it.failure }).containsExactly(AiFailureKind.INVALID_OUTPUT, AiFailureKind.INVALID_OUTPUT)
    }

    @Test
    fun `the repair retry still goes through the budget guard`() = runBlocking<Unit> {
        // The first reply is unusable and uses up the whole budget, so the repair must be refused, not sent.
        val env = FakeAiEnvironment(budgetUsd = 0.004)
        val gw = gateway(env, Wire(body = ok("garbage", cost = "0.0042")), Wire(body = ok("""{"a":1}""")))
        assertThrows(AiError.BudgetReached::class.java) { runBlocking { gw.ask() } }
        assertThat(sentBodies).hasSize(1)
    }

    @Test
    fun `the repair retry counts toward spend`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = ok("garbage", cost = "0.002")), Wire(body = ok("""{"a":1}""", cost = "0.003")))
        val out = gw.ask()
        assertThat(env.records.sumOf { it.costUsd }).isWithin(1e-9).of(0.005)
        assertThat(out.response.costUsd).isWithin(1e-9).of(0.005)
    }

    @Test
    fun `the repair does not repeat a web search`() = runBlocking<Unit> {
        val gw = gateway(FakeAiEnvironment(), Wire(body = ok("garbage")), Wire(body = ok("""{"a":1}""")))
        gw.structured(
            AiFeature.PLAN_B, AiTrigger.USER_ACTION, Reply.serializer(), schema, "sys", listOf(AiPart.Text("x")),
            tools = listOf(ServerTool.WebSearch()),
        )
        assertThat(sentBodies[0].containsKey("tools")).isTrue()
        assertThat(sentBodies[1].containsKey("tools")).isFalse()
    }

    // --- rate limits and server errors ----------------------------------------------------------------

    @Test
    fun `a 429 with retry-after waits that long and then succeeds`() = runBlocking<Unit> {
        val gw = gateway(
            FakeAiEnvironment(),
            Wire(429, """{"error":{"code":429,"message":"slow down"}}""", mapOf("Retry-After" to "3")),
            Wire(body = ok("""{"a":1}""")),
        )
        assertThat(gw.ask().value.a).isEqualTo(1)
        assertThat(sentBodies).hasSize(2)
        assertThat(pauses.single()).isAtLeast(3_000)
        assertThat(pauses.single()).isAtMost(3_250)
    }

    @Test
    fun `a 503 backs off exponentially and gives up after three tries with a rate limit or server error`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(503, """{"error":{"code":503,"message":"busy"}}"""))
        assertThrows(AiError.Server::class.java) { runBlocking { gw.ask() } }
        assertThat(sentBodies).hasSize(RetryPolicyAttempts)
        assertThat(pauses).containsExactly(1_000L, 2_000L).inOrder()
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.SERVER)
    }

    @Test
    fun `a rate limit that persists is logged as rate limited`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(429, """{"error":{"code":429,"message":"x"}}"""))
        val e = assertThrows(AiError.RateLimited::class.java) { runBlocking { gw.ask() } }
        assertThat(e.message).contains("Too many requests")
        assertThat(sentBodies).hasSize(RetryPolicyAttempts)
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.RATE_LIMITED)
    }

    @Test
    fun `a retry-after longer than the cap is not waited out`() = runBlocking<Unit> {
        val gw = gateway(
            FakeAiEnvironment(),
            Wire(429, """{"error":{"code":429,"message":"x"}}""", mapOf("Retry-After" to "600")),
        )
        val e = assertThrows(AiError.RateLimited::class.java) { runBlocking { gw.ask() } }
        assertThat(e.retryAfterMs).isEqualTo(600_000L)
        assertThat(sentBodies).hasSize(1)
        assertThat(pauses).isEmpty()
    }

    @Test
    fun `a refusal is not retried and is logged as a refusal`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(403, """{"error":{"code":403,"message":"flagged"}}"""))
        assertThrows(AiError.Refused::class.java) { runBlocking { gw.ask() } }
        assertThat(sentBodies).hasSize(1)
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.REFUSED)
    }

    @Test
    fun `a missing provider for the privacy setting is logged as a privacy failure`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(404, """{"error":{"code":404,"message":"No endpoints found matching your data policy"}}"""))
        assertThrows(AiError.NoEndpointForPrivacy::class.java) { runBlocking { gw.ask() } }
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.PRIVACY)
    }

    @Test
    fun `going offline is logged as no connection`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = "{}"), throwing = { IOException("offline") })
        assertThrows(AiError.Network::class.java) { runBlocking { gw.ask() } }
        assertThat(env.records.single().failure).isEqualTo(AiFailureKind.NETWORK)
    }

    // --- cancel ---------------------------------------------------------------------------------------

    @Test
    fun `cancelling the coroutine cancels the OkHttp call and logs a cancelled attempt`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val started = CountDownLatch(1)
        val sawCancel = CountDownLatch(1)
        val gw = gateway(
            env, Wire(body = ok("""{"a":1}""")),
            onCall = { chain ->
                started.countDown()
                // Hold the request open until the call is cancelled, as a slow model would.
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!chain.call().isCanceled() && System.nanoTime() < deadline) Thread.sleep(5)
                if (chain.call().isCanceled()) sawCancel.countDown()
                throw IOException("Canceled")
            },
        )
        val job = launch(kotlinx.coroutines.Dispatchers.Default) { gw.ask() }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        job.cancel()
        job.join()
        assertThat(sawCancel.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(job.isCancelled).isTrue()
        assertThat(env.records.map { it.failure }).containsExactly(AiFailureKind.CANCELLED)
    }

    // --- the photo schema returns a list --------------------------------------------------------------

    private val threeFoods = """
        {"items":[
          {"name":"grilled chicken","estimatedGrams":150,"gramsLow":120,"gramsHigh":180,"kcal":250,"protein":46,"carbs":0,"fat":5,
           "confidence":"high","containsAllergens":[],"allergenReasoning":""},
          {"name":"rice","estimatedGrams":180,"gramsLow":150,"gramsHigh":220,"kcal":234,"protein":4,"carbs":50,"fat":1,
           "confidence":"medium","containsAllergens":[],"allergenReasoning":""},
          {"name":"creamy sauce","estimatedGrams":40,"gramsLow":null,"gramsHigh":null,"kcal":90,"protein":1,"carbs":3,"fat":8,
           "confidence":"low","containsAllergens":["dairy"],"allergenReasoning":"cream"}
        ],
        "hiddenCalorieSources":[{"source":"1 tbsp butter in the pan","kcal":100}],
        "range":{"kcalLow":500,"kcalHigh":700,"proteinLow":45,"proteinHigh":55,"carbsLow":45,"carbsHigh":60,"fatLow":10,"fatHigh":20},
        "portionAssumption":"a 10 inch plate","overallConfidence":"medium","notFood":false}
    """.trimIndent()

    @Test
    fun `one photo decodes into a list of foods with ranges, confidence and allergen notes, and is logged once`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val gw = gateway(env, Wire(body = ok(threeFoods)))
        val out = gw.structured(
            AiFeature.PHOTO_ESTIMATE, AiTrigger.USER_ACTION, VisionEstimate.serializer(), VisionSchema.schema,
            VisionSchema.systemPrompt(listOf("dairy")),
            listOf(AiPart.Image(byteArrayOf(9, 9)), AiPart.Text("Estimate this meal."), AiPart.Text("Note from the user about this photo: cooked in butter")),
        )
        val items = out.value.items
        assertThat(items.map { it.name }).containsExactly("grilled chicken", "rice", "creamy sauce").inOrder()
        assertThat(items[0].gramsLow).isEqualTo(120.0)
        assertThat(items[0].gramsHigh).isEqualTo(180.0)
        assertThat(items[2].gramsLow).isNull()
        assertThat(items[2].containsAllergens).containsExactly("dairy")
        assertThat(items.map { it.confidence }).containsExactly("high", "medium", "low").inOrder()

        val body = sentBodies.single()
        val content = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
        assertThat(content.map { it.jsonObject["type"]!!.jsonPrimitive.content }).containsExactly("image_url", "text", "text").inOrder()
        assertThat(userText(body)).contains("cooked in butter")
        assertThat(readTimeouts.single()).isEqualTo(120_000)
        assertThat(env.records.single().feature).isEqualTo(AiFeature.PHOTO_ESTIMATE)
        assertThat(env.records.single().success).isTrue()
    }

    @Test
    fun `the photo schema asks for a list and a range and every property is required`() {
        val props = VisionSchema.schema["properties"]!!.jsonObject
        val item = props["items"]!!.jsonObject["items"]!!.jsonObject
        val itemProps = item["properties"]!!.jsonObject.keys
        assertThat(itemProps).containsAtLeast("name", "estimatedGrams", "gramsLow", "gramsHigh", "kcal", "protein", "carbs", "fat", "confidence", "containsAllergens")
        assertThat(item["required"]!!.jsonArray.map { it.jsonPrimitive.content }).containsExactlyElementsIn(itemProps)
        assertThat(VisionSchema.systemPrompt(emptyList())).contains("one entry in items for every distinct food")
    }

    // --- the usage meter shows why calls fail ---------------------------------------------------------

    @Test
    fun `the usage meter groups this month's failures by feature and reason`() = runBlocking<Unit> {
        val env = FakeAiEnvironment()
        val timeout = gateway(env, Wire(body = "{}"), throwing = { SocketTimeoutException("t") })
        repeat(2) { assertThrows(AiError.Timeout::class.java) { runBlocking { timeout.ask(AiFeature.PHOTO_ESTIMATE) } } }
        val refused = gateway(env, Wire(403, """{"error":{"code":403,"message":"x"}}"""))
        assertThrows(AiError.Refused::class.java) { runBlocking { refused.ask(AiFeature.PLAN_B) } }

        val rows = UsageSummarizer.failures(env.records, now, ZoneOffset.UTC)
        assertThat(rows.map { Triple(it.feature, it.kind, it.count) }).containsExactly(
            Triple(AiFeature.PHOTO_ESTIMATE, AiFailureKind.TIMEOUT, 2),
            Triple(AiFeature.PLAN_B, AiFailureKind.REFUSED, 1),
        ).inOrder()
        // "Reset counters" moves the line: nothing before it is listed.
        assertThat(UsageSummarizer.failures(env.records, now, ZoneOffset.UTC, resetAt = now + 1)).isEmpty()
    }

    private companion object {
        const val RetryPolicyAttempts = com.zeroindex.intake.domain.ai.RetryPolicy.MAX_ATTEMPTS
    }
}
