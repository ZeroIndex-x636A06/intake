package com.zeroindex.intake.data.ai.recipes

import com.zeroindex.intake.data.ai.S
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

/** The recipe-extraction prompt and schema, shared with the in-app benchmark. */
internal object RecipePrompts {
    fun system(avoided: Set<String>) = buildString {
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

    val schema: JsonObject = S.obj(
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

/** What the model returns for one Recipe Hunter round: pages to look at, not recipes. The app reads the pages itself. */
@Serializable
data class HuntCandidates(val candidates: List<HuntCandidate>)

@Serializable
data class HuntCandidate(
    val title: String,
    val sourceUrl: String,
    val sourceName: String,
    /** Allergens the search snippet suggests, a hint that can only add a warning. */
    val possibleAllergens: List<String> = emptyList(),
    val allergenNote: String = "",
)

internal object HuntPrompts {
    fun system(avoided: Set<String>) = buildString {
        appendLine("You find meal-prep recipe pages on the web for someone eating to macro targets.")
        appendLine("Use web search. Return the address of individual recipe pages from the search results, one recipe per page.")
        appendLine(
            "Not roundups, category pages, videos or shopping lists. Never invent a recipe or an address: " +
                "only return pages that appeared in your search results.",
        )
        appendLine(
            "Prefer sites that publish ingredient lists and nutrition facts per serving. " +
                "Spread the results over different sites and different dishes.",
        )
        appendLine("You do not need to open or copy the recipes: the app reads each page itself and checks it.")
        if (avoided.isNotEmpty()) {
            appendLine(
                "The user must avoid: ${avoided.joinToString()}. Skip any recipe that obviously contains them, including " +
                    "hidden forms (for dairy: milk, butter, ghee, cream, cheese, yoghurt, whey, casein). If the snippet " +
                    "suggests one, list it in possibleAllergens and say why in allergenNote.",
            )
        }
        append("Reply with JSON only, matching the schema.")
    }

    val schema: JsonObject = S.obj(
        "candidates" to S.array(
            S.obj(
                "title" to S.string(),
                "sourceUrl" to S.string("The recipe page's address, exactly as in the search results"),
                "sourceName" to S.string("Site name"),
                "possibleAllergens" to S.array(S.string()),
                "allergenNote" to S.string(),
            ),
        ),
    )
}
