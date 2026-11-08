package com.zeroindex.intake.data.ai.menu

import android.content.Context
import android.net.Uri
import com.zeroindex.intake.data.ai.AiError
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.ai.ServerTool
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.MenuItemEntity
import com.zeroindex.intake.data.db.entity.MenuRunStatus
import com.zeroindex.intake.data.db.entity.MenuSourceEntity
import com.zeroindex.intake.data.db.entity.MenuSourceKind
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.domain.ai.AiTask
import com.zeroindex.intake.domain.ai.MenuText
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class ParsedMenu(val items: List<ParsedItem>)

@Serializable
data class ParsedItem(
    val name: String,
    val description: String,
    val ingredients: String,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val macrosPublished: Boolean,
    val priceCents: Double?,
    val containsAllergens: List<String>,
    val allergenNotes: String,
)

/** A parsed item with the local engine's verdict, before anything is saved. */
data class StagedItem(val entity: MenuItemEntity, val accepted: Boolean = entity.allergenStatus != AllergenStatus.CONTAINS)

class MenuImportException(message: String) : Exception(message)

/**
 * Amendment §1 menu ingestion: fetch, extract, parse with a large-context
 * model, then judge every item locally before it can reach Plan B.
 *
 * Fetching is an ordinary request with an honest user agent. Pages built
 * entirely in JavaScript (Square Online, most ordering sites) come back
 * nearly empty, so for those the model reads the page itself through
 * OpenRouter's web_fetch. The exception is delivery marketplaces whose
 * terms forbid automated access: they get one ordinary request, and if that
 * is refused the import fails with a clear reason and a suggestion to use a
 * PDF or pasted text - it never tries another way in.
 */
@Singleton
class MenuIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val menuDao: MenuDao,
    private val clock: Clock,
) {
    private companion object {
        const val USER_AGENT = "Intake/1.0 (personal menu import; one request per menu per week)"
    }

    /** Fetches and parses one source into staged items. Writes nothing. */
    suspend fun stage(source: MenuSourceEntity): List<StagedItem> {
        val parts: List<AiPart>
        val tools: List<ServerTool>
        when (source.kind) {
            MenuSourceKind.TEXT -> {
                parts = listOf(AiPart.Text(source.pastedText.orEmpty().take(MenuText.MAX_CHARS)))
                tools = emptyList()
            }
            MenuSourceKind.PDF -> {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(source.location))?.use { it.readBytes() }
                } ?: throw MenuImportException("The PDF could not be opened. Pick it again.")
                parts = listOf(AiPart.Pdf(bytes, "${source.restaurantName}.pdf"))
                tools = emptyList()
            }
            MenuSourceKind.URL -> {
                val text = fetchText(source.location)
                if (text != null && !MenuText.looksBlocked(text)) {
                    parts = listOf(AiPart.Text("Menu page text from ${source.location}:\n\n$text"))
                    tools = emptyList()
                } else if (MenuText.forbidsScraping(source.location)) {
                    throw MenuImportException(
                        "This site blocks automated access and its terms forbid scraping. " +
                            "Save the menu as a PDF or paste its text instead.",
                    )
                } else {
                    parts = listOf(AiPart.Text("Read the menu at ${source.location} and extract it."))
                    tools = listOf(ServerTool.WebFetch(maxUses = 4, maxContentTokens = 60_000))
                }
            }
        }

        val avoided = allergens.avoidedAllergens()
        val parsed = gateway.structured(
            task = AiTask.MENU_INGESTION,
            serializer = ParsedMenu.serializer(),
            schema = MenuPrompts.schema,
            system = MenuPrompts.system(source.restaurantName, avoided),
            parts = parts,
            tools = tools,
        ).value

        if (parsed.items.isEmpty()) throw MenuImportException("No menu items were found on that page.")
        val now = clock.nowMillis()
        return parsed.items.map { item ->
            val local = allergens.scan(
                name = item.name,
                ingredients = listOf(item.description, item.ingredients).filter { it.isNotBlank() }.joinToString("\n"),
            )
            val verdict = ModelAllergenPolicy.merge(
                local = listOf(local),
                claims = listOf(ModelAllergenClaim(item.name, item.containsAllergens, item.allergenNotes)),
                avoided = avoided,
                source = "Menu import",
            ).scan
            StagedItem(
                MenuItemEntity(
                    sourceId = source.id,
                    restaurantName = source.restaurantName,
                    name = item.name,
                    description = item.description,
                    ingredientsText = item.ingredients,
                    kcal = item.kcal,
                    proteinG = item.protein,
                    carbsG = item.carbs,
                    fatG = item.fat,
                    macrosPublished = item.macrosPublished,
                    allergenStatus = when {
                        verdict.blocks -> AllergenStatus.CONTAINS
                        verdict.mayContain.isNotEmpty() -> AllergenStatus.MAY_CONTAIN
                        verdict.checkLabel.isNotEmpty() -> AllergenStatus.CHECK_LABEL
                        else -> AllergenStatus.CLEAR
                    },
                    allergenMatches = verdict.matches.map { it.matchedTerm }.distinct().joinToString(", "),
                    priceCents = item.priceCents?.toInt(),
                    importedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Replaces the source's items with [items]; the previous run is soft deleted. */
    suspend fun commit(source: MenuSourceEntity, items: List<MenuItemEntity>) {
        val now = clock.nowMillis()
        menuDao.softDeleteItemsOf(source.id, now)
        menuDao.upsertItems(items)
        menuDao.upsertSource(
            source.copy(lastRunAt = now, lastStatus = MenuRunStatus.OK, lastError = null, itemCount = items.size, updatedAt = now),
        )
    }

    suspend fun markFailed(source: MenuSourceEntity, reason: String) {
        val now = clock.nowMillis()
        menuDao.upsertSource(source.copy(lastRunAt = now, lastStatus = MenuRunStatus.FAILED, lastError = reason, updatedAt = now))
    }

    /**
     * The scheduled run: no one is there to review, so items the local engine
     * found to contain an allergen are stored but marked, and Plan B only
     * ever offers items marked clear.
     */
    suspend fun runScheduled(): Int {
        var ok = 0
        menuDao.sources().filter { it.enabled && it.kind != MenuSourceKind.TEXT }.forEach { source ->
            runCatching { stage(source) }
                .onSuccess { staged -> commit(source, staged.map { it.entity }); ok++ }
                .onFailure { markFailed(source, (it as? AiError)?.message ?: it.message ?: "Import failed") }
        }
        return ok
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        val request = runCatching { Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build() }
            .getOrElse { throw MenuImportException("That does not look like a web address.") }
        try {
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val type = r.header("Content-Type").orEmpty()
                val body = r.body.string()
                if ("html" in type || body.trimStart().startsWith("<")) MenuText.fromHtml(body) else body.take(MenuText.MAX_CHARS)
            }
        } catch (_: IOException) {
            null
        }
    }
}

/** The menu-import prompt and schema, shared with the in-app benchmark so both measure the same request. */
internal object MenuPrompts {
    fun system(restaurant: String, avoided: Set<String>) = buildString {
        appendLine("You extract a restaurant menu into structured items. The restaurant is $restaurant.")
        appendLine("One entry per orderable dish or drink. Skip section headers, prices-only lines and boilerplate.")
        appendLine("Copy ingredients and descriptions as written. Never invent dishes.")
        appendLine(
            "Use nutrition the menu publishes (macrosPublished true). Otherwise estimate per standard serving from the " +
                "description, or use null when there is too little to go on.",
        )
        if (avoided.isNotEmpty()) {
            appendLine(
                "The user avoids ${avoided.joinToString()}. For each item list any of these it contains or is likely " +
                    "to contain - cheese, butter, cream, sauces, dressings - in containsAllergens with a short reason.",
            )
        }
        append("Reply with JSON only, matching the schema.")
    }

    val schema = S.obj(
        "items" to S.array(
            S.obj(
                "name" to S.string(),
                "description" to S.string(),
                "ingredients" to S.string("As listed; empty if none"),
                "kcal" to S.nullableNumber(),
                "protein" to S.nullableNumber(),
                "carbs" to S.nullableNumber(),
                "fat" to S.nullableNumber(),
                "macrosPublished" to S.boolean(),
                "priceCents" to S.nullableNumber(),
                "containsAllergens" to S.array(S.string()),
                "allergenNotes" to S.string(),
            ),
        ),
    )
}
