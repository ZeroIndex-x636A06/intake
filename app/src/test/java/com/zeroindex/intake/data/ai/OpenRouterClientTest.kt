package com.zeroindex.intake.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertThrows
import org.junit.Test

/** No network and no key: an interceptor stands in for OpenRouter. */
class OpenRouterClientTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var lastRequest: Request? = null

    private fun client(code: Int = 200, body: String): OpenRouterClient {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            lastRequest = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("x")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return OpenRouterClient(http, json, baseUrl = "https://test.invalid/api/v1", pause = {})
    }

    private val okBody = """
        {"model":"openai/gpt-6-luna","choices":[{"message":{"role":"assistant","content":"{\"a\":1}"}}],
         "usage":{"prompt_tokens":10,"completion_tokens":5,"cost":0.00042}}
    """.trimIndent()

    private fun sentJson(): JsonObject {
        val buffer = Buffer()
        lastRequest!!.body!!.writeTo(buffer)
        return json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private val schema = buildJsonObject { put("type", "object") }

    @Test
    fun `default privacy denies data collection without demanding zero retention`() {
        val p = client(body = okBody).buildPayload(AiRequest("m", listOf(AiPart.Text("hi"))))
        val provider = p["provider"]!!.jsonObject
        assertThat(provider["data_collection"]!!.jsonPrimitive.content).isEqualTo("deny")
        assertThat(provider["zdr"]).isNull()
    }

    @Test
    fun `zero retention sets both flags and any sets neither`() {
        val c = client(body = okBody)
        val zdr = c.buildPayload(AiRequest("m", listOf(AiPart.Text("hi")), privacy = AiPrivacy.ZERO_RETENTION))
        assertThat(zdr["provider"]!!.jsonObject["zdr"]!!.jsonPrimitive.boolean).isTrue()
        val any = c.buildPayload(AiRequest("m", listOf(AiPart.Text("hi")), privacy = AiPrivacy.ANY))
        assertThat(any["provider"]!!.jsonObject.keys).isEmpty()
    }

    @Test
    fun `a schema is strict and only routes to providers that honour it`() {
        val p = client(body = okBody).buildPayload(AiRequest("m", listOf(AiPart.Text("hi")), schema = schema, schemaName = "s"))
        val format = p["response_format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        assertThat(format["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(p["provider"]!!.jsonObject["require_parameters"]!!.jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun `images go as data uris and server tools use openrouter types`() {
        val p = client(body = okBody).buildPayload(
            AiRequest(
                "m",
                listOf(AiPart.Image(byteArrayOf(1, 2, 3))),
                tools = listOf(ServerTool.WebSearch(maxResults = 4), ServerTool.WebFetch()),
            ),
        )
        val part = p["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray.first().jsonObject
        assertThat(part["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content).startsWith("data:image/jpeg;base64,")
        val tools = p["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertThat(tools).containsExactly("openrouter:web_search", "openrouter:web_fetch").inOrder()
    }

    @Test
    fun `the key travels only in the authorization header`() = runTest {
        val response = client(body = okBody).complete("sk-or-secret", AiRequest("m", listOf(AiPart.Text("hi"))))
        val request = lastRequest!!
        assertThat(request.header("Authorization")).isEqualTo("Bearer sk-or-secret")
        assertThat(request.url.toString()).doesNotContain("sk-or-secret")
        assertThat(sentJson().toString()).doesNotContain("sk-or-secret")
        assertThat(response.content).isEqualTo("""{"a":1}""")
        assertThat(response.costUsd).isEqualTo(0.00042)
        assertThat(response.model).isEqualTo("openai/gpt-6-luna")
    }

    @Test
    fun `http failures map to messages fit for the user`() = runTest {
        suspend fun failWith(code: Int, message: String = "x"): AiError = runCatching {
            client(code, """{"error":{"code":$code,"message":"$message"}}""")
                .complete("sk-or-distinctive-9f3", AiRequest("m", listOf(AiPart.Text("hi"))))
        }.exceptionOrNull() as AiError

        assertThat(failWith(401)).isEqualTo(AiError.Unauthorized)
        assertThat(failWith(402)).isEqualTo(AiError.InsufficientCredits)
        assertThat(failWith(429)).isEqualTo(AiError.RateLimited())
        assertThat(failWith(404, "No endpoints found matching your data policy")).isEqualTo(AiError.NoEndpointForPrivacy)
        assertThat(failWith(503)).isInstanceOf(AiError.Server::class.java)
        // Error messages never echo the key back.
        listOf(401, 402, 429, 503).forEach { assertThat(failWith(it).message).doesNotContain("9f3") }
    }

    @Test
    fun `an error object inside a 200 is still an error`() {
        val c = client(200, """{"error":{"code":402,"message":"Insufficient credits"}}""")
        assertThrows(AiError.InsufficientCredits::class.java) {
            kotlinx.coroutines.runBlocking { c.complete("k", AiRequest("m", listOf(AiPart.Text("hi")))) }
        }
    }

    @Test
    fun `json is recovered from fenced or chatty replies`() {
        assertThat(AiGateway.extractJson("```json\n{\"a\":1}\n```")).isEqualTo("""{"a":1}""")
        assertThat(AiGateway.extractJson("Sure! {\"a\":{\"b\":2}} hope that helps")).isEqualTo("""{"a":{"b":2}}""")
        assertThat(AiGateway.extractJson("no json here")).isNull()
    }
}
