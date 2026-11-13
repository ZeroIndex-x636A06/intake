package com.zeroindex.intake.domain.videos

import com.zeroindex.intake.domain.training.muscles.BodyRegion

/** One search for the Recommended page and the plain reason shown on its rows. */
data class RecommendedQuery(val query: String, val reason: String)

/** Everything the query builder may look at. All of it comes from this phone; none of it is sent anywhere but the words of the queries. */
data class RecommendInput(
    /** Regions under their weekly target, most under first. */
    val underTarget: List<BodyRegion> = emptyList(),
    /** The equipment the person owns (library names: barbell, dumbbell, machine, cable, bodyweight). */
    val equipment: Set<String> = emptySet(),
    /** The active program's focus or name ("Upper / Lower"), if there is one. */
    val programFocus: String? = null,
    /** Titles of saved videos. */
    val savedTitles: List<String> = emptyList(),
    /** Channels of the saved videos, one per title in the same order (blank when unknown). */
    val savedChannels: List<String> = emptyList(),
    /** The person's session length in minutes, if known. */
    val sessionMinutes: Int? = null,
)

/**
 * Recommended is a few searches built from local data: this week's under-target muscles, equipment, the program's focus and
 * what the person saved. With "Personalise Recommended" off, a fixed set of popular queries is used instead and nothing
 * about the person shapes them. No model is involved either way.
 */
object RecommendedQueries {
    /** Each search costs about 100 quota units, so there are at most this many per refresh. */
    const val MAX_QUERIES = 3

    val GENERIC: List<RecommendedQuery> = listOf(
        RecommendedQuery("full body workout", "Popular: full body"),
        RecommendedQuery("15 minute workout", "Popular: short and quick"),
        RecommendedQuery("yoga for beginners", "Popular: yoga"),
        RecommendedQuery("mobility routine", "Popular: mobility"),
    )

    private val REGION_WORDS = mapOf(
        BodyRegion.CHEST to "chest", BodyRegion.BACK to "back", BodyRegion.SHOULDERS to "shoulder",
        BodyRegion.ARMS to "arm", BodyRegion.LEGS to "leg", BodyRegion.CORE to "core abs",
    )

    private val STYLE_WORDS = listOf("pilates", "yoga", "mobility", "hiit", "stretching", "calisthenics", "kettlebell", "boxing")

    fun build(personalise: Boolean, input: RecommendInput): List<RecommendedQuery> {
        if (!personalise) return GENERIC.take(MAX_QUERIES)
        val out = mutableListOf<RecommendedQuery>()
        val gear = equipmentWords(input.equipment)

        input.underTarget.take(2).forEach { region ->
            val word = REGION_WORDS.getValue(region)
            val label = region.label.lowercase()
            out += RecommendedQuery("$word workout $gear".trim(), "For: $label under target this week")
        }

        // "Because you saved ...": the saved videos' channels and title keywords (SavedSimilarity), with the style named when
        // a saved title is a known style.
        if (input.savedTitles.isNotEmpty()) {
            val saved = input.savedTitles.mapIndexed { i, t -> VideoMeta(id = "s$i", title = t, channelTitle = input.savedChannels.getOrNull(i).orEmpty()) }
            val style = input.savedTitles.firstNotNullOfOrNull { title ->
                STYLE_WORDS.firstOrNull { WorkoutVideoClassifier.containsWord(title.lowercase(), it) }
            }
            val q = SavedSimilarity.query(saved)
            if (q != null) {
                val withStyle = if (style != null && style !in q.lowercase()) "$q $style" else q
                out += RecommendedQuery(withStyle, if (style != null) "Because you saved a $style video" else SavedSimilarity.reason(saved))
            }
        }

        input.programFocus?.takeIf { it.isNotBlank() }?.let { focus ->
            val f = focus.lowercase()
            val body = when {
                "upper" in f && "lower" in f -> "upper lower split"
                "upper" in f -> "upper body"
                "lower" in f || "leg" in f -> "lower body"
                "push" in f -> "push day"
                "pull" in f -> "pull day"
                "full" in f -> "full body"
                else -> null
            }
            if (body != null) out += RecommendedQuery("$body workout $gear".trim(), "For your program: ${focus.trim()}")
        }

        input.sessionMinutes?.takeIf { it in 10..90 }?.let { m ->
            val rounded = (m / 5) * 5
            out += RecommendedQuery("$rounded minute workout $gear".trim(), "Fits your $m-minute sessions")
        }

        val distinct = out.distinctBy { it.query }.take(MAX_QUERIES)
        // Nothing local to go on (a new install, everything on target): the popular ones.
        return distinct.ifEmpty { GENERIC.take(MAX_QUERIES) }
    }

    /** "no equipment", "dumbbell" and so on, from what the person owns. */
    internal fun equipmentWords(owned: Set<String>): String {
        val o = owned.map { it.lowercase() }.toSet()
        return when {
            o.isEmpty() -> ""
            o == setOf("bodyweight") -> "no equipment"
            o.all { it == "bodyweight" || it == "dumbbell" } -> "dumbbell"
            "barbell" in o || "machine" in o || "cable" in o -> "gym"
            else -> ""
        }
    }
}
