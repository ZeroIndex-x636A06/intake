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
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.MenuStructured
import com.zeroindex.intake.domain.ai.MenuText
import com.zeroindex.intake.domain.ai.StructuredMenuItem
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.menu.MenuTableParse
import com.zeroindex.intake.domain.menu.NutritionTableParser
import com.zeroindex.intake.domain.menu.StatedAllergens
import com.zeroindex.intake.domain.pdf.PdfChunkPlanner
import com.zeroindex.intake.domain.pdf.PdfDocument
import com.zeroindex.intake.domain.pdf.PdfUnsupportedException
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

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

open class MenuImportException(message: String) : Exception(message)

/**
 * The on-phone reader found no nutrition table in a PDF (or this phone cannot read PDF text). Nothing was sent
 * anywhere. The message says why, and the person can choose to have the AI path read the file instead.
 */
class MenuNoTableException(message: String) : MenuImportException(message)

/** How a PDF is read: on the phone first, and whether the AI path may follow. */
enum class PdfRoute {
    /** On the phone; if it finds no table, stop with [MenuNoTableException] so the person can choose AI. */
    PHONE_ONLY,

    /** On the phone; if it finds no table, go on to the AI path (the scheduled refresh, which the person opted into). */
    PHONE_THEN_AI,

    /** Straight to the AI path: the person chose it after the phone found nothing. */
    AI_ONLY,
}

/**
 * Amendment §1 menu ingestion: fetch, extract, parse, then judge every item
 * locally before it can reach Plan B.
 *
 * Rules before models (SPEC-v3 "AI policy"): a page that publishes its menu
 * as schema.org `MenuItem` data is read directly with no model call. Only
 * unstructured text - a pasted menu, a PDF, a page without that data - goes
 * to a large-context model, and the gateway guards that call.
 *
 * Fetching is an ordinary request with an honest user agent. Pages built
 * entirely in JavaScript (Square Online, most ordering sites) come back
 * nearly empty, so for those the model reads the page itself through
 * OpenRouter's web_fetch. The exception is delivery marketplaces whose
 * terms forbid automated access: they get one ordinary request, and if that
 * is refused the import fails with a clear reason and a suggestion to use a
 * PDF or pasted text - it never tries another way in.
 */
/** What a menu import produced: the staged items, and plain notes about anything that could not be read. */
data class StageOutcome(
    val items: List<StagedItem>,
    val warnings: List<String> = emptyList(),
    /** True when the items were read on this phone from the PDF's own text, with no AI. */
    val readOnPhone: Boolean = false,
)

/** Where an import is up to, for the progress line. [fraction] is null when it cannot be told. */
data class MenuProgress(val message: String, val fraction: Float? = null)

@Singleton
class MenuIngestor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: MenuFetcher,
    private val gateway: AiGateway,
    private val phonePdf: PhonePdfText,
    private val allergens: AllergenRepository,
    private val menuDao: MenuDao,
    private val clock: Clock,
) {
    /**
     * Fetches and parses one source into staged items. Writes nothing. [trigger] says why a model may be
     * called: a person tapping Import, or the weekly refresh they switched on.
     *
     * Everything here runs off the main thread, whichever scope called it: the download and the cap, reading
     * and splitting a PDF, the allergen check of every item, and the model calls themselves. [onProgress] says
     * where it is up to, and cancelling the caller cancels the download or the request in flight.
     */
    suspend fun stage(
        source: MenuSourceEntity,
        trigger: AiTrigger,
        onProgress: (MenuProgress) -> Unit = {},
        pdfRoute: PdfRoute = PdfRoute.PHONE_THEN_AI,
    ): StageOutcome = withContext(Dispatchers.Default) {
        val avoided = allergens.avoidedAllergens()
        val parts: List<AiPart>
        val tools: List<ServerTool>
        when (source.kind) {
            MenuSourceKind.TEXT -> {
                parts = listOf(AiPart.Text(source.pastedText.orEmpty().take(MenuText.MAX_CHARS)))
                tools = emptyList()
            }
            MenuSourceKind.PDF -> {
                onProgress(MenuProgress("Opening the PDF…"))
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(Uri.parse(source.location))?.use { readPdfStream(it) }
                } ?: throw MenuImportException("The PDF could not be opened. Pick it again.")
                return@withContext stagePdf(source, bytes, avoided, trigger, onProgress, pdfRoute)
            }
            MenuSourceKind.URL -> {
                onProgress(MenuProgress("Downloading…"))
                val fetched = fetcher.fetch(source.location) { read, total ->
                    onProgress(
                        MenuProgress(
                            "Downloading… ${read / 1024} KB" + (total?.let { " of ${it / 1024} KB" } ?: ""),
                            total?.let { (read.toFloat() / it).coerceIn(0f, 1f) },
                        ),
                    )
                }
                if (fetched is Fetched.Refused) throw MenuImportException(fetched.message)
                if (fetched is Fetched.Pdf) return@withContext stagePdf(source, fetched.bytes, avoided, trigger, onProgress, pdfRoute)
                val page = fetched as? Fetched.Page
                // The deterministic path: structured menu data on the page needs no model.
                val structured = page?.html?.let(MenuStructured::parse).orEmpty()
                if (structured.isNotEmpty()) {
                    return@withContext StageOutcome(stageItems(source, structured.map(::toParsed), avoided, structured = true))
                }
                val text = page?.text
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

        onProgress(MenuProgress("Reading ${source.restaurantName}'s menu…"))
        val parsed = gateway.structured(
            feature = AiFeature.MENU_IMPORT,
            trigger = trigger,
            serializer = ParsedMenu.serializer(),
            schema = MenuPrompts.schema,
            system = MenuPrompts.system(source.restaurantName, avoided),
            parts = parts,
            tools = tools,
        ).value

        onProgress(MenuProgress("Checking allergens on this phone…"))
        StageOutcome(stageItems(source, parsed.items, avoided, structured = false))
    }

    /**
     * A PDF, from a link or a picked file. It is split into a few pages per request ([PdfChunkPlanner]) and
     * each piece goes to OpenRouter's PDF input with the free text engine, so no request is huge and a failure
     * costs one piece, not the import. The pieces' items are merged ([MenuMerge]) and every item is then judged
     * by the local allergen engine; a "Contains:" line in the PDF can only raise a warning, never clear one.
     */
    private suspend fun stagePdf(
        source: MenuSourceEntity,
        bytes: ByteArray,
        avoided: Set<String>,
        trigger: AiTrigger,
        onProgress: (MenuProgress) -> Unit,
        route: PdfRoute,
    ): StageOutcome {
        if (route != PdfRoute.AI_ONLY) {
            readOnPhone(source, bytes, route, onProgress)?.let { return it }
        }
        onProgress(MenuProgress("Reading the PDF's pages…"))
        val doc = try {
            PdfDocument.parse(bytes)
        } catch (e: PdfUnsupportedException) {
            throw MenuImportException("${e.message} Choose a different PDF, or paste the menu text instead.")
        }
        if (doc.pageCount > PdfChunkPlanner.MAX_PAGES) {
            throw MenuImportException(
                "This PDF has ${doc.pageCount} pages, and the limit is ${PdfChunkPlanner.MAX_PAGES}. " +
                    "Use a shorter PDF, or paste the part of the menu you want.",
            )
        }
        val chunks = PdfChunkPlanner.plan((0 until doc.pageCount).map(doc::estimatedBytes))
        val results = ArrayList<List<ParsedItem>>()
        val warnings = ArrayList<String>()
        var stoppedBy: AiError? = null

        for ((i, chunk) in chunks.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(
                MenuProgress(
                    "Reading ${chunk.label} of ${doc.pageCount} (request ${i + 1} of ${chunks.size})…",
                    i.toFloat() / chunks.size,
                ),
            )
            val part = doc.extract(chunk.from, chunk.to)
            try {
                val parsed = gateway.structured(
                    feature = AiFeature.MENU_IMPORT,
                    trigger = trigger,
                    serializer = ParsedMenu.serializer(),
                    schema = MenuPrompts.schema,
                    system = MenuPrompts.system(source.restaurantName, avoided, pdfPages = "${chunk.label} of ${doc.pageCount}"),
                    parts = listOf(
                        AiPart.Pdf(part, "menu-p${chunk.from + 1}-${chunk.to + 1}.pdf"),
                        AiPart.Text("Extract every menu item on ${chunk.label} of this ${doc.pageCount}-page PDF."),
                    ),
                    // A page of a nutrition table is dozens of rows; leave room for all of them.
                    maxTokens = 16_000,
                ).value
                results += parsed.items
            } catch (e: AiError) {
                if (e.endsImport()) {
                    stoppedBy = e
                    break
                }
                warnings += "${chunk.label.replaceFirstChar { it.uppercase() }} could not be read (${e.failureKind.label.lowercase()})."
            }
        }

        val merged = MenuMerge.merge(results)
        if (merged.isEmpty()) throw stoppedBy ?: MenuImportException("No menu items were found in that PDF.")
        stoppedBy?.let {
            warnings += "Stopped early: ${it.message} The items below are the pages read so far."
        }
        onProgress(MenuProgress("Checking allergens on this phone…", 1f))
        return StageOutcome(stageItems(source, merged, avoided, structured = false), warnings)
    }

    /**
     * The no-AI route: the platform's PDF reader gives the text, [NutritionTableParser] reads the table, and every row
     * is judged by the local allergen engine. Returns null when no table was found and the AI path may follow
     * ([PdfRoute.PHONE_THEN_AI]); throws [MenuNoTableException] when the person is to be asked first.
     */
    private suspend fun readOnPhone(
        source: MenuSourceEntity,
        bytes: ByteArray,
        route: PdfRoute,
        onProgress: (MenuProgress) -> Unit,
    ): StageOutcome? {
        onProgress(MenuProgress("Reading the PDF on this phone…"))
        val why: String = when (val read = phonePdf.read(bytes)) {
            is PhonePdfText.Result.Pages -> {
                onProgress(MenuProgress("Looking for a nutrition table…"))
                val table = NutritionTableParser.parse(read.pages)
                if (table.foundTable) {
                    onProgress(MenuProgress("Checking allergens on this phone…", 1f))
                    val warnings = if (table.skipped > 0) {
                        listOf(
                            "${table.skipped} line${if (table.skipped == 1) "" else "s"} that looked like menu items were left out because their numbers did not fit the columns. Nothing was guessed.",
                        )
                    } else {
                        emptyList()
                    }
                    return StageOutcome(stageTable(source, table), warnings, readOnPhone = true)
                }
                "The reader on this phone found no nutrition table in that file. It needs a PDF with selectable text."
            }
            is PhonePdfText.Result.Unsupported -> read.reason
            is PhonePdfText.Result.Failed -> read.reason
        }
        if (route == PdfRoute.PHONE_ONLY) throw MenuNoTableException(why)
        return null
    }

    /** Judges each table row with the local engine, handing it the restaurant's stated allergens as structured inputs. */
    private suspend fun stageTable(source: MenuSourceEntity, table: MenuTableParse): List<StagedItem> {
        val now = clock.nowMillis()
        val scan = allergens.menuScanner()
        return table.rows.map { row ->
            coroutineContext.ensureActive()
            // A chart lists the major allergens an item contains. An item it lists none for says so in words, so the
            // item carries ingredient information and is not shown as "ingredients not listed".
            val ingredients = row.stated.toText().ifBlank { CHART_LISTS_NONE }
            val description = listOfNotNull(
                row.category,
                row.servingWeightG?.let { "${num(it)} g serving" } ?: row.servingLabel,
                "saturated fat ${num(row.saturatedFatG)} g",
                "sodium ${num(row.sodiumMg)} mg",
                "fibre ${num(row.fiberG)} g",
                "sugars ${num(row.sugarsG)} g",
                "cooked or made where other allergens are present".takeIf { row.sharedFacility },
            ).joinToString(" · ")
            val verdict = withContext(Dispatchers.Default) { scan(row.name, description, ingredients) }
            StagedItem(
                MenuItemEntity(
                    sourceId = source.id,
                    restaurantName = source.restaurantName,
                    name = row.name,
                    description = description,
                    ingredientsText = ingredients,
                    kcal = row.kcal,
                    proteinG = row.proteinG,
                    carbsG = row.carbsG,
                    fatG = row.fatG,
                    macrosPublished = true,
                    allergenStatus = when {
                        verdict.blocks -> AllergenStatus.CONTAINS
                        verdict.mayContain.isNotEmpty() -> AllergenStatus.MAY_CONTAIN
                        verdict.checkLabel.isNotEmpty() -> AllergenStatus.CHECK_LABEL
                        else -> AllergenStatus.CLEAR
                    },
                    allergenMatches = verdict.matches.map { it.matchedTerm }.distinct().joinToString(", "),
                    importedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun num(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)

    /** Problems with the account or the connection stop the whole import; a bad page only costs that page. */
    private fun AiError.endsImport(): Boolean = when (this) {
        AiError.NoKey, AiError.Off, is AiError.BudgetReached, is AiError.NotAllowed, AiError.Unauthorized,
            AiError.InsufficientCredits, is AiError.Network, is AiError.RateLimited, is AiError.NoModel,
            // The gateway already tried the other compatible models; every page would fail the same way.
            AiError.NoEndpointForPrivacy, AiError.NoEndpointForParameters -> true
        AiError.Timeout, is AiError.Refused, is AiError.Server, is AiError.BadResponse -> false
    }

    private fun toParsed(item: StructuredMenuItem) = ParsedItem(
        name = item.name,
        description = item.description,
        ingredients = "",
        kcal = item.kcal,
        protein = item.protein,
        carbs = item.carbs,
        fat = item.fat,
        macrosPublished = item.kcal != null,
        priceCents = item.priceCents,
        containsAllergens = emptyList(),
        allergenNotes = "",
    )

    /**
     * Judges each parsed dish with the local allergen engine. Dishes read straight from structured data have
     * no ingredient panel and no model second opinion, so they are never marked clear: at best "check the
     * label", which keeps them out of Plan B's offers until the person has looked.
     */
    private suspend fun stageItems(
        source: MenuSourceEntity,
        items: List<ParsedItem>,
        avoided: Set<String>,
        structured: Boolean,
    ): List<StagedItem> {
        if (items.isEmpty()) throw MenuImportException("No menu items were found on that page.")
        val now = clock.nowMillis()
        return items.map { item ->
            coroutineContext.ensureActive()
            // A "Contains: ... ; May contain: ..." line in the text is also handed over as structured allergens.
            val stated = StatedAllergens.parse(item.ingredients)
            val local = allergens.scan(
                name = item.name,
                ingredients = listOf(item.description, item.ingredients).filter { it.isNotBlank() }.joinToString("\n"),
                structuredContains = stated.contains,
                structuredTraces = stated.mayContain,
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
                        verdict.checkLabel.isNotEmpty() || (structured && item.ingredients.isBlank()) -> AllergenStatus.CHECK_LABEL
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
            runCatching { stage(source, AiTrigger.OPT_IN_SCHEDULE) }
                .onSuccess { staged -> commit(source, staged.items.map { it.entity }); ok++ }
                .onFailure { markFailed(source, (it as? AiError)?.message ?: it.message ?: "Import failed") }
        }
        return ok
    }
}

internal const val CHART_LISTS_NONE = "Restaurant allergen chart: no major allergens listed"

/** The menu-import prompt and schema, shared with the in-app benchmark so both measure the same request. */
internal object MenuPrompts {
    fun system(restaurant: String, avoided: Set<String>, pdfPages: String? = null) = buildString {
        appendLine("You extract a restaurant menu into structured items. The restaurant is $restaurant.")
        appendLine("One entry per orderable dish or drink. Skip section headers, prices-only lines and boilerplate.")
        appendLine("Copy ingredients and descriptions as written. Never invent dishes.")
        appendLine(
            "Use nutrition the menu publishes (macrosPublished true). Otherwise estimate per standard serving from the " +
                "description, or use null when there is too little to go on.",
        )
        if (pdfPages != null) {
            appendLine(
                "You are reading $pdfPages of a restaurant's nutrition and allergen PDF. Each table row is one menu " +
                    "item. Take calories, total fat, total carbohydrate and protein from the column the header names, " +
                    "in kcal and grams, and set macrosPublished to true. If a row says \"Contains: ...\", copy that line " +
                    "exactly into ingredients and list the same allergens in containsAllergens. Section titles, " +
                    "footnotes and column headers are not items. Skip a row that is cut off by the page edge.",
            )
        }
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
