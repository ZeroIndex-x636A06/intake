package com.zeroindex.intake.data.ai.recipes

import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.ai.ServerTool
import com.zeroindex.intake.data.db.dao.RecipeDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.RecipeEntity
import com.zeroindex.intake.data.db.entity.RecipeOrigin
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.RecipeCandidate
import com.zeroindex.intake.domain.ai.RecipeFilter
import com.zeroindex.intake.domain.ai.RecipeQuery
import com.zeroindex.intake.domain.ai.RecipeVerdict
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class FoundRecipes(val recipes: List<FoundRecipe>)

@Serializable
data class FoundRecipe(
    val title: String,
    val sourceUrl: String,
    val sourceName: String,
    val servings: Int,
    /** A number rather than an Int: models sometimes send 35.0. */
    val totalMinutes: Double?,
    val ingredients: List<String>,
    val steps: List<String>,
    val kcalPerServing: Double,
    val proteinPerServing: Double,
    val carbsPerServing: Double,
    val fatPerServing: Double,
    /** True when the source published nutrition; false when the model computed it. */
    val macrosPublished: Boolean,
    val containsAllergens: List<String>,
    val allergenReasoning: String,
)

/** One result with the local verdict attached. */
data class HuntedRecipe(val recipe: FoundRecipe, val verdict: RecipeVerdict, val scan: AllergenScanResult)

data class HuntProgress(val message: String, val round: Int)

data class HuntResult(
    val results: List<HuntedRecipe>,
    val rounds: Int,
    val costUsd: Double,
    val model: String,
) {
    val usable: List<HuntedRecipe> get() = results.filter { it.verdict !is RecipeVerdict.Rejected }
}

/**
 * Amendment §1 Recipe Hunter: an agentic loop of search, extract, verify.
 *
 * 1. The model searches the web and reads recipe pages (OpenRouter's server
 *    side web_search and web_fetch tools) and extracts them to a schema.
 * 2. Every recipe is checked here, locally - the allergen engine over the
 *    full ingredient list, then the macro goals ([RecipeFilter]).
 * 3. If too few pass, the rejects go back to the model with their reasons
 *    and it is asked for replacements. At most [MAX_ROUNDS] rounds, so a
 *    query that cannot be met ends with an honest short list rather than an
 *    unbounded bill.
 *
 * Dairy-free is not a prompt instruction the model may or may not follow: it
 * is step 2, in code, on every result.
 */
@Singleton
class RecipeHunter @Inject constructor(
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val recipeDao: RecipeDao,
    private val clock: Clock,
) {
    companion object {
        const val TARGET = 5
        const val MAX_ROUNDS = 3
    }

    suspend fun hunt(query: RecipeQuery, onProgress: (HuntProgress) -> Unit): HuntResult {
        val avoided = query.avoidedAllergens.ifEmpty { allergens.avoidedAllergens() }
        val kept = mutableListOf<HuntedRecipe>()
        val rejected = mutableListOf<HuntedRecipe>()
        var cost = 0.0
        var model = ""
        var round = 0

        while (round < MAX_ROUNDS && kept.size < TARGET) {
            round++
            onProgress(
                HuntProgress(
                    if (round == 1) "Searching the web for recipes…" else "Finding replacements for ${rejected.size} removed…",
                    round,
                ),
            )
            val outcome = gateway.structured(
                task = AiTask.RECIPE_HUNTER,
                serializer = FoundRecipes.serializer(),
                schema = schema,
                system = systemPrompt(avoided),
                parts = listOf(AiPart.Text(userPrompt(query, avoided, TARGET - kept.size, kept, rejected))),
                tools = listOf(ServerTool.WebSearch(maxResults = 6, maxUses = 3), ServerTool.WebFetch(maxUses = 6)),
            )
            cost += outcome.response.costUsd ?: 0.0
            model = outcome.response.model

            onProgress(HuntProgress("Checking ingredients against your allergens…", round))
            val seen = (kept + rejected).map { it.recipe.sourceUrl }.toSet()
            outcome.value.recipes
                .filter { it.sourceUrl !in seen }
                .forEach { recipe ->
                    val judged = judge(recipe, query, avoided)
                    if (judged.verdict is RecipeVerdict.Rejected) rejected += judged else kept += judged
                }
        }
        return HuntResult(results = kept.take(TARGET) + rejected, rounds = round, costUsd = cost, model = model)
    }

    internal suspend fun judge(recipe: FoundRecipe, query: RecipeQuery, avoided: Set<String>): HuntedRecipe {
        val local = allergens.scan(name = recipe.title, ingredients = recipe.ingredients.joinToString("\n"))
        val scan = ModelAllergenPolicy.merge(
            local = listOf(local),
            claims = listOf(ModelAllergenClaim(recipe.title, recipe.containsAllergens, recipe.allergenReasoning)),
            avoided = avoided,
            source = "Recipe Hunter",
        ).scan
        val verdict = RecipeFilter.evaluate(
            RecipeCandidate(
                title = recipe.title,
                sourceUrl = recipe.sourceUrl,
                ingredients = recipe.ingredients,
                kcalPerServing = recipe.kcalPerServing,
                proteinPerServing = recipe.proteinPerServing,
                totalMinutes = recipe.totalMinutes?.toInt(),
            ),
            query,
            scan,
        )
        return HuntedRecipe(recipe, verdict, scan)
    }

    /** Saves a result to My recipes. Rejected recipes cannot be saved. */
    suspend fun save(hunted: HuntedRecipe): RecipeEntity {
        require(hunted.verdict !is RecipeVerdict.Rejected) { "A rejected recipe cannot be saved" }
        val r = hunted.recipe
        val now = clock.nowMillis()
        val entity = RecipeEntity(
            name = r.title,
            servings = r.servings.coerceAtLeast(1),
            instructions = r.steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"),
            totalTimeMinutes = r.totalMinutes?.toInt() ?: 0,
            origin = RecipeOrigin.HUNTER,
            sourceUrl = r.sourceUrl,
            ingredientsText = r.ingredients.joinToString("\n"),
            kcalPerServing = r.kcalPerServing,
            proteinPerServing = r.proteinPerServing,
            carbsPerServing = r.carbsPerServing,
            fatPerServing = r.fatPerServing,
            allergenStatus = when (hunted.verdict) {
                is RecipeVerdict.NeedsReview -> AllergenStatus.MAY_CONTAIN
                else -> AllergenStatus.CLEAR
            },
            createdAt = now,
            updatedAt = now,
        )
        recipeDao.upsertRecipe(entity)
        return entity
    }

    private fun systemPrompt(avoided: Set<String>) = buildString {
        appendLine("You find meal-prep recipes on the web for someone eating to macro targets.")
        appendLine("Use web search to find candidates and web fetch to read each recipe page before extracting it.")
        appendLine("Only return recipes you actually read, with the page's real URL. Never invent a recipe or a URL.")
        appendLine("Copy ingredient lines with their quantities as written on the page.")
        appendLine(
            "Use the page's published nutrition per serving when it has it (macrosPublished true); otherwise compute " +
                "it from the ingredients and set macrosPublished false.",
        )
        if (avoided.isNotEmpty()) {
            appendLine(
                "The user must avoid: ${avoided.joinToString()}. Skip any recipe containing them, including hidden " +
                    "forms (for dairy: milk, butter, ghee, cream, cheese, yoghurt, whey, casein). If an ingredient " +
                    "might contain one, list it in containsAllergens and explain.",
            )
        }
        append("Reply with JSON only, matching the schema.")
    }

    private fun userPrompt(
        q: RecipeQuery,
        avoided: Set<String>,
        wanted: Int,
        kept: List<HuntedRecipe>,
        rejected: List<HuntedRecipe>,
    ) = buildString {
        appendLine("Find $wanted recipes.")
        q.minProteinPerServing?.let { appendLine("At least $it g protein per serving.") }
        q.maxKcalPerServing?.let { appendLine("At most $it kcal per serving.") }
        appendLine("About ${q.servings} servings, good for batch cooking and reheating.")
        q.maxMinutes?.let { appendLine("Total time at most $it minutes.") }
        if (q.cuisines.isNotEmpty()) appendLine("Cuisines: ${q.cuisines.joinToString()}.")
        if (avoided.isNotEmpty()) appendLine("Must be free of: ${avoided.joinToString()}.")
        if (q.notes.isNotBlank()) appendLine("Also: ${q.notes.trim()}")
        if (kept.isNotEmpty()) {
            appendLine("Already found (do not repeat): ${kept.joinToString { it.recipe.sourceUrl }}")
        }
        if (rejected.isNotEmpty()) {
            appendLine("These were rejected by the user's checks; do not return them, and avoid the same problems:")
            rejected.forEach { appendLine("- ${it.recipe.title} (${it.recipe.sourceUrl}): ${(it.verdict as RecipeVerdict.Rejected).reason}") }
        }
    }

    private val schema: JsonObject = S.obj(
        "recipes" to S.array(
            S.obj(
                "title" to S.string(),
                "sourceUrl" to S.string("The page the recipe was read from"),
                "sourceName" to S.string("Site name"),
                "servings" to S.integer(),
                "totalMinutes" to S.nullableNumber("Total time in minutes, null if not stated"),
                "ingredients" to S.array(S.string()),
                "steps" to S.array(S.string()),
                "kcalPerServing" to S.number(),
                "proteinPerServing" to S.number(),
                "carbsPerServing" to S.number(),
                "fatPerServing" to S.number(),
                "macrosPublished" to S.boolean(),
                "containsAllergens" to S.array(S.string()),
                "allergenReasoning" to S.string(),
            ),
        ),
    )
}
