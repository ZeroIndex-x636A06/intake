package com.zeroindex.intake.data.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zeroindex.intake.domain.ai.AiFeature
import java.io.File
import org.junit.Test

/**
 * The audit invariant (SPEC-v3 "AI policy": audit every OpenRouter call site). It scans the app's source for
 * everything that can reach a model and fails if a call site appears that is not in [KNOWN] below, or in the
 * table in `docs/AI-COSTS.md`. To add an AI feature: route it through `AiGateway`, give it an `AiFeature`
 * and an `AiTrigger`, add it to [KNOWN], and add a row to the audit table.
 */
class AiAuditTest {

    private val sourceRoot = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
    private val docs = listOf(File("../docs/AI-COSTS.md"), File("docs/AI-COSTS.md")).first { it.isFile }

    /** Every file that makes a model call through the gateway, and the features it calls with. */
    private val KNOWN: Map<String, Set<AiFeature>> = mapOf(
        "data/ai/vision/VisionTracker.kt" to setOf(AiFeature.PHOTO_ESTIMATE),
        "data/ai/recipes/RecipeHunter.kt" to setOf(AiFeature.RECIPE_HUNTER),
        "data/ai/planb/PlanBFinder.kt" to setOf(AiFeature.PLAN_B),
        "data/ai/menu/MenuIngestor.kt" to setOf(AiFeature.MENU_IMPORT),
        "data/ai/insight/DailyInsights.kt" to setOf(AiFeature.DAILY_INSIGHT),
        "data/coach/CoachReworder.kt" to setOf(AiFeature.COACH_REWORD),
        "data/recipe/RecipeAiExtractor.kt" to setOf(AiFeature.RECIPE_TRY_AI),
        "data/ai/video/VideoWorkoutAi.kt" to setOf(AiFeature.VIDEO_TO_WORKOUT),
        "data/nearby/NearbyMenuSearch.kt" to setOf(AiFeature.MENU_IMPORT),
        "data/ai/benchmark/BenchmarkRunner.kt" to setOf(AiFeature.BENCHMARK),
    )

    private val sources: Map<String, String> by lazy {
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(sourceRoot.resolve("com/zeroindex/intake")).invariantSeparatorsPath to it.readText() }
    }

    private val callRegex = Regex("""\b(?:gateway|ai)\.(?:structured|complete)\(""")

    @Test
    fun `only the known files call the gateway`() {
        val callers = sources.filter { (path, text) -> path != "data/ai/AiGateway.kt" && callRegex.containsMatchIn(text) }.keys
        assertWithMessage("A new file calls the AI gateway. Add it to KNOWN here and to the table in docs/AI-COSTS.md.")
            .that(callers).containsExactlyElementsIn(KNOWN.keys)
    }

    @Test
    fun `each call names the feature and a trigger`() {
        KNOWN.forEach { (path, features) ->
            val text = sources.getValue(path)
            val calls = callRegex.findAll(text).toList()
            assertThat(calls).isNotEmpty()
            calls.forEach { m ->
                val args = text.substring(m.range.last, minOf(text.length, m.range.last + 400)).lowercase()
                assertWithMessage("$path: a gateway call without a trigger").that(args).contains("trigger")
                assertWithMessage("$path: a gateway call without a feature").that(args).contains("aifeature.")
            }
            val used = Regex("""AiFeature\.([A-Z_]+)""").findAll(text).map { AiFeature.valueOf(it.groupValues[1]) }.toSet()
            assertWithMessage(path).that(used).containsAtLeastElementsIn(features)
        }
    }

    @Test
    fun `every feature has a call site`() {
        val used = KNOWN.values.flatten().toSet()
        assertThat(used).containsExactlyElementsIn(AiFeature.entries)
    }

    @Test
    fun `the raw client is used only by the gateway, the catalog and dependency injection`() {
        val users = sources.filter { (path, text) ->
            Regex("""\bOpenRouterClient\b""").containsMatchIn(text) && path != "data/ai/OpenRouterClient.kt"
        }.keys
        assertThat(users).containsExactly(
            "data/ai/AiGateway.kt", "data/ai/ModelCatalogRepository.kt", "data/di/NetworkModule.kt",
        )
        val raw = sources.filter { (path, text) -> path != "data/ai/AiGateway.kt" && Regex("""\bclient\.complete\(""").containsMatchIn(text) }
        assertThat(raw.keys).isEmpty()
    }

    @Test
    fun `the OpenRouter address appears only in the client and the privacy screen's text`() {
        val hosts = sources.filter { (_, text) -> text.contains("openrouter.ai/") }.keys
        assertThat(hosts).containsExactly("data/ai/OpenRouterClient.kt")
    }

    @Test
    fun `things that must never use AI do not reach the gateway`() {
        val forbiddenAreas = listOf(
            "domain/", "data/repository/", "data/remote/", "data/profile/", "data/training/", "data/plan/",
            "data/weather/", "data/foodref/", "data/seed/", "data/health/", "data/sync/", "data/backup/",
            "ui/screens/script/", "ui/screens/allergen/", "ui/screens/profile/", "ui/screens/body/", "ui/screens/workout/",
            "ui/screens/train/", "ui/screens/progress/",
        )
        val offenders = sources.filter { (path, text) ->
            forbiddenAreas.any { path.startsWith(it) } &&
                Regex("""\bAiGateway\b|\bOpenRouterClient\b|import com\.zeroindex\.intake\.data\.ai\.""").containsMatchIn(text)
        }.keys
        assertWithMessage("Targets, water, programs, allergens, lookups and scripts must never call AI")
            .that(offenders).isEmpty()
    }

    @Test
    fun `the audit table lists every call site and every feature`() {
        val text = docs.readText()
        KNOWN.keys.forEach { path ->
            assertWithMessage("docs/AI-COSTS.md has no audit row for $path").that(text).contains(path.substringAfterLast('/'))
        }
        AiFeature.entries.forEach { f ->
            assertWithMessage("docs/AI-COSTS.md never mentions ${f.name}").that(text).contains(f.name)
        }
    }

    @Test
    fun `scheduled work cannot reach the model unless it is opt in`() {
        // The weekly menu job and the coach worker are the only background callers; both must be gated by a setting.
        val menuWorker = sources.getValue("work/MenuIngestionWorker.kt")
        assertThat(menuWorker).contains("menuAutoRefresh")
        val app = sources.getValue("IntakeApplication.kt")
        assertThat(app).doesNotContain("MenuIngestionWorker.schedule(")
        val insights = sources.getValue("data/ai/insight/DailyInsights.kt")
        assertThat(insights).contains("dailyInsightEnabled")
        val reworder = sources.getValue("data/coach/CoachReworder.kt")
        assertThat(reworder).contains("coachAiReword")
    }

    @Test
    fun `the usage log stays on the device`() {
        val files = sources.filter { (path, _) -> path.startsWith("data/backup/") || path.startsWith("data/sync/") }
        files.forEach { (path, text) ->
            assertWithMessage("$path must not include the per-device usage log").that(text).doesNotContain("ai_usage")
            assertWithMessage("$path must not include the per-device usage log").that(text).doesNotContain("AiUsageEntity")
        }
        assertThat(sources.getValue("data/backup/BackupTables.kt")).doesNotContain("AiUsage")
    }

    @Test
    fun `daily insight, coach reword and the weekly menu refresh are off by default`() {
        val defaults = com.zeroindex.intake.data.settings.AppSettings()
        assertThat(defaults.dailyInsightEnabled).isFalse()
        assertThat(defaults.coachAiReword).isFalse()
        assertThat(defaults.menuAutoRefresh).isFalse()
    }
}
