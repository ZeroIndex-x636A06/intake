package com.zeroindex.intake.domain.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The parameters of a Recipe Hunter search, as saved. The avoided allergens are not stored: they are read fresh on every reopen. */
@Serializable
data class SavedHuntQuery(
    val minProteinPerServing: Int? = null,
    val maxKcalPerServing: Int? = null,
    val servings: Int = 4,
    val maxMinutes: Int? = null,
    val cuisines: List<String> = emptyList(),
    val notes: String = "",
    val aiReadPages: Boolean = false,
) {
    fun toQuery(avoided: Set<String>) = RecipeQuery(
        minProteinPerServing, maxKcalPerServing, servings, maxMinutes, cuisines, notes, avoided, aiReadPages,
    )

    companion object {
        fun of(q: RecipeQuery) = SavedHuntQuery(
            q.minProteinPerServing, q.maxKcalPerServing, q.servings, q.maxMinutes, q.cuisines, q.notes, q.aiReadPages,
        )
    }
}

/**
 * One result as saved: the recipe as the page gave it, plus the hint the search snippet carried. The verdict is not
 * stored: reopening re-judges every recipe on this phone with the allergen profile as it is now, so a changed
 * profile is honoured and a stored "passed" is never trusted.
 */
@Serializable
data class SavedHuntRecipe(
    val title: String,
    val sourceUrl: String,
    val sourceName: String = "",
    val servings: Int = 4,
    val totalMinutes: Int? = null,
    val ingredients: List<String> = emptyList(),
    val steps: List<String> = emptyList(),
    val kcalPerServing: Double? = null,
    val proteinPerServing: Double? = null,
    val carbsPerServing: Double? = null,
    val fatPerServing: Double? = null,
    val imageUrl: String? = null,
    val aiRead: Boolean = false,
    /** Set when the page could not be read: it reopens as "Could not check" with this reason. */
    val unverifiedReason: String? = null,
    val possibleAllergens: List<String> = emptyList(),
    val allergenNote: String = "",
)

/** The counts at the time of the search, so the list can show a summary without re-judging anything. */
@Serializable
data class SavedHuntCounts(
    val kept: Int = 0,
    val close: Int = 0,
    val adaptable: Int = 0,
    val removed: Int = 0,
    val unverified: Int = 0,
)

@Serializable
data class SavedHunt(
    val id: String,
    val createdAt: Long,
    val query: SavedHuntQuery,
    val counts: SavedHuntCounts,
    val costUsd: Double,
    val rounds: Int,
    val model: String = "",
    val stop: String = HuntStop.TARGET.name,
    val candidatesSeen: Int = 0,
    val aiReads: Int = 0,
    /** Empty once the search is soft-deleted: the entry stays as a marker, its payload is dropped. */
    val results: List<SavedHuntRecipe> = emptyList(),
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/**
 * The Recent searches list: newest first, [CAP] live entries, soft delete. Pure, so it is unit tested; the store
 * ([com.zeroindex.intake.data.ai.recipes.RecentHuntsStore]) only reads and writes the JSON.
 */
object RecentHunts {
    const val CAP = 20

    /** Soft-deleted markers kept besides the live ones, so the file cannot grow without end. */
    const val MAX_MARKERS = 60

    private val json = Json { ignoreUnknownKeys = true }

    fun live(all: List<SavedHunt>): List<SavedHunt> = all.filter { !it.isDeleted }.sortedByDescending { it.createdAt }

    /** [hunt] added as the newest; live entries beyond [CAP] (the oldest) are soft-deleted. An entry with the same id is replaced. */
    fun add(all: List<SavedHunt>, hunt: SavedHunt, now: Long): List<SavedHunt> =
        trim(enforceCap(all.filterNot { it.id == hunt.id } + hunt, now))

    fun softDelete(all: List<SavedHunt>, id: String, now: Long): List<SavedHunt> =
        all.map { if (it.id == id && !it.isDeleted) it.tombstone(now) else it }

    private fun enforceCap(all: List<SavedHunt>, now: Long): List<SavedHunt> {
        val extra = live(all).drop(CAP).map { it.id }.toSet()
        return all.map { if (it.id in extra) it.tombstone(now) else it }
    }

    private fun trim(all: List<SavedHunt>): List<SavedHunt> {
        val markers = all.filter { it.isDeleted }.sortedByDescending { it.deletedAt }.drop(MAX_MARKERS).map { it.id }.toSet()
        return all.filterNot { it.id in markers }
    }

    private fun SavedHunt.tombstone(now: Long) = copy(deletedAt = now, results = emptyList())

    fun encode(all: List<SavedHunt>): String = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(SavedHunt.serializer()), all)

    /** A damaged or unreadable value gives an empty list, never an exception. */
    fun decode(text: String?): List<SavedHunt> =
        if (text.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SavedHunt.serializer()), text) }.getOrDefault(emptyList())
}
