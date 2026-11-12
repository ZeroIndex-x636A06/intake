package com.zeroindex.intake.wearprotocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * The watch as a quick remote (docs/WEAR.md). The phone stays the source of truth.
 *
 * - Phone -> watch: one [RemoteState] DataItem at [RemoteProtocol.PATH_STATE], the day at a glance plus the short lists
 *   the watch offers (water sizes, recent foods, templates, today's planned meal and workout).
 * - Watch -> phone: one [RemoteRequest] DataItem per tap at `PATH_REQ_PREFIX + id`. A DataItem is stored by Play
 *   services and delivered when the other side is next reachable, so a request made with the phone out of range waits and
 *   goes across later. That stored item is the watch's offline queue; "pending" is simply a request with no answer yet.
 * - Phone -> watch: a [RemoteResponse] at `PATH_RES_PREFIX + id` once the phone has acted. The phone then deletes the
 *   request; the watch deletes the response once it has shown it.
 *
 * Every request has a client-generated id, and the phone remembers the answer it gave ([RequestLedger]), so a request
 * delivered twice is applied once.
 */
object RemoteProtocol {
    const val PATH_STATE = "/intake/remote/state"
    const val PATH_REQ_PREFIX = "/intake/remote/req/"
    const val PATH_RES_PREFIX = "/intake/remote/res/"

    /** A request older than this is not applied: a glass of water logged two days late would be a wrong number. */
    const val MAX_REQUEST_AGE_MS = 48L * 60 * 60 * 1000

    /** How far ahead of the phone's clock a request's time may be before it is treated as "now". */
    const val CLOCK_SKEW_MS = 5L * 60 * 1000

    const val MAX_WATER_ML = 3000.0
    const val MAX_QUICK_KCAL = 3000
    const val MAX_FOOD_GRAMS = 3000.0

    const val ML_PER_US_FL_OZ = 29.5735295625

    fun encode(state: RemoteState): String = WearProtocol.json.encodeToString(RemoteState.serializer(), state)
    fun decodeState(text: String): RemoteState = WearProtocol.json.decodeFromString(RemoteState.serializer(), text)
    fun encode(request: RemoteRequest): String = WearProtocol.json.encodeToString(RemoteRequest.serializer(), request)
    fun decodeRequest(text: String): RemoteRequest = WearProtocol.json.decodeFromString(RemoteRequest.serializer(), text)
    fun encode(response: RemoteResponse): String = WearProtocol.json.encodeToString(RemoteResponse.serializer(), response)
    fun decodeResponse(text: String): RemoteResponse = WearProtocol.json.decodeFromString(RemoteResponse.serializer(), text)

    /** "500 ml" or "16.9 fl oz": the phone's own wording, from a stored millilitre amount. */
    fun formatVolume(ml: Double, unit: String): String {
        if (unit != UNIT_OZ) return "${Math.round(ml)} ml"
        val oz = Math.round(ml / ML_PER_US_FL_OZ * 10) / 10.0
        val text = if (oz % 1.0 == 0.0) oz.toLong().toString() else oz.toString()
        return "$text fl oz"
    }

    /** One tap of the custom +/- picker: 50 ml, or 1 fl oz. */
    fun stepMl(unit: String): Double = if (unit == UNIT_OZ) ML_PER_US_FL_OZ else 50.0

    /** Rounds a picker amount to whole millilitres, as the phone does for a typed size. */
    fun wholeMl(ml: Double): Double = Math.round(ml).toDouble().coerceAtLeast(1.0)

    const val UNIT_ML = "ml"
    const val UNIT_OZ = "fl oz"
}

/** The meal slots, with the phone's own time-of-day boundaries (TemplateLink.slotFor): before 10, 15, 21. */
enum class WatchSlot(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    ;

    companion object {
        fun forHour(hour: Int): WatchSlot = when {
            hour < 10 -> BREAKFAST
            hour < 15 -> LUNCH
            hour < 21 -> DINNER
            else -> SNACK
        }
    }
}

@Serializable
enum class RequestKind { WATER, LOG_PLANNED, LOG_FOOD, LOG_TEMPLATE, QUICK_ADD, START_WORKOUT }

/** One tap on the watch. Only the fields its [kind] needs are set. */
@Serializable
data class RemoteRequest(
    /** Generated on the watch. The phone applies a given id once. */
    val id: String,
    val kind: RequestKind,
    /** When the person tapped, UTC millis. The phone logs the entry at this time. */
    val at: Long,
    val volumeMl: Double? = null,
    /** A water quick size's preset id, so a tea or coffee preset keeps its drink type. */
    val presetId: String? = null,
    val foodId: String? = null,
    val grams: Double? = null,
    val templateId: String? = null,
    val kcal: Int? = null,
    val slot: WatchSlot? = null,
    /** START_WORKOUT: the program day, or null for an empty workout. */
    val programDayId: String? = null,
    /** What the person saw on the button, for the pending list ("Log Greek yogurt"). */
    val label: String = "",
) {
    /** Null when the request is well formed, else why the phone should refuse it. */
    fun validate(): String? = when {
        id.isBlank() -> "No request id"
        kind == RequestKind.WATER && (volumeMl == null || volumeMl <= 0.0 || volumeMl > RemoteProtocol.MAX_WATER_ML) -> "Bad water amount"
        kind == RequestKind.LOG_FOOD && (foodId.isNullOrBlank() || (grams != null && (grams <= 0.0 || grams > RemoteProtocol.MAX_FOOD_GRAMS))) -> "Bad food entry"
        kind == RequestKind.LOG_TEMPLATE && templateId.isNullOrBlank() -> "No template"
        kind == RequestKind.QUICK_ADD && (kcal == null || kcal <= 0 || kcal > RemoteProtocol.MAX_QUICK_KCAL) -> "Bad calories"
        else -> null
    }

    /**
     * The time to log at, or null when the request is too old. A time slightly ahead of the phone's clock (the two clocks
     * differ by a few seconds) counts as now.
     */
    fun effectiveTime(nowMillis: Long): Long? = when {
        nowMillis - at > RemoteProtocol.MAX_REQUEST_AGE_MS -> null
        at > nowMillis + RemoteProtocol.CLOCK_SKEW_MS -> nowMillis
        else -> minOf(at, nowMillis)
    }
}

@Serializable
enum class ResponseStatus {
    /** Done. [RemoteResponse.message] says what was logged. */
    DONE,
    /** The phone's allergen engine found something the person avoids in the food. Nothing was logged. */
    BLOCKED,
    /** Not logged: the phone wants a decision (a possible allergen, incomplete macros). Finish it on the phone. */
    CHECK_ON_PHONE,
    NOT_FOUND,
    EXPIRED,
    FAILED,
    ;

    val logged: Boolean get() = this == DONE
}

@Serializable
data class RemoteResponse(
    val requestId: String,
    val status: ResponseStatus,
    val message: String,
    /** Allergen terms, when the phone's check had something to say. Shown as given; the watch never clears one. */
    val warnings: List<String> = emptyList(),
)

@Serializable
data class WaterSize(val label: String, val ml: Int, val presetId: String? = null)

@Serializable
data class WatchFood(
    val id: String,
    val name: String,
    /** The default portion, in grams. */
    val grams: Double,
    val portionLabel: String,
    val kcal: Int,
)

@Serializable
data class WatchTemplate(val id: String, val name: String, val kcal: Int)

@Serializable
data class PlannedMeal(
    val name: String,
    val kcal: Int?,
    val proteinG: Int?,
    /** The phone's allergen verdict for it now: NONE, MAY_CONTAIN or CONTAINS. Shown on the button. */
    val allergen: String = "NONE",
)

@Serializable
data class TodayWorkout(val dayId: String, val name: String)

/** The phone's day, as the watch shows it. */
@Serializable
data class RemoteState(
    /** The phone's date, `yyyy-MM-dd`. A watch whose own date differs treats the numbers as stale. */
    val dateKey: String,
    val kcal: Int,
    val kcalTarget: Int?,
    val proteinG: Int,
    val proteinTargetG: Int?,
    val waterMl: Int,
    val waterTargetMl: Int?,
    /** "ml" or "fl oz": the phone's volume unit. */
    val volumeUnit: String = RemoteProtocol.UNIT_ML,
    val waterSizes: List<WaterSize> = emptyList(),
    val planned: PlannedMeal? = null,
    val recents: List<WatchFood> = emptyList(),
    val favourites: List<WatchFood> = emptyList(),
    val templates: List<WatchTemplate> = emptyList(),
    val todayWorkout: TodayWorkout? = null,
    val updatedAt: Long,
) {
    fun isFor(dateKey: String): Boolean = this.dateKey == dateKey

    companion object {
        /** Cap on each list, so the DataItem stays far below the Data Layer's 100 KB limit. */
        const val MAX_LIST = 8
    }
}

/**
 * What the phone has already answered, by request id, so a request that arrives twice is not applied twice and the
 * watch still gets its answer. Oldest entries go first. Pure; the phone keeps its encoded form in preferences.
 */
class RequestLedger(private val capacity: Int = 200, entries: Map<String, RemoteResponse> = emptyMap()) {
    private val map = LinkedHashMap<String, RemoteResponse>().apply { putAll(entries) }

    operator fun get(requestId: String): RemoteResponse? = map[requestId]

    fun record(response: RemoteResponse) {
        map.remove(response.requestId)
        map[response.requestId] = response
        while (map.size > capacity) map.remove(map.keys.first())
    }

    val size: Int get() = map.size

    fun encode(): String = WearProtocol.json.encodeToString(
        MapSerializer(String.serializer(), RemoteResponse.serializer()), map,
    )

    companion object {
        fun decode(text: String?, capacity: Int = 200): RequestLedger {
            val entries = text?.let {
                runCatching {
                    WearProtocol.json.decodeFromString(MapSerializer(String.serializer(), RemoteResponse.serializer()), it)
                }.getOrNull()
            }.orEmpty()
            return RequestLedger(capacity, entries)
        }
    }
}

/**
 * The answers the phone gives. Allergen wording is fixed here so the watch only ever shows a warning as the phone
 * worded it: nothing on the watch turns a BLOCKED or CHECK_ON_PHONE answer into a logged one.
 */
object RemoteReplies {
    fun done(id: String, message: String, warnings: List<String> = emptyList()) =
        RemoteResponse(id, ResponseStatus.DONE, message, warnings)

    fun blocked(id: String, subject: String, terms: List<String>) = RemoteResponse(
        id, ResponseStatus.BLOCKED,
        "Not logged: $subject matches your allergen profile" + termsText(terms) + ".",
        terms.distinct(),
    )

    /** A possible allergen (may contain) or anything else that needs a person's decision: the phone's sheet decides. */
    fun checkOnPhone(id: String, subject: String, why: String, terms: List<String> = emptyList()) = RemoteResponse(
        id, ResponseStatus.CHECK_ON_PHONE, "Not logged: $subject. $why", terms.distinct(),
    )

    fun notFound(id: String, what: String) = RemoteResponse(id, ResponseStatus.NOT_FOUND, "$what is not on the phone any more.")
    fun expired(id: String) = RemoteResponse(id, ResponseStatus.EXPIRED, "Too old to log. Add it again.")
    fun failed(id: String, why: String) = RemoteResponse(id, ResponseStatus.FAILED, why)

    private fun termsText(terms: List<String>) = terms.distinct().takeIf { it.isNotEmpty() }?.joinToString(prefix = " (", postfix = ")") ?: ""
}

/** How the watch words a pending request: "Pending, will sync". */
fun pendingLine(request: RemoteRequest): String {
    val what = request.label.ifBlank {
        when (request.kind) {
            RequestKind.WATER -> "Water"
            RequestKind.LOG_PLANNED -> "Planned meal"
            RequestKind.LOG_FOOD -> "Food"
            RequestKind.LOG_TEMPLATE -> "Meal"
            RequestKind.QUICK_ADD -> "Calories"
            RequestKind.START_WORKOUT -> "Start workout"
        }
    }
    return "$what: pending, will sync"
}
