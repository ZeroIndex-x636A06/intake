package com.zeroindex.intake.domain.nutrition

/** One logged entry as the dashboard sees it: its macros and the optional nutrients its source did not give. */
data class DayEntry(val macros: Macros, val unlisted: Set<NutrientKey> = emptySet())

/** How far a nutrient's total can be trusted. */
enum class Coverage {
    /** Nothing logged yet: the total is a true zero. */
    NONE_LOGGED,

    /** Every logged entry lacked it: show "not listed", never 0. */
    ALL_UNLISTED,

    /** Some entries lacked it: the total is a floor, and the card says how many foods had no value. */
    PARTIAL,

    /** Every entry listed it. */
    COMPLETE,
}

/** One optional nutrient summed over the day. [total] only counts entries that listed it. */
data class NutrientTotal(val key: NutrientKey, val total: Double, val unlistedEntries: Int, val entries: Int) {
    val coverage: Coverage
        get() = when {
            entries == 0 -> Coverage.NONE_LOGGED
            unlistedEntries == entries -> Coverage.ALL_UNLISTED
            unlistedEntries > 0 -> Coverage.PARTIAL
            else -> Coverage.COMPLETE
        }

    /** The number to show, or null for "not listed". */
    val value: Double? get() = if (coverage == Coverage.ALL_UNLISTED) null else total

    /** "2 foods have no fibre value", or null when nothing is missing. */
    val note: String?
        get() = if (coverage == Coverage.PARTIAL) {
            "$unlistedEntries ${if (unlistedEntries == 1) "food has" else "foods have"} no ${key.label} value"
        } else null
}

/**
 * The Nutrition dashboard's day: energy and macros are always known, the four optional nutrients carry their
 * coverage so an unknown is "not listed" and a partly known total says so ([NutrientTotal]).
 */
data class DayNutrition(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: NutrientTotal,
    val sugar: NutrientTotal,
    val sodium: NutrientTotal,
    val saturatedFat: NutrientTotal,
    val entries: Int,
) {
    fun total(key: NutrientKey): NutrientTotal = when (key) {
        NutrientKey.FIBER -> fiber
        NutrientKey.SUGAR -> sugar
        NutrientKey.SODIUM -> sodium
        NutrientKey.SATURATED_FAT -> saturatedFat
    }

    /** Sodium as salt in grams, or null when sodium is not listed. */
    val saltGrams: Double? get() = sodium.value?.let(DailyValues::saltGrams)

    companion object {
        fun aggregate(entries: List<DayEntry>): DayNutrition {
            fun total(key: NutrientKey, pick: (Macros) -> Double): NutrientTotal {
                val listed = entries.filter { key !in it.unlisted }
                return NutrientTotal(key, listed.sumOf { pick(it.macros) }, entries.size - listed.size, entries.size)
            }
            return DayNutrition(
                kcal = entries.sumOf { it.macros.kcal },
                protein = entries.sumOf { it.macros.protein },
                carbs = entries.sumOf { it.macros.carbs },
                fat = entries.sumOf { it.macros.fat },
                fiber = total(NutrientKey.FIBER) { it.fiber },
                sugar = total(NutrientKey.SUGAR) { it.sugar },
                sodium = total(NutrientKey.SODIUM) { it.sodium },
                saturatedFat = total(NutrientKey.SATURATED_FAT) { it.saturatedFat },
                entries = entries.size,
            )
        }
    }
}

/** Common reference limits for the optional nutrients (no personal target exists for them); the card labels them as such. */
object ReferenceLimits {
    const val FIBER_G = DailyValues.FIBER_G
    const val SATURATED_FAT_G = DailyValues.SATURATED_FAT_G
    const val SODIUM_MG = DailyValues.SODIUM_MG
    val SALT_G: Double = DailyValues.saltGrams(DailyValues.SODIUM_MG)
}
