package com.zeroindex.intake.domain.hydration

/**
 * What a drink is, and what it does to the day's water. Sources and reasoning are in
 * docs/HYDRATION.md; the short version:
 *
 * - The factors are "volume x factor = what counts toward the target". They follow the beverage
 *   hydration index (Maughan et al. 2016, Am J Clin Nutr), where tea, coffee, cola, sports drinks and
 *   lager did not differ from water, and milk and oral rehydration solutions held fluid longer. Where
 *   the study's number was higher than ours (juice, milk) we rounded down on purpose.
 * - Tea and coffee count in full. At normal intakes they hydrate about as well as water (Killer et al.
 *   2014, PLoS ONE); caffeine has only a small, short diuretic effect, and mostly at higher single doses.
 * - Wine and spirits were not tested in that study. Their numbers are our own estimates from the
 *   direct effect of alcohol on urine output, and every one of them can be edited.
 */
enum class DrinkType(
    val label: String,
    val defaultFactor: Double,
    /** Caffeine per 250 ml. Tea is black tea; green tea is nearer 28 and can be set in Advanced. */
    val caffeineMgPer250: Double,
    val abvPercent: Double,
) {
    WATER("Water", 1.0, 0.0, 0.0),
    SPARKLING("Sparkling water", 1.0, 0.0, 0.0),
    TEA("Tea", 1.0, 47.0, 0.0),
    COFFEE("Coffee", 1.0, 95.0, 0.0),
    MILK("Milk", 1.5, 0.0, 0.0),
    JUICE("Juice", 1.1, 0.0, 0.0),
    SODA("Soda", 1.0, 24.0, 0.0),
    SPORTS_DRINK("Sports drink", 1.1, 0.0, 0.0),
    ORS("Oral rehydration", 1.5, 0.0, 0.0),
    BEER("Beer", 0.9, 0.0, 5.0),
    WINE("Wine", 0.7, 0.0, 12.0),
    SPIRITS("Spirits", -0.4, 0.0, 40.0),
    CUSTOM("Custom", 1.0, 0.0, 0.0);

    val isAlcoholic: Boolean get() = abvPercent > 0.0
}

/** A user edit to one drink type. A null field means "use the default". */
data class DrinkOverride(
    val factor: Double? = null,
    val caffeineMgPer250: Double? = null,
    val abvPercent: Double? = null,
) {
    val isEmpty: Boolean get() = factor == null && caffeineMgPer250 == null && abvPercent == null
}

/** The numbers actually used for one drink type: the defaults with the user's edits applied. */
data class DrinkProfile(
    val type: DrinkType,
    val factor: Double,
    val caffeineMgPer250: Double,
    val abvPercent: Double,
)

object Drinks {
    /** Light beer at or under this strength counts like water. */
    const val LIGHT_BEER_MAX_ABV = 3.5

    /** Pure alcohol is 0.789 g/ml; a US standard drink is 14 g of it (about 17.7 ml). */
    const val ALCOHOL_G_PER_ML = 0.789
    const val STANDARD_DRINK_G = 14.0

    /**
     * The factor with no user edit. Beer depends on strength (about 1.0 when light, 0.9 otherwise) and
     * spirits are `-1 x the pure alcohol`, which is `-ABV` times the volume: a 44 ml shot at 40% is
     * -17.6 ml. The water in the spirit is deliberately not counted.
     */
    fun defaultFactor(type: DrinkType, abvPercent: Double): Double = when (type) {
        DrinkType.BEER -> if (abvPercent <= LIGHT_BEER_MAX_ABV) 1.0 else 0.9
        DrinkType.SPIRITS -> -(abvPercent / 100.0)
        else -> type.defaultFactor
    }

    fun profile(type: DrinkType, override: DrinkOverride? = null): DrinkProfile {
        val abv = (override?.abvPercent ?: type.abvPercent).coerceIn(0.0, 100.0)
        return DrinkProfile(
            type = type,
            factor = (override?.factor ?: defaultFactor(type, abv)).coerceIn(-1.0, 3.0),
            caffeineMgPer250 = (override?.caffeineMgPer250 ?: type.caffeineMgPer250).coerceIn(0.0, 1_000.0),
            abvPercent = abv,
        )
    }

    /** What one logged drink is worth, frozen at the moment it is logged. */
    fun entry(volumeMl: Double, profile: DrinkProfile): DrinkEntry = DrinkEntry(
        volumeMl = volumeMl,
        factor = profile.factor,
        caffeineMg = profile.caffeineMgPer250 * volumeMl / 250.0,
        alcoholMl = volumeMl * profile.abvPercent / 100.0,
    )

    fun standardDrinks(alcoholMl: Double): Double = alcoholMl * ALCOHOL_G_PER_ML / STANDARD_DRINK_G
}

/** One logged drink reduced to the numbers the day's maths needs. */
data class DrinkEntry(
    val volumeMl: Double,
    val factor: Double,
    val caffeineMg: Double,
    val alcoholMl: Double,
) {
    /** Can be negative (spirits). The day total is floored at zero, not each entry. */
    val effectiveMl: Double get() = volumeMl * factor
    val standardDrinks: Double get() = Drinks.standardDrinks(alcoholMl)
}

/** Stored as `TYPE=factor|caffeine|abv;TYPE=...` with an empty slot for "default". */
object DrinkOverrides {
    fun encode(map: Map<DrinkType, DrinkOverride>): String = map.entries
        .filter { !it.value.isEmpty }
        .joinToString(";") { (type, o) ->
            "${type.name}=${o.factor ?: ""}|${o.caffeineMgPer250 ?: ""}|${o.abvPercent ?: ""}"
        }

    fun decode(text: String?): Map<DrinkType, DrinkOverride> {
        if (text.isNullOrBlank()) return emptyMap()
        val out = linkedMapOf<DrinkType, DrinkOverride>()
        for (part in text.split(';')) {
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) continue
            val type = DrinkType.entries.firstOrNull { it.name == pieces[0] } ?: continue
            val v = pieces[1].split('|')
            val o = DrinkOverride(v.getOrNull(0)?.toDoubleOrNull(), v.getOrNull(1)?.toDoubleOrNull(), v.getOrNull(2)?.toDoubleOrNull())
            if (!o.isEmpty) out[type] = o
        }
        return out
    }
}
