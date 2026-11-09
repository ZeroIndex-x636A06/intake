package com.zeroindex.intake.domain.hydration

import kotlin.math.max
import kotlin.math.min

/** The day's drinks added up. [effectiveMl] is what counts toward the target and is never below zero. */
data class DayDrinkTotals(
    val rawMl: Double,
    val effectiveMl: Double,
    val caffeineMg: Double,
    val standardDrinks: Double,
    val largestCaffeineServingMg: Double,
) {
    companion object {
        val EMPTY = DayDrinkTotals(0.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Caffeine and alcohol, as plain numbers and plain notes.
 *
 * - 400 mg a day is the FDA's general guidance for healthy adults, so that is where the gentle note starts.
 * - Large amounts of caffeine can raise urine output a little, so from 300 mg we add 100 ml, and 200 ml
 *   above 400 mg. Tea and coffee still count in full toward the day.
 * - A single serving over 200 mg gets its own note: the acute effect is a high-single-dose one.
 * - Alcohol adds 200 ml per standard drink (14 g of alcohol), up to 1 litre, because it makes you pass more
 *   urine. The drink's own hydration factor already counts what it contributes.
 */
object CaffeineAlcohol {
    const val DAILY_NOTE_MG = 400.0
    const val TOPUP_FROM_MG = 300.0
    const val SERVING_NOTE_MG = 200.0
    const val CAFFEINE_TOPUP_MODERATE_ML = 100.0
    const val CAFFEINE_TOPUP_HIGH_ML = 200.0
    const val ALCOHOL_TOPUP_ML_PER_STANDARD_DRINK = 200.0
    const val ALCOHOL_TOPUP_CAP_ML = 1_000.0

    fun totals(entries: List<DrinkEntry>): DayDrinkTotals = DayDrinkTotals(
        rawMl = entries.sumOf { it.volumeMl },
        effectiveMl = max(0.0, entries.sumOf { it.effectiveMl }),
        caffeineMg = entries.sumOf { it.caffeineMg },
        standardDrinks = entries.sumOf { it.standardDrinks },
        largestCaffeineServingMg = entries.maxOfOrNull { it.caffeineMg } ?: 0.0,
    )

    fun caffeineTopUpMl(caffeineMg: Double): Double = when {
        caffeineMg > DAILY_NOTE_MG -> CAFFEINE_TOPUP_HIGH_ML
        caffeineMg >= TOPUP_FROM_MG -> CAFFEINE_TOPUP_MODERATE_ML
        else -> 0.0
    }

    fun alcoholTopUpMl(standardDrinks: Double): Double =
        min(max(0.0, standardDrinks) * ALCOHOL_TOPUP_ML_PER_STANDARD_DRINK, ALCOHOL_TOPUP_CAP_ML)

    const val TEA_COFFEE_COUNT =
        "Tea and coffee count toward your water. At normal amounts they hydrate about as well as water does."
    const val CAFFEINE_TOPUP_WHY = "Large amounts of caffeine can increase urine output a little."
    const val ALCOHOL_TOPUP_WHY =
        "Alcohol makes you pass more urine, so each standard drink adds a glass of water to aim for."

    /** Gentle, factual notes for the day. Never alarming: nothing here is a diagnosis. */
    fun notes(t: DayDrinkTotals): List<String> = buildList {
        if (t.caffeineMg > DAILY_NOTE_MG) {
            add(
                "About ${t.caffeineMg.toInt()} mg of caffeine today. Around 400 mg a day is the general guidance for healthy " +
                    "adults, so you may want to ease off for the rest of the day, especially before sleep.",
            )
        }
        if (t.largestCaffeineServingMg > SERVING_NOTE_MG) {
            add(
                "One drink had about ${t.largestCaffeineServingMg.toInt()} mg of caffeine. A single dose over about 200 mg " +
                    "can raise urine output a little and is a lot in one go.",
            )
        }
        if (t.standardDrinks >= 0.5) {
            add("About ${"%.1f".format(t.standardDrinks)} standard drinks of alcohol today.")
        }
    }
}
