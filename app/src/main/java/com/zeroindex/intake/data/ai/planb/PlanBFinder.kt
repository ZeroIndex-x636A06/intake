package com.zeroindex.intake.data.ai.planb

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.zeroindex.intake.data.ai.AiGateway
import com.zeroindex.intake.data.ai.AiPart
import com.zeroindex.intake.data.ai.S
import com.zeroindex.intake.data.ai.ServerTool
import com.zeroindex.intake.data.db.dao.MenuDao
import com.zeroindex.intake.data.db.dao.PlanDao
import com.zeroindex.intake.data.db.entity.AllergenStatus
import com.zeroindex.intake.data.db.entity.EatOutOptionEntity
import com.zeroindex.intake.data.db.entity.EatOutOrigin
import com.zeroindex.intake.data.repository.AllergenRepository
import com.zeroindex.intake.data.repository.FoodLogRepository
import com.zeroindex.intake.data.repository.TargetRepository
import com.zeroindex.intake.domain.ai.AiFeature
import com.zeroindex.intake.domain.ai.AiTrigger
import com.zeroindex.intake.domain.ai.PlanBFit
import com.zeroindex.intake.domain.allergen.AllergenScanResult
import com.zeroindex.intake.domain.allergen.ModelAllergenClaim
import com.zeroindex.intake.domain.allergen.ModelAllergenPolicy
import com.zeroindex.intake.domain.menu.MenuSearch
import com.zeroindex.intake.domain.model.MealSlot
import com.zeroindex.intake.domain.nutrition.DayProgress
import com.zeroindex.intake.domain.nutrition.Macros
import com.zeroindex.intake.domain.swap.MealNutrition
import com.zeroindex.intake.domain.swap.NearbyMode
import com.zeroindex.intake.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

@Serializable
data class FoundOptions(val options: List<FoundOption>)

@Serializable
data class FoundOption(
    val restaurant: String,
    val address: String,
    val distanceMiles: Double?,
    val order: String,
    /** What to ask to change, e.g. "no cheese, no sour cream". */
    val modifications: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val containsAllergens: List<String>,
    val allergenNotes: String,
    /** The exact words to say at the counter. */
    val sayThis: String,
    val crossContactRisk: String,
    val sourceUrl: String,
    /** Optional detail for the healthier ranking. Null means the source did not give it; never read as zero. */
    val fiberG: Double? = null,
    val satFatG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
)

/** Where to search: rounded coordinates, or a zip code the user typed. */
sealed interface SearchArea {
    data class Coordinates(val lat: Double, val lng: Double) : SearchArea
    data class PostalCode(val code: String) : SearchArea
}

/** One option, local or found, with how it fits today and its allergen verdict. */
data class PlanBOption(
    val key: String,
    val restaurant: String,
    val address: String?,
    val distanceMiles: Double?,
    val order: String,
    val modifications: String,
    val macros: Macros,
    val sayThis: String,
    val caution: String,
    val crossContactRisk: String?,
    val scan: AllergenScanResult,
    val fit: PlanBFit.Fit,
    /** Already stored on the phone: usable with no signal. */
    val offline: Boolean,
    val savedId: String? = null,
    /** The imported menu item behind this option, so it can be scanned again when it is chosen. */
    val menuItemId: String? = null,
    /**
     * An imported menu item with no ingredient text and no stated allergens. A clear scan then rules nothing out, so
     * it is offered as "check the label", never as clear (`AllergenEvidence`).
     */
    val nameOnly: Boolean = false,
    /** Fibre, saturated fat, sugar (g) and sodium (mg) when known. Null is unknown, never zero. */
    val fiberG: Double? = null,
    val satFatG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
) {
    val nutrition: MealNutrition
        get() = MealNutrition(macros.kcal, macros.protein, fiberG, satFatG, sugarG, sodiumMg)

    val rejected: Boolean get() = scan.blocks
}

/**
 * Amendment §1 Plan B: somewhere to eat right now that fits what is left of
 * today and avoids the user's allergens.
 *
 * Offline first: saved eat-out options and imported menu items the local
 * engine has cleared are ranked immediately, with no network. The web search
 * then adds nearby options. Every option - local or found - is run through
 * the local allergen engine; a "contains" match is shown as removed, never
 * hidden, so the user knows why a nearby place is missing.
 */
@Singleton
class PlanBFinder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: AiGateway,
    private val allergens: AllergenRepository,
    private val planDao: PlanDao,
    private val menuDao: MenuDao,
    private val foodLog: FoodLogRepository,
    private val targets: TargetRepository,
    private val clock: Clock,
) {
    companion object {
        const val DEFAULT_RADIUS_MI = 3.2
        const val MENU_OPTION_LIMIT = 60
    }

    suspend fun remaining(): DayProgress {
        val today = clock.today()
        return DayProgress(foodLog.observeDayTotals(today).first(), targets.targetFor(today))
    }

    /** Saved options and cleared menu items, ranked. Never touches the network. */
    suspend fun offlineOptions(): List<PlanBOption> =
        (savedOptions() + menuOptions()).sortedByDescending { it.fit.score }

    suspend fun savedOptions(): List<PlanBOption> {
        val left = remaining()
        return planDao.allEatOutOptions().map { o ->
            option(
                key = "saved:${o.id}", restaurant = o.venueName, address = o.address, distance = null,
                order = o.whatToOrder, modifications = "", macros = Macros(o.estimatedKcal.toDouble(), o.estimatedProtein.toDouble(), o.estimatedCarbs.toDouble(), o.estimatedFat.toDouble()),
                sayThis = o.whatToOrder, caution = o.cautionNote, risk = null, claims = null, left = left,
                offline = true, savedId = o.id,
            )
        }
    }

    /**
     * Imported menu items that were clear when imported and have calories and protein, ranked by how they fit what is
     * left today. The stored verdict can be stale (the person may have changed what they avoid since), so every item is
     * scanned again here, off the main thread, with its stated allergens. An item with no ingredient information is
     * kept but marked [PlanBOption.nameOnly]. At most [limit] are returned: the best fits.
     */
    suspend fun menuOptions(limit: Int = MENU_OPTION_LIMIT): List<PlanBOption> {
        val left = remaining()
        val items = menuDao.clearItems().filter { it.kcal != null && it.proteinG != null }
        if (items.isEmpty()) return emptyList()
        val scanner = allergens.menuScanner()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            items.map { m ->
                val scan = scanner(m.name, m.description, m.ingredientsText)
                val macros = Macros(m.kcal ?: 0.0, m.proteinG ?: 0.0, m.carbsG ?: 0.0, m.fatG ?: 0.0)
                PlanBOption(
                    key = "menu:${m.id}", restaurant = m.restaurantName, address = null, distanceMiles = null,
                    order = m.name, modifications = "", macros = macros, sayThis = "",
                    caution = "From an imported menu - confirm current ingredients when ordering.",
                    crossContactRisk = null, scan = scan,
                    fit = PlanBFit.score(macros.kcal, macros.protein, left.kcalRemaining, left.proteinRemaining),
                    offline = true, menuItemId = m.id,
                    nameOnly = !scan.hasAnything && !scan.hasCheckLabel && MenuSearch.hasNoIngredientInfo(m.ingredientsText),
                )
            }.sortedByDescending { it.fit.score }.take(limit)
        }
    }

    /**
     * The items of one saved menu source, scanned now against what the person avoids, as options for the nearby list:
     * every item with calories and protein, including ones that contain an allergen (shown disabled, never hidden).
     * [distanceMiles] is how far the restaurant is, when known.
     */
    suspend fun menuOptionsOfSource(sourceId: String, distanceMiles: Double?): List<PlanBOption> {
        val left = remaining()
        val items = menuDao.itemsOf(sourceId).filter { it.kcal != null && it.proteinG != null }
        if (items.isEmpty()) return emptyList()
        val scanner = allergens.menuScanner()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            items.map { m ->
                val scan = scanner(m.name, m.description, m.ingredientsText)
                val macros = Macros(m.kcal ?: 0.0, m.proteinG ?: 0.0, m.carbsG ?: 0.0, m.fatG ?: 0.0)
                PlanBOption(
                    key = "menu:${m.id}", restaurant = m.restaurantName, address = null, distanceMiles = distanceMiles,
                    order = m.name, modifications = "", macros = macros, sayThis = "",
                    caution = "From an imported menu - confirm current ingredients when ordering.",
                    crossContactRisk = null, scan = scan,
                    fit = PlanBFit.score(macros.kcal, macros.protein, left.kcalRemaining, left.proteinRemaining),
                    offline = true, menuItemId = m.id,
                    nameOnly = !scan.hasAnything && !scan.hasCheckLabel && MenuSearch.hasNoIngredientInfo(m.ingredientsText),
                )
            }
        }
    }

    /** The current scan of the menu item behind [option]: what is stored with it, against what the person avoids now. */
    suspend fun rescanMenuItem(option: PlanBOption): AllergenScanResult? {
        val id = option.menuItemId ?: return null
        val m = menuDao.item(id) ?: return null
        return allergens.menuScanner()(m.name, m.description, m.ingredientsText)
    }

    /**
     * @param radiusMi how far to look, from Settings > Nutrition > Eat out nearby (miles). It steers the prompt and the
     * results farther than it are dropped here, so a model that ignores the limit still cannot show a far option.
     * @param mode [NearbyMode.SWAP_VS_PLANNED] asks for orders close to [planned] (when there is one); [NearbyMode.HEALTHIER]
     * asks for the healthier orders on the menus. The model only supplies candidates: every one is scanned locally and a
     * claim can only add to a warning.
     */
    suspend fun search(
        area: SearchArea,
        radiusMi: Double = DEFAULT_RADIUS_MI,
        mode: NearbyMode = NearbyMode.SWAP_VS_PLANNED,
        planned: Macros? = null,
        /** "restaurant: order" lines already shown, so a repeat search ("Search again for more") returns different ones. */
        exclude: List<String> = emptyList(),
    ): Pair<List<PlanBOption>, Double> {
        val left = remaining()
        val avoided = allergens.avoidedAllergens()
        val notes = allergens.profileNotes()
        val outcome = gateway.structured(
            feature = AiFeature.PLAN_B,
            trigger = AiTrigger.USER_ACTION,
            serializer = FoundOptions.serializer(),
            schema = PlanBPrompts.schema,
            system = PlanBPrompts.system(avoided, mode),
            parts = listOf(AiPart.Text(userPrompt(area, left, avoided, radiusMi, mode, planned, notes, exclude))),
            tools = listOf(ServerTool.WebSearch(maxResults = 6, maxUses = 3), ServerTool.WebFetch(maxUses = 5)),
        )
        val found = outcome.value.options.filter { o -> o.distanceMiles?.let { it <= radiusMi * 1.25 } ?: true }.map { o ->
            option(
                key = "web:${o.restaurant}:${o.order}", restaurant = o.restaurant, address = o.address,
                distance = o.distanceMiles, order = o.order, modifications = o.modifications,
                macros = Macros(o.kcal, o.protein, o.carbs, o.fat), sayThis = o.sayThis, caution = o.allergenNotes,
                risk = o.crossContactRisk, claims = ModelAllergenClaim(o.order, o.containsAllergens, o.allergenNotes),
                left = left, offline = false,
                fiberG = o.fiberG, satFatG = o.satFatG, sugarG = o.sugarG, sodiumMg = o.sodiumMg,
            )
        }
        return found.sortedWith(compareBy<PlanBOption> { it.rejected }.thenByDescending { it.fit.score }) to
            (outcome.response.costUsd ?: 0.0)
    }

    private suspend fun option(
        key: String, restaurant: String, address: String?, distance: Double?, order: String, modifications: String,
        macros: Macros, sayThis: String, caution: String, risk: String?, claims: ModelAllergenClaim?,
        left: DayProgress, offline: Boolean, savedId: String? = null,
        fiberG: Double? = null, satFatG: Double? = null, sugarG: Double? = null, sodiumMg: Double? = null,
    ): PlanBOption {
        // Scan what is eaten. The modifications are instructions ("no
        // cheese"), and relying on one is a cross-contact risk the matcher
        // already treats as a softened, amber match when it appears in text.
        // Both the order and the modifications are wording about the order, so a clearly negated mention ("no cheese",
        // "without cheese") is not counted as the ingredient. It clears nothing: the rest of the evidence still counts.
        val local = allergens.scan(
            name = order, ingredients = modifications.takeIf { it.isNotBlank() },
            checkLabelInIngredients = modifications.isNotBlank(), freeText = true,
        )
        val scan = ModelAllergenPolicy.merge(
            local = listOf(local),
            claims = listOfNotNull(claims),
            avoided = allergens.avoidedAllergens(),
            source = "Plan B",
        ).scan
        return PlanBOption(
            key = key, restaurant = restaurant, address = address, distanceMiles = distance, order = order,
            modifications = modifications, macros = macros, sayThis = sayThis, caution = caution, crossContactRisk = risk,
            scan = scan, fit = PlanBFit.score(macros.kcal, macros.protein, left.kcalRemaining, left.proteinRemaining),
            offline = offline, savedId = savedId,
            // A negated mention was the only thing the engine found: nothing is known to be in it and nothing is known to
            // be out of it, so it is offered as "check with the restaurant", never as clear.
            nameOnly = scan.hasMentionedAvoided && !scan.hasAnything && !scan.hasCheckLabel,
            fiberG = fiberG, satFatG = satFatG, sugarG = sugarG, sodiumMg = sodiumMg,
        )
    }

    /** "Saved options are available without a signal." */
    suspend fun saveForOffline(option: PlanBOption) {
        if (option.savedId != null) return
        val now = clock.nowMillis()
        planDao.upsertEatOutOption(
            EatOutOptionEntity(
                venueName = option.restaurant,
                whatToOrder = option.sayThis.ifBlank { "${option.order}${if (option.modifications.isNotBlank()) " - ${option.modifications}" else ""}" },
                cautionNote = option.caution,
                estimatedKcal = option.macros.kcal.toInt(),
                estimatedProtein = option.macros.protein.toInt(),
                estimatedCarbs = option.macros.carbs.toInt(),
                estimatedFat = option.macros.fat.toInt(),
                origin = EatOutOrigin.PLAN_B,
                address = option.address,
                allergenStatus = when {
                    option.scan.blocks -> AllergenStatus.CONTAINS
                    option.scan.mayContain.isNotEmpty() -> AllergenStatus.MAY_CONTAIN
                    option.scan.hasCheckLabel -> AllergenStatus.CHECK_LABEL
                    else -> AllergenStatus.CLEAR
                },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun log(option: PlanBOption, slot: MealSlot): com.zeroindex.intake.data.db.entity.FoodLogEntity {
        return foodLog.logPrepared(
            name = "${option.order} · ${option.restaurant}",
            macros = option.macros,
            slot = slot,
            servingLabel = "eat-out estimate",
        )
    }

    /** Keeps the person's "Choose anyway" on a logged entry. The warning itself is never cleared. */
    suspend fun markOverridden(log: com.zeroindex.intake.data.db.entity.FoodLogEntity, note: String) = foodLog.markOverridden(log, note)

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Approximate location only (the coarse permission), then rounded further
     * to about a kilometre by [PlanBFit.coarsen] before it is used.
     */
    @SuppressLint("MissingPermission") // checked by hasLocationPermission() first
    suspend fun currentArea(): SearchArea.Coordinates? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fix: Location? = provider?.let {
            withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { cont ->
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        it,
                        null as android.os.CancellationSignal?,
                        ContextCompat.getMainExecutor(context),
                        androidx.core.util.Consumer<Location?> { loc -> if (cont.isActive) cont.resume(loc) },
                    )
                }
            }
        } ?: runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull()
        return fix?.let { SearchArea.Coordinates(PlanBFit.coarsen(it.latitude), PlanBFit.coarsen(it.longitude)) }
    }

    private fun userPrompt(
        area: SearchArea, left: DayProgress, avoided: Set<String>, radiusMi: Double, mode: NearbyMode, planned: Macros?,
        notes: String, exclude: List<String>,
    ) = buildString {
        when (area) {
            is SearchArea.Coordinates -> appendLine("Approximate location: latitude ${area.lat}, longitude ${area.lng}.")
            is SearchArea.PostalCode -> appendLine("Near postal code ${area.code}.")
        }
        appendLine("Only places within about ${"%.1f".format(java.util.Locale.US, radiusMi)} miles.")
        if (mode == NearbyMode.SWAP_VS_PLANNED && planned != null) {
            appendLine(
                "These stand in for a planned meal of about ${planned.kcal.toInt()} kcal and ${planned.protein.toInt()} g " +
                    "protein: prefer orders close to that.",
            )
        }
        left.kcalRemaining?.let { appendLine("About $it kcal left today.") }
        left.proteinRemaining?.let { appendLine("About $it g protein left today; favour high-protein orders.") }
        if (avoided.isNotEmpty()) appendLine("Allergies to avoid: ${avoided.joinToString()}.")
        if (notes.isNotBlank()) appendLine("Dietary notes from the user: ${notes.take(300)}")
        if (exclude.isNotEmpty()) {
            appendLine("Already shown, do not repeat these; find different orders and, where you can, different restaurants:")
            exclude.take(40).forEach { appendLine("- $it") }
        }
    }
}

/** The Plan B prompt and schema, shared with the in-app benchmark. */
internal object PlanBPrompts {
    fun system(avoided: Set<String>, mode: NearbyMode = NearbyMode.SWAP_VS_PLANNED) = buildString {
        appendLine("You help someone pick a fast, safe meal near them right now.")
        appendLine("Search for restaurants near the given area and read their current menus or nutrition pages.")
        appendLine(
            "Return 8 to 15 options, each a specific order at a specific restaurant, with nutrition for the order as modified. " +
                "Cover as many different restaurants near the area as you can, and give several options per restaurant " +
                "rather than only the single best one. Include chains and independent places.",
        )
        appendLine("Prefer published nutrition; otherwise estimate from the menu description.")
        if (mode == NearbyMode.HEALTHIER) {
            appendLine(
                "Pick the healthier orders on each menu: more protein for the calories, more fibre, and less saturated " +
                    "fat, sugar and salt. Fill fiberG, satFatG, sugarG and sodiumMg only when the menu or nutrition page " +
                    "states them; use null when unknown. Never use 0 for unknown.",
            )
        } else {
            appendLine("Fill fiberG, satFatG, sugarG and sodiumMg when published, otherwise null. Never use 0 for unknown.")
        }
        if (avoided.isNotEmpty()) {
            appendLine(
                "The user must avoid: ${avoided.joinToString()}. Do not only list orders that are already free of them: " +
                    "most menus have items that become safe with the usual modifications, for example, for dairy, " +
                    "\"no cheese, no butter, ask for oil instead\", sauce on the side, grilled not buttered or breaded. " +
                    "Offer those, and say exactly what to ask to leave off in modifications. Name the order as it is " +
                    "ordered, for example \"Whopper, no cheese\". List anything in the order that may still contain " +
                    "them in containsAllergens, and never leave an allergen out of it to make an option look safe.",
            )
            appendLine(
                "In sayThis, write the words to say when ordering: name the allergy, ask for clean gloves and " +
                    "utensils, list what to leave off, and ask them to confirm ingredients.",
            )
        }
        appendLine("Rate crossContactRisk low, medium or high for how the item is usually prepared.")
        append("Never invent a restaurant. Reply with JSON only, matching the schema.")
    }

    val schema = S.obj(
        "options" to S.array(
            S.obj(
                "restaurant" to S.string(),
                "address" to S.string(),
                "distanceMiles" to S.nullableNumber(),
                "order" to S.string("The dish as ordered"),
                "modifications" to S.string("What to ask to leave off or change; empty if none"),
                "kcal" to S.number(), "protein" to S.number(), "carbs" to S.number(), "fat" to S.number(),
                "containsAllergens" to S.array(S.string()),
                "allergenNotes" to S.string(),
                "sayThis" to S.string(),
                "crossContactRisk" to S.enumOf("low", "medium", "high"),
                "sourceUrl" to S.string(),
                "fiberG" to S.nullableNumber("Grams of fibre, or null if unknown"),
                "satFatG" to S.nullableNumber("Grams of saturated fat, or null if unknown"),
                "sugarG" to S.nullableNumber("Grams of sugar, or null if unknown"),
                "sodiumMg" to S.nullableNumber("Milligrams of sodium, or null if unknown"),
            ),
        ),
    )
}
