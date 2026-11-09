package com.zeroindex.intake.domain.coach

import com.zeroindex.intake.domain.training.Prescription
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Applies the coach's temporary deload or easier day to a prescription the progression rules made. */
object CoachAdjustments {

    fun apply(p: Prescription, state: CoachState, nowMillis: Long, incrementKg: Double): Prescription {
        var out = p
        val deload = state.deloadUntilMillis?.takeIf { nowMillis < it }
        if (deload != null && p.weightKg > 0) {
            val inc = if (incrementKg > 0) incrementKg else 2.5
            val lighter = (p.weightKg * (100 - state.deloadPercent) / 100.0 / inc).roundToInt() * inc
            out = out.copy(
                weightKg = max(0.0, lighter),
                isDeload = true,
                reason = "${out.reason} Coach deload week: ${state.deloadPercent}% lighter.",
            )
        }
        val easy = state.easyUntilMillis?.takeIf { nowMillis < it }
        if (easy != null && out.sets > 1) {
            out = out.copy(
                sets = max(1, out.sets - state.easySetsLess),
                reason = "${out.reason} Easier day from your coach: ${state.easySetsLess} set fewer.",
            )
        }
        return out
    }

    /** Hands out [delta] sets over exercises, one at a time, within [MIN_SETS]..[MAX_SETS]. */
    fun distributeSets(current: List<Pair<String, Int>>, delta: Int): Map<String, Int> {
        val sets = current.toMap().toMutableMap()
        if (sets.isEmpty() || delta == 0) return sets
        val order = current.map { it.first }
        val step = if (delta > 0) 1 else -1
        var left = abs(delta)
        while (left > 0) {
            val movable = order.filter { sets.getValue(it) + step in MIN_SETS..MAX_SETS }
            if (movable.isEmpty()) break
            // Add to the exercise with the fewest sets, take from the one with the most; ties keep list order.
            val pick = if (step > 0) movable.minByOrNull { sets.getValue(it) }!! else movable.maxByOrNull { sets.getValue(it) }!!
            sets[pick] = sets.getValue(pick) + step
            left--
        }
        return sets
    }

    const val MIN_SETS = 2
    const val MAX_SETS = 8
}

/** When the weekly check-in is due. */
object CoachSchedule {

    /** The weekday (ISO 1..7) to use: the person's choice, else the baseline's start day, else Monday. */
    fun weekday(chosen: Int?, baselineStartedWeekday: Int?): Int =
        (chosen ?: baselineStartedWeekday ?: DayOfWeek.MONDAY.value).coerceIn(1, 7)

    /** The latest day (epoch day) up to and including [today] that falls on [isoWeekday]. */
    fun latestScheduledDay(today: Long, isoWeekday: Int): Long {
        val todayIso = LocalDate.ofEpochDay(today).dayOfWeek.value
        val back = ((todayIso - isoWeekday) % 7 + 7) % 7
        return today - back
    }

    /** Due when no check-in has happened since the latest scheduled day. */
    fun isDue(today: Long, isoWeekday: Int, lastCheckInDay: Long?): Boolean {
        val scheduled = latestScheduledDay(today, isoWeekday)
        return lastCheckInDay == null || lastCheckInDay < scheduled
    }
}

/** Which earlier suggestions stop a new one from appearing. */
object SuggestionHistory {

    data class Decided(val key: String, val status: SuggestionStatus, val decidedDay: Long)

    fun suppressedKeys(history: List<Decided>, today: Long, c: CoachConfig = CoachConfig()): Set<String> =
        history.filter { it.status != SuggestionStatus.PENDING && today - it.decidedDay < c.repeatSuppressDays }
            .map { it.key }.toSet()

    /** An applied calorie change keeps the trend rule quiet while the trend catches up. */
    fun calorieCooldown(history: List<Decided>, today: Long, c: CoachConfig = CoachConfig()): Boolean =
        history.any {
            it.status == SuggestionStatus.APPLIED && it.key.startsWith("cal:") && today - it.decidedDay < c.calorieCooldownDays
        }
}

/**
 * Picks high-protein ideas. The caller says which foods the allergen engine accepts; nothing here
 * decides allergen safety.
 */
object ProteinIdeas {

    data class Food(val name: String, val proteinPer100g: Double, val kcalPer100g: Double, val servingGrams: Double)

    /** Whole foods the offline database search falls back to. */
    val FALLBACK_QUERIES = listOf("chicken breast", "eggs", "greek yogurt", "tuna", "lentils", "cottage cheese", "tofu", "turkey breast")

    /** At least 8 g protein per 100 g and at least 30% of calories from protein. */
    fun qualifies(f: Food): Boolean = f.proteinPer100g >= 8.0 && f.kcalPer100g > 0 && f.proteinPer100g * 4 / f.kcalPer100g >= 0.3

    fun describe(f: Food): String {
        val g = f.servingGrams.takeIf { it > 0 } ?: 100.0
        return "${f.name} (${(f.proteinPer100g * g / 100.0).roundToInt()} g protein in ${g.roundToInt()} g)"
    }

    fun pick(candidates: List<Food>, max: Int = 3): List<String> =
        candidates.filter(::qualifies).sortedByDescending { it.proteinPer100g * 4 / it.kcalPer100g }
            .distinctBy { it.name.lowercase() }.take(max).map(::describe)
}

/** The AI reword may change wording only. A rewrite is refused unless it keeps every number. */
object CoachReword {

    private val number = Regex("\\d+(?:[.,]\\d+)?")

    fun numbers(text: String): List<String> = number.findAll(text).map { it.value.replace(',', '.') }.sorted().toList()

    fun keepsNumbers(original: String, reworded: String): Boolean = numbers(original) == numbers(reworded)

    /** Original text unless the rewrite is non-empty, short enough and has exactly the same numbers. */
    fun accept(original: String, reworded: String?, maxLength: Int = 400): String {
        val r = reworded?.trim().orEmpty()
        return if (r.isNotEmpty() && r.length <= maxLength && keepsNumbers(original, r)) r else original
    }
}
