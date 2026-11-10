package com.zeroindex.intake.data.ai.benchmark

import com.google.common.truth.Truth.assertThat
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPrivacy
import com.zeroindex.intake.data.ai.FakeAiEnvironment
import com.zeroindex.intake.data.ai.OpenRouterClient
import com.zeroindex.intake.data.db.dao.AiInsightDao
import com.zeroindex.intake.data.db.entity.AiInsightEntity
import com.zeroindex.intake.data.db.entity.ModelBenchmarkEntity
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.BenchmarkScore
import com.zeroindex.intake.domain.ai.ModelInfo
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkScoring
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkSuite
import com.zeroindex.intake.domain.ai.benchmark.InsightFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.MenuFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.PlanBFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.RecipeFixtureSet
import com.zeroindex.intake.domain.time.Clock
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The runner end to end with no network: an interceptor plays OpenRouter, and
 * the fixtures are the real bundled files, so a "perfect" model here is one
 * that reads each fixture's published answer back.
 */
class BenchmarkRunnerTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true; explicitNulls = false }
    private val key = "sk-or-v1-SECRET-0123456789"

    private val fixtures = FixtureReader { File("src/main/assets/benchmark/$it").readText() }
    private val menus = json.decodeFromString(MenuFixtureSet.serializer(), fixtures.read("menu-ingestion.json")).fixtures
    private val recipes = json.decodeFromString(RecipeFixtureSet.serializer(), fixtures.read("recipe-hunter.json")).fixtures
    private val planBs = json.decodeFromString(PlanBFixtureSet.serializer(), fixtures.read("plan-b.json")).fixtures
    private val insights = json.decodeFromString(InsightFixtureSet.serializer(), fixtures.read("daily-insight.json")).fixtures

    private fun model(id: String, prompt: Double = 1.0, completion: Double = 2.0) = ModelInfo(
        id = id, name = id, description = "", contextLength = 1_000_000,
        promptPerMillion = prompt, completionPerMillion = completion, perImage = 0.0, perRequest = 0.0,
        acceptsImages = false, acceptsFiles = false, outputsText = true, structuredOutputs = true, tools = true,
    )

    private class FakeDao : AiInsightDao {
        val saved = mutableListOf<ModelBenchmarkEntity>()
        override suspend fun upsertBenchmarks(results: List<ModelBenchmarkEntity>) {
            saved.removeAll { old -> results.any { it.id == old.id } }
            saved += results
        }
        override suspend fun upsert(insight: AiInsightEntity) = error("unused")
        override suspend fun upsertAll(insights: List<AiInsightEntity>) = error("unused")
        override fun observeForDay(dayStart: Long): Flow<AiInsightEntity?> = emptyFlow()
        override suspend fun forDay(dayStart: Long): AiInsightEntity? = null
        override suspend fun all(): List<AiInsightEntity> = emptyList()
        override suspend fun dismiss(id: String, now: Long) = error("unused")
        override fun observeBenchmarks(): Flow<List<ModelBenchmarkEntity>> = emptyFlow()
        override suspend fun allBenchmarks(): List<ModelBenchmarkEntity> = saved
    }

    private val clock = object : Clock {
        override fun nowMillis() = 1_700_000_000_000L
        override fun zone(): ZoneId = ZoneOffset.UTC
    }

    /** What the fake OpenRouter sends back for one request. */
    private class Reply(val content: String = "", val cost: Double? = 0.001, val code: Int = 200, val body: String? = null)

    private class Seen(val request: Request, val body: JsonObject) {
        val model: String get() = body["model"]!!.jsonPrimitive.content
        val user: String
            get() = body["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
    }

    private val seen = mutableListOf<Seen>()

    private fun runner(dao: AiInsightDao, respond: (Seen) -> Reply): BenchmarkRunner {
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val buffer = Buffer()
            chain.request().body!!.writeTo(buffer)
            val call = Seen(chain.request(), json.parseToJsonElement(buffer.readUtf8()).jsonObject)
            seen += call
            val reply = respond(call)
            val body = reply.body ?: buildJsonObject {
                put("model", call.model)
                put(
                    "choices",
                    buildJsonArray { add(buildJsonObject { put("message", buildJsonObject { put("content", reply.content) }) }) },
                )
                put("usage", buildJsonObject { put("cost", reply.cost?.let(::JsonPrimitive) ?: JsonNull) })
            }.toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(reply.code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val client = OpenRouterClient(http, json, baseUrl = "https://test.invalid/api/v1", pause = {})
        return BenchmarkRunner(AiGateway(client, FakeAiEnvironment(key), json, clock), BenchmarkCases(fixtures, json), dao, clock)
    }

    // --- Replies a model could give, built from the fixtures' published answers -------------------

    private fun obj(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    private fun str(s: String) = JsonPrimitive(s)
    private fun num(n: Double) = JsonPrimitive(n)
    private fun strings(vararg s: String) = JsonArray(s.map(::str))

    private fun menuReply(seen: Seen, flagDairy: Boolean): String {
        val fixture = menus.first { seen.user == it.text }
        return obj(
            "items" to JsonArray(
                fixture.items.map {
                    obj(
                        "name" to str(it.name), "description" to str(""), "ingredients" to str(""),
                        "kcal" to num(it.kcal), "protein" to num(it.proteinG), "carbs" to JsonNull, "fat" to JsonNull,
                        "macrosPublished" to JsonPrimitive(true), "priceCents" to JsonNull,
                        "containsAllergens" to if (it.dairy && flagDairy) strings("dairy") else strings(),
                        "allergenNotes" to str(""),
                    )
                },
            ),
        ).toString()
    }

    private fun recipeReply(seen: Seen): String {
        val fixture = recipes.first { seen.user.endsWith(it.pageText) }
        return obj(
            "recipes" to JsonArray(
                listOf(
                    obj(
                        "title" to str("Recipe"), "sourceUrl" to str("https://recipes.example.invalid/x"), "sourceName" to str("x"),
                        "servings" to JsonPrimitive(fixture.servings), "totalMinutes" to JsonNull,
                        "ingredients" to strings(), "steps" to strings(),
                        "kcalPerServing" to num(fixture.kcalPerServing), "proteinPerServing" to num(fixture.proteinPerServing),
                        "carbsPerServing" to num(0.0), "fatPerServing" to num(0.0),
                        "macrosPublished" to JsonPrimitive(true),
                        "containsAllergens" to if (fixture.dairy) strings("dairy") else strings(),
                        "allergenReasoning" to str(""),
                    ),
                ),
            ),
        ).toString()
    }

    private fun planBReply(seen: Seen): String {
        val fixture = planBs.first { seen.user.endsWith(it.menuText) }
        return obj(
            "options" to JsonArray(
                fixture.items.filter { it.name in fixture.acceptable }.map {
                    obj(
                        "restaurant" to str("r"), "address" to str(""), "distanceMiles" to JsonNull, "order" to str(it.name),
                        "modifications" to str(""), "kcal" to num(it.kcal), "protein" to num(it.proteinG),
                        "carbs" to num(0.0), "fat" to num(0.0), "containsAllergens" to strings(), "allergenNotes" to str(""),
                        "sayThis" to str(""), "crossContactRisk" to str("low"), "sourceUrl" to str(""),
                    )
                },
            ),
        ).toString()
    }

    private fun insightReply(seen: Seen): String {
        val fixture = insights.first { seen.user == "The last two weeks:\n" + it.digest.joinToString("\n") }
        return obj(
            "insight" to str(fixture.digest.first()),
            "focus" to str("consistency"),
            "basedOn" to strings(fixture.digest.first()),
        ).toString()
    }

    private fun perfect(seen: Seen, flagDairy: Boolean = true, cost: Double? = 0.001): Reply = Reply(
        content = when {
            "Extract the one recipe" in seen.user -> recipeReply(seen)
            seen.user.startsWith("Web search is not available for this task. Choose only") -> planBReply(seen)
            seen.user.startsWith("The last two weeks:") -> insightReply(seen)
            else -> menuReply(seen, flagDairy)
        },
        cost = cost,
    )

    private fun selection(task: AiTask, vararg ids: String) = BenchmarkSelection(task, ids.map { model(it) })

    private suspend fun BenchmarkRunner.runWith(
        selections: List<BenchmarkSelection>,
        cap: Double = 1.0,
        privacy: AiPrivacy = AiPrivacy.NO_TRAINING,
        progress: MutableList<BenchmarkProgress> = mutableListOf(),
    ) = run(selections, cap, privacy, onProgress = { progress += it }, pacing = BenchmarkPacing.NONE)

    // --- Tests ---------------------------------------------------------------------------------

    @Test
    fun `every task scores full marks for a model that reads the fixtures correctly, and each result is stored`() = runTest {
        val dao = FakeDao()
        val report = runner(dao) { perfect(it) }.runWith(BenchmarkSuite.tasks.map { selection(it, "good/model") })

        assertThat(report.results.map { it::class }).containsExactly(*Array(4) { ModelResult.Saved::class })
        assertThat(dao.saved.map { it.task }).containsExactly(
            "MENU_INGESTION", "RECIPE_HUNTER", "PLAN_B", "DAILY_INSIGHT",
        )
        dao.saved.forEach {
            assertThat(it.modelId).isEqualTo("good/model")
            assertThat(it.fixtures).isEqualTo(5)
            assertThat(it.accuracy).isEqualTo(1.0)
            assertThat(it.jsonValidity).isEqualTo(1.0)
            assertThat(it.costUsdPerRequest).isWithin(1e-12).of(0.001)
            assertThat(it.runAt).isEqualTo(1_700_000_000_000L)
            assertThat(it.medianLatencyMs).isAtLeast(0L)
        }
        assertThat(report.spentUsd).isWithin(1e-9).of(0.020)
        assertThat(report.stoppedByCap).isFalse()
    }

    @Test
    fun `a rerun replaces the stored row for the same task and model`() = runTest {
        val dao = FakeDao()
        val r = runner(dao) { perfect(it) }
        r.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")))
        r.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")))
        assertThat(dao.saved).hasSize(1)
    }

    @Test
    fun `the entity mapping carries the measured numbers`() {
        val score = BenchmarkScore(
            AiTask.PLAN_B, "x/y", accuracy = 0.8, jsonValidity = 0.6, fixtures = 5, medianLatencyMs = 1_500, costUsdPerRequest = 0.004,
        )
        val entity = BenchmarkRunner.toEntity(score, now = 42)
        assertThat(entity.task).isEqualTo("PLAN_B")
        assertThat(entity.modelId).isEqualTo("x/y")
        assertThat(entity.accuracy).isEqualTo(0.8)
        assertThat(entity.jsonValidity).isEqualTo(0.6)
        assertThat(entity.fixtures).isEqualTo(5)
        assertThat(entity.medianLatencyMs).isEqualTo(1_500)
        assertThat(entity.costUsdPerRequest).isEqualTo(0.004)
        assertThat(entity.createdAt).isEqualTo(42)
        assertThat(entity.id).isEqualTo(BenchmarkRunner.toEntity(score, now = 99).id)
    }

    @Test
    fun `the spend cap stops the run and a model it cut short is not stored`() = runTest {
        val dao = FakeDao()
        // Costs are powers of two so the running total is exact: the third request takes spend past the cap.
        val report = runner(dao) { perfect(it, cost = 0.0625) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/one", "a/two")), cap = 0.15)

        assertThat(seen).hasSize(3)
        assertThat(report.stoppedByCap).isTrue()
        assertThat(report.spentUsd).isEqualTo(0.1875)
        assertThat(dao.saved).isEmpty()
        assertThat(report.results).hasSize(1)
        assertThat((report.results.single() as ModelResult.Skipped).modelId).isEqualTo("a/one")
    }

    @Test
    fun `models finished before the cap are kept and later ones never start`() = runTest {
        val dao = FakeDao()
        // Five requests finish "a/one" at exactly the cap; "a/two" must not be asked anything.
        val report = runner(dao) { perfect(it, cost = 0.0625) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/one", "a/two")), cap = 0.3125)

        assertThat(dao.saved.map { it.modelId }).containsExactly("a/one")
        assertThat(seen.map { it.model }.toSet()).containsExactly("a/one")
        assertThat(report.stoppedByCap).isTrue()
    }

    @Test
    fun `a reply with no reported cost is charged at the estimate so it cannot dodge the cap`() = runTest {
        val dao = FakeDao()
        val r = runner(dao) { perfect(it, cost = null) }
        val sel = listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(model("a/b", prompt = 1_000.0, completion = 1_000.0))))
        val report = r.runWith(sel, cap = 0.5)

        assertThat(seen).hasSize(1)
        assertThat(report.stoppedByCap).isTrue()
        assertThat(dao.saved).isEmpty()
    }

    @Test
    fun `a model that never reports a cost is not stored with an invented one`() = runTest {
        val dao = FakeDao()
        val report = runner(dao) { perfect(it, cost = null) }.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")))
        assertThat(dao.saved).isEmpty()
        assertThat((report.results.single() as ModelResult.Skipped).reason).contains("cost")
    }

    @Test
    fun `a missed dairy item fails its fixture`() = runTest {
        val dao = FakeDao()
        val report = runner(dao) { perfect(it, flagDairy = false) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "careless/model")))

        // Every bundled menu has a dairy item, so a model that never raises one fails all five.
        val saved = (report.results.single() as ModelResult.Saved).score
        assertThat(saved.accuracy).isEqualTo(0.0)
        assertThat(dao.saved.single().accuracy).isEqualTo(0.0)
        assertThat(dao.saved.single().jsonValidity).isEqualTo(1.0)
    }

    @Test
    fun `a model that raises dairy where it exists is not penalised`() = runTest {
        val dao = FakeDao()
        runner(dao) { perfect(it, flagDairy = true) }.runWith(listOf(selection(AiTask.MENU_INGESTION, "careful/model")))
        assertThat(dao.saved.single().accuracy).isEqualTo(1.0)
    }

    @Test
    fun `a reply that does not parse counts against json validity and scores nothing`() = runTest {
        val dao = FakeDao()
        var n = 0
        runner(dao) { if (n++ == 0) Reply(content = "Sure! Here is the menu: not json") else perfect(it) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")))
        val row = dao.saved.single()
        assertThat(row.jsonValidity).isWithin(1e-9).of(0.8)
        assertThat(row.accuracy).isWithin(1e-9).of(0.8)
    }

    @Test
    fun `the key goes only in the Authorization header and the privacy setting is applied`() = runTest {
        runner(FakeDao()) { perfect(it) }.runWith(
            listOf(selection(AiTask.DAILY_INSIGHT, "a/b")),
            privacy = AiPrivacy.NO_TRAINING,
        )
        seen.forEach {
            assertThat(it.request.header("Authorization")).isEqualTo("Bearer $key")
            assertThat(it.request.url.toString()).doesNotContain(key)
            assertThat(it.body.toString()).doesNotContain(key)
            assertThat(it.body["provider"]!!.jsonObject["data_collection"]!!.jsonPrimitive.content).isEqualTo("deny")
            assertThat(it.body["response_format"]!!.jsonObject["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content)
                .isEqualTo("true")
            assertThat(it.body.containsKey("tools")).isFalse()
        }
    }

    @Test
    fun `any provider privacy sends no restriction`() = runTest {
        runner(FakeDao()) { perfect(it) }.runWith(listOf(selection(AiTask.DAILY_INSIGHT, "a/b")), privacy = AiPrivacy.ANY)
        assertThat(seen.first().body["provider"]!!.jsonObject.containsKey("data_collection")).isFalse()
    }

    @Test
    fun `the key never appears in a network failure`() = runTest {
        val dao = FakeDao()
        val http = OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException("connect failed, sent Authorization: Bearer $key") }).build()
        val runner = BenchmarkRunner(AiGateway(OpenRouterClient(http, json, baseUrl = "https://test.invalid/api/v1", pause = {}), FakeAiEnvironment(key), json, clock), BenchmarkCases(fixtures, json), dao, clock)
        val report = runner.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")))

        assertThat(report.abortReason).isNotNull()
        assertThat(report.toString()).doesNotContain(key)
        assertThat(dao.saved).isEmpty()
    }

    @Test
    fun `the key never appears when a server echoes it back`() = runTest {
        val echo = """{"error":{"code":500,"message":"bad request for Bearer $key"}}"""
        val dao = FakeDao()
        val report = runner(dao) { Reply(code = 500, body = echo) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b", "c/d")))

        // A server error is the model's problem: both models are tried and both skipped.
        assertThat(report.results).hasSize(2)
        assertThat(report.abortReason).isNull()
        assertThat(report.toString()).doesNotContain(key)
    }

    @Test
    fun `a rejected key ends the whole run with a message that does not contain it`() = runTest {
        val body = """{"error":{"code":401,"message":"Invalid key $key"}}"""
        val report = runner(FakeDao()) { Reply(code = 401, body = body) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b", "c/d")))

        assertThat(seen).hasSize(1)
        assertThat(report.abortReason).contains("rejected the key")
        assertThat(report.toString()).doesNotContain(key)
    }

    @Test
    fun `an invalid cap is refused without echoing the key`() = runTest {
        val e = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                runner(FakeDao()) { perfect(it) }.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/b")), cap = 0.0)
            }
        }
        assertThat(e.message).doesNotContain(key)
    }

    @Test
    fun `a model with no endpoint for the privacy setting is skipped and the run continues`() = runTest {
        val dao = FakeDao()
        val body = """{"error":{"code":404,"message":"No endpoints found matching your data policy"}}"""
        val report = runner(dao) { if (it.model == "a/blocked") Reply(code = 404, body = body) else perfect(it) }
            .runWith(listOf(selection(AiTask.MENU_INGESTION, "a/blocked", "b/fine")))

        assertThat(report.results.map { it.modelId }).containsExactly("a/blocked", "b/fine").inOrder()
        assertThat(report.results[0]).isInstanceOf(ModelResult.Skipped::class.java)
        assertThat(dao.saved.map { it.modelId }).containsExactly("b/fine")
    }

    @Test
    fun `cancelling keeps the models that already finished`() = runTest {
        val dao = FakeDao()
        lateinit var job: Job
        val r = runner(dao) {
            // Seven requests in: the first model is done and the second is mid-way.
            if (seen.size == 7) job.cancel()
            perfect(it)
        }
        job = launch { r.runWith(listOf(selection(AiTask.MENU_INGESTION, "a/one", "a/two"))) }
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(dao.saved.map { it.modelId }).containsExactly("a/one")
    }

    @Test
    fun `progress names the model and counts fixtures`() = runTest {
        val progress = mutableListOf<BenchmarkProgress>()
        runner(FakeDao()) { perfect(it, cost = 0.002) }
            .runWith(listOf(selection(AiTask.PLAN_B, "a/b")), progress = progress)

        assertThat(progress.map { it.done }).containsExactly(0, 1, 2, 3, 4).inOrder()
        assertThat(progress.first().total).isEqualTo(5)
        assertThat(progress.first().modelId).isEqualTo("a/b")
        assertThat(progress.last().spentUsd).isWithin(1e-9).of(0.008)
    }

    @Test
    fun `the estimate is a ceiling that scales with models and is zero for free ones`() = runTest {
        val r = runner(FakeDao()) { perfect(it) }
        val one = r.estimateUsd(listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(model("a/b")))))
        val two = r.estimateUsd(listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(model("a/b"), model("c/d")))))
        val free = r.estimateUsd(listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(model("a/free", 0.0, 0.0)))))

        // Five menus at $1/M in and $2/M out, with the whole 3000-token reply budget assumed used.
        assertThat(one).isGreaterThan(5 * 3_000 * 2.0 / 1e6)
        assertThat(one).isLessThan(0.05)
        assertThat(two).isWithin(1e-12).of(one * 2)
        assertThat(free).isEqualTo(0.0)
    }

    @Test
    fun `the estimate covers what the run actually bills at the catalog price`() = runTest {
        val m = model("a/b", prompt = 3.0, completion = 15.0)
        // Billed the way a provider would: about four characters a token in, and a 600-token reply.
        val r = runner(FakeDao()) { call ->
            perfect(call, cost = (call.body.toString().length / 4 * 3.0 + 600 * 15.0) / 1e6)
        }
        val estimate = r.estimateUsd(listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(m))))
        val report = r.runWith(listOf(BenchmarkSelection(AiTask.MENU_INGESTION, listOf(m))))
        assertThat(report.spentUsd).isLessThan(estimate)
    }

    @Test
    fun `vision cannot be run`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                runner(FakeDao()) { perfect(it) }.runWith(listOf(selection(AiTask.VISION_TRACKER, "a/b")))
            }
        }
    }

    @Test
    fun `fixtures still carry the allergen the benchmark tests for`() {
        assertThat(BenchmarkScoring.AVOIDED_ALLERGEN).isEqualTo("dairy")
        assertThat(menus.all { fixture -> fixture.items.any { it.dairy } }).isTrue()
    }
}
