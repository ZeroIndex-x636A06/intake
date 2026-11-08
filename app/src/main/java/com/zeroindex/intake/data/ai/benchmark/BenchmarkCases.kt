package com.zeroindex.intake.data.ai.benchmark

import android.content.Context
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.insight.InsightPrompts
import com.zeroindex.intake.data.ai.insight.InsightReply
import com.zeroindex.intake.data.ai.menu.MenuPrompts
import com.zeroindex.intake.data.ai.menu.ParsedMenu
import com.zeroindex.intake.data.ai.planb.FoundOptions
import com.zeroindex.intake.data.ai.planb.PlanBPrompts
import com.zeroindex.intake.data.ai.recipes.FoundRecipes
import com.zeroindex.intake.data.ai.recipes.RecipePrompts
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.benchmark.BenchmarkScoring
import com.zeroindex.intake.domain.ai.benchmark.FixtureScore
import com.zeroindex.intake.domain.ai.benchmark.InsightFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.MenuFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.ObservedInsight
import com.zeroindex.intake.domain.ai.benchmark.ObservedItem
import com.zeroindex.intake.domain.ai.benchmark.PlanBFixtureSet
import com.zeroindex.intake.domain.ai.benchmark.RecipeFixtureSet
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Reads a bundled fixture file; a seam so JVM tests can read the same files without Android. */
fun interface FixtureReader {
    fun read(file: String): String
}

@Singleton
class AssetFixtureReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : FixtureReader {
    override fun read(file: String): String =
        context.assets.open("benchmark/$file").bufferedReader().use { it.readText() }
}

/**
 * One request of the benchmark: the exact system prompt and schema the real
 * feature sends, with a fixture standing in for what the feature would have
 * fetched. [grade] scores the first reply, or returns null when that reply
 * does not decode against the schema.
 */
class BenchmarkCase(
    val fixtureId: String,
    val system: String,
    val user: String,
    val schema: JsonObject,
    val grade: (reply: String) -> FixtureScore?,
) {
    /** Everything sent that costs input tokens: prompts and the response schema. */
    val inputChars: Int get() = system.length + user.length + schema.toString().length
}

/**
 * Builds the benchmark requests for each text task from the bundled fixtures.
 *
 * The web tools are deliberately left out: a model that can read a page is
 * what is being measured, and live search would make a run unrepeatable and
 * bill per search. The page or menu text is handed over instead, with one
 * line saying so, since the shared prompts mention searching.
 */
@Singleton
class BenchmarkCases @Inject constructor(
    private val reader: FixtureReader,
    private val json: Json,
) {
    private val cache = mutableMapOf<AiTask, List<BenchmarkCase>>()

    @Synchronized
    fun forTask(task: AiTask): List<BenchmarkCase> = cache.getOrPut(task) {
        when (task) {
            AiTask.MENU_INGESTION -> menu()
            AiTask.RECIPE_HUNTER -> recipes()
            AiTask.PLAN_B -> planB()
            AiTask.DAILY_INSIGHT -> insights()
            AiTask.VISION_TRACKER -> error("Vision has no bundled fixtures, so it is not benchmarked")
        }
    }

    private val avoided = setOf(BenchmarkScoring.AVOIDED_ALLERGEN)

    private fun <T> load(file: String, serializer: KSerializer<T>): T = json.decodeFromString(serializer, reader.read(file))

    private fun <T> parse(serializer: KSerializer<T>, reply: String): T? = AiGateway.decodeReply(json, serializer, reply)

    private fun menu() = load("menu-ingestion.json", MenuFixtureSet.serializer()).fixtures.map { f ->
        BenchmarkCase(f.id, MenuPrompts.system(f.restaurant, avoided), f.text, MenuPrompts.schema) { reply ->
            parse(ParsedMenu.serializer(), reply)?.let { menu ->
                BenchmarkScoring.scoreMenu(
                    f,
                    menu.items.map { ObservedItem(it.name, it.kcal, it.protein, BenchmarkScoring.raisesDairy(it.containsAllergens)) },
                )
            }
        }
    }

    private fun recipes() = load("recipe-hunter.json", RecipeFixtureSet.serializer()).fixtures.map { f ->
        val user = "Web search is not available for this task. Extract the one recipe on the page below and return " +
            "it as the only entry in recipes.\n\n${f.pageText}"
        BenchmarkCase(f.id, RecipePrompts.system(avoided), user, RecipePrompts.schema) { reply ->
            parse(FoundRecipes.serializer(), reply)?.let { found ->
                val first = found.recipes.firstOrNull()?.let {
                    ObservedItem(it.title, it.kcalPerServing, it.proteinPerServing, BenchmarkScoring.raisesDairy(it.containsAllergens))
                }
                BenchmarkScoring.scoreRecipe(f, first)
            }
        }
    }

    private fun planB() = load("plan-b.json", PlanBFixtureSet.serializer()).fixtures.map { f ->
        val user = buildString {
            appendLine("Web search is not available for this task. Choose only from the menu below and return up to 3 options.")
            appendLine("About ${f.kcalLeft} kcal left today.")
            appendLine("About ${f.proteinLeft} g protein left today; favour high-protein orders.")
            appendLine("Allergies: ${avoided.joinToString()}.")
            appendLine()
            append(f.menuText)
        }
        BenchmarkCase(f.id, PlanBPrompts.system(avoided), user, PlanBPrompts.schema) { reply ->
            parse(FoundOptions.serializer(), reply)?.let { found ->
                BenchmarkScoring.scorePlanB(
                    f,
                    found.options.map { ObservedItem(it.order, it.kcal, it.protein, BenchmarkScoring.raisesDairy(it.containsAllergens)) },
                )
            }
        }
    }

    private fun insights() = load("daily-insight.json", InsightFixtureSet.serializer()).fixtures.map { f ->
        BenchmarkCase(
            f.id,
            InsightPrompts.system(avoided),
            "The last two weeks:\n" + f.digest.joinToString("\n"),
            InsightPrompts.schema,
        ) { reply ->
            parse(InsightReply.serializer(), reply)?.let {
                BenchmarkScoring.scoreInsight(f, ObservedInsight(it.insight, it.focus, it.basedOn))
            }
        }
    }
}
