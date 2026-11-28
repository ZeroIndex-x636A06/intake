package com.zeroindex.intake.domain.ai

import java.net.URI
import kotlin.math.roundToInt

/**
 * The pure rules behind Recipe Hunter's loop: what counts as the same recipe, how a recipe's numbers
 * compare with the search, and when the rounds stop. No network, no Android, so all of it is unit tested.
 */

/** Normalises addresses and titles so one recipe is not counted twice. */
object HuntKeys {
    private val TRACKING = setOf("fbclid", "gclid", "ref", "source", "mc_cid", "mc_eid", "igshid", "cmp", "ncid")

    /** Host without "www.", path without a trailing slash, tracking parameters and fragment dropped, scheme ignored. */
    fun urlKey(url: String): String {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val host = uri?.host?.lowercase()?.removePrefix("www.") ?: return trimmed.lowercase().trimEnd('/')
        val path = (uri.rawPath ?: "").trimEnd('/')
        val query = (uri.rawQuery ?: "").split('&')
            .filter { it.isNotBlank() }
            .filterNot { p -> p.substringBefore('=').lowercase().let { it.startsWith("utm_") || it in TRACKING } }
            .sorted()
            .joinToString("&")
        return host + path.lowercase() + if (query.isEmpty()) "" else "?$query"
    }

    /** Lower-case letters and digits only, so "Chicken Burrito-Bowls!" and "chicken burrito bowls" match. */
    fun titleKey(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }
}

/** Remembers every address and title the hunt has seen, whatever became of it, so none is asked for twice. */
class HuntDedupe {
    private val urls = mutableSetOf<String>()
    private val titles = mutableSetOf<String>()

    fun isDuplicate(url: String, title: String): Boolean =
        HuntKeys.urlKey(url) in urls || HuntKeys.titleKey(title).let { it.isNotEmpty() && it in titles }

    /** True when it was new (and is now remembered). */
    fun add(url: String, title: String): Boolean {
        if (isDuplicate(url, title)) return false
        urls += HuntKeys.urlKey(url)
        HuntKeys.titleKey(title).takeIf { it.isNotEmpty() }?.let { titles += it }
        return true
    }
}

enum class Fit {
    /** Every stated number is inside the limits. */
    MATCH,

    /** Something is over a limit, but by no more than [RecipeFit.NEAR_MISS]. */
    CLOSE,

    /** Something is further out than that. */
    OUT,

    /** The page states no protein or calories, so there was nothing to check. Shown, never dropped. */
    UNCHECKED,
}

/** Which limit a recipe misses. [chip] is the short label for a card; the full wording is in [FitResult.reasons]. */
enum class FitIssue(val chip: String) {
    PROTEIN("Under protein goal"),
    KCAL("Over calorie cap"),
    TIME("Over time limit"),
}

/**
 * [reasons] lists every limit the recipe misses, near misses included, so a recipe that fails on several counts says
 * so. [fit] is the worst of them; [reason] is all of them joined, or null when none.
 */
data class FitResult(
    val fit: Fit,
    val reasons: List<String> = emptyList(),
    val issues: List<FitIssue> = emptyList(),
) {
    val reason: String? get() = reasons.takeIf { it.isNotEmpty() }?.joinToString("; ")
}

object RecipeFit {
    /** How far past a limit still counts as a near miss. */
    const val NEAR_MISS = 0.10

    /**
     * Compares a page's own published numbers with the search. A null number is "not stated": it is not
     * checked and never counted against the recipe. Any OUT wins over any CLOSE, and every miss is listed.
     */
    fun assess(protein: Double?, kcal: Double?, minutes: Int?, query: RecipeQuery): FitResult {
        var worst = Fit.MATCH
        val reasons = mutableListOf<String>()
        val issues = mutableListOf<FitIssue>()
        fun note(level: Fit, issue: FitIssue, text: String) {
            if (level.ordinal > worst.ordinal) worst = level
            reasons += text
            issues += issue
        }
        query.minProteinPerServing?.let { goal ->
            if (protein != null && protein < goal) {
                val text = "${protein.roundToInt()} g protein per serving, goal ≥ $goal g"
                note(if (protein >= goal * (1 - NEAR_MISS)) Fit.CLOSE else Fit.OUT, FitIssue.PROTEIN, text)
            }
        }
        query.maxKcalPerServing?.let { cap ->
            if (kcal != null && kcal > cap) {
                val text = "${kcal.roundToInt()} kcal per serving, goal ≤ $cap"
                note(if (kcal <= cap * (1 + NEAR_MISS)) Fit.CLOSE else Fit.OUT, FitIssue.KCAL, text)
            }
        }
        query.maxMinutes?.let { cap ->
            if (minutes != null && minutes > cap) {
                val text = "Takes $minutes min, goal ≤ $cap"
                note(if (minutes <= cap * (1 + NEAR_MISS)) Fit.CLOSE else Fit.OUT, FitIssue.TIME, text)
            }
        }
        val checkedSomething = protein != null || kcal != null
        return when {
            worst != Fit.MATCH -> FitResult(worst, reasons, issues)
            !checkedSomething -> FitResult(Fit.UNCHECKED)
            else -> FitResult(Fit.MATCH)
        }
    }
}

/** Why the loop stopped. Shown to the person so a short list is explained, not mysterious. */
enum class HuntStop(val label: String) {
    TARGET("Found enough"),
    ROUNDS("Reached the round limit"),
    COST("Reached the cost limit"),
    DRIED_UP("The search ran out of new recipes"),
    FAILED("A search round failed, so this is what was found so far"),
}

/** The limits of one search, or of one "Find more" continuation. */
data class HuntPolicy(
    val targetKept: Int = 12,
    val maxRounds: Int = 4,
    /** USD. About the price of the old three-round search; "Find more" gets a fresh one. */
    val capUsd: Double = 0.08,
    /** Candidates asked of the model in each round. */
    val perRound: Int = 10,
)

object HuntRules {
    /**
     * Whether to stop before starting another round, or null to go on. [lastRoundCostUsd] predicts the
     * next round's price, so the cap is a ceiling and not a line the last round steps over.
     * [roundsDone] and [costUsd] count from the start of this search or continuation.
     */
    fun stopReason(
        policy: HuntPolicy,
        keptThisRun: Int,
        roundsDone: Int,
        costUsd: Double,
        lastRoundCostUsd: Double,
        lastRoundNewCandidates: Int?,
    ): HuntStop? = when {
        keptThisRun >= policy.targetKept -> HuntStop.TARGET
        roundsDone >= policy.maxRounds -> HuntStop.ROUNDS
        roundsDone > 0 && costUsd + lastRoundCostUsd > policy.capUsd -> HuntStop.COST
        lastRoundNewCandidates == 0 -> HuntStop.DRIED_UP
        else -> null
    }
}

/**
 * What "Use AI to read pages the phone can't" may still spend: the part of the search's cap not already used,
 * and a count of pages. Shared by the pages of one round, which are read a few at a time, so it is thread safe.
 * The estimate is a prediction; [record] adds what the call really cost.
 */
class AiReadBudget(private var remainingUsd: Double, private var remainingReads: Int) {
    var spentUsd: Double = 0.0
        private set
    var reads: Int = 0
        private set

    /** True, and the estimate is held back, when one more read fits under the cap and the page limit. */
    @Synchronized
    fun tryReserve(estimateUsd: Double): Boolean {
        if (remainingReads <= 0 || estimateUsd > remainingUsd) return false
        remainingReads--
        remainingUsd -= estimateUsd
        return true
    }

    /** Settles a reserved read: [actualUsd] replaces its [estimateUsd]. A failed call still cost what the provider billed. */
    @Synchronized
    fun record(estimateUsd: Double, actualUsd: Double) {
        remainingUsd += estimateUsd - actualUsd
        spentUsd += actualUsd
        reads++
    }
}
